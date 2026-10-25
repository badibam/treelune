package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.transaction
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.grid.Groups
import app.treelune.core.grid.SectionItem
import app.treelune.core.grid.ToolPositions
import app.treelune.core.look.Looks
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonNulls
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleConfig
import app.treelune.core.validation.SchemaValidator
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The Tasks (docs/CORE.md, « Tasks »): steps the app runs itself, in a zone, with a routine's
 * envelope — a tile of its section, a schedule, missed runs caught up — and no access of its own:
 * running one takes using its zone (AccessRules), and an AI held to a mask writes one only with
 * steps it could take itself (TaskSteps.reachRefusal).
 *
 * Operations:
 * - tasks.list (zone_id): its Tasks (Task.fromResult); .list_all: every one; .get (task_id): one
 * - .create (zone_id, its settings at the top as TaskSettings declares them, `steps`)
 * - .update (task_id, what changes: a setting left out kept, one given null removed; `steps`, `zone_id`)
 * - .delete (task_id), its runs with it
 * - .run (task_id): a run started now, its `run_id`; refused while one of its runs goes on
 * - .cancel (run_id): a run going on or waiting, stopped before its next step
 * - .runs (task_id, limit): its last runs, the newest first; .get_run (run_id): one, each step's result
 * - .seen (run_id): its end read
 * - .next_execution (task_id): when its schedule runs it next, `next_execution_at`, absent for never
 * - .outputs (steps): what each of these steps gives the ones after it, read without running them
 */
class TaskService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.taskDao() }
    private val sections by lazy { app.treelune.core.grid.SectionGrid(context) }
    private val json = Json { ignoreUnknownKeys = true }

    /** The keys of a Task's settings, beside its zone and steps. */
    private val SETTING_KEYS = Looks.keys(TaskSettings.LOOK) + listOf("group", "is_enabled", "notify_end", "schedule", "catch_up", app.treelune.core.triggers.Triggers.KEY, app.treelune.core.events.EventPoints.KEY)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult = try {
        when (operation) {
            "list" -> OperationResult.success(mapOf("tasks" to dao.getByZone(params.optString("zone_id")).map { describe(it) }))
            "list_all" -> OperationResult.success(mapOf("tasks" to dao.getAll().map { describe(it) }))
            "get" -> task(params)?.let { OperationResult.success(mapOf("task" to describe(it))) } ?: notFound()
            "create" -> create(params)
            "update" -> update(params)
            "delete" -> delete(params)
            "run" -> run(params)
            "cancel" -> cancel(params)
            "runs" -> task(params)?.let { task -> OperationResult.success(mapOf("runs" to dao.getRuns(task.id, params.optInt("limit", 20)).map { runOf(it) })) } ?: notFound()
            "get_run" -> dao.getRun(params.optString("run_id"))?.let { OperationResult.success(mapOf("run" to runOf(it))) } ?: OperationResult.error(s.shared("task_error_run_not_found"))
            "seen" -> { dao.markSeen(params.optString("run_id"), System.currentTimeMillis()); OperationResult.success() }
            "next_execution" -> task(params)?.let { task -> OperationResult.success(buildMap { TaskScheduler.nextAt(context, task)?.let { put("next_execution_at", it) } }) } ?: notFound()
            "outputs" -> outputs(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    } catch (e: Exception) {
        LogManager.service("TaskService.$operation failed: ${e.message}", "ERROR", e)
        OperationResult.error(s.shared("task_error").format(e.message ?: ""))
    }

    private suspend fun task(params: JSONObject): TaskEntity? = params.optString("task_id").ifEmpty { null }?.let { dao.get(it) }

    private fun notFound() = OperationResult.error(s.shared("task_error_not_found"))

    /** [task] as a result gives it: what Task.fromResult reads. */
    private suspend fun describe(task: TaskEntity): Map<String, Any?> {
        val last = dao.getRuns(task.id, 1).firstOrNull()
        return mapOf(
            "id" to task.id,
            "zone_id" to task.zoneId,
            "settings" to JsonUtils.toMap(TaskSettings.of(task)),
            "steps" to JsonUtils.toList(JSONArray(task.stepsJson)),
            "grid_x" to task.gridX,
            "grid_y" to task.gridY,
            "updated_at" to task.updatedAt,
            "last_run" to last?.let { runOf(it) },
            "condition_judgements" to app.treelune.core.triggers.ConditionTriggers.judgements(context, app.treelune.core.triggers.ConditionTriggers.TASK, task.id, task.triggersJson)
        )
    }

    private fun runOf(run: TaskRunEntity): Map<String, Any?> = mapOf(
        "id" to run.id,
        "task_id" to run.taskId,
        "trigger" to run.trigger,
        "scheduled_at" to run.scheduledAt,
        "started_at" to run.startedAt,
        "ended_at" to run.endedAt,
        "state" to run.state,
        "step" to run.step,
        "results" to JsonUtils.toList(JSONArray(run.resultsJson)),
        "next_poll_at" to run.waitJson?.let { TaskRun.Wait.of(it).nextAt },
        "waits" to (run.endedAt != null && run.seenAt == null),
        // Started by a condition: what it read, in words
        "condition_said" to (run.receivedJson?.let { TaskRun.Received.of(it) } as? TaskRun.Received.Condition)?.said
    )

    private suspend fun create(params: JSONObject): OperationResult {
        val zoneId = params.optString("zone_id").ifEmpty { return OperationResult.error(s.shared("error_param_zone_id_required")) }
        val zone = database.zoneDao().getZoneById(zoneId) ?: return OperationResult.error(s.shared("service_error_zone_not_found"))
        val now = System.currentTimeMillis()
        val base = TaskEntity(UUID.randomUUID().toString(), zoneId, "", null, TaskSettings.DEFAULT_ICON, null, null, TaskSettings.DEFAULT_MODE,
            null, 0, 0, true, null, null, null, false, "", "[]", now, now)
        val task = checked(base, given(params, null), null, params).getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        database.transaction {
            // At the end of its section's grid, among the tools, the variables and the routines
            val place = sections.arrive(SectionItem.of(task), zone.tool_groups)
            dao.insert(task.copy(gridX = place.column, gridY = place.row))
        }
        DataChangeNotifier.notifyZonesChanged()
        LogManager.service("Task ${task.id} created in zone $zoneId", "INFO")
        return OperationResult.success(mapOf("task_id" to task.id, "name" to task.name))
    }

    private suspend fun update(params: JSONObject): OperationResult {
        val task = task(params) ?: return notFound()
        val moved = params.optString("zone_id").ifEmpty { null }?.takeIf { it != task.zoneId }
        val settings = given(params, TaskSettings.of(task))
        // A Task changing zone without a group given leaves its group behind
        if (moved != null && !params.has("group")) settings.remove("group")
        val updated = checked(task.copy(zoneId = moved ?: task.zoneId, updatedAt = System.currentTimeMillis()), settings, TaskSettings.of(task), params)
            .getOrElse { return OperationResult.error(it.message ?: s.shared("message_validation_error_simple")) }
        database.transaction {
            val place = sections.move(SectionItem.of(task), SectionItem.of(updated),
                database.zoneDao().getZoneById(task.zoneId)?.tool_groups, database.zoneDao().getZoneById(updated.zoneId)?.tool_groups)
            dao.update(updated.copy(gridX = place.column, gridY = place.row))
        }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("task_id" to task.id, "name" to updated.name))
    }

    private suspend fun delete(params: JSONObject): OperationResult {
        val task = task(params) ?: return notFound()
        database.transaction {
            sections.leave(SectionItem.of(task), database.zoneDao().getZoneById(task.zoneId)?.tool_groups)
            dao.delete(task.id)
        }
        DataChangeNotifier.notifyZonesChanged()
        return OperationResult.success(mapOf("task_id" to task.id, "name" to task.name))
    }

    private suspend fun run(params: JSONObject): OperationResult {
        val task = task(params) ?: return notFound()
        if (dao.getRunsIn(listOf(TaskRun.State.RUNNING.key, TaskRun.State.WAITING.key)).any { it.taskId == task.id }) {
            return OperationResult.error(s.shared("task_error_running").format(task.name))
        }
        val trigger = if (app.treelune.core.coordinator.currentOrigin() == app.treelune.core.coordinator.Source.AI) TaskRun.Trigger.AI else TaskRun.Trigger.MANUAL
        // Asked by a routine's AI, it continues that run's chain, refused past its cap
        val chainId = when (val admission = app.treelune.core.coordinator.Chains.admit(context, task.name)) {
            is app.treelune.core.coordinator.Chains.Admission.Capped -> return OperationResult.error(admission.message)
            is app.treelune.core.coordinator.Chains.Admission.Continues -> admission.chainId
            app.treelune.core.coordinator.Chains.Admission.Starts -> null
        }
        val runId = TaskRunner.start(context, task, trigger, System.currentTimeMillis(), awaited = false, chainId = chainId)
        return OperationResult.success(mapOf("run_id" to runId, "task_id" to task.id))
    }

    private suspend fun cancel(params: JSONObject): OperationResult {
        val run = dao.getRun(params.optString("run_id")) ?: return OperationResult.error(s.shared("task_error_run_not_found"))
        if (TaskRun.State.of(run.state).ended) return OperationResult.success()
        val task = dao.get(run.taskId) ?: return notFound()
        TaskRunner.end(context, task, run, TaskRun.State.CANCELLED)
        return OperationResult.success()
    }

    private suspend fun outputs(params: JSONObject): OperationResult {
        val steps = params.optJSONArray("steps") ?: JSONArray()
        return OperationResult.success(mapOf("outputs" to (0 until steps.length()).map { index ->
            TaskSteps.outputs(steps.getJSONObject(index), context).map { mapOf("value" to it.value, "type" to it.type) }
        }))
    }

    /** [stored] with the settings [params] give: a setting left out keeps its value, one given null is removed. */
    private fun given(params: JSONObject, stored: JSONObject?): JSONObject {
        val settings = stored?.let { JSONObject(it.toString()) } ?: JSONObject()
        SETTING_KEYS.forEach { key ->
            if (!params.has(key)) return@forEach
            if (params.isNull(key)) settings.remove(key) else settings.put(key, params.get(key))
        }
        return settings
    }

    /**
     * [task] holding [sent] and what [params] give of its steps, checked: its settings
     * against their schema and its look as every look is, the catch-up with a schedule and only
     * with it, its group one of its zone's, its steps (TaskSteps.refusal), each within the mask of
     * whoever writes them.
     */
    private suspend fun checked(task: TaskEntity, sent: JSONObject, before: JSONObject?, params: JSONObject): Result<TaskEntity> {
        fun refused(message: String) = Result.failure<TaskEntity>(IllegalArgumentException(message))
        val settings = JsonNulls.withoutNullKeys(sent)
        if (!settings.has(Looks.ICON)) settings.put(Looks.ICON, TaskSettings.DEFAULT_ICON)
        if (!settings.has(Looks.DISPLAY_MODE)) settings.put(Looks.DISPLAY_MODE, TaskSettings.DEFAULT_MODE)
        (Looks.check(TaskSettings.LOOK, settings, before, context) as? Looks.Checked.Refused)?.let { return refused(it.message) }
        val look = Looks.read(TaskSettings.LOOK, settings)
        val validation = SchemaValidator.validate(TaskSettings.schema(context), JsonUtils.toMap(settings), context)
        if (!validation.isValid) return refused(validation.errorMessage ?: s.shared("message_validation_error_simple"))
        val schedule = settings.optJSONObject("schedule")
        val catchUp = settings.optJSONObject("catch_up")
        if (schedule != null && catchUp == null) return refused(s.shared("automation_catch_up_required"))
        if (schedule == null && catchUp != null) return refused(s.shared("automation_catch_up_without_schedule"))
        schedule?.let {
            try {
                json.decodeFromString<ScheduleConfig>(it.toString())
            } catch (e: Exception) {
                return refused("Invalid schedule: ${e.message}")
            }
        }
        val group = settings.optString("group").ifEmpty { null }
        val zone = database.zoneDao().getZoneById(task.zoneId) ?: return refused(s.shared("service_error_zone_not_found"))
        Groups.refusal(group, ToolPositions.zoneGroups(zone.tool_groups), s)?.let { return refused(it) }

        // What starts it (Triggers.refusal), and the entry it then gives the steps
        app.treelune.core.triggers.Triggers.refusal(settings, TaskSettings.nodes(context), context, s)?.let { return refused(it) }
        // What a run that ends well emits carries nothing: no entry is the run's
        app.treelune.core.events.EventPoints.refusal(settings, null, context, s)?.let { return refused(it) }
        val triggers = app.treelune.core.triggers.Triggers.of(settings)
        val read = app.treelune.core.triggers.Triggers.read(triggers)

        val steps = if (params.has("steps")) params.optJSONArray("steps") ?: return refused(s.shared("task_error_steps")) else JSONArray(task.stepsJson)
        TaskSteps.refusal(steps, context, s, TaskSteps.receivedFields(read, context, s), TaskSteps.receivedValues(read, context))?.let { return refused(it) }

        // An AI held to a mask writes only steps it could take itself: the Task never reaches further than it
        kotlin.coroutines.coroutineContext[app.treelune.core.access.AccessScope]?.let { scope ->
            TaskSteps.reachRefusal(steps, scope, context, s)?.let { return refused(it) }
        }

        val values = SettingValues(TaskSettings.nodes(context), settings)
        return Result.success(task.copy(
            name = look.name,
            description = look.description,
            iconName = look.iconName!!,
            iconColor = look.iconColor,
            texture = look.texture,
            displayMode = look.displayMode.name,
            group = group,
            isEnabled = values.boolean("is_enabled"),
            notifyEnd = values.string("notify_end") ?: error("Setting 'notify_end' has neither a value nor a default"),
            scheduleJson = schedule?.toString(),
            catchUp = catchUp?.getString("limit"),
            catchUpWindow = catchUp?.takeIf { it.has("window") }?.getLong("window"),
            dismissOlderInstances = SettingValues(app.treelune.core.ai.data.AutomationSettings.catchUpNodes(context), catchUp ?: JSONObject()).boolean("dismiss_older_instances"),
            stepsJson = steps.toString(),
            triggersJson = triggers?.toString(),
            emitEvent = app.treelune.core.events.EventPoints.idIn(settings)
        ))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val name = params.optString("name").ifEmpty { null } ?: params.optString("task_id").ifEmpty { null }?.let { dao.get(it)?.name } ?: "?"
        return when (operation) {
            "create" -> s.shared("task_verbalize_create").format(name)
            "update" -> s.shared("task_verbalize_update").format(name)
            "delete" -> s.shared("task_verbalize_delete").format(name)
            "run" -> s.shared("task_verbalize_run").format(name)
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
