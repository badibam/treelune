package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the stored selections to their v121 form (docs/BRICKS.md, « View »): a variable's series
 * is a view of the variable, which a selection names (`"view": "series"`) beside its target, the
 * variable. Before, a selection of a variable stood for its series by its target alone.
 *
 * A selection is found by its key, `selection`, wherever it is: only a reading selects a variable,
 * in a tool's config (a chart's layer, a goal's criterion), a variable's term, a readout's value, a
 * Task's step or a routine's trigger. Frozen: the name is the one of v121.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object SeriesViewAtV121 {

    /** [selection] naming its view when it selects a variable without one, in place; whether it changed. */
    private fun selection(selection: JSONObject): Boolean {
        if (selection.optJSONObject("target")?.optString("kind") != "VARIABLE") return false
        if (selection.optString("view").isNotEmpty()) return false
        selection.put("view", "series")
        return true
    }

    /** [json] walked whole, in place: each selection under `selection`. Whether anything changed. */
    private fun walk(json: Any?): Boolean {
        var changed = false
        when (json) {
            is JSONObject -> for (name in json.keys().asSequence().toList()) {
                val child = json.opt(name)
                if (name == "selection" && child is JSONObject) changed = selection(child) || changed
                changed = walk(child) || changed
            }
            is JSONArray -> for (i in 0 until json.length()) changed = walk(json.opt(i)) || changed
        }
        return changed
    }

    /** A JSON that may hold readings (a tool's config, a Task's steps…), null when nothing changed. */
    fun json(json: String): String? {
        val value = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
        return if (walk(value)) value.toString() else null
    }

    /** The JSON columns of each table that hold readings. */
    val COLUMNS = mapOf(
        "tool_instances" to listOf("config_json"),
        "tasks" to listOf("steps_json", "triggers_json"),
        "routines" to listOf("triggers_json"),
        "readouts" to listOf("settings_json"),
        "variables" to listOf("definition_json")
    )

    /** A row (column -> value) of [table] at v121: the JSON columns of [COLUMNS]. */
    fun row(table: String, row: JSONObject): JSONObject {
        COLUMNS[table]?.forEach { column ->
            row.optString(column).takeIf { it.isNotEmpty() && !row.isNull(column) }?.let { json -> json(json)?.let { row.put(column, it) } }
        }
        return row
    }

    /** Rewrites the backup document's tables in place. */
    fun backup(data: JSONObject) {
        COLUMNS.keys.forEach { table ->
            val rows = data.optJSONArray(table) ?: return@forEach
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { row(table, it) }
        }
    }
}
