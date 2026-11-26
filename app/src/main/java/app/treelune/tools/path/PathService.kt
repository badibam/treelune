package app.treelune.tools.path

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldType
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Path's operations, for the screen and the AI alike (docs/design/progression.md, « Ce que
 * l'IA fait sur un Parcours »). Its stages are written by these alone, an ordinary creation being
 * refused; each operation is one step of the history of writes, however many stages it writes.
 *
 * - write_plan: `stages`, a tree of stages, each `{key, kind, name, fields, links, content}`, its
 *   links naming stages of the send by their `key` or stages already there by id; under `parent`,
 *   by default the current run's root. Checked whole (kinds allowed where they stand, link types,
 *   loops of a type a rule reads), then written; the ids given back by key.
 * - update_stage: `stage`, and `name`, `fields` (merged, a null removing one) or `links` (replaced).
 * - move_stage: `stage`, into `parent` (at its end) or to `position` among its siblings.
 * - delete_stage: `stage`, with its content, the links that arrived from it removed.
 * - note: `stage`, `state` — a manual state its `note_if` allows — at `at` (now by default),
 *   with `note`; unnote: `stage`, its last move removed when it is a manual state's.
 * - start: a run opened, closing the current one, its plan copied from the latest run unless
 *   `plan` is `empty`; stop: `run` (the current by default) closed, every stage's state frozen;
 *   validate: a run awaiting validation closed.
 * - judge: the judge's records written and the runs that reached their end closed, by the app.
 * - state: the states of the stages of every run, now, and what the replay could not do.
 */
class PathService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = PathRows.TOOLTYPE, context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val toolId = params.optString("tool_instance_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        return try {
            val path = PathRead.of(context, toolId) ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found").format(toolId))
            val result = when (operation) {
                "write_plan" -> writePlan(path, params)
                "update_stage" -> updateStage(path, params)
                "move_stage" -> moveStage(path, params)
                "delete_stage" -> deleteStage(path, params)
                "note" -> note(path, params)
                "unnote" -> unnote(path, params)
                "start" -> start(path, params)
                "stop" -> stop(path, params)
                "validate" -> validate(path, params)
                "judge" -> judge(path)
                "state" -> state(path)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
            // A change of the Path itself may make a rule hold: the judge passes after it
            if (result.success && operation in CHANGES) PathRead.of(context, toolId)?.let { judge(it) }
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Refused) {
            OperationResult.error(e.message ?: "")
        } catch (e: IllegalStateException) {
            OperationResult.error(e.message ?: s.shared("service_error_operation_failed"))
        }
    }

    /** A refusal in words, which the operation gives back as its error. */
    private class Refused(message: String) : Exception(message)

    // -----------------------------------------------------------------------------------------
    // The plan
    // -----------------------------------------------------------------------------------------

    /** One stage of a plan sent, read. */
    private data class Planned(val key: String?, val kind: String, val name: String, val fields: Map<String, Any?>, val links: List<Pair<String, String>>, val content: List<Planned>)

    private fun planned(json: JSONObject): Planned = Planned(
        key = json.optString("key").takeIf { it.isNotEmpty() },
        kind = json.optString("kind").takeIf { it.isNotEmpty() } ?: throw Refused(s.tool("path_error_plan_kind").format(json.optString("name"))),
        name = json.optString("name").takeIf { it.isNotBlank() } ?: throw Refused(s.tool("path_error_plan_name")),
        fields = json.optJSONObject("fields")?.let { JsonUtils.toMap(it) }.orEmpty(),
        links = objects(json.optJSONArray("links")).map { (it.optString("from") to it.optString("type")) },
        content = objects(json.optJSONArray("content")).map { planned(it) }
    )

    private suspend fun writePlan(path: PathRead, params: JSONObject): OperationResult {
        val parentId = params.optString("parent").takeIf { it.isNotEmpty() }
            ?: path.currentRun()?.id ?: throw Refused(s.tool("path_error_no_run"))
        val parent = path.stage(parentId) ?: throw Refused(s.tool("path_error_stage").format(parentId))
        refuseClosed(path, parent)
        val sent = objects(params.optJSONArray("stages")).map { planned(it) }
        if (sent.isEmpty()) throw Refused(s.shared("service_error_missing_required_params").format("stages"))
        // Every stage of the send, with the parent it goes under and its place there
        val placed = mutableListOf<Triple<Planned, String, Int>>()
        val keys = mutableMapOf<String, Planned>()
        fun place(stages: List<Planned>, under: Stage?, underPlanned: Planned?, firstPosition: Int) {
            val parentKind = under?.kind ?: underPlanned!!.kind
            stages.forEachIndexed { i, stage ->
                val kind = path.grammar.kind(stage.kind) ?: throw Refused(s.tool("path_problem_kind").format(stage.kind))
                if (stage.kind !in path.grammar.kind(parentKind)!!.contains) throw Refused(s.tool("path_error_contains").format(stage.name, kind.name, parentKind))
                refuseFields(kind, stage.fields, stage.name)
                stage.key?.let { key -> if (keys.put(key, stage) != null) throw Refused(s.tool("path_error_plan_key").format(key)) }
                placed += Triple(stage, under?.id ?: "", firstPosition + i)
                place(stage.content, null, stage, 0)
            }
        }
        val after = path.stages.filter { it.parent == parent.id }.maxOfOrNull { it.position }?.plus(1) ?: 0
        place(sent, parent, null, after)
        // The links name a stage of the send by its key, or one already there by id
        val readTypes = PathOrder.linkTypesRead(path.grammar)
        for ((stage, _, _) in placed) for ((from, type) in stage.links) {
            if (path.grammar.linkTypes.none { it.key == type }) throw Refused(s.tool("path_error_link_type").format(type, path.grammar.linkTypes.joinToString(", ") { it.key }))
            if (from !in keys && path.stage(from) == null) throw Refused(s.tool("path_error_link_from").format(stage.name, from))
        }
        // Written in the order of the tree, a stage's parent before it; links once every id is known
        val ids = mutableMapOf<Planned, String>()
        for ((stage, parentOf, position) in placed) {
            val parentOfIt = parentOf.ifEmpty { ids.getValue(placed.first { (p, _, _) -> stage in p.content }.first) }
            val result = coordinator.processUserAction("tool_data.create", mapOf(
                "tool_instance_id" to path.toolId,
                "name" to stage.name,
                "data" to stage.fields,
                "state" to PathStages.state(stage.kind, parentOfIt, position, emptyList(), emptyList())
            ))
            if (!result.isSuccess) throw Refused(result.error ?: "")
            ids[stage] = result.data?.get("id") as String
        }
        val linked = placed.filter { it.first.links.isNotEmpty() }
        for ((stage, _, _) in linked) {
            val links = stage.links.map { (from, type) -> Link(keys[from]?.let { ids.getValue(it) } ?: from, type) }
            setState(ids.getValue(stage), mapOf(PathStages.LINKS to links.map { mapOf("from" to it.from, "type" to it.type) }))
        }
        refuseLoop(path.toolId, readTypes)
        return OperationResult.success(mapOf(
            "ids" to placed.mapNotNull { (stage, _, _) -> stage.key?.let { it to ids.getValue(stage) } }.toMap(),
            "written" to placed.size
        ))
    }

    private suspend fun updateStage(path: PathRead, params: JSONObject): OperationResult {
        val stage = stageOf(path, params)
        refuseClosed(path, stage)
        val kind = path.grammar.kind(stage.kind) ?: throw Refused(s.tool("path_problem_kind").format(stage.kind))
        val update = mutableMapOf<String, Any?>("id" to stage.id)
        if (params.has("name")) update["name"] = params.optString("name").takeIf { it.isNotBlank() } ?: throw Refused(s.tool("path_error_plan_name"))
        params.optJSONObject("fields")?.let { fields ->
            val given = JsonUtils.toMap(fields)
            refuseFields(kind, given.filterValues { it != null }, stage.name)
            update["data"] = given
        }
        params.optJSONArray("links")?.let { array ->
            val links = objects(array).map { Link(it.optString("from"), it.optString("type")) }
            links.firstOrNull { link -> path.grammar.linkTypes.none { it.key == link.type } }?.let { throw Refused(s.tool("path_error_link_type").format(it.type, path.grammar.linkTypes.joinToString(", ") { t -> t.key })) }
            links.firstOrNull { path.stage(it.from) == null }?.let { throw Refused(s.tool("path_error_link_from").format(stage.name, it.from)) }
            update["state"] = mapOf(PathStages.LINKS to links.map { mapOf("from" to it.from, "type" to it.type) })
        }
        val result = coordinator.processUserAction("tool_data.update", update)
        if (!result.isSuccess) throw Refused(result.error ?: "")
        refuseLoop(path.toolId, PathOrder.linkTypesRead(path.grammar))
        return OperationResult.success(mapOf("id" to stage.id))
    }

    private suspend fun moveStage(path: PathRead, params: JSONObject): OperationResult {
        val stage = stageOf(path, params)
        if (stage.isRoot) throw Refused(s.tool("path_error_move_root"))
        refuseClosed(path, stage)
        val parentId = params.optString("parent").takeIf { it.isNotEmpty() } ?: stage.parent!!
        val parent = path.stage(parentId) ?: throw Refused(s.tool("path_error_stage").format(parentId))
        if (parent.run != stage.run) throw Refused(s.tool("path_error_move_run"))
        // Not into itself, nor into what it holds
        var up: Stage? = parent
        while (up != null) {
            if (up.id == stage.id) throw Refused(s.tool("path_error_move_inside").format(stage.name))
            up = up.parent?.let { path.stage(it) }
        }
        if (stage.kind !in (path.grammar.kind(parent.kind)?.contains ?: emptyList())) {
            throw Refused(s.tool("path_error_contains").format(stage.name, path.grammar.kind(stage.kind)?.name ?: stage.kind, parent.kind))
        }
        // Its new siblings in order, itself put at its place: their positions written again
        val siblings = path.stages.filter { it.parent == parent.id && it.id != stage.id }.sortedBy { it.position }.toMutableList()
        val at = (if (params.has("position")) params.optInt("position") else siblings.size).coerceIn(0, siblings.size)
        siblings.add(at, stage)
        siblings.forEachIndexed { i, sibling ->
            if (sibling.position != i || sibling.id == stage.id) setState(sibling.id, mapOf(PathStages.POSITION to i) + if (sibling.id == stage.id) mapOf(PathStages.PARENT to parent.id) else emptyMap())
        }
        // The old siblings close the gap
        if (parent.id != stage.parent) {
            path.stages.filter { it.parent == stage.parent && it.id != stage.id }.sortedBy { it.position }.forEachIndexed { i, sibling ->
                if (sibling.position != i) setState(sibling.id, mapOf(PathStages.POSITION to i))
            }
        }
        refuseLoop(path.toolId, PathOrder.linkTypesRead(path.grammar))
        return OperationResult.success(mapOf("id" to stage.id, "parent" to parent.id, "position" to at))
    }

    private suspend fun deleteStage(path: PathRead, params: JSONObject): OperationResult {
        val stage = stageOf(path, params)
        val gone = mutableSetOf<String>()
        fun collect(id: String) { gone += id; path.stages.filter { it.parent == id }.forEach { collect(it.id) } }
        collect(stage.id)
        // The links that arrived from what goes are removed from what stays
        for (other in path.stages.filter { it.id !in gone && it.links.any { l -> l.from in gone } }) {
            setState(other.id, mapOf(PathStages.LINKS to other.links.filter { it.from !in gone }.map { mapOf("from" to it.from, "type" to it.type) }))
        }
        // Content first, its parent last
        for (id in gone.reversed()) {
            val result = coordinator.processUserAction("tool_data.delete", mapOf("id" to id))
            if (!result.isSuccess) throw Refused(result.error ?: "")
        }
        return OperationResult.success(mapOf("deleted" to gone.size))
    }

    /** Refuses fields [kind] does not have; their values are held to their types by the entry's own check. */
    private fun refuseFields(kind: Kind, fields: Map<String, Any?>, name: String) {
        fields.keys.firstOrNull { key -> kind.fields.none { it.name == key } }?.let {
            throw Refused(s.tool("path_error_field").format(name, it, kind.name, kind.fields.joinToString(", ") { f -> f.name }.ifEmpty { "-" }))
        }
    }

    /** Refuses the plan as now written when links of a type a rule reads make stages wait for one another. */
    private suspend fun refuseLoop(toolId: String, readTypes: Set<String>) {
        val path = PathRead.of(context, toolId) ?: return
        for ((_, run) in path.stages.groupBy { it.run }) {
            val loop = PathOrder.of(run, readTypes) as? PathOrder.Result.Loop ?: continue
            throw Refused(s.tool("path_problem_loop").format(loop.stages.joinToString(", ") { it.name }))
        }
    }

    // -----------------------------------------------------------------------------------------
    // The journal
    // -----------------------------------------------------------------------------------------

    private suspend fun note(path: PathRead, params: JSONObject): OperationResult {
        val stage = stageOf(path, params)
        refuseClosed(path, stage)
        val kind = path.grammar.kind(stage.kind) ?: throw Refused(s.tool("path_problem_kind").format(stage.kind))
        val key = params.optString("state")
        val state = kind.state(key)?.takeIf { it.manual }
            ?: throw Refused(s.tool("path_error_note_state").format(key, kind.name, kind.states.filter { it.manual }.joinToString(", ") { it.key }.ifEmpty { "-" }))
        val now = System.currentTimeMillis()
        refuseNote(path, stage, state, now)?.let { throw Refused(it) }
        val at = (params.opt("at") as? Number)?.toLong() ?: now
        val move = Move(at, state.key, params.optString("note").takeIf { it.isNotBlank() })
        setState(stage.id, mapOf(PathStages.MOVES to (stage.moves + move).sortedBy { it.at }.map { it.toJson().let { j -> JsonUtils.toMap(j) } }))
        return OperationResult.success(mapOf("id" to stage.id, "state" to state.key, "at" to at))
    }

    /** Why [state] may not be noted on [stage] now (its `note_if`, judged on the Path as it is); null when it may. */
    suspend fun refuseNote(path: PathRead, stage: Stage, state: StateRule, now: Long): String? {
        val rule = state.noteIf ?: return null
        val outcome = path.replay(now)
        return when (val judged = path.rules().judge(rule, path.selfOf(stage, outcome), path.rowsOf(outcome), now)) {
            is PathRules.Judged.Answer -> if (judged.holds == true) null else s.tool("path_error_note_if").format(state.name, stage.name)
            is PathRules.Judged.Failed -> judged.message
        }
    }

    private suspend fun unnote(path: PathRead, params: JSONObject): OperationResult {
        val stage = stageOf(path, params)
        refuseClosed(path, stage)
        val kind = path.grammar.kind(stage.kind)
        val last = stage.moves.lastOrNull { it.state != Move.START && it.state != Move.END }
            ?.takeIf { kind?.state(it.state)?.manual == true } ?: throw Refused(s.tool("path_error_unnote").format(stage.name))
        setState(stage.id, mapOf(PathStages.MOVES to (stage.moves - last).map { JsonUtils.toMap(it.toJson()) }))
        return OperationResult.success(mapOf("id" to stage.id, "removed" to last.state))
    }

    // -----------------------------------------------------------------------------------------
    // The runs
    // -----------------------------------------------------------------------------------------

    private suspend fun start(path: PathRead, params: JSONObject): OperationResult {
        val now = System.currentTimeMillis()
        path.currentRun()?.let { close(path, it, now) }
        val root = open(path, params.optString("name").takeIf { it.isNotBlank() }, now, copy = params.optString("plan") != "empty")
        return OperationResult.success(mapOf("run" to root))
    }

    /**
     * A run opened at [at], named [name] or as the latest run, its plan copied from the latest
     * run when [copy] and there is one, an empty plan otherwise. Its planned end is the rhythm's
     * (PathGrammar.plannedEnd): [at] plus its length, the next start of its schedule if it comes
     * first, none without either.
     */
    private suspend fun open(path: PathRead, name: String?, at: Long, copy: Boolean): String {
        val latest = path.runs().firstOrNull()
        val rootKind = path.grammar.kind(path.grammar.rootKind) ?: throw Refused(s.tool("path_problem_kind").format(path.grammar.rootKind))
        val named = name ?: latest?.name ?: rootKind.name
        val plannedEnd = path.grammar.plannedEnd(at)
        return if (copy && latest != null) copyPlan(path, latest, named, at, plannedEnd)
        else create(path.toolId, named, emptyMap(), PathStages.state(rootKind.key, null, 0, emptyList(), listOf(Move(at, Move.START)), plannedEnd = plannedEnd))
    }

    /**
     * A new run whose plan is [from]'s: its stages copied with their plan fields, never the
     * fields to fill nor the moves; what designates a stage of [from] — a link, a Reference field
     * of the plan — designating its copy. Its root opened at [now].
     */
    private suspend fun copyPlan(path: PathRead, from: Stage, name: String, now: Long, plannedEnd: Long?): String {
        val stages = PathOrder.planOrder(path.stages.filter { it.run == from.run })
        val ids = mutableMapOf<String, String>()
        fun translated(value: Any?): Any? = when (value) {
            is Map<*, *> -> if (value["kind"] == "ENTRY" && value["id"] in ids) value + ("id" to ids[value["id"]]) else value
            is List<*> -> value.map { translated(it) }
            else -> value
        }
        // Parents before content, as the plan's order goes; links and references once all are known
        for (stage in stages) {
            val kind = path.grammar.kind(stage.kind)
            val fields = stage.fields.filterKeys { key -> kind?.planFields?.any { it.name == key } == true }
            ids[stage.id] = create(path.toolId, if (stage.isRoot) name else stage.name, fields,
                PathStages.state(stage.kind, stage.parent?.let { ids.getValue(it) }, stage.position, emptyList(), if (stage.isRoot) listOf(Move(now, Move.START)) else emptyList(),
                    plannedEnd = plannedEnd.takeIf { stage.isRoot }))
        }
        for (stage in stages) {
            val kind = path.grammar.kind(stage.kind)
            val references = kind?.planFields?.filter { it.type == FieldType.REFERENCE }?.mapNotNull { f -> stage.fields[f.name]?.let { f.name to translated(it) } }.orEmpty()
            val links = stage.links.filter { it.from in ids }.map { mapOf("from" to ids.getValue(it.from), "type" to it.type) }
            if (references.isEmpty() && links.isEmpty()) continue
            val result = coordinator.processUserAction("tool_data.update", buildMap {
                put("id", ids.getValue(stage.id))
                if (references.isNotEmpty()) put("data", references.toMap())
                if (links.isNotEmpty()) put("state", mapOf(PathStages.LINKS to links))
            })
            if (!result.isSuccess) throw Refused(result.error ?: "")
        }
        return ids.getValue(from.id)
    }

    private suspend fun stop(path: PathRead, params: JSONObject): OperationResult {
        val run = params.optString("run").takeIf { it.isNotEmpty() }?.let { path.stage(it)?.takeIf { s -> s.isRoot } ?: throw Refused(s.tool("path_error_run").format(it)) }
            ?: path.currentRun() ?: throw Refused(s.tool("path_error_no_run"))
        if (path.isClosed(run)) throw Refused(s.tool("path_error_closed"))
        close(path, run, System.currentTimeMillis())
        return OperationResult.success(mapOf("run" to run.id))
    }

    private suspend fun validate(path: PathRead, params: JSONObject): OperationResult {
        val run = path.stage(params.optString("run"))?.takeIf { it.isRoot } ?: throw Refused(s.tool("path_error_run").format(params.optString("run")))
        if (path.entity(run.id)?.state?.let { JSONObject(it).has(PathStages.AWAITING) } != true) throw Refused(s.tool("path_error_not_awaiting").format(run.name))
        val now = System.currentTimeMillis()
        close(path, run, now)
        chain(path, now)
        return OperationResult.success(mapOf("run" to run.id))
    }

    /** The run [root] heads closed at [now]: each stage's state frozen on it, the root's end written. */
    private suspend fun close(path: PathRead, root: Stage, now: Long) {
        val outcome = path.replay(now)
        for (stage in path.stages.filter { it.run == root.id }) {
            val frozen = mutableMapOf<String, Any?>(PathStages.FINAL_STATE to outcome.states[stage.id]?.state)
            if (stage.isRoot) {
                frozen[PathStages.MOVES] = (stage.moves + Move(now, Move.END)).map { JsonUtils.toMap(it.toJson()) }
                frozen[PathStages.AWAITING] = null
            }
            setState(stage.id, frozen)
        }
    }

    /**
     * The judge (« Les états », who writes a `record`): the records the replay at now finds,
     * written; then each open run that reached its end — its root in a state that closes it, or
     * its planned end passed — closed, or offered for validation when the Path asks for it.
     */
    private suspend fun judge(path: PathRead): OperationResult {
        val now = System.currentTimeMillis()
        val outcome = path.replay(now, judge = true)
        for ((id, moves) in outcome.records.groupBy({ it.first }, { it.second })) {
            val stage = path.stage(id) ?: continue
            setState(id, mapOf(PathStages.MOVES to (stage.moves + moves).map { JsonUtils.toMap(it.toJson()) }))
        }
        val written = if (outcome.records.isEmpty()) path else PathRead.of(context, path.toolId) ?: return OperationResult.success(emptyMap())
        // Told before a run closes, while its stages still compute
        tell(written, now)
        val fresh = PathRead.of(context, path.toolId) ?: return OperationResult.success(emptyMap())
        val ending = fresh.runs().filter { !fresh.isClosed(it) }.filter { root -> root.id in outcome.closes || (root.plannedEnd != null && root.plannedEnd <= now) }
        var closed = 0
        for (root in ending) {
            if (!path.grammar.confirm) { close(fresh, root, now); closed++ }
            else if (fresh.entity(root.id)?.state?.let { JSONObject(it).has(PathStages.AWAITING) } != true) {
                setState(root.id, mapOf(PathStages.AWAITING to now))
                if (path.grammar.notifyValidation) notifyAwaiting(path, root, outcome)
            }
        }
        // The rhythm: the next run chained once one closed, or opened at its schedule's start
        if (closed > 0) chain(fresh, now)
        if (path.grammar.opens == Opens.SCHEDULED) PathRead.of(context, path.toolId)?.let { scheduled(it, now) }
        // Then the stages of a run the rhythm opened
        if (closed > 0 || path.grammar.opens == Opens.SCHEDULED) PathRead.of(context, path.toolId)?.let { tell(it, now) }
        return OperationResult.success(mapOf("records" to outcome.records.size, "closed" to closed))
    }

    /** Says that the run [root] heads awaits its validation, with the state its root holds for now; it opens the Path. */
    private suspend fun notifyAwaiting(path: PathRead, root: Stage, outcome: PathReplay.Outcome) {
        val state = outcome.states[root.id]?.state?.let { path.grammar.kind(root.kind)?.state(it)?.name } ?: "–"
        val sent = coordinator.processUserAction("notifications.send", mapOf(
            "title" to path.config.optString("name").ifEmpty { s.tool("display_name") },
            "content" to s.tool("notification_awaiting").format(root.name, state),
            "priority" to "default",
            "icon_name" to path.config.optString("icon_name").ifEmpty { PathToolType.getDefaultIconName() },
            "tool_instance_id" to path.toolId
        ))
        if (!sent.isSuccess) app.treelune.core.utils.LogManager.service("Path ${path.toolId}: ${root.name} awaiting, not notified: ${sent.error}", "ERROR")
    }

    /** For a Path whose runs are chained, the next opened at [now], its plan copied, unless one is open already. */
    private suspend fun chain(path: PathRead, now: Long) = rhythm.withLock {
        if (path.grammar.opens != Opens.CHAINED) return@withLock
        val fresh = PathRead.of(context, path.toolId) ?: return@withLock
        if (fresh.runs().none { !fresh.isClosed(it) && fresh.entity(it.id)?.state?.let { s -> JSONObject(s).has(PathStages.AWAITING) } != true }) open(fresh, null, now, copy = true)
    }

    /**
     * For a Path whose runs open on a schedule, the run of the last start come since the latest
     * run opened (since the Path was made, for the first), the starts in between not caught up,
     * opened at that start, its plan copied: the run before, if still open, closes first, unless
     * it awaits validation, which it keeps waiting for. Read again under the lock: two judges at
     * once (a tick, a change of the Path) would each open the same run.
     */
    private suspend fun scheduled(stale: PathRead, now: Long): Unit = rhythm.withLock {
        val path = PathRead.of(context, stale.toolId) ?: return@withLock
        val latest = path.runs().firstOrNull()?.moves?.firstOrNull { it.state == Move.START }?.at
        var cursor = latest ?: AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(path.toolId)?.created_at ?: return@withLock
        var due: Long? = null
        var steps = 0
        while (steps++ < MAX_STEPS) {
            val next = path.grammar.nextStart(cursor) ?: break
            if (next > now || next <= cursor) break
            due = next
            cursor = next
        }
        val start = due ?: return@withLock
        path.currentRun()?.let { current ->
            if (path.entity(current.id)?.state?.let { JSONObject(it).has(PathStages.AWAITING) } != true) close(path, current, now)
        }
        open(PathRead.of(context, path.toolId) ?: return@withLock, null, start, copy = true)
    }

    /**
     * What the app tells when a stage enters a state (EventPoints): for each stage of an open run
     * whose state is not the one last told, its state's notification and event, if it has them;
     * the state told noted on the stage, out of the history. A stage seen for the first time
     * tells the state it is in.
     */
    private suspend fun tell(path: PathRead, now: Long) {
        val tellers = path.grammar.kinds.filter { kind -> kind.states.any { it.notify || it.emitEvent != null } }.map { it.key }.toSet()
        if (tellers.isEmpty()) return
        val outcome = path.replay(now)
        val title = path.config.optString("name").ifEmpty { s.tool("display_name") }
        for (stage in path.stages.filter { it.kind in tellers }) {
            val root = path.stage(stage.run) ?: continue
            if (path.isClosed(root)) continue
            val state = outcome.states[stage.id]?.state ?: continue
            val told = path.entity(stage.id)?.state?.let { JSONObject(it).optString(PathStages.TOLD).takeIf { t -> t.isNotEmpty() } }
            if (state == told) continue
            val rule = path.grammar.kind(stage.kind)?.state(state)
            if (rule?.notify == true) {
                val sent = coordinator.processUserAction("notifications.send", mapOf(
                    "title" to title,
                    "content" to s.tool("notification_entered").format(stage.name, rule.name),
                    "priority" to "default",
                    "icon_name" to (path.grammar.kind(stage.kind)?.icon ?: path.config.optString("icon_name").ifEmpty { PathToolType.getDefaultIconName() }),
                    "tool_instance_id" to path.toolId
                ))
                if (!sent.isSuccess) app.treelune.core.utils.LogManager.service("Path ${path.toolId}: ${stage.name} not notified: ${sent.error}", "ERROR")
            }
            rule?.emitEvent?.let { app.treelune.core.events.EventPoints.emit(context, app.treelune.core.events.EventPoints.stored(it), stage.id) }
            setState(stage.id, mapOf(PathStages.TOLD to state))
        }
    }

    private suspend fun state(path: PathRead): OperationResult {
        val outcome = path.replay(System.currentTimeMillis())
        return OperationResult.success(mapOf(
            "stages" to outcome.rows,
            "problems" to outcome.problems.map { problem -> listOfNotNull(problem.stage?.let { "stage" to it }, "message" to problem.message).toMap() }
        ))
    }

    // -----------------------------------------------------------------------------------------

    private fun stageOf(path: PathRead, params: JSONObject): Stage {
        val id = params.optString("stage").takeIf { it.isNotEmpty() } ?: throw Refused(s.shared("service_error_missing_required_params").format("stage"))
        return path.stage(id) ?: throw Refused(s.tool("path_error_stage").format(id))
    }

    /** A closed run's stages are inert data: no plan change nor move written by the Path's operations there. */
    private fun refuseClosed(path: PathRead, stage: Stage) {
        val root = path.stage(stage.run) ?: return
        if (path.isClosed(root)) throw Refused(s.tool("path_error_closed"))
    }

    private suspend fun create(toolId: String, name: String, fields: Map<String, Any?>, state: Map<String, Any?>): String {
        val result = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "name" to name, "data" to fields, "state" to state))
        if (!result.isSuccess) throw Refused(result.error ?: "")
        return result.data?.get("id") as String
    }

    private suspend fun setState(id: String, state: Map<String, Any?>) {
        val result = coordinator.processUserAction("tool_data.update", mapOf("id" to id, "state" to state))
        if (!result.isSuccess) throw Refused(result.error ?: "")
    }

    private fun objects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it) }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = PathRows.TOOLTYPE, context = context)
        return s.tool("verbalize_$operation").takeIf { operation in OPERATIONS } ?: s.shared("action_verbalize_unknown")
    }

    companion object {
        /** Every operation, each verbalised by its own text. */
        val OPERATIONS = setOf("write_plan", "update_stage", "move_stage", "delete_stage", "note", "unnote", "start", "stop", "validate", "judge", "state")

        /** The operations that change the Path, after which the judge passes. */
        private val CHANGES = setOf("write_plan", "update_stage", "move_stage", "delete_stage", "note", "unnote", "start")

        /** Held while the rhythm opens a run, read again under it, so that no two judges open the same one. */
        private val rhythm = kotlinx.coroutines.sync.Mutex()

        /** Guards the walk through a schedule's starts. */
        private const val MAX_STEPS = 10_000
    }
}
