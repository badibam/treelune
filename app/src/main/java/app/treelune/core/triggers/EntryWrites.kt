package app.treelune.core.triggers

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.EntryFilters
import app.treelune.core.fields.ToolFields
import app.treelune.core.history.CoveredTable
import app.treelune.core.strings.Strings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The entries a step of the history wrote (docs/DATA.md, « History of writes »), read from its
 * copies: what a trigger on an entry written listens to. A change the history does not keep (a
 * part the app alone writes, a reminder marked sent) is not one.
 */
object EntryWrites {

    /**
     * An entry written: [row], its copy as written, or as it was for a deletion; [write], what
     * became of it over the whole step — created then changed is created.
     */
    data class EntryWrite(val entryId: String, val toolInstanceId: String, val write: Triggers.Write, val row: JSONObject)

    /**
     * What the step [stepId] wrote of the entries, one per entry, in the order they were first
     * touched; [undone] reads it backwards, as its undo writes it.
     */
    suspend fun ofStep(context: Context, stepId: String, undone: Boolean = false): List<EntryWrite> {
        val rows = AppDatabase.getDatabase(context).historyDao().getRows(stepId).filter { it.tableName == CoveredTable.TOOL_DATA.tableName }
        return rows.groupBy { it.rowId }.mapNotNull { (id, touched) ->
            val first = touched.first().before?.let { JSONObject(it) }
            val last = touched.last().after?.let { JSONObject(it) }
            val (before, after) = if (undone) last to first else first to last
            when {
                before == null && after == null -> null
                before == null -> EntryWrite(id, after!!.getString("tool_instance_id"), Triggers.Write.CREATED, after)
                after == null -> EntryWrite(id, before.getString("tool_instance_id"), Triggers.Write.DELETED, before)
                else -> EntryWrite(id, after.getString("tool_instance_id"), Triggers.Write.UPDATED, after)
            }
        }
    }

    /**
     * Whether [write]'s entry passes [filters], judged on its copy as SQL judges the tool's
     * entries (EntryFilters): the copy stands for the table, so a deleted entry is judged as it
     * was. A filter compared with a variable or a reading reads it now (FilterTerms). Filters that
     * no longer read (a field removed since, a term without a value) pass nothing.
     */
    suspend fun passes(context: Context, write: EntryWrite, filters: JSONArray): Boolean {
        if (filters.length() == 0) return true
        val s = Strings.`for`(context = context)
        val fields = ToolFields.filterable(write.toolInstanceId, context, s)
        val given = try {
            app.treelune.core.fields.FilterTerms.resolved(filters, System.currentTimeMillis(), context) { s.shared(it) }
        } catch (e: IllegalStateException) {
            app.treelune.core.utils.LogManager.service("Trigger filters on ${write.toolInstanceId} not read: ${e.message}", "WARN")
            return false
        }
        val parsed = EntryFilters.parse(given, fields) { s.shared(it) } as? EntryFilters.Parsed.Ready ?: return false
        val count = EntryFilters.count(write.toolInstanceId, parsed.filters, fields)
        val columns = write.row.keys().asSequence().toList()
        // The copy, under the table's name, which the query reads in its place
        val copy = "WITH tool_data AS (SELECT ${columns.joinToString { "? AS `$it`" }}) "
        val args = columns.map { write.row.opt(it).takeUnless { value -> value == JSONObject.NULL } } + count.args
        return AppDatabase.getDatabase(context).toolDataDao().countFiltered(SimpleSQLiteQuery(copy + count.clause, args.toTypedArray())) > 0
    }
}
