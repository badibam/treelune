package app.treelune.core.selection

import android.content.Context
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.EntrySort
import app.treelune.core.fields.SortKey
import app.treelune.core.tools.ToolTypeManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The order a selection is written with when none is given (docs/BRICKS.md, « Entry selection »):
 * the newest first, by the entry's date (`timestamp`), or by its creation (`created_at`) for a
 * tool whose entries have no date; a variable's series by its steps' date. The one place this
 * default is said: the selector writes it into a new selection, the migration into the stored
 * ones, and a selection written without a sort (by the AI, in the app or through the connector)
 * gets it once, at its writing, before it is stored — the sort is then explicit like any other.
 *
 * A stored selection is found by the key it is kept under: `selection` wherever it is (a
 * message's pointer, a reading, a Task's input, a chart's layer), and the keys of a tool config's
 * selections that read in order (SettingNode.Selection, `ordered`). A trigger's (`entries`) is
 * not one: it judges one entry, with no order.
 */
object SelectionOrder {

    const val SELECTION = "selection"

    /** The newest first, for a tool whose entries are [declared]. */
    fun defaultSort(declared: EntryFields): List<SortKey> = defaultSort(dated = declared.timestamp != CoreFieldUsage.ABSENT)

    /** The newest first: by the entry's date when its entries have one ([dated]), by its creation otherwise. */
    fun defaultSort(dated: Boolean): List<SortKey> = listOf(SortKey(if (dated) "timestamp" else "created_at", ascending = false))

    /** The newest first, for a variable's series: its steps' date. */
    val SERIES_DEFAULT: List<SortKey> = listOf(SortKey("timestamp", ascending = false))

    /**
     * The default sort of [target]'s entries; null where there is no order to read (one entry, a
     * zone) or the tool is gone, whose selection the reading then refuses as it is.
     */
    suspend fun defaultFor(target: Reference, context: Context): List<SortKey>? = when (target.kind) {
        ReferenceKind.VARIABLE -> SERIES_DEFAULT
        ReferenceKind.TOOL_INSTANCE -> {
            val tool = app.treelune.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(target.id!!)
            val type = tool?.let { ToolTypeManager.getToolType(it.tooltype) }
            if (tool == null || type == null) null else defaultSort(type.getEntryFields(JSONObject(tool.config_json), context))
        }
        else -> null
    }

    /**
     * Writes its default sort into each selection of [json] that has none, in place: under
     * `selection` at any depth, and under the top-level [keys] (a tool config's ordered
     * selections). Whether it wrote one.
     */
    suspend fun complete(json: Any?, context: Context, keys: Set<String> = emptySet()): Boolean {
        var wrote = false
        suspend fun visit(value: Any?, top: Boolean) {
            when (value) {
                is JSONObject -> for (key in value.keys().asSequence().toList()) {
                    val child = value.opt(key)
                    if ((key == SELECTION || (top && key in keys)) && child is JSONObject) wrote = completeOne(child, context) || wrote
                    visit(child, false)
                }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i), false)
            }
        }
        visit(json, true)
        return wrote
    }

    /** [selection] given its default sort when it has none and reads in order; whether it wrote. */
    private suspend fun completeOne(selection: JSONObject, context: Context): Boolean {
        if ((selection.optJSONArray("sort")?.length() ?: 0) > 0) return false
        // A target that does not read is left for the writing's own check to refuse
        val target = Reference.fromJson(selection.optJSONObject("target") ?: return false)
        val sort = defaultFor(target, context) ?: return false
        selection.put("sort", EntrySort.toJson(sort))
        return true
    }
}
