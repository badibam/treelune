package app.treelune.core.tasks

import android.content.Context
import android.net.Uri
import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.apis.ApiService
import app.treelune.core.apis.ApiSettings
import app.treelune.core.coordinator.ChainLink
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.ServiceRegistry
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FileField
import app.treelune.core.files.FilePreparation
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Runs a Task's steps (docs/CORE.md, « Tasks »), one after another, each through the dispatcher
 * as the Task's (Coordinator.processTaskStep: a step of the history of its own), its inputs read first: a
 * term at the run's instant, what an earlier step gave. A step that fails ends the run; a deferred
 * API's leaves it waiting, asked again by the scheduler (TaskScheduler) until ready.
 *
 * A run is written after each step: the app stopping between two steps leaves it as it was, and a
 * run found going on at the next start is said interrupted rather than run again — a step made twice
 * would write twice.
 */
object TaskRunner {

    /** The runs going on in this process: one found going on and not here was cut by the app stopping. */
    private val active: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Where a run asked from a screen goes on, the screen left or not. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The longest a file an API gives may take to come down. */
    private val DOWNLOADS = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).build()

    fun isActive(runId: String) = runId in active

    /** A step's failure, said in words. */
    private class StepFailed(message: String, val raw: String? = null) : Exception(message)

    /**
     * A run of [task] started by [trigger], reading at [at], continuing the chain [chainId] or
     * starting its own when null (Chains), [received] what a trigger gave it: its row written,
     * then its steps run, awaited when [awaited] (the scheduler's tick, which the phone stays
     * awake for), in the app's own scope otherwise. Asked while another run of the Task goes on,
     * a trigger's run waits its turn (queued), started once that one ends. Its id.
     */
    suspend fun start(context: Context, task: TaskEntity, trigger: TaskRun.Trigger, at: Long, awaited: Boolean, chainId: String? = null, received: TaskRun.Received? = null): String {
        val now = System.currentTimeMillis()
        val dao = AppDatabase.getDatabase(context).taskDao()
        val busy = dao.getRunsIn(listOf(TaskRun.State.QUEUED.key, TaskRun.State.RUNNING.key, TaskRun.State.WAITING.key)).any { it.taskId == task.id }
        val state = if (busy) TaskRun.State.QUEUED else TaskRun.State.RUNNING
        val run = TaskRunEntity(UUID.randomUUID().toString(), task.id, trigger.key, at, now, null, state.key, 0, "[]", null, null, chainId, received?.toJson())
        dao.insertRun(run)
        LogManager.service("Task ${task.id} run ${run.id} ${state.key} (${trigger.key})", "INFO")
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()
        if (busy) return run.id
        if (awaited) proceed(context, run.id) else scope.launch { proceed(context, run.id) }
        return run.id
    }

    /** The oldest run of [taskId] waiting its turn started, when there is one. */
    suspend fun next(context: Context, taskId: String) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        val queued = dao.getNextQueued(taskId) ?: return
        // Its reading instant is when it was asked: what it reads is what was there then
        dao.updateRun(queued.copy(state = TaskRun.State.RUNNING.key))
        LogManager.service("Task $taskId run ${queued.id} takes its turn", "INFO")
        scope.launch { proceed(context, queued.id) }
    }

    /**
     * Run [runId] going on from the step it stands at, until it ends or waits; what its steps
     * start continues its chain (ChainLink).
     */
    suspend fun proceed(context: Context, runId: String) {
        if (!active.add(runId)) return
        try {
            val dao = AppDatabase.getDatabase(context).taskDao()
            val started = dao.getRun(runId) ?: return
            withContext(ChainLink(started.chainId ?: started.id)) { steps(context, started) }
        } finally {
            active.remove(runId)
        }
    }

    /** The steps of [started] run from the one it stands at, until it ends or waits. */
    private suspend fun steps(context: Context, started: TaskRunEntity) {
        val runId = started.id
        val dao = AppDatabase.getDatabase(context).taskDao()
        var run = started
        val task = dao.get(run.taskId) ?: return
        val steps = org.json.JSONArray(task.stepsJson)
        while (run.step < steps.length()) {
            // A run cancelled meanwhile stops before its next step
            if (TaskRun.State.of(dao.getRun(runId)?.state ?: return).ended) return
            val step = steps.getJSONObject(run.step)
            val results = TaskRun.results(run.resultsJson)
            val outcome = try {
                val received = run.receivedJson?.let { TaskRun.Received.of(it) }
                Outcome.Done(skipped(context, task, steps, run.step, results, run.scheduledAt, received)
                    ?: if (TaskSteps.repeats(step)) repeated(context, task, steps, run.step, results, run.scheduledAt, received)
                    else runStep(context, step, steps, results, run.scheduledAt, received))
            } catch (e: StepFailed) {
                Outcome.Failed(e.message ?: "", e.raw)
            } catch (e: Waits) {
                Outcome.Waiting(e.result, e.wait)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogManager.service("Task ${task.id} run $runId step ${run.step + 1}: ${e.message}", "ERROR", e)
                Outcome.Failed(e.message ?: e.javaClass.simpleName, null)
            }
            run = when (outcome) {
                is Outcome.Done -> run.copy(step = run.step + 1, resultsJson = TaskRun.resultsJson(results + outcome.result))
                is Outcome.Waiting -> run.copy(state = TaskRun.State.WAITING.key, resultsJson = TaskRun.resultsJson(results + outcome.result), waitJson = outcome.wait.toJson())
                is Outcome.Failed -> run.copy(resultsJson = TaskRun.resultsJson(results + TaskRun.StepResult(summary(context, step), raw = outcome.raw, error = outcome.message, at = System.currentTimeMillis())))
            }
            dao.updateRun(run)
            when (outcome) {
                is Outcome.Waiting -> { LogManager.service("Task ${task.id} run $runId waits on step ${run.step + 1}", "INFO"); return }
                is Outcome.Failed -> { end(context, task, run, TaskRun.State.FAILED); return }
                is Outcome.Done -> Unit
            }
        }
        end(context, task, run, TaskRun.State.DONE)
    }

    private sealed interface Outcome {
        data class Done(val result: TaskRun.StepResult) : Outcome
        data class Waiting(val result: TaskRun.StepResult, val wait: TaskRun.Wait) : Outcome
        data class Failed(val message: String, val raw: String?) : Outcome
    }

    /** A step that started a deferred job: its result so far, and the wait. */
    private class Waits(val result: TaskRun.StepResult, val wait: TaskRun.Wait) : Exception()

    /**
     * The waiting run [run] asked where its job stands, at [now]: not ready, it waits longer, up to
     * its deadline; failed or past it, the run fails; ready, the values join the step's result and
     * the run goes on.
     */
    suspend fun poll(context: Context, run: TaskRunEntity, now: Long) {
        if (run.id in active) return
        val dao = AppDatabase.getDatabase(context).taskDao()
        val task = dao.get(run.taskId) ?: return
        val s = Strings.`for`(context = context)
        val wait = TaskRun.Wait.of(run.waitJson ?: return)
        if (now < wait.nextAt) return
        val results = TaskRun.results(run.resultsJson)
        fun withLast(result: TaskRun.StepResult) = TaskRun.resultsJson(results.dropLast(1) + result)
        val asked = Coordinator(context).processScheduledTask("apis.status", mapOf("api_id" to wait.apiId, "job_id" to wait.jobId))
        val last = results.last()
        val raw = (asked.data?.get("raw") as? String) ?: last.raw
        when {
            !asked.isSuccess || asked.data?.get("status") == "failed" -> {
                val failed = run.copy(resultsJson = withLast(last.copy(raw = raw, error = asked.error ?: s.shared("task_error_job_failed"))), waitJson = null)
                dao.updateRun(failed)
                end(context, task, failed, TaskRun.State.FAILED)
            }
            asked.data?.get("status") == "ready" -> {
                @Suppress("UNCHECKED_CAST")
                val values = asked.data?.get("values") as Map<String, Any?>
                dao.updateRun(run.copy(state = TaskRun.State.RUNNING.key, step = run.step + 1, waitJson = null,
                    resultsJson = withLast(last.copy(values = values, raw = raw, at = now))))
                LogManager.service("Task ${task.id} run ${run.id}: job ${wait.jobId} ready", "INFO")
                proceed(context, run.id)
            }
            now >= wait.deadline -> {
                val failed = run.copy(resultsJson = withLast(last.copy(raw = raw, error = s.shared("task_error_job_late"))), waitJson = null)
                dao.updateRun(failed)
                end(context, task, failed, TaskRun.State.FAILED)
            }
            else -> dao.updateRun(run.copy(waitJson = wait.again(now).toJson(), resultsJson = withLast(last.copy(raw = raw))))
        }
    }

    /**
     * A run found going on when no process runs it: cut by the app stopping. Said interrupted, its
     * step not run twice.
     */
    suspend fun interrupted(context: Context, run: TaskRunEntity) {
        if (run.id in active) return
        val task = AppDatabase.getDatabase(context).taskDao().get(run.taskId) ?: return
        LogManager.service("Task ${task.id} run ${run.id} found cut at step ${run.step + 1}", "WARN")
        end(context, task, run, TaskRun.State.INTERRUPTED)
    }

    /** [run] ended as [state]: written, and waiting to be read or not as its Task's setting says, a notification with it. */
    suspend fun end(context: Context, task: TaskEntity, run: TaskRunEntity, state: TaskRun.State) {
        val now = System.currentTimeMillis()
        val problem = state != TaskRun.State.DONE
        val waits = when (task.notifyEnd) {
            RoutineSettings.NOTIFY_ALWAYS -> state != TaskRun.State.CANCELLED
            RoutineSettings.NOTIFY_ON_PROBLEM -> problem && state != TaskRun.State.CANCELLED
            else -> false
        }
        AppDatabase.getDatabase(context).taskDao().updateRun(run.copy(state = state.key, endedAt = now, waitJson = null, seenAt = if (waits) null else now))
        LogManager.service("Task ${task.id} run ${run.id} ended: ${state.key}", "INFO")
        app.treelune.core.utils.DataChangeNotifier.notifyZonesChanged()
        // A run cancelled before its turn leaves the one going on in place
        if (TaskRun.State.of(run.state) != TaskRun.State.QUEUED) next(context, task.id)
        // A run that ends well emits its event, in its chain
        if (state == TaskRun.State.DONE && task.emitEvent != null) withContext(ChainLink(run.chainId ?: run.id)) {
            app.treelune.core.events.EventPoints.emit(context, app.treelune.core.events.EventPoints.stored(task.emitEvent), null)
        }
        if (!waits) return
        val s = Strings.`for`(context = context)
        Coordinator(context).processScheduledTask("notifications.send", mapOf(
            "title" to task.name,
            // Started by a condition, the end says what it read: "82.4 > 80"
            "content" to s.shared("task_run_end_${state.key}") +
                ((run.receivedJson?.let { TaskRun.Received.of(it) } as? TaskRun.Received.Condition)?.let { " · " + s.shared("task_run_end_condition").format(it.said) } ?: ""),
            "priority" to "default",
            "icon_name" to task.iconName,
            "place" to app.treelune.core.navigation.Place.TaskRun(run.id, task.id, task.zoneId).address
        ))
    }

    /** Where a `run_routine` step's blocks go, resolved, in the params of routines.execute_manual. */
    private const val JOINED = "joined"

    /** What [step] does, said in words: its service's verbalization of it. */
    private suspend fun summary(context: Context, step: JSONObject): String {
        val (action, params) = actionOf(step, emptyMap(), context)
        val (resource, operation) = action.split('.', limit = 2).let { it[0] to it[1] }
        return ServiceRegistry(context).getService(resource)?.verbalize(operation, JsonUtils.toJSONObject(params), context) ?: action
    }

    /** The operation [step] runs and its params, its inputs given as [resolved] (by name, a path for an entry's values). */
    private suspend fun actionOf(step: JSONObject, resolved: Map<String, Any?>, context: Context): Pair<String, Map<String, Any?>> = when (step.getString(TaskSteps.KIND)) {
        TaskSteps.CALL_API -> "apis.call" to mapOf("api_id" to step.getString(TaskSteps.API_ID), "inputs" to resolved)
        TaskSteps.CREATE_ENTRY -> "tool_data.create" to entryParams(step.getString(TaskSteps.TOOL_INSTANCE_ID), resolved)
        TaskSteps.UPDATE_ENTRY -> "tool_data.update" to entryParams(step.getString(TaskSteps.TOOL_INSTANCE_ID), resolved - TaskSteps.ENTRY) + ("id" to resolved[TaskSteps.ENTRY])
        TaskSteps.TOOL_OPERATION -> {
            val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(step.getString(TaskSteps.TOOL_INSTANCE_ID))
                ?: throw StepFailed(Strings.`for`(context = context).shared("task_error_tool").format("", step.getString(TaskSteps.TOOL_INSTANCE_ID)))
            "${tool.tooltype}.${step.getString(TaskSteps.OPERATION)}" to (resolved + ("tool_instance_id" to tool.id))
        }
        TaskSteps.RUN_ROUTINE -> "routines.execute_manual" to buildMap {
            put("routine_id", step.getString(TaskSteps.ROUTINE_ID))
            resolved[JOINED]?.let { put(JOINED, it) }
        }
        TaskSteps.EMIT_EVENT -> "events.emit" to mapOf("event_id" to step.getString(TaskSteps.EVENT_ID), "values" to resolved)
        else -> throw StepFailed(step.getString(TaskSteps.KIND))
    }

    /** An entry's params from its values by path: name and timestamp at the top, the others in data and extra. */
    private fun entryParams(toolId: String, values: Map<String, Any?>): Map<String, Any?> {
        val data = values.filterKeys { it.startsWith("data.") }.mapKeys { it.key.removePrefix("data.") }
        val extra = values.filterKeys { it.startsWith("extra.") }.mapKeys { it.key.removePrefix("extra.") }
        return buildMap {
            put("tool_instance_id", toolId)
            values["name"]?.let { put("name", it) }
            values["timestamp"]?.let { put("timestamp", it) }
            if (data.isNotEmpty()) put("data", data)
            if (extra.isNotEmpty()) put("extra", extra)
        }
    }

    /**
     * Step [index] of [steps] skipped, said why (StepConditions): it reads what a skipped step
     * gave, or one of its conditions does not hold — or cannot be known, a value it needs missing;
     * null when it runs. Its conditions read as its inputs do, at [at].
     */
    private suspend fun skipped(context: Context, task: TaskEntity, steps: org.json.JSONArray, index: Int, results: List<TaskRun.StepResult>, at: Long, received: TaskRun.Received?): TaskRun.StepResult? {
        val s = Strings.`for`(context = context)
        val step = steps.getJSONObject(index)
        StepConditions.resultsRead(step).sorted().firstOrNull { results.getOrNull(it)?.skipped != null }?.let { earlier ->
            return TaskRun.StepResult(summary(context, step), skipped = s.shared("task_run_skipped_step").format(earlier + 1), at = System.currentTimeMillis())
        }
        // A repeated step's conditions are judged for each element
        if (TaskSteps.repeats(step)) return null
        return unmet(context, task, steps, index, results, at, received, null)?.let { TaskRun.StepResult(summary(context, step), skipped = it, at = System.currentTimeMillis()) }
    }

    /**
     * Why step [index] of [steps] does not run, one of its conditions not holding or not known,
     * judged at [at] on the element [item] it stands at when repeated; null when it runs.
     */
    private suspend fun unmet(context: Context, task: TaskEntity, steps: org.json.JSONArray, index: Int, results: List<TaskRun.StepResult>, at: Long,
                              received: TaskRun.Received?, item: Map<String, Any?>?): String? {
        val s = Strings.`for`(context = context)
        val step = steps.getJSONObject(index)
        val conditions = try {
            StepConditions.of(step, index + 1, s)
        } catch (e: IllegalArgumentException) {
            // Checked when stored: one that does not read now is said as it is
            throw StepFailed(e.message ?: "")
        }
        if (conditions.isEmpty()) return null
        val triggers = app.treelune.core.triggers.Triggers.read(task.triggersJson)
        val fields = TaskSteps.receivedFields(triggers, context, s)
        val values = TaskSteps.receivedValues(triggers, context)
        val given = (0 until index).map { steps.getJSONObject(it).let { earlier -> if (TaskSteps.repeats(earlier)) emptyList() else TaskSteps.outputs(earlier, context) } }
        val items = TaskSteps.itemFields(step, given, s)
        conditions.forEachIndexed { i, condition ->
            val label = StepConditions.label(index + 1, i, s)
            val inputs = (listOf(condition.left) + condition.right).map { TaskSteps.input(it, label) { key -> s.shared(key) } }
            // Compared as the first side that has a type, a written value read in it
            val field = inputs.firstNotNullOfOrNull { StepConditions.fieldOf(it, given, fields, values, items, context, s, label) }
                ?: throw StepFailed(s.shared("condition_error_untyped").format(label))
            val read = inputs.map { resolve(context, it, results, at, received, item) }
            val holds = app.treelune.core.conditions.ConditionJudge.holds(field, condition.op, read.first(), read.drop(1),
                app.treelune.core.selection.TimeResolver.at(at)) { key -> s.shared(key) }
            if (holds != true) return s.shared(if (holds == null) "task_run_skipped_unknown" else "task_run_skipped_false").format(i + 1)
        }
        return null
    }

    /**
     * Step [index] of [steps] made once per element of the list it names (`for_each`), each
     * element whose conditions hold: all its writes one step of the history, opened here and
     * closed once every element is made — what it writes then starts what listens, as one
     * operation's writes do. An element that fails undoes what the ones before wrote, the step of
     * the history gone with them, and fails the step naming it: an API called or a routine run
     * stays done, the history going back on the database only.
     */
    private suspend fun repeated(context: Context, task: TaskEntity, steps: org.json.JSONArray, index: Int, results: List<TaskRun.StepResult>, at: Long, received: TaskRun.Received?): TaskRun.StepResult {
        val s = Strings.`for`(context = context)
        val step = steps.getJSONObject(index)
        val source = TaskSteps.input(step.getJSONObject(TaskSteps.FOR_EACH), TaskSteps.FOR_EACH) { s.shared(it) }
        val list = resolve(context, source, results, at, received) as? List<*> ?: throw StepFailed(s.shared("task_error_for_each").format(index + 1))
        val history = app.treelune.core.history.Step(app.treelune.core.coordinator.Source.SCHEDULER, isStep = true, sessionId = null)
        var made = 0
        try {
            withContext(history) {
                list.forEachIndexed { i, element ->
                    val item = (element as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
                    if (unmet(context, task, steps, index, results, at, received, item) != null) return@forEachIndexed
                    try {
                        runStep(context, step, steps, results, at, received, item)
                    } catch (e: StepFailed) {
                        throw StepFailed(s.shared("task_error_element").format(i + 1, e.message ?: ""), e.raw)
                    }
                    made++
                }
            }
        } catch (e: Exception) {
            // What the elements before wrote goes: the step is made whole or not at all
            when (val undone = app.treelune.core.history.Undo(context).undo(history.id, app.treelune.core.coordinator.Source.SCHEDULER)) {
                is app.treelune.core.history.Undo.Outcome.Done -> AppDatabase.getDatabase(context).historyDao().deleteSteps(listOf(history.id))
                // Nothing written leaves no step to undo
                is app.treelune.core.history.Undo.Outcome.Refused -> if (AppDatabase.getDatabase(context).historyDao().getStep(history.id) != null)
                    LogManager.service("Task ${task.id}: step ${index + 1}'s writes not undone: ${undone.reason}", "ERROR")
            }
            throw e
        }
        val summary = summary(context, step)
        app.treelune.core.history.Steps.close(context, history) { summary }
        try {
            app.treelune.core.triggers.EntryTriggers.dispatch(context) { app.treelune.core.triggers.EntryWrites.ofStep(context, history.id) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.service("Task ${task.id}: triggers of step ${index + 1} not run: ${e.message}", "ERROR", e)
        }
        return TaskRun.StepResult(summary, repeated = made, at = System.currentTimeMillis())
    }

    /** [step] run: its inputs read, its operation through the dispatcher, what it gave. */
    private suspend fun runStep(context: Context, step: JSONObject, steps: org.json.JSONArray, results: List<TaskRun.StepResult>, at: Long, received: TaskRun.Received?,
                                item: Map<String, Any?>? = null): TaskRun.StepResult {
        val s = Strings.`for`(context = context)
        val kind = step.getString(TaskSteps.KIND)
        // The fields an entry's values go to, for a file to come down into one
        val fields = if (kind == TaskSteps.CREATE_ENTRY || kind == TaskSteps.UPDATE_ENTRY)
            TaskSteps.writableFields(step.getString(TaskSteps.TOOL_INSTANCE_ID), context, s) else emptyMap()
        val holder = when (kind) {
            TaskSteps.CALL_API, TaskSteps.EMIT_EVENT -> step.optJSONObject(TaskSteps.INPUTS)
            TaskSteps.CREATE_ENTRY, TaskSteps.UPDATE_ENTRY -> step.optJSONObject(TaskSteps.VALUES)
            TaskSteps.TOOL_OPERATION -> step.optJSONObject(TaskSteps.PARAMS)
            else -> null
        }
        val resolved = buildMap<String, Any?> {
            holder?.keys()?.forEach { key ->
                val input = TaskSteps.input(holder.getJSONObject(key), key) { s.shared(it) }
                val value = resolve(context, input, results, at, received, item)
                val field = fields[key]
                put(key, when {
                    // A file an API gives comes down into the field, with that API's key on its server
                    field?.type == FieldType.FILE && value is String -> listOf(download(context, value, FileField.kinds(field.config), apiOf(input, steps)))
                    // The entry a step created, designated in a field that holds a Reference; a
                    // multiple one holds it as a list of one
                    field?.type == FieldType.REFERENCE && value is String -> mapOf("kind" to "ENTRY", "id" to value)
                        .let { if (app.treelune.core.fields.ReferenceTarget.isMultiple(field.config)) listOf(it) else it }
                    // An operation's entry, given by its id; an entry an event carries, too
                    kind == TaskSteps.TOOL_OPERATION && key == "id" -> idOf(value)
                    kind == TaskSteps.EMIT_EVENT -> idOf(value)
                    else -> value
                })
            }
            if (kind == TaskSteps.UPDATE_ENTRY) put(TaskSteps.ENTRY, idOf(resolve(context, TaskSteps.input(step.getJSONObject(TaskSteps.ENTRY), TaskSteps.ENTRY) { s.shared(it) }, results, at, received, item)))
            if (kind == TaskSteps.RUN_ROUTINE) put(JOINED, joined(context, step, results, at, received, item))
        }
        val summary = summary(context, step)
        // An entry already holding the value the step writes in its field: left as it is, or changed
        val existing = step.optJSONObject(TaskSteps.EXISTING)?.takeIf { kind == TaskSteps.CREATE_ENTRY }
        val found = existing?.let { found(context, step.getString(TaskSteps.TOOL_INSTANCE_ID), it.getString(TaskSteps.FIELD), resolved[it.getString(TaskSteps.FIELD)]) }
        if (found != null && existing.optString(TaskSteps.THEN) == TaskSteps.SKIP) return TaskRun.StepResult(summary, entryId = found, at = System.currentTimeMillis())
        // What it changes adds to what its text fields hold, where it says so
        val written = appended(context, step, resolved, if (kind == TaskSteps.UPDATE_ENTRY) idOf(resolved[TaskSteps.ENTRY]) as? String else found)
        val (action, params) = if (found != null) "tool_data.update" to entryParams(step.getString(TaskSteps.TOOL_INSTANCE_ID), written) + ("id" to found)
            else actionOf(step, written, context)
        val done = Coordinator(context).processTaskStep(action, params)
        val raw = done.data?.get("raw") as? String
        if (!done.isSuccess) throw StepFailed(done.error ?: s.shared("error_unknown"), raw)
        val now = System.currentTimeMillis()
        return when (kind) {
            TaskSteps.CALL_API -> {
                val api = AppDatabase.getDatabase(context).apiDao().get(step.getString(TaskSteps.API_ID))
                val config = JSONObject(api?.configJson ?: "{}")
                val types = TaskSteps.outputs(step, context).associate { it.value!! to it.type }
                val job = done.data?.get("job_id") as? String
                if (job != null) {
                    val limit = (ApiSettings.read(config, context).number(ApiSettings.WAIT_LIMIT) ?: 0).toLong()
                    throw Waits(TaskRun.StepResult(summary, types = types, raw = raw, at = now),
                        TaskRun.Wait(step.getString(TaskSteps.API_ID), job, now + TaskRun.Wait.FIRST_INTERVAL, TaskRun.Wait.FIRST_INTERVAL, now + limit))
                }
                @Suppress("UNCHECKED_CAST")
                TaskRun.StepResult(summary, values = done.data?.get("values") as Map<String, Any?>, types = types, raw = raw, at = now)
            }
            TaskSteps.CREATE_ENTRY -> TaskRun.StepResult(summary, entryId = found ?: done.data?.get("id") as? String, at = now)
            else -> TaskRun.StepResult(summary, at = now)
        }
    }

    /**
     * The most recent entry of [toolId] holding [value] in [field], as a filter compares it; null
     * when none does, or for no value: nothing is found by an empty one.
     */
    private suspend fun found(context: Context, toolId: String, field: String, value: Any?): String? {
        if (value == null || value == "") return null
        val read = Coordinator(context).processTaskStep("tool_data.get", mapOf("tool_instance_id" to toolId,
            "filters" to listOf(JsonUtils.toMap(app.treelune.core.conditions.Conditions.onField(field, "=", value))), "limit" to 1))
        if (!read.isSuccess) throw StepFailed(read.error ?: Strings.`for`(context = context).shared("error_unknown"))
        return ((read.data?.get("entries") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("id") as? String
    }

    /**
     * [resolved] with the value of each text field [step] adds to (`append`) put on a line of its
     * own after what the entry [entryId] holds there; as they are when it changes none, or the
     * field holds nothing yet, or the value is empty.
     */
    private suspend fun appended(context: Context, step: JSONObject, resolved: Map<String, Any?>, entryId: String?): Map<String, Any?> {
        val paths = step.optJSONArray(TaskSteps.APPEND)?.let { list -> (0 until list.length()).map { list.optString(it) }.toSet() }.orEmpty()
        if (paths.isEmpty() || entryId == null) return resolved
        val row = AppDatabase.getDatabase(context).toolDataDao().getById(entryId) ?: return resolved
        val entry = TaskSteps.entryMap(row)
        return resolved.mapValues { (path, value) ->
            if (path !in paths || value == null || value == "") value
            else (app.treelune.core.reading.FieldReading.valueAt(entry, path) as? String)?.takeIf { it.isNotBlank() }?.let { "$it\n$value" } ?: value
        }
    }

    /**
     * The entries of [selection] read at [at] as a list, in the order they happened: the values
     * of its one field kept, or an object per entry of its fields, each by the last part of its
     * path (TaskSteps.keyOf), the entry's own id as `id`; an entry designated by its id.
     */
    private suspend fun listed(context: Context, selection: app.treelune.core.selection.EntrySelection, at: Long): List<Any?> {
        val s = Strings.`for`(context = context)
        val toolId = selection.target.id ?: throw StepFailed(s.shared("task_error_list_selection").format(""))
        val fields = try {
            app.treelune.core.fields.ToolFields.filterable(toolId, context, s)
        } catch (e: IllegalStateException) {
            throw StepFailed(s.shared("task_error_tool").format("", toolId))
        }
        val read = Coordinator(context).processTaskStep("tool_data.get", mapOf("tool_instance_id" to toolId,
            "filters" to JsonUtils.toList(selection.storedFilters(fields, app.treelune.core.selection.TimeResolver.at(at)) { s.shared(it) }),
            "sort" to listOf(mapOf("field" to "timestamp", "direction" to app.treelune.core.fields.EntrySort.ASC))))
        if (!read.isSuccess) throw StepFailed(read.error ?: s.shared("error_unknown"))
        val kept = selection.fields.orEmpty()
        return (read.data?.get("entries") as? List<*>).orEmpty().map { it as Map<*, *> }.map { entry ->
            @Suppress("UNCHECKED_CAST")
            val values = kept.map { path ->
                val value = app.treelune.core.reading.FieldReading.valueAt(entry as Map<String, Any?>, path)
                TaskSteps.keyOf(path) to ((value as? Map<*, *>)?.takeIf { fields[path]?.type == FieldType.REFERENCE }?.get("id") ?: value)
            }
            if (values.size == 1) values.single().second else values.toMap()
        }
    }

    /** The API whose answer [input] reads, when it is a value an earlier step's call gave. */
    private fun apiOf(input: TaskSteps.Input, steps: org.json.JSONArray): String? =
        (input as? TaskSteps.Input.Result)?.let { steps.optJSONObject(it.step) }
            ?.takeIf { it.optString(TaskSteps.KIND) == TaskSteps.CALL_API }?.optString(TaskSteps.API_ID)?.ifEmpty { null }

    /**
     * The blocks of the `run_routine` [step] (StepBlocks) as its routine's (RoutineMessage), which
     * join the end of its message: a text and a pointer as written; an entry the run gives as a
     * pointer to it, the entry attached or only mentioned; a value as a text, none when it has none.
     */
    private suspend fun joined(context: Context, step: JSONObject, results: List<TaskRun.StepResult>, at: Long, received: TaskRun.Received?, item: Map<String, Any?>?): List<Map<String, Any?>> {
        val s = Strings.`for`(context = context)
        val blocks = try {
            StepBlocks.of(step)
        } catch (e: IllegalArgumentException) {
            // Its blocks were checked when stored: one that does not read now is said as it is
            throw StepFailed(s.shared("task_error_block").format("-", e.message ?: "", step.optJSONArray(StepBlocks.BLOCKS)?.toString() ?: "-"))
        }
        return blocks.mapIndexedNotNull { index, block ->
            when (block) {
                is StepBlocks.Block.Fixed -> block.json
                is StepBlocks.Block.Entry -> {
                    val input = TaskSteps.input(block.input, TaskSteps.ENTRY) { s.shared(it) }
                    // The entry a trigger gave, deleted: nothing is left to point at
                    if (input is TaskSteps.Input.Received && input.value == null && (received as? TaskRun.Received.Entry)?.write == app.treelune.core.triggers.Triggers.Write.DELETED) {
                        throw StepFailed(s.shared("task_error_block_deleted").format(index + 1))
                    }
                    val id = idOf(resolve(context, input, results, at, received, item)) as? String
                        ?: throw StepFailed(s.shared("task_error_block_no_entry").format(index + 1))
                    AppDatabase.getDatabase(context).toolDataDao().getById(id) ?: throw StepFailed(s.shared("task_error_block_deleted").format(index + 1))
                    val pointer = app.treelune.core.ai.enrichments.PointerConfig(
                        app.treelune.core.selection.EntrySelection(target = app.treelune.core.selection.Reference(app.treelune.core.selection.ReferenceKind.ENTRY, id)),
                        entries = block.attach
                    )
                    JSONObject().put(StepBlocks.TYPE, StepBlocks.POINTER).put(StepBlocks.POINTER, pointer.toJson())
                }
                is StepBlocks.Block.Value -> resolve(context, TaskSteps.input(block.input, TaskSteps.VALUE) { s.shared(it) }, results, at, received, item)?.let { value ->
                    JSONObject().put(StepBlocks.TYPE, StepBlocks.TEXT).put(StepBlocks.TEXT, said(value))
                }
            }
        }.map { JsonUtils.toMap(it) }
    }

    /** [value] as a text: an object or a list in JSON, anything else as it writes. */
    private fun said(value: Any): String = when (value) {
        is Map<*, *> -> JSONObject(value).toString()
        is List<*> -> JsonUtils.toJSONArray(value).toString()
        else -> value.toString()
    }

    /** An entry's id, from its id or a Reference designating it. */
    private fun idOf(value: Any?): Any? = (value as? Map<*, *>)?.get("id") ?: value

    /**
     * What [input] gives: a term read at [at], what an earlier step gave, the entry [received] as
     * it is now, a field of the element [item] a repeated step stands at.
     */
    private suspend fun resolve(context: Context, input: TaskSteps.Input, results: List<TaskRun.StepResult>, at: Long, received: TaskRun.Received?, item: Map<String, Any?>? = null): Any? {
        val s = Strings.`for`(context = context)
        return when (input) {
            is TaskSteps.Input.Item -> (item ?: throw StepFailed(s.shared("task_error_item_unrepeated").format(""))) [input.field]
            is TaskSteps.Input.Selection -> listed(context, input.selection, at)
            is TaskSteps.Input.Received -> when {
                input.value != null -> when (received) {
                    is TaskRun.Received.Event -> received.values[input.value]
                    is TaskRun.Received.Condition -> received.values[input.value]
                    else -> throw StepFailed(s.shared("task_error_received_no_event"))
                }
                else -> {
                    val entry = received as? TaskRun.Received.Entry ?: throw StepFailed(s.shared("task_error_received_none"))
                    if (input.field == null) return entry.entryId
                    val row = AppDatabase.getDatabase(context).toolDataDao().getById(entry.entryId)
                        ?: throw StepFailed(s.shared("task_error_received_gone"))
                    app.treelune.core.reading.FieldReading.valueAt(TaskSteps.entryMap(row), input.field)
                }
            }
            is TaskSteps.Input.Result -> {
                val given = results.getOrNull(input.step) ?: throw StepFailed(s.shared("task_error_result_step").format("", input.step + 1))
                if (input.value == null) given.entryId ?: throw StepFailed(s.shared("task_error_result_value").format("", input.step + 1, s.shared("task_output_entry")))
                else given.values[input.value]
            }
            is TaskSteps.Input.Of -> when (val term = input.term) {
                is Term.Constant -> term.value
                else -> try {
                    when (val read = TermReader(context).read(term, at, null)) {
                        is TermReader.Read.Value -> read.value
                        is TermReader.Read.Failed -> throw StepFailed(read.message)
                    }
                } catch (e: IllegalStateException) {
                    throw StepFailed(e.message ?: "")
                }
            }
        }
    }

    /**
     * The file at [address] downloaded and set aside for the entry the step writes, of one of
     * [kinds] (FilePreparation): its id, until the entry owns it. Given by the API [apiId]'s answer,
     * it is asked as that API asks it (ApiService.fileRequest: its key on its own server).
     */
    private suspend fun download(context: Context, address: String, kinds: Collection<app.treelune.core.files.FileKind>, apiId: String?): String = withContext(Dispatchers.IO) {
        val s = Strings.`for`(context = context)
        val name = Uri.parse(address).lastPathSegment?.takeIf { it.contains('.') } ?: "download"
        val dir = File(context.cacheDir, "task_downloads").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}-$name")
        val request = try {
            apiId?.let { ApiService(context).fileRequest(it, address) } ?: Request.Builder().url(address).get().build()
        } catch (e: IllegalStateException) {
            throw StepFailed(s.shared("task_error_download").format(address, e.message ?: ""))
        }
        try {
            DOWNLOADS.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw StepFailed(s.shared("task_error_download").format(address, response.code.toString()))
                response.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: throw StepFailed(s.shared("task_error_download").format(address, "-"))
            }
            when (val prepared = FilePreparation.prepare(context, Uri.fromFile(file), kinds, name = name) { s.shared(it) }) {
                is FilePreparation.Result.Done -> prepared.id
                is FilePreparation.Result.Refused -> throw StepFailed(prepared.message)
            }
        } catch (e: java.io.IOException) {
            throw StepFailed(s.shared("task_error_download").format(address, e.message ?: ""))
        } finally {
            file.delete()
        }
    }
}
