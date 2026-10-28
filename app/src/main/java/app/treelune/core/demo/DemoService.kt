package app.treelune.core.demo

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.LongOperation
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.transaction
import app.treelune.core.grid.Grid
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.ui.DisplayMode
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import androidx.room.useWriterConnection
import app.treelune.tools.goal.GoalDefinition
import app.treelune.tools.goal.GoalToolType
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The demo, as the resource `demo`:
 * - install: removes the demo there is, then builds the one the app ships through the services,
 *   as the app itself (Source.SYSTEM), which alone may give the demo's ids; its zone group joins
 *   the home screen's groups when it is not there yet
 * - remove: removes the demo, what lives in it included, and the zone groups its zones stood in
 *   that no zone stands in any more
 *
 * Built through the services, the demo is what the app produces and is checked as anything else
 * written. A step refused stops the install and removes what it built: the demo is whole or
 * absent, and the refusal says why.
 */
class DemoService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database = AppDatabase.getDatabase(context)
    private val coordinator = Coordinator(context)

    /** A step of the install refused, with the service's reason. */
    private class Refused(message: String) : Exception(message)

    /** Both rewrite the whole demo: never two at once, nor with an import or a backup. */
    override val longOperations = setOf("install", "remove")

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "install" -> writing { install() }
            "remove" -> writing { remove() }
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    /** [block] run with the schedulers held off: they see the demo whole or absent. */
    private suspend fun writing(block: suspend () -> OperationResult): OperationResult =
        app.treelune.core.scheduling.SchedulersHeld.during(block)

    private suspend fun install(): OperationResult {
        val texts: JSONObject
        val content = try {
            texts = asset("demo/${s.shared("demo_texts_file")}")
            DemoContent.read(asset("demo/structure.json"), texts)
        } catch (e: Exception) {
            LogManager.service("Demo not read: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("demo_error_unreadable").format(e.message ?: ""))
        }
        val data = DemoData(app.treelune.core.selection.TimeResolver.at(System.currentTimeMillis()), asset("demo/entries.json"))
        LongOperation.at(s.shared("demo_phase_zones"))
        removeDemo()
        return try {
            app.treelune.core.grid.HomeGroups(::run).add(listOf(content.group))
            build(content, data)
            wire(content)
            fill(texts, data, content)
            tasks(content)
            automate(content)
            LogManager.service("Demo installed: ${content.identities.size} identities, ${content.zones.size} zones, ${content.tools.size} tools, ${content.variables.size} variables", "INFO")
            OperationResult.success(mapOf("zones" to content.zones.size, "tools" to content.tools.size, "variables" to content.variables.size))
        } catch (e: Refused) {
            removeDemo()
            DataChangeNotifier.notifyZonesChanged()
            LogManager.service("Demo not installed: ${e.message}", "ERROR")
            OperationResult.error(s.shared("demo_error_refused").format(e.message ?: ""))
        }
    }

    /**
     * The identities, which the zones and the routines name, each created then written; the zones, placed; the tools that read no variable; the variables, which read those tools;
     * the tools that read a variable (a goal, a chart); then every tool and variable placed, section by section.
     * A tool's moments are put in [data]'s weeks.
     */
    private suspend fun build(content: DemoContent, data: DemoData) {
        content.identities.forEach { identity ->
            run("identities.create", JSONObject(identity.toString()).apply { remove("texts") })
            run("identities.update", JSONObject(identity.getJSONObject("texts").toString()).put("identity_id", identity.getString("id")))
        }
        content.zones.forEach { run("zones.create", DemoContent.paramsOf(it)) }
        placeZones(content)
        val (readingVariables, others) = content.tools.partition { DemoContent.readsVariable(it) }
        var created = 0
        suspend fun create(tool: JSONObject) {
            LongOperation.at(s.shared("demo_phase_tools").format((created++).toString(), content.tools.size.toString()))
            run("tools.create", DemoContent.paramsOf(tool))
        }
        others.forEach { create(it) }
        content.variables.forEach { run("variables.create", it) }
        readingVariables.forEach { create(it) }
        val tiles = content.tools.map { it to it.getJSONObject("config").optString("group") } + content.variables.map { it to it.optString("group") }
        tiles.groupBy({ (item, group) -> item.getString("zone_id") to group.takeIf { it.isNotEmpty() } }, { it.first })
            .forEach { (section, items) ->
                run("zones.place_section", JSONObject()
                    .put("zone_id", section.first)
                    .apply { section.second?.let { put("group", it) } }
                    .put("places", places(items)))
            }
    }

    /**
     * The events, which the Tasks emit and the Tasks and routines listen to, and the APIs the Tasks
     * call. The demo's API kept by a reinstall, a Task of the user's calling it (DemoRemoval), is
     * written again as the demo ships it.
     */
    private suspend fun wire(content: DemoContent) {
        content.events.forEach { run("events.create", it) }
        for (api in content.apis) {
            val id = api.getString("id")
            if (database.apiDao().get(id) == null) run("apis.create", api)
            else run("apis.update", JSONObject(api.toString()).apply { remove("id") }.put("api_id", id))
        }
    }

    /**
     * The Tasks, once the entries are written, each at the end of its section as a routine: none
     * has a past, and a condition one listens to is judged a first time at the next tick, which
     * starts nothing.
     */
    private suspend fun tasks(content: DemoContent) {
        content.tasks.forEach { run("tasks.create", it) }
    }

    /**
     * The routines, off — on, a routine calls the AI at the user's cost: each one with its
     * seed session, its message the instruction followed by its pointers (`pointers`, each in the
     * stored form of PointerConfig), its identity the one it names or none; none has a past. Their
     * provider is the default, or none when there is none: switching one on then says to choose one.
     */
    private suspend fun automate(content: DemoContent) {
        if (content.routines.isEmpty()) return
        LongOperation.at(s.shared("demo_phase_automations"))
        val provider: Any = run("ai_provider_config.get_default", JSONObject())["provider_id"] ?: JSONObject.NULL
        for (routine in content.routines) {
            val message = org.json.JSONArray().put(JSONObject().put("type", app.treelune.core.ai.data.RoutineMessage.TEXT).put("text", routine.getString("seed")))
            routine.optJSONArray("pointers")?.let { pointers ->
                (0 until pointers.length()).forEach { message.put(JSONObject().put("type", app.treelune.core.ai.data.RoutineMessage.POINTER).put("pointer", pointers.getJSONObject(it))) }
            }
            val identity = routine.opt(app.treelune.core.ai.data.RoutineSettings.IDENTITY_ID) ?: JSONObject.NULL
            run("automations.create", JSONObject(routine.toString()).apply { remove("seed"); remove("pointers") }.put("provider_id", provider).put(app.treelune.core.ai.data.RoutineSettings.IDENTITY_ID, identity).put("message", message))
        }
    }

    /**
     * The entries, afloat now: each tool's written in one batch, as the app; then the goals'
     * attempts, opened with their copy of the goal as the scheduler opens them, and those before
     * last week validated as the user validates them, judged on the demo's own entries.
     */
    private suspend fun fill(texts: JSONObject, data: DemoData, content: DemoContent) {
        val all = data.entries()
        val total = all.values.sumOf { it.size }
        var written = 0
        all.forEach { (tool, entries) ->
            if (entries.isEmpty()) return@forEach
            LongOperation.at(s.shared("demo_phase_entries").format(written.toString(), total.toString()))
            written += entries.size
            val batch = run("tool_data.batch_create", JSONObject()
                .put("tool_instance_id", tool)
                .put("entries", JSONArray(entries.map { kept(DemoContent.pictured(DemoContent.resolve(it, texts), ::prepareImage) as JSONObject) })))
            val failed = (batch["failed_count"] as? Number)?.toInt() ?: 0
            if (failed > 0) throw Refused("tool_data.batch_create $tool: $failed refused, ${batch["refusals"]}")
        }
        LongOperation.at(s.shared("demo_phase_goals"))
        for (attempt in data.attempts(content.tools.associate { it.getString("id") to it.getJSONObject("config") })) {
            @Suppress("UNCHECKED_CAST")
            val tool = run("tools.get", JSONObject().put("tool_instance_id", attempt.toolId))["tool_instance"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val config = JsonUtils.toJSONObject(tool["config"] as Map<String, Any?>)
            val id = "demo-e-attempt-${attempt.toolId}-${attempt.start}"
            run("tool_data.create", JSONObject()
                .put("id", id)
                .put("tool_instance_id", attempt.toolId)
                .put("name", config.getString("name"))
                .put("timestamp", attempt.start)
                .put("data", JSONObject(attempt.entered.toString()).put(GoalToolType.DEFINITION, GoalDefinition.copyOf(config).toString()))
                .apply { if (attempt.extra.length() > 0) put("extra", DemoContent.resolve(attempt.extra, texts)) }
                .put("state", JSONObject().put(GoalToolType.STATUS, attempt.status).put(GoalToolType.PERIOD_END, attempt.end)))
            // Validated by the user, as Camille would: a goal's validation is a person's or the AI's
            if (attempt.validate) withContext(Origin(Source.USER, byTheApp = true)) {
                val validated = coordinator.processUserAction("goal.validate", mapOf("id" to id))
                if (!validated.isSuccess) throw Refused("goal.validate $id: ${validated.error}")
            }
        }
    }

    /**
     * [entry] with the steps a session keeps written as the JSON text it stores (SequenceSteps):
     * a list in DemoData, so that their names are texts put in like any other.
     */
    private fun kept(entry: JSONObject): JSONObject = entry.apply {
        optJSONObject("data")?.let { data -> data.optJSONArray(app.treelune.tools.sequence.SequenceToolType.KEPT_STEPS)?.let { data.put(app.treelune.tools.sequence.SequenceToolType.KEPT_STEPS, it.toString()) } }
    }

    /**
     * The image [name] of assets/demo/images prepared for an entry as a photo picked in the
     * gallery is (files.prepare), from a copy in the cache: its id, which the entry's write
     * makes the entry's.
     */
    private suspend fun prepareImage(name: String): String {
        val copy = java.io.File(context.cacheDir, "demo-image-$name")
        try {
            context.assets.open("demo/images/$name").use { input -> copy.outputStream().use { input.copyTo(it) } }
            return run("files.prepare", JSONObject().put("uri", android.net.Uri.fromFile(copy).toString()).put("kinds", org.json.JSONArray(listOf("IMAGE"))))["id"] as String
        } finally {
            copy.delete()
        }
    }

    /**
     * The demo's zones at their places in their group; a zone of the user's own standing in the
     * same group goes below them, in the order they had.
     */
    private suspend fun placeZones(content: DemoContent) {
        val listed = run("zones.list", JSONObject().put("include_position", true))
        @Suppress("UNCHECKED_CAST")
        val others = (listed["zones"] as List<Map<String, Any?>>)
            .filter { it["group"] == content.group && !(it["id"] as String).startsWith(DemoContent.PREFIX) }
            .sortedWith(compareBy({ (it["grid_y"] as Number).toInt() }, { (it["grid_x"] as Number).toInt() }))
        var tiles = content.zones.map { zone ->
            val size = Grid.size(DisplayMode.valueOf(zone.getString("display_mode")))
            Grid.Tile(zone.getString("id"), zone.getInt("grid_x"), zone.getInt("grid_y"), size.width, size.height)
        }
        others.forEach { zone ->
            tiles = Grid.arrive(tiles, zone["id"] as String, Grid.size(DisplayMode.valueOf(zone["display_mode"] as String)))
        }
        val places = JSONObject()
        tiles.forEach { places.put(it.id, JSONObject().put("grid_x", it.column).put("grid_y", it.row)) }
        run("zones.place", JSONObject().put("group", content.group).put("places", places))
    }

    private fun places(items: List<JSONObject>): JSONObject = JSONObject().also { places ->
        items.forEach { places.put(it.getString("id"), JSONObject().put("grid_x", it.getInt("grid_x")).put("grid_y", it.getInt("grid_y"))) }
    }

    /** Runs [action] as the app itself, its data on success; a refusal stops the install, naming the step. */
    private suspend fun run(action: String, params: JSONObject): Map<String, Any?> {
        val result = coordinator.process(Source.SYSTEM, action, JsonUtils.toMap(params))
        if (!result.isSuccess) throw Refused("$action ${params.optString("id")}: ${result.error}")
        return result.data ?: emptyMap()
    }

    private suspend fun remove(): OperationResult {
        removeDemo()
        DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Demo removed", "INFO")
        return OperationResult.success()
    }

    /**
     * Everything of the demo (DemoRemoval) in one transaction, then the zone groups its zones stood in that are left empty — read
     * from the zones themselves, so a group named in another language than the demo's texts now
     * goes too. A group where a zone of the user's still stands stays.
     */
    private suspend fun removeDemo() {
        val demoGroups = database.zoneDao().getAllZones()
            .filter { it.id.startsWith(DemoContent.PREFIX) }
            .mapNotNull { it.group }.toSet()
        val writes = app.treelune.core.history.Writes(context)
        database.transaction {
            database.useWriterConnection { connection ->
                DemoRemoval.OUTSIDE_HISTORY.forEach { sql -> connection.usePrepared(sql) { it.step() } }
            }
            DemoRemoval.SELECTIONS.forEach { (table, query) -> writes.deleteSelected(table, query) }
        }
        val stillUsed = database.zoneDao().getAllZones().mapNotNull { it.group }.toSet()
        app.treelune.core.grid.HomeGroups(::run).remove(demoGroups - stillUsed)
    }

    private fun asset(path: String): JSONObject =
        JSONObject(context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() })

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "install" -> s.shared("action_verbalize_demo_install")
            "remove" -> s.shared("action_verbalize_demo_remove")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
