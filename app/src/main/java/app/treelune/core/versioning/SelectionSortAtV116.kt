package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the stored selections and readings to their v116 form (docs/design/entry-sort-limit.md):
 *
 * - a selection of a tool or a variable's series says its order, `sort`, which was implied: the
 *   newest first, by the entry's date (`timestamp`), or by its creation (`created_at`) for a tool
 *   whose entries have none (a table, a chart); a Task's list input (`steps` › `inputs`) kept the
 *   order the entries happened in, the oldest first, which it keeps;
 * - a Session's media (`media_source`) takes its order from `media_sort`, which goes, or the
 *   newest first when it had none;
 * - a reading's reduction LAST is FIRST, the first entry of its selection's order; EARLIEST and
 *   LATEST are MIN and MAX, the same computation said once — in a Session started too, whose run
 *   (`state` › `run`, a JSON written as a text) holds its steps' readings.
 *
 * A selection is found by its key: `selection` wherever it is, `media_source` of a Sequence's
 * config, `source` of a Flashcards deck's. A trigger's (`entries`) has no order and is left as it
 * is; a selection of a zone or of one entry too. Frozen: the tool types without an entry date are
 * those of v116, never read from the current code.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object SelectionSortAtV116 {

    /** The tool types whose entries have no date at v116. */
    private val UNDATED = setOf("structured", "chart")

    private val REDUCTIONS = mapOf("LAST" to "FIRST", "EARLIEST" to "MIN", "LATEST" to "MAX")

    /**
     * The tool types of the tools [json] may name, by id, for the default of a selection; a tool
     * not found (deleted) gets the date's order, the one most tools have.
     */
    class Tools(private val tooltypes: Map<String, String>) {
        fun dated(id: String?): Boolean = tooltypes[id] !in UNDATED
    }

    /** A key of a sort, `{"field", "direction"}`. */
    private fun key(field: String, ascending: Boolean) = JSONObject().put("field", field).put("direction", if (ascending) "asc" else "desc")

    /**
     * [selection] given its order when it has none, in place; whether it changed. [ascending] for
     * the order a Task's list input read in.
     */
    private fun selection(selection: JSONObject, tools: Tools, ascending: Boolean): Boolean {
        if ((selection.optJSONArray("sort")?.length() ?: 0) > 0) return false
        val target = selection.optJSONObject("target") ?: return false
        val field = when (target.optString("kind")) {
            "TOOL_INSTANCE" -> if (tools.dated(target.optString("id"))) "timestamp" else "created_at"
            "VARIABLE" -> "timestamp"
            else -> return false
        }
        selection.put("sort", JSONArray().put(key(field, ascending)))
        return true
    }

    /**
     * [json] walked whole, in place: each selection under `selection`, each reduction renamed;
     * [ascending] under a Task's `inputs`. Whether anything changed.
     */
    private fun walk(json: Any?, tools: Tools, ascending: Boolean = false): Boolean {
        var changed = false
        when (json) {
            is JSONObject -> for (name in json.keys().asSequence().toList()) {
                val child = json.opt(name)
                if (name == "reduction" && child is String) REDUCTIONS[child]?.let { json.put(name, it); changed = true }
                if (name == "selection" && child is JSONObject) changed = selection(child, tools, ascending) || changed
                changed = walk(child, tools, ascending || name == "inputs") || changed
            }
            is JSONArray -> for (i in 0 until json.length()) changed = walk(json.opt(i), tools, ascending) || changed
        }
        return changed
    }

    /** A tool's config (its JSON) at v116, null when nothing changed. */
    fun config(tooltype: String, json: String, tools: Tools): String? {
        val config = JSONObject(json)
        var changed = walk(config, tools)
        when (tooltype) {
            "sequence" -> config.optJSONObject("media_source")?.let { source ->
                config.optJSONArray("media_sort")?.takeIf { it.length() > 0 }?.let { if (!source.has("sort")) source.put("sort", it) }
                changed = selection(source, tools, ascending = false) || changed
            }
            "flashcards" -> config.optJSONObject("source")?.let { changed = selection(it, tools, ascending = false) || changed }
        }
        if (config.has("media_sort")) { config.remove("media_sort"); changed = true }
        return if (changed) config.toString() else null
    }

    /** Any other JSON that holds readings and pointers (a Task's steps and triggers, a routine's triggers, a readout's settings, a variable's definition), null when nothing changed. */
    fun json(json: String, tools: Tools): String? {
        val value = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json)
        return if (walk(value, tools)) value.toString() else null
    }

    /**
     * A message's rich content at v116, null when nothing changed: each POINTER segment's config,
     * a JSON written as a text, its selection given its order.
     */
    fun richContent(json: String, tools: Tools): String? {
        val content = JSONObject(json)
        val segments = content.optJSONArray("segments") ?: return null
        var changed = false
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONObject(i) ?: continue
            if (segment.optString("type") != "enrichment" || segment.optString("enrichment_type") != "POINTER") continue
            val config = segment.optString("config")
            if (config.isEmpty()) continue
            json(config, tools)?.let { segment.put("config", it); changed = true }
        }
        return if (changed) content.toString() else null
    }

    /**
     * A Session's entry state (its JSON) at v116, null when nothing changed: its run, a JSON
     * written as a text, its steps' readings renamed.
     */
    fun entryState(json: String, tools: Tools): String? {
        val state = JSONObject(json)
        val run = state.opt("run") as? String ?: return null
        val next = json(run, tools) ?: return null
        return state.put("run", next).toString()
    }

    /** The JSON columns of each table that hold selections or readings, outside tool configs. */
    val COLUMNS = mapOf(
        "tasks" to listOf("steps_json", "triggers_json"),
        "routines" to listOf("triggers_json"),
        "readouts" to listOf("settings_json"),
        "variables" to listOf("definition_json")
    )

    /** A row (column -> value) of [table] at v116: a tool's config, or the JSON columns of [COLUMNS]. */
    fun row(table: String, row: JSONObject, tools: Tools): JSONObject {
        if (table == "tool_instances") {
            row.optString("config_json").takeIf { it.isNotEmpty() && !row.isNull("config_json") }
                ?.let { json -> config(row.optString("tooltype"), json, tools)?.let { row.put("config_json", it) } }
        }
        COLUMNS[table]?.forEach { column ->
            row.optString(column).takeIf { it.isNotEmpty() && !row.isNull(column) }?.let { json -> json(json, tools)?.let { row.put(column, it) } }
        }
        if (table == "tool_data" && row.optString("tooltype") == "sequence") {
            row.optString("state").takeIf { it.isNotEmpty() && !row.isNull("state") }
                ?.let { json -> entryState(json, tools)?.let { row.put("state", it) } }
        }
        if (table == "session_messages") {
            row.optString("rich_content_json").takeIf { it.isNotEmpty() && !row.isNull("rich_content_json") }
                ?.let { json -> richContent(json, tools)?.let { row.put("rich_content_json", it) } }
        }
        return row
    }

    /** Rewrites the backup document's tables in place. */
    fun backup(data: JSONObject) {
        val tools = Tools(buildMap {
            data.optJSONArray("tool_instances")?.let { array ->
                for (i in 0 until array.length()) array.optJSONObject(i)?.let { put(it.optString("id"), it.optString("tooltype")) }
            }
        })
        (COLUMNS.keys + "tool_instances" + "session_messages" + "tool_data").forEach { table ->
            val rows = data.optJSONArray(table) ?: return@forEach
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { row(table, it, tools) }
        }
    }
}
