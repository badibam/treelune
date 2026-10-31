package app.treelune.core.variables

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.EntryFilters
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FilterTerms
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimeResolver
import app.treelune.core.selection.normalizeTimestampWithConfig
import app.treelune.core.services.ToolDataEntries
import app.treelune.core.strings.StringsContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * A variable's series (docs/design/variable-series.md): a value per step of its unit, the
 * variable computed at the step's end — its last millisecond, as every end of a period in the
 * app — or at the instant read for the step holding it; read as a tool's entries are, one entry
 * per step. Nothing is stored: each reading computes the steps it needs again, so a correction of
 * what the variable reads corrects its past.
 *
 * An entry of a step holds `timestamp`, the instant its value is computed at, inside its step,
 * and `data.value`, the value; a step without a value (a term without an entry) has no entry.
 */
object VariableSeries {

    /** The path of a step's value in its entry. */
    const val VALUE = "data.value"

    /** The tool type an entry of a series says it is of: no tool type, a variable. */
    const val TOOLTYPE = "variable"

    /**
     * The fields of a series' entries, by path, as a tool's are (EntryFilters.filterableFields):
     * the instant of its step, and its value, of the variable's own type, under [label].
     */
    fun fields(definition: VariableDefinition, label: String, text: (String) -> String): Map<String, FieldDefinition> = mapOf(
        "timestamp" to CoreFields.timestamp(text),
        VALUE to definition.field.copy(name = "value", displayName = label)
    )

    /**
     * The instants a series of [unit] is computed at between [start] and [end] read at [at]: each
     * step's last millisecond, from the step holding [start] to the one holding the earlier of
     * [end] and [at]; the step holding [at] is computed at [at]. None when [start] comes after.
     */
    fun instants(start: Long, end: Long?, at: Long, unit: PeriodType, calendar: TimeResolver): List<Long> {
        val last = minOf(end ?: at, at)
        val instants = mutableListOf<Long>()
        var step = stepStart(start, unit, calendar)
        while (step <= last) {
            val next = nextStart(step, unit, calendar)
            instants.add(minOf(next - 1, at))
            step = next
        }
        return instants
    }

    /** The start of the step of [unit] holding [millis], as the user's [calendar] cuts it. */
    fun stepStart(millis: Long, unit: PeriodType, calendar: TimeResolver): Long =
        normalizeTimestampWithConfig(millis, unit, calendar.dayStartHour, calendar.weekStartDay, calendar.zone)

    /** The start of the step after the one starting at [start]. */
    private fun nextStart(start: Long, unit: PeriodType, calendar: TimeResolver): Long {
        val date = Instant.ofEpochMilli(start).atZone(calendar.zone)
        val next = when (unit) {
            PeriodType.HOUR -> date.plusHours(1)
            PeriodType.DAY -> date.plusDays(1)
            PeriodType.WEEK -> date.plusWeeks(1)
            PeriodType.MONTH -> date.plusMonths(1)
            PeriodType.YEAR -> date.plusYears(1)
        }
        // Cut again: a change of hour moves the day's start, which the step keeps on the calendar
        return stepStart(next.toInstant().toEpochMilli(), unit, calendar)
    }

    /** The entry of the step computed at [instant], [value] in [variableId]'s series. */
    fun entry(variableId: String, instant: Long, value: Any): ToolDataEntity = ToolDataEntity(
        id = "$variableId@$instant",
        toolInstanceId = variableId,
        tooltype = TOOLTYPE,
        timestamp = instant,
        name = null,
        data = JSONObject().put("value", value).toString(),
        createdAt = instant,
        updatedAt = instant,
        extra = null,
        state = null
    )

    /**
     * The entries of [variableId]'s series of [unit] in the period [start] to [end] read at [at],
     * those [filters] keep (stored as a tool's are, EntrySelection.storedFilters), newest first as
     * tool_data.get gives a tool's.
     *
     * @throws IllegalStateException when the variable cannot be computed, or its filters read
     */
    suspend fun entries(
        context: Context,
        variableId: String,
        unit: PeriodType,
        fields: Map<String, FieldDefinition>,
        start: Long,
        end: Long?,
        at: Long,
        filters: JSONArray,
        s: StringsContext
    ): List<Map<String, Any?>> {
        val instants = instants(start, end, at, unit, TimeResolver.at(at))
        if (instants.isEmpty()) return emptyList()
        val result = Coordinator(context).processUserAction("variables.evaluate", mapOf("variable_id" to variableId, "at" to instants))
        if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
        // A step without a value has no entry, as a day nothing was noted on
        val rows = (result.data?.get("values") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { step ->
            val value = step["value"] ?: return@mapNotNull null
            entry(variableId, (step["at"] as Number).toLong(), value)
        }.sortedByDescending { it.timestamp }
        if (rows.isEmpty() || filters.length() == 0) return rows.map { ToolDataEntries.toMap(it) }
        return filtered(context, variableId, rows, fields, filters, at, s).map { ToolDataEntries.toMap(it) }
    }

    /**
     * [rows] [filters] keep, judged by the SQL that judges a tool's entries (EntryFilters), the
     * rows standing for the table (`WITH tool_data`), as the triggers judge an entry's copy
     * (EntryWrites.passes): a filter means the same on a series as on a tool.
     */
    private suspend fun filtered(
        context: Context,
        variableId: String,
        rows: List<ToolDataEntity>,
        fields: Map<String, FieldDefinition>,
        filters: JSONArray,
        at: Long,
        s: StringsContext
    ): List<ToolDataEntity> {
        val given = FilterTerms.resolved(filters, at, context) { s.shared(it) }
        val parsed = when (val read = EntryFilters.parse(given, fields) { s.shared(it) }) {
            is EntryFilters.Parsed.Refused -> throw IllegalStateException(read.error)
            is EntryFilters.Parsed.Ready -> read.filters
        }
        val select = EntryFilters.select(variableId, parsed, fields, null, 0)
        val dao = AppDatabase.getDatabase(context).toolDataDao()
        // In slices, the arguments of one query being bounded
        return rows.chunked(ROWS_PER_QUERY).flatMap { slice ->
            val table = slice.joinToString(" UNION ALL ") { "SELECT ${COLUMNS.joinToString { column -> "? AS `$column`" }}" }
            val args = slice.flatMap { listOf(it.id, it.toolInstanceId, it.tooltype, it.timestamp, it.name, it.data, it.createdAt, it.updatedAt, it.extra, it.state) } + select.args
            dao.getFiltered(SimpleSQLiteQuery("WITH tool_data AS ($table) " + select.clause, args.toTypedArray()))
        }.sortedByDescending { it.timestamp }
    }

    /** The columns of tool_data an entry of a series fills, in the order [filtered] binds them. */
    private val COLUMNS = listOf("id", "tool_instance_id", "tooltype", "timestamp", "name", "data", "created_at", "updated_at", "extra", "state")

    /** Rows per query: ten arguments each, far under SQLite's bound. */
    private const val ROWS_PER_QUERY = 500
}
