package app.treelune.core.variables

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.CoreFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimeResolver
import app.treelune.core.selection.normalizeTimestampWithConfig
import app.treelune.core.strings.StringsContext
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

    /** The newest first, a series' default order: its steps' date (SelectionOrder). */
    val DEFAULT_SORT: List<app.treelune.core.fields.SortKey> = listOf(app.treelune.core.fields.SortKey("timestamp", ascending = false))

    /**
     * The series of the variable [variableId] as a view (core/views, its name Views.SERIES): its
     * steps' fields, a period needed with a start, its rows the steps of that period computed at
     * the instant read, a step without a value having no row, as a day nothing was noted on.
     *
     * @throws IllegalStateException when the variable is gone, does not read, or has no series
     */
    suspend fun view(context: Context, variableId: String, s: StringsContext): app.treelune.core.views.View {
        val variable = AppDatabase.getDatabase(context).variableDao().getById(variableId)
            ?: throw IllegalStateException(s.shared("variable_error_not_found").format(variableId))
        val definition = try {
            VariableDefinition.fromJson(JSONObject(variable.definitionJson), variable.name) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(e.message ?: "")
        }
        val unit = definition.series ?: throw IllegalStateException(s.shared("variable_error_no_series").format(variable.label))
        val fields = fields(definition, variable.label) { s.shared(it) }
        return object : app.treelune.core.views.View {
            override val fields = fields
            override val period = app.treelune.core.views.ViewPeriod.NEEDS_START
            override suspend fun rows(start: Long?, end: Long?, at: Long): List<ToolDataEntity> {
                val instants = instants(start!!, end, at, unit, TimeResolver.at(at))
                if (instants.isEmpty()) return emptyList()
                val result = Coordinator(context).processUserAction("variables.evaluate", mapOf("variable_id" to variableId, "at" to instants))
                if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
                return (result.data?.get("values") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { step ->
                    val value = step["value"] ?: return@mapNotNull null
                    entry(variableId, (step["at"] as Number).toLong(), value)
                }
            }
        }
    }
}
