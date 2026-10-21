package app.treelune.core.backup

import app.treelune.core.grid.Groups
import app.treelune.core.grid.ToolPositions
import org.json.JSONArray
import org.json.JSONObject

/**
 * What is ticked of the app to share (docs/DATA.md, « A part of the app »): zones, and in them tools — each with
 * its entries or without —, variables and routines. An element is kept only with its zone; a
 * zone may be kept alone, empty.
 */
data class PartSelection(
    val zones: Set<String>,
    /** Each tool kept, with whether its entries go with it */
    val tools: Map<String, Boolean>,
    val variables: Set<String>,
    val automations: Set<String>
) {
    /** The stored form: `{"zones": [id], "tools": [{"id", "entries"}], "variables": [id], "automations": [id]}`. */
    fun toJson(): JSONObject = JSONObject()
        .put("zones", JSONArray(zones.toList()))
        .put("tools", JSONArray(tools.map { (id, entries) -> JSONObject().put("id", id).put("entries", entries) }))
        .put("variables", JSONArray(variables.toList()))
        .put("automations", JSONArray(automations.toList()))

    companion object {
        /** The selection stored as [json]; a key missing or of the wrong form throws (org.json's JSONException). */
        fun read(json: JSONObject): PartSelection {
            fun ids(key: String): Set<String> = json.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
            val tools = json.getJSONArray("tools").let { array ->
                (0 until array.length()).associate { array.getJSONObject(it).let { tool -> tool.getString("id") to tool.getBoolean("entries") } }
            }
            return PartSelection(ids("zones"), tools, ids("variables"), ids("automations"))
        }
    }
}

/**
 * A backup cut down to a [PartSelection]: the same file, the same version and transformations
 * (docs/DATA.md, « Export/import format »), marked partial in its metadata, holding only what is
 * ticked. What never goes is left out whole: the app's settings, the AI providers, the
 * identities, the conversations, the files joined to them; a routine keeps its seed session, the
 * message each of its runs starts from, and nothing of its runs.
 */
object BackupPart {

    /** The metadata's mark of a part: true, absent from a whole backup. */
    const val PARTIAL = "partial"

    /** Whether the backup [document] is a part. */
    fun isPartial(document: JSONObject): Boolean =
        document.getJSONObject("metadata").optBoolean(PARTIAL, false)

    /**
     * [document], a whole backup at the current version, reduced to [selection].
     *
     * @throws IllegalArgumentException when the selection names what the backup does not hold, or
     *   an element without its zone
     */
    fun cut(document: JSONObject, selection: PartSelection): JSONObject {
        val data = document.getJSONObject("data")
        val zones = rows(data, "zones")
        val tools = rows(data, "tool_instances")
        val variables = rows(data, "variables")
        val automations = rows(data, "automations")

        // Each element named exists, in a zone kept
        unknown(selection.zones, zones)?.let { throw IllegalArgumentException("No zone $it in the backup") }
        unknown(selection.tools.keys, tools)?.let { throw IllegalArgumentException("No tool $it in the backup") }
        unknown(selection.variables, variables)?.let { throw IllegalArgumentException("No variable $it in the backup") }
        unknown(selection.automations, automations)?.let { throw IllegalArgumentException("No routine $it in the backup") }
        val keptTools = tools.filter { it.getString("id") in selection.tools }
        val keptVariables = variables.filter { it.getString("id") in selection.variables }
        val keptAutomations = automations.filter { it.getString("id") in selection.automations }
        (keptTools + keptVariables + keptAutomations).firstOrNull { it.getString("zone_id") !in selection.zones }?.let {
            throw IllegalArgumentException("${it.getString("id")} is kept without its zone ${it.getString("zone_id")}")
        }

        // A zone keeps the tool groups its kept elements hold, in its order
        val heldGroups = keptTools.mapNotNull { tool -> Groups.held(JSONObject(tool.getString("config_json")))?.let { tool.getString("zone_id") to it } } +
            (keptVariables + keptAutomations).mapNotNull { element -> Groups.held(element)?.let { element.getString("zone_id") to it } }
        val keptZones = zones.filter { it.getString("id") in selection.zones }.map { zone ->
            val id = zone.getString("id")
            val groups = ToolPositions.zoneGroups(zone.optString("tool_groups").takeIf { it.isNotEmpty() }).filter { (id to it) in heldGroups }
            copy(zone).apply {
                if (groups.isEmpty()) remove("tool_groups") else put("tool_groups", JSONArray(groups).toString())
                // Identities never go: a zone arrives speaking through the app's own AI
                put("identity_id", JSONObject.NULL)
            }
        }

        val withEntries = selection.tools.filterValues { it }.keys
        val entries = rows(data, "tool_data").filter { it.getString("tool_instance_id") in withEntries }
        val entryIds = entries.map { it.getString("id") }.toSet()
        // A routine without its provider, its identity and its runs: none of them goes
        val keptRoutines = keptAutomations.map { automation ->
            copy(automation).apply {
                put("provider_id", JSONObject.NULL)
                put("identity_id", JSONObject.NULL)
                put("last_execution_id", JSONObject.NULL)
                put("execution_history_json", "[]")
            }
        }
        val seeds = keptAutomations.map { it.getString("seed_session_id") }.toSet()
        val sessions = rows(data, "ai_sessions").filter { it.getString("id") in seeds }
        val messages = rows(data, "session_messages").filter { it.getString("session_id") in seeds }
        // The images of the entries kept, and of the routines' messages
        val images = rows(data, "images").filter { it.optString("entry_id") in entryIds || it.optString("session_id") in seeds }
        val imageIds = images.map { it.getString("id") }.toSet()
        val missing = data.optJSONArray("missing_images")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } }.orEmpty().filter { it in imageIds }

        return JSONObject()
            .put("metadata", copy(document.getJSONObject("metadata")).put(PARTIAL, true))
            .put("data", JSONObject()
                .put("zones", JSONArray(keptZones))
                .put("tool_instances", JSONArray(keptTools))
                .put("tool_data", JSONArray(entries))
                .put("variables", JSONArray(keptVariables))
                .put("automations", JSONArray(keptRoutines))
                .put("ai_sessions", JSONArray(sessions))
                .put("session_messages", JSONArray(messages))
                .put("images", JSONArray(images))
                .put("missing_images", JSONArray(missing)))
    }

    /** The ids of the images [part] holds, whose files go beside it. */
    fun imageIds(part: JSONObject): Set<String> =
        rows(part.getJSONObject("data"), "images").map { it.getString("id") }.toSet()

    private fun rows(data: JSONObject, table: String): List<JSONObject> =
        data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()

    /** An id of [named] that none of [rows] bears, or null. */
    private fun unknown(named: Set<String>, rows: List<JSONObject>): String? {
        val ids = rows.map { it.getString("id") }.toSet()
        return named.firstOrNull { it !in ids }
    }

    private fun copy(json: JSONObject): JSONObject = JSONObject(json.toString())
}
