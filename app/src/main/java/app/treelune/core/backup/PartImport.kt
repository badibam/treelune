package app.treelune.core.backup

import android.content.Context
import app.treelune.core.ai.data.AutomationMessage
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.GivenId
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.grid.HomeGroups
import app.treelune.core.grid.ToolPositions
import app.treelune.core.images.ImageFormat
import app.treelune.core.images.Images
import app.treelune.core.scheduling.SchedulersHeld
import app.treelune.core.services.BackupService
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.variables.Formula
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A part of a backup brought into the app (docs/DATA.md, « A part of the app »): everything is
 * added, nothing replaced, through the ordinary services as the app itself, so that what arrives
 * is checked as anything written.
 *
 * Every element gets a new id before anything is written, each id replaced in the whole part at
 * once (IdWords): what arrives names itself under its new ids from the start, whatever the order
 * of creation, and the services take them as given (GivenId.Made). A zone goes into a new zone, or
 * into one of the app's, whose id then takes its place. A variable whose name is taken arrives
 * under `_2`, `_3`…; a routine arrives switched off, without provider nor identity. An image the
 * file says was already missing at its export is taken out of its entry.
 *
 * The part arrives whole or not at all: a step refused removes what was written before it, and the
 * refusal names the step. The schedulers are held off meanwhile (SchedulersHeld).
 */
class PartImport(private val context: Context) {

    /** A step refused by its service, or a part that cannot arrive; the message says which. */
    class Refused(message: String) : Exception(message)

    /**
     * What arrived.
     *
     * @property renamed Each variable's name in the file, and the one it arrived under when taken
     * @property imagesMissing The images taken out of their entries and tools, missing already at the export
     */
    data class Report(val zones: Int, val elements: Int, val entries: Int, val renamed: Map<String, String>, val imagesMissing: Int)

    private val coordinator = Coordinator(context)
    private val database = AppDatabase.getDatabase(context)

    /**
     * [part], a backup at the current version cut to what is brought and holding no blocking link
     * (PartLinks), written into the app; its images' files are in [staging], named after their ids.
     *
     * @param arrivals For a zone of the part, the id of the app's zone it goes into; a zone left
     *   out goes into a new zone
     * @throws Refused when a zone of arrival is gone, or a service refuses a step
     */
    suspend fun run(part: JSONObject, staging: File, arrivals: Map<String, String>): Report {
        val data = JSONObject(part.getJSONObject("data").toString())
        arrivals.values.firstOrNull { database.zoneDao().getZoneById(it) == null }?.let { throw Refused("No zone $it to arrive in") }

        // The images already missing at the export, taken out of the entries and the tools that held them
        val missing = data.optJSONArray("missing_images")?.let { ids -> (0 until ids.length()).map { ids.getString(it) }.toSet() }.orEmpty()
        val imagesMissing = rows(data, "tool_data").sumOf { withoutImages(it, missing) } +
            rows(data, "tool_instances").sumOf { withoutConfigImages(it, missing) }

        // A new id for each thing brought, the zone of arrival's for a zone going into one
        val ids = HashMap<String, String>()
        rows(data, "zones").forEach { zone -> ids[zone.getString("id")] = arrivals[zone.getString("id")] ?: newId() }
        listOf("tool_instances", "tool_data", "variables", "automations").forEach { table -> rows(data, table).forEach { ids[it.getString("id")] = newId() } }
        val images = rows(data, "images").filter { !it.has("session_id") && it.getString("id") !in missing }
        images.forEach { ids[it.getString("id")] = newId() }
        val former = images.associate { ids.getValue(it.getString("id")) to it.getString("id") }
        IdWords.replace(data, ids)
        val made = ids.filterKeys { it !in arrivals }.values.toSet()

        val written = Written()
        return try {
            withContext(GivenId.Made(made)) {
                SchedulersHeld.during { write(data, staging, former, arrivals.values.toSet(), written) }
            }.copy(imagesMissing = imagesMissing)
        } catch (e: Refused) {
            undo(written)
            throw e
        } catch (e: Exception) {
            undo(written)
            LogManager.service("Part import failed: ${e.message}", "ERROR", e)
            throw Refused(e.message ?: e::class.simpleName.orEmpty())
        } finally {
            images.forEach { image -> pending(image).delete() }
        }
    }

    /** What was written so far, for [undo]. */
    private class Written {
        val homeGroups = mutableListOf<String>()
        val zones = mutableListOf<String>()
        /** The app's zones whose tool groups grew, with the ones they had */
        val toolGroups = mutableMapOf<String, String?>()
        /** Each thing created in a zone of the app: its operation of deletion and its params */
        val inExisting = mutableListOf<Pair<String, JSONObject>>()
    }

    private suspend fun write(data: JSONObject, staging: File, former: Map<String, String>, existing: Set<String>, written: Written): Report {
        val zones = rows(data, "zones")
        val newZones = zones.filter { it.getString("id") !in existing }

        // A new zone in its group of the home screen, added when missing
        written.homeGroups += HomeGroups(::run).add(newZones.mapNotNull { it.optString("group").takeIf { g -> g.isNotEmpty() } })
        newZones.forEach { zone ->
            run("zones.create", JSONObject().apply {
                put("id", zone.getString("id"))
                listOf("name", "description", "icon_name", "icon_color", "texture", "display_mode", "group").forEach { key ->
                    if (zone.has(key) && !zone.isNull(key)) put(key, zone.get(key))
                }
                put("validate", zone.optBoolean("validate", false))
                zone.optString("tool_groups").takeIf { it.isNotEmpty() }?.let { put("tool_groups", JSONArray(it)) }
            })
            written.zones += zone.getString("id")
        }
        // A zone of the app receiving tools gets the tool groups they stand in, added at its end
        zones.filter { it.getString("id") in existing }.forEach { zone ->
            val id = zone.getString("id")
            val held = ToolPositions.zoneGroups(zone.optString("tool_groups").takeIf { it.isNotEmpty() })
            val stored = database.zoneDao().getZoneById(id)!!.tool_groups
            val has = ToolPositions.zoneGroups(stored)
            val missing = held.filter { it !in has }
            if (missing.isEmpty()) return@forEach
            run("zones.update", JSONObject().put("zone_id", id).put("tool_groups", JSONArray(has + missing)))
            written.toolGroups[id] = stored
        }

        // The images of the entries and the tools waiting where a form leaves one, which their
        // owner then takes
        val imageRows = rows(data, "images").filter { !it.has("session_id") && it.getString("id") in former }
        imageRows.forEach { image ->
            val format = ImageFormat.valueOf(image.getString("format"))
            Images.copy(File(staging, format.fileName(former.getValue(image.getString("id")))), pending(image))
        }

        // The tools and the variables, each after what it reads, a variable whose name is taken
        // under the next free one
        val variables = rows(data, "variables")
        val names = arrivalNames(variables.map { it.getString("id") to it.getString("name") }, database.variableDao().getAll().map { it.name }.toSet())
        val renamed = variables.filter { names.getValue(it.getString("id")) != it.getString("name") }
            .associateTo(linkedMapOf()) { it.getString("name") to names.getValue(it.getString("id")) }
        val elements = rows(data, "tool_instances") + rows(data, "variables")
        for (element in ordered(elements)) {
            val zone = element.getString("zone_id")
            if (element.has("tooltype")) {
                run("tools.create", JSONObject()
                    .put("id", element.getString("id"))
                    .put("zone_id", zone)
                    .put("tooltype", element.getString("tooltype"))
                    .put("config", JSONObject(element.getString("config_json"))))
                if (zone in existing) written.inExisting += "tools.delete" to JSONObject().put("tool_instance_id", element.getString("id"))
            } else {
                run("variables.create", JSONObject()
                    .put("id", element.getString("id"))
                    .put("zone_id", zone)
                    .put("name", names.getValue(element.getString("id")))
                    .apply {
                        listOf("label", "description", "group", "icon_name", "icon_color", "texture", "display_mode").forEach { key ->
                            element.optString(key).takeIf { it.isNotEmpty() }?.let { put(key, it) }
                        }
                    }
                    .put("definition", given(JSONObject(element.getString("definition_json")), names)))
                if (zone in existing) written.inExisting += "variables.delete" to JSONObject().put("variable_id", element.getString("id"))
            }
        }
        // The routines, off, with no provider nor identity: both are the user's to choose
        val messages = rows(data, "session_messages").groupBy { it.getString("session_id") }
        rows(data, "automations").forEach { automation ->
            val seed = messages[automation.getString("seed_session_id")].orEmpty().firstOrNull { it.optString("sender") == "USER" }
            val message = seed?.optString("rich_content_json")?.takeIf { it.isNotEmpty() }?.let { RichMessage.fromJson(it) }
            run("automations.create", AutomationSettings.of(BackupService.automationOf(automation)).apply {
                put("id", automation.getString("id"))
                put("zone_id", automation.getString("zone_id"))
                put(AutomationSettings.PROVIDER_ID, JSONObject.NULL)
                put(AutomationSettings.IDENTITY_ID, JSONObject.NULL)
                put("is_enabled", false)
                put("access", JSONArray(automation.optString("access_json", "[]")))
                message?.let { put("message", AutomationMessage.toBlocks(it)) }
            })
            if (automation.getString("zone_id") in existing) written.inExisting += "automations.delete" to JSONObject().put("automation_id", automation.getString("id"))
        }

        // The tools, the variables and the routines of a new zone at the places they had, section by section
        val tiles = rows(data, "tool_instances").map { it to JSONObject(it.getString("config_json")).optString("group") } +
            rows(data, "variables").map { it to it.optString("group") } +
            rows(data, "automations").map { it to it.optString("group") }
        tiles.filter { (item, _) -> item.getString("zone_id") !in existing }
            .groupBy({ (item, group) -> item.getString("zone_id") to group.takeIf { it.isNotEmpty() } }, { it.first })
            .forEach { (section, items) ->
                run("zones.place_section", JSONObject()
                    .put("zone_id", section.first)
                    .apply { section.second?.let { put("group", it) } }
                    .put("places", JSONObject().apply { items.forEach { put(it.getString("id"), JSONObject().put("grid_x", it.getInt("grid_x")).put("grid_y", it.getInt("grid_y"))) } }))
            }

        // The entries, each after the ones it designates, a tool's together
        val entries = ordered(rows(data, "tool_data"))
        var start = 0
        while (start < entries.size) {
            val tool = entries[start].getString("tool_instance_id")
            val end = (start until entries.size).firstOrNull { entries[it].getString("tool_instance_id") != tool } ?: entries.size
            val batch = run("tool_data.batch_create", JSONObject()
                .put("tool_instance_id", tool)
                .put("entries", JSONArray(entries.subList(start, end).map { entry ->
                    JSONObject().apply {
                        put("id", entry.getString("id"))
                        put("data", JSONObject(entry.getString("data")))
                        if (!entry.isNull("name") && entry.has("name")) put("name", entry.getString("name"))
                        if (!entry.isNull("timestamp") && entry.has("timestamp")) put("timestamp", entry.getLong("timestamp"))
                        if (!entry.isNull("extra") && entry.has("extra")) put("extra", JSONObject(entry.getString("extra")))
                        if (!entry.isNull("state") && entry.has("state")) put("state", JSONObject(entry.getString("state")))
                    }
                })))
            if (((batch["failed_count"] as? Number)?.toInt() ?: 0) > 0) throw Refused("tool_data.batch_create $tool: ${batch["refusals"]}")
            start = end
        }

        return Report(newZones.size, elements.size + rows(data, "automations").size, entries.size, renamed, 0)
    }

    companion object {
        /**
         * The name each variable of [variables] (id and name, in order) arrives under: its own, or
         * when [taken] by the app or by one before it, the first of `_2`, `_3`… that is free.
         */
        fun arrivalNames(variables: List<Pair<String, String>>, taken: Set<String>): Map<String, String> {
            val used = taken.toMutableSet()
            return variables.associate { (id, name) ->
                val free = (sequenceOf(name) + generateSequence(2) { it + 1 }.map { "${name}_$it" }).first { it !in used }
                used += free
                id to free
            }
        }
    }

    /**
     * [definition] stored, in the form a create takes: the variables its formula reads by name,
     * those brought under the name they arrive with. A constant has no formula.
     */
    private fun given(definition: JSONObject, names: Map<String, String>): JSONObject {
        val formula = definition.optString("formula").takeIf { it.isNotEmpty() } ?: return definition
        val read = Formula.parse(formula) as? Formula.Parsed.Read ?: throw Refused("Formula not read: $formula")
        return definition.put("formula", read.formula.text({ id -> names[id] ?: throw Refused("Variable $id read by a formula and not brought") }))
    }

    /** What was written taken out again, the last first: the part arrives whole or not at all. */
    private suspend fun undo(written: Written) {
        try {
            written.inExisting.asReversed().forEach { (action, params) -> run(action, params) }
            written.zones.asReversed().forEach { run("zones.delete", JSONObject().put("zone_id", it)) }
            written.toolGroups.forEach { (zone, groups) ->
                run("zones.update", JSONObject().put("zone_id", zone).put("tool_groups", groups?.let { JSONArray(it) } ?: JSONObject.NULL))
            }
            HomeGroups(::run).remove(written.homeGroups.toSet())
        } catch (e: Exception) {
            LogManager.service("Part import: what it wrote not all taken out again: ${e.message}", "ERROR", e)
        }
    }

    /**
     * [rows] ordered so that each comes after those whose ids it names, in their order otherwise.
     * Two naming each other keep their order: the service refuses the first, naming why.
     */
    private fun ordered(rows: List<JSONObject>): List<JSONObject> {
        val index = rows.withIndex().associate { (i, row) -> row.getString("id") to i }
        val needs = rows.map { row -> IdWords.words(row).mapNotNull { index[it] }.filter { it != index[row.getString("id")] }.toSet() }
        val waiting = needs.map { it.size }.toIntArray()
        val neededBy = Array(rows.size) { mutableListOf<Int>() }
        needs.forEachIndexed { i, need -> need.forEach { neededBy[it].add(i) } }
        // The first ready in the rows' order each time; what a loop leaves, in its order at the end
        val ready = java.util.PriorityQueue<Int>().apply { addAll(rows.indices.filter { waiting[it] == 0 }) }
        val done = mutableListOf<Int>()
        val placed = BooleanArray(rows.size)
        while (ready.isNotEmpty()) {
            val i = ready.poll()!!
            done += i
            placed[i] = true
            neededBy[i].forEach { if (--waiting[it] == 0) ready.add(it) }
        }
        return (done + rows.indices.filter { !placed[it] }).map { rows[it] }
    }

    /** The ids of [missing] taken out of [entry]'s image fields (a list of ids); how many. */
    private fun withoutImages(entry: JSONObject, missing: Set<String>): Int {
        if (missing.isEmpty()) return 0
        var removed = 0
        listOf("data", "extra").filter { entry.has(it) && !entry.isNull(it) }.forEach { column ->
            val values = JSONObject(entry.getString(column))
            values.keys().asSequence().toList().forEach { key ->
                val list = values.optJSONArray(key) ?: return@forEach
                val kept = (0 until list.length()).map { list.get(it) }.filter { it !is String || it !in missing }
                if (kept.size != list.length()) {
                    removed += list.length() - kept.size
                    values.put(key, JSONArray(kept))
                }
            }
            entry.put(column, values.toString())
        }
        return removed
    }

    /**
     * [tool]'s config without the ids of [missing], wherever its lists hold them (a step's
     * images); how many it held.
     */
    private fun withoutConfigImages(tool: JSONObject, missing: Set<String>): Int {
        if (missing.isEmpty()) return 0
        var removed = 0
        fun clean(value: Any?): Any? = when (value) {
            is JSONObject -> value.apply { keys().asSequence().toList().forEach { put(it, clean(get(it))) } }
            is JSONArray -> {
                val kept = (0 until value.length()).map { value.get(it) }.filter { it !is String || it !in missing }
                removed += value.length() - kept.size
                JSONArray(kept.map { clean(it) })
            }
            else -> value
        }
        tool.put("config_json", clean(JSONObject(tool.getString("config_json"))).toString())
        return removed
    }

    /** Where [image]'s file waits for its owner. */
    private fun pending(image: JSONObject): File =
        File(Images.pendingDir(context).apply { mkdirs() }, ImageFormat.valueOf(image.getString("format")).fileName(image.getString("id")))

    /** Runs [action] as the app itself, its data on success; a refusal stops the import, naming the step. */
    private suspend fun run(action: String, params: JSONObject): Map<String, Any?> {
        val result = coordinator.process(Source.SYSTEM, action, JsonUtils.toMap(params))
        if (!result.isSuccess) throw Refused("$action ${params.optString("id")}: ${result.error}")
        return result.data ?: emptyMap()
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun rows(data: JSONObject, table: String): List<JSONObject> =
        data.optJSONArray(table)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()
}
