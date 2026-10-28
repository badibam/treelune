package app.treelune.core.backup

import app.treelune.core.grid.Groups
import app.treelune.core.grid.ToolPositions
import org.json.JSONArray
import org.json.JSONObject

/**
 * What is ticked of the app to share (docs/DATA.md, « A part of the app »): zones, and in them tools — each with
 * its entries or without —, variables, routines, Tasks and the identities the zone or its routines
 * speak as; beside the zones, APIs. An element of a zone is kept only with its zone; a zone may be
 * kept alone, empty. The events go with what names them, unticked.
 */
data class PartSelection(
    val zones: Set<String>,
    /** Each tool kept, with whether its entries go with it */
    val tools: Map<String, Boolean>,
    val variables: Set<String>,
    val routines: Set<String>,
    val identities: Set<String>,
    val tasks: Set<String> = emptySet(),
    val apis: Set<String> = emptySet()
) {
    /**
     * The stored form: `{"zones": [id], "tools": [{"id", "entries"}], "variables": [id],
     * "routines": [id], "identities": [id], "tasks": [id], "apis": [id]}`.
     */
    fun toJson(): JSONObject = JSONObject()
        .put("zones", JSONArray(zones.toList()))
        .put("tools", JSONArray(tools.map { (id, entries) -> JSONObject().put("id", id).put("entries", entries) }))
        .put("variables", JSONArray(variables.toList()))
        .put("automations", JSONArray(routines.toList()))
        .put("identities", JSONArray(identities.toList()))
        .put("tasks", JSONArray(tasks.toList()))
        .put("apis", JSONArray(apis.toList()))

    /**
     * The selection cut to what [tree] offers: a selection the connector gives may name what no
     * screen offers, the demo first (PartTree.withoutDemo), which no part carries.
     */
    fun within(tree: PartTree): PartSelection {
        val offered = tree.elements.map { it.id }.toSet()
        return PartSelection(zones.filter { id -> tree.zones.any { it.id == id } }.toSet(), tools.filterKeys { it in offered },
            variables.filter { it in offered }.toSet(), routines.filter { it in offered }.toSet(), identities.filter { it in offered }.toSet(),
            tasks.filter { it in offered }.toSet(), apis.filter { id -> tree.apis.any { it.id == id } }.toSet())
    }

    companion object {
        /** The selection stored as [json]; a key missing or of the wrong form throws (org.json's JSONException). */
        fun read(json: JSONObject): PartSelection {
            fun ids(key: String): Set<String> = json.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
            val tools = json.getJSONArray("tools").let { array ->
                (0 until array.length()).associate { array.getJSONObject(it).let { tool -> tool.getString("id") to tool.getBoolean("entries") } }
            }
            return PartSelection(ids("zones"), tools, ids("variables"), ids("automations"), ids("identities"), ids("tasks"), ids("apis"))
        }
    }
}

/**
 * A backup cut down to a [PartSelection]: the same file, the same version and transformations
 * (docs/DATA.md, « Export/import format »), marked partial in its metadata, holding only what is
 * ticked. What never goes is left out whole: the app's settings, the AI providers, the
 * conversations, the files joined to them; a routine keeps its seed session, the message each of
 * its runs starts from, and nothing of its runs. An identity goes when ticked, a zone and a routine
 * keeping it then, and speaking through the app's own AI otherwise. A Task goes switched off; an
 * API without its secret settings, its key; an event when what goes names it, in the zone it
 * arrives in (PartTree.eventArrivals).
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
     * @param apiSecrets The names of an API's secret settings (SecretSettings.names of ApiSettings), left out
     * @throws IllegalArgumentException when the selection names what the backup does not hold, an
     *   element without its zone, or an identity no zone kept speaks as, nor any of its routines
     */
    fun cut(document: JSONObject, selection: PartSelection, apiSecrets: List<String>): JSONObject {
        val data = document.getJSONObject("data")
        val zones = rows(data, "zones")
        val tools = rows(data, "tool_instances")
        val variables = rows(data, "variables")
        val routines = rows(data, "automations")
        val identities = rows(data, "identities")
        val tasks = rows(data, "tasks")
        val apis = rows(data, "apis")

        // Each element named exists, in a zone kept
        unknown(selection.zones, zones)?.let { throw IllegalArgumentException("No zone $it in the backup") }
        unknown(selection.tools.keys, tools)?.let { throw IllegalArgumentException("No tool $it in the backup") }
        unknown(selection.variables, variables)?.let { throw IllegalArgumentException("No variable $it in the backup") }
        unknown(selection.routines, routines)?.let { throw IllegalArgumentException("No routine $it in the backup") }
        unknown(selection.identities, identities)?.let { throw IllegalArgumentException("No identity $it in the backup") }
        unknown(selection.tasks, tasks)?.let { throw IllegalArgumentException("No Task $it in the backup") }
        unknown(selection.apis, apis)?.let { throw IllegalArgumentException("No API $it in the backup") }
        val keptTools = tools.filter { it.getString("id") in selection.tools }
        val keptVariables = variables.filter { it.getString("id") in selection.variables }
        val keptRoutineRows = routines.filter { it.getString("id") in selection.routines }
        val keptTasks = tasks.filter { it.getString("id") in selection.tasks }
        (keptTools + keptVariables + keptRoutineRows + keptTasks).firstOrNull { it.getString("zone_id") !in selection.zones }?.let {
            throw IllegalArgumentException("${it.getString("id")} is kept without its zone ${it.getString("zone_id")}")
        }
        // An identity goes with a zone kept that speaks as it, or one of whose routines does
        val spoken = (zones.filter { it.getString("id") in selection.zones } + routines.filter { it.getString("zone_id") in selection.zones })
            .mapNotNull { identityOf(it) }.toSet()
        selection.identities.firstOrNull { it !in spoken }?.let { throw IllegalArgumentException("The identity $it is kept without a zone that speaks as it") }
        // What a zone or a routine kept speaks as: its identity when kept, the app's own AI otherwise
        fun spokenAs(row: JSONObject): Any = identityOf(row)?.takeIf { it in selection.identities } ?: JSONObject.NULL

        // A zone keeps the tool groups its kept elements hold, in its order
        val heldGroups = keptTools.mapNotNull { tool -> Groups.held(JSONObject(tool.getString("config_json")))?.let { tool.getString("zone_id") to it } } +
            (keptVariables + keptRoutineRows + keptTasks).mapNotNull { element -> Groups.held(element)?.let { element.getString("zone_id") to it } }
        val keptZones = zones.filter { it.getString("id") in selection.zones }.map { zone ->
            val id = zone.getString("id")
            val groups = ToolPositions.zoneGroups(zone.optString("tool_groups").takeIf { it.isNotEmpty() }).filter { (id to it) in heldGroups }
            copy(zone).apply {
                if (groups.isEmpty()) remove("tool_groups") else put("tool_groups", JSONArray(groups).toString())
                put("identity_id", spokenAs(zone))
            }
        }

        val withEntries = selection.tools.filterValues { it }.keys
        val entries = rows(data, "tool_data").filter { it.getString("tool_instance_id") in withEntries }
        val entryIds = entries.map { it.getString("id") }.toSet()
        // A routine without its provider and its runs, which never go, and its identity unless kept
        val keptRoutines = keptRoutineRows.map { routine ->
            copy(routine).apply {
                put("provider_id", JSONObject.NULL)
                put("identity_id", spokenAs(routine))
                put("last_execution_id", JSONObject.NULL)
                put("execution_history_json", "[]")
            }
        }
        // A Task off, not to run where nobody has read it yet
        val keptTaskRows = keptTasks.map { copy(it).put("is_enabled", false) }
        // An API without its key, which is the user's own
        val keptApis = apis.filter { it.getString("id") in selection.apis }.map { api ->
            copy(api).put("config_json", JSONObject(api.getString("config_json")).apply { apiSecrets.forEach { remove(it) } }.toString())
        }
        // The events what goes names, each in the zone it arrives in
        val arrivals = PartTree.of(document).eventArrivals(selection)
        val keptEvents = rows(data, "events").filter { it.getString("id") in arrivals }.map { copy(it).put("zone_id", arrivals.getValue(it.getString("id"))) }
        val seeds = keptRoutineRows.map { it.getString("seed_session_id") }.toSet()
        val sessions = rows(data, "ai_sessions").filter { it.getString("id") in seeds }
        val messages = rows(data, "session_messages").filter { it.getString("session_id") in seeds }
        // The files of the entries and the tools kept, and of the routines' messages
        val toolIds = keptTools.map { it.getString("id") }.toSet()
        val files = rows(data, "files").filter {
            it.optString("entry_id") in entryIds || it.optString("tool_instance_id") in toolIds || it.optString("session_id") in seeds
        }
        val fileIds = files.map { it.getString("id") }.toSet()
        val missing = data.optJSONArray("missing_files")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } }.orEmpty().filter { it in fileIds }

        return JSONObject()
            .put("metadata", copy(document.getJSONObject("metadata")).put(PARTIAL, true))
            .put("data", JSONObject()
                .put("zones", JSONArray(keptZones))
                .put("tool_instances", JSONArray(keptTools))
                .put("tool_data", JSONArray(entries))
                .put("variables", JSONArray(keptVariables))
                .put("automations", JSONArray(keptRoutines))
                .put("tasks", JSONArray(keptTaskRows))
                .put("apis", JSONArray(keptApis))
                .put("events", JSONArray(keptEvents))
                .put("identities", JSONArray(identities.filter { it.getString("id") in selection.identities }))
                .put("ai_sessions", JSONArray(sessions))
                .put("session_messages", JSONArray(messages))
                .put("files", JSONArray(files))
                .put("missing_files", JSONArray(missing)))
    }

    /** The ids of the files [part] holds, which go beside it. */
    fun fileIds(part: JSONObject): Set<String> =
        rows(part.getJSONObject("data"), "files").map { it.getString("id") }.toSet()

    /** The identity a backup's [row] of `identities` holds. */
    fun identity(row: JSONObject): app.treelune.core.identities.Identity = app.treelune.core.identities.Identity(
        id = row.getString("id"),
        name = row.getString("name"),
        iconName = row.getString("icon_name"),
        iconColor = if (row.isNull("icon_color")) null else row.getString("icon_color"),
        language = row.getString("language"),
        languages = app.treelune.core.identities.Identity.languagesFromJson(JSONObject(row.getString("languages_json"))),
        publishedAt = if (row.isNull("published_at")) null else row.getString("published_at"),
        importedAt = if (row.isNull("imported_at")) null else row.getLong("imported_at"),
        createdAt = row.getLong("created_at"),
        updatedAt = row.getLong("updated_at")
    )

    /** The identity a zone's or a routine's [row] speaks as, null for the app's own AI. */
    fun identityOf(row: JSONObject): String? =
        if (row.isNull("identity_id")) null else row.optString("identity_id").ifEmpty { null }

    private fun rows(data: JSONObject, table: String): List<JSONObject> =
        data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()

    /** An id of [named] that none of [rows] bears, or null. */
    private fun unknown(named: Set<String>, rows: List<JSONObject>): String? {
        val ids = rows.map { it.getString("id") }.toSet()
        return named.firstOrNull { it !in ids }
    }

    private fun copy(json: JSONObject): JSONObject = JSONObject(json.toString())
}
