package app.treelune.core.views

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.EntryFilters
import app.treelune.core.fields.EntrySort
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FilterTerms
import app.treelune.core.fields.SortKey
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.services.ToolDataEntries
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.variables.VariableSeries
import org.json.JSONArray
import org.json.JSONObject

/**
 * The views of the app, found by a selection's target and its `view` (View): a variable's series,
 * [SERIES]; a tool's, by its type's declaration (ToolTypeContract.views).
 */
object Views {

    /** A variable's series, the one view a variable has (VariableSeries). */
    const val SERIES = "series"

    /**
     * The view [name] of [target].
     *
     * @throws IllegalStateException when the target has no view of that name, is gone, or the
     *   view cannot be opened, with what to say
     */
    suspend fun of(target: Reference, name: String, context: Context, s: StringsContext): View = when (target.kind) {
        ReferenceKind.VARIABLE -> {
            if (name != SERIES) throw IllegalStateException(s.shared("selection_problem_view_unknown").format(name, SERIES))
            VariableSeries.view(context, target.id!!, s)
        }
        ReferenceKind.TOOL_INSTANCE -> {
            val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(target.id!!)
                ?: throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
            val declared = ToolTypeManager.getToolType(tool.tooltype)?.views().orEmpty()
            val view = declared.firstOrNull { it.name == name }
                ?: throw IllegalStateException(s.shared("selection_problem_view_unknown").format(name, declared.joinToString(", ") { it.name }.ifEmpty { "-" }))
            view.open(context, tool.id, JSONObject(tool.config_json), s)
        }
        else -> throw IllegalStateException(s.shared("selection_problem_view_target").format(target.kind.name))
    }

    /** The default order of the view [name] of [target]; null when it has none of that name, which its reading then refuses. */
    suspend fun defaultSort(target: Reference, name: String, context: Context): List<SortKey>? = when (target.kind) {
        ReferenceKind.VARIABLE -> if (name == SERIES) VariableSeries.DEFAULT_SORT else null
        ReferenceKind.TOOL_INSTANCE -> AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(target.id!!)
            ?.let { ToolTypeManager.getToolType(it.tooltype) }?.views()?.firstOrNull { it.name == name }?.defaultSort
        else -> null
    }

    /**
     * The row [reference] designates (a ROW: its thing, view and row), as its view gives it now;
     * null when its thing is gone or its view no longer gives it. The thing is a variable for its
     * series, a tool for any other view. A series' step is never designated: its rows need a period.
     *
     * @throws IllegalStateException when the view cannot be opened or read
     */
    suspend fun row(reference: Reference, context: Context, s: StringsContext): ToolDataEntity? {
        val view = reference.view ?: return null
        if (view == SERIES) throw IllegalStateException(s.shared("reference_row_series"))
        val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(reference.id ?: return null) ?: return null
        return of(Reference(ReferenceKind.TOOL_INSTANCE, tool.id), view, context, s).rows(null, null, System.currentTimeMillis())
            .firstOrNull { it.id == reference.row }
    }

    /** The names of the views of the tool type [tooltype], for a selector to offer. */
    fun namesOf(tooltype: String): List<String> = ToolTypeManager.getToolType(tooltype)?.views()?.map { it.name }.orEmpty()
}

/**
 * A view's rows as a selection reads them: computed at the instant read, kept by its filters —
 * judged by the SQL that judges a tool's entries, so that a filter means the same on a view as on
 * a tool —, in its sort, the first of its limit.
 */
object ViewRows {

    /**
     * The rows of [view] [selection] reads at [at], its period bounds [start] to [end], kept by
     * the stored [filters] (EntrySelection.storedFilters, the period's bounds among them), as
     * entries are given (ToolDataEntries.toMap). [sourceId] stands for the tool the rows are
     * judged as rows of.
     *
     * @throws IllegalStateException when they cannot be computed, or the filters do not read
     */
    suspend fun read(
        context: Context,
        view: View,
        sourceId: String,
        selection: EntrySelection,
        start: Long?,
        end: Long?,
        filters: JSONArray,
        at: Long,
        s: StringsContext
    ): List<Map<String, Any?>> {
        val rows = view.rows(start, end, at)
        val kept = if (rows.isEmpty() || filters.length() == 0) rows else filtered(context, sourceId, rows, view.fields, filters, at, s)
        val ordered = EntrySort.ordered(kept.map { ToolDataEntries.toMap(it) }, selection.sort, view.fields)
        return selection.limit?.let { ordered.take(it) } ?: ordered
    }

    /**
     * [rows] [filters] keep, judged by the SQL that judges a tool's entries (EntryFilters), the
     * rows standing for the table (`WITH tool_data`), as the triggers judge an entry's copy
     * (EntryWrites.passes).
     */
    private suspend fun filtered(
        context: Context,
        sourceId: String,
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
        // Every row judged as one of the same tool
        val select = EntryFilters.select(sourceId, parsed, fields, null, 0)
        val dao = AppDatabase.getDatabase(context).toolDataDao()
        // In slices, the arguments of one query being bounded
        return rows.map { it.copy(toolInstanceId = sourceId) }.chunked(ROWS_PER_QUERY).flatMap { slice ->
            val table = slice.joinToString(" UNION ALL ") { "SELECT ${COLUMNS.joinToString { column -> "? AS `$column`" }}" }
            val args = slice.flatMap { listOf(it.id, it.toolInstanceId, it.tooltype, it.timestamp, it.name, it.data, it.createdAt, it.updatedAt, it.extra, it.state) } + select.args
            dao.getFiltered(SimpleSQLiteQuery("WITH tool_data AS ($table) " + select.clause, args.toTypedArray()))
        }
    }

    /** The columns of tool_data a row fills, in the order [filtered] binds them. */
    private val COLUMNS = listOf("id", "tool_instance_id", "tooltype", "timestamp", "name", "data", "created_at", "updated_at", "extra", "state")

    /** Rows per query: ten arguments each, far under SQLite's bound. */
    private const val ROWS_PER_QUERY = 500
}
