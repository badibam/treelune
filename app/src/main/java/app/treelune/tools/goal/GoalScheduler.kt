package app.treelune.tools.goal

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.StoredSchedule
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The life of goals' attempts, at each tick (docs/TOOLS.md, « Goal »):
 *
 * 1. An attempt whose period ended is to be validated: its verdict waits to be confirmed, so a
 *    criterion forgotten is caught up rather than turned into a false failure. One notification
 *    tells it, once, unless the goal says not to.
 * 2. An attempt left to validate past the goal's delay expires, which is not a failure.
 * 3. A goal switched on with a schedule opens an attempt at the last occurrence that came, the
 *    ones in between not caught up; one launched by hand opens now (launch). An attempt lasts
 *    the goal's duration, without an end of its own when it has none: any start sends the open
 *    one to be validated, its period ending there. An attempt to validate does not hold the next
 *    one back: periods would slide.
 */
object GoalScheduler : ToolScheduler {

    /** Guards the walk through a schedule's occurrences. */
    private const val MAX_STEPS = 10_000

    private val passLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun checkScheduled(context: Context) {
        if (!passLock.tryLock()) return
        try {
            val coordinator = Coordinator(context)
            val tools = coordinator.processUserAction("tools.list_all", mapOf("include_config" to true))
            if (!tools.isSuccess) {
                LogManager.service("GoalScheduler: tools not read: ${tools.error}", "ERROR")
                return
            }
            (tools.data?.get("tool_instances") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
                .filter { it["tooltype"] == "goal" }
                .forEach { tool ->
                    try {
                        pass(context, coordinator, tool)
                    } catch (e: Exception) {
                        LogManager.service("GoalScheduler: goal ${tool["id"]} not handled: ${e.message}", "ERROR", e)
                    }
                }
        } finally {
            passLock.unlock()
        }
    }

    private suspend fun pass(context: Context, coordinator: Coordinator, tool: Map<*, *>) {
        val id = tool["id"] as String
        @Suppress("UNCHECKED_CAST")
        val config = JsonUtils.toJSONObject(tool["config"] as Map<String, Any?>)
        val now = System.currentTimeMillis()
        val attempts = attemptsOf(coordinator, id)

        // 1 and 2: periods ended, delays passed; a duration's days counted on the calendar
        val calendar = app.treelune.core.selection.TimeResolver.at(now)
        val expiry = (config.opt("expiry_delay") as? Number)?.toLong() ?: GoalToolType.DEFAULT_EXPIRY
        for (attempt in attempts) {
            val end = (attempt.state.opt(GoalToolType.PERIOD_END) as? Number)?.toLong() ?: continue
            when (attempt.state.optString(GoalToolType.STATUS)) {
                GoalToolType.Status.ACTIVE -> if (end <= now) toValidate(context, coordinator, id, config, attempt, null)
                GoalToolType.Status.TO_VALIDATE -> if (calendar.after(end, expiry) <= now) {
                    setState(coordinator, attempt.id, mapOf(GoalToolType.STATUS to GoalToolType.Status.EXPIRED))
                }
            }
        }

        // 3: the attempt its schedule opens, if any: the last occurrence come since the last
        // attempt (since the goal was made, for the first), the ones in between not caught up
        if (!config.optBoolean("enabled", true)) return
        val schedule = when (val stored = StoredSchedule.of(config)) {
            is StoredSchedule.Unreadable -> {
                LogManager.service("GoalScheduler: schedule of goal $id unreadable (${stored.cause}), nothing opened", "ERROR")
                return
            }
            // Launched by hand alone
            StoredSchedule.None -> return
            is StoredSchedule.Readable -> stored.schedule
        }
        val latest = attempts.maxOfOrNull { it.start }
        var cursor = latest ?: ((tool["created_at"] as? Number)?.toLong() ?: now)
        var due: Long? = null
        var steps = 0
        while (steps++ < MAX_STEPS) {
            val next = ScheduleCalculator.calculateNextExecution(schedule.pattern, cursor) ?: break
            if (next > now || next <= cursor) break
            due = next
            cursor = next
        }
        due?.takeIf { d -> attempts.none { it.start == d } }?.let { open(context, coordinator, id, config, attempts, it) }
    }

    /**
     * An attempt of the goal [toolInstanceId] opened now by hand (« Launch now »: its page, a
     * Task's step, the AI), its schedule left as it is: the next occurrence will send it to be
     * validated as any start does.
     */
    suspend fun launch(context: Context, toolInstanceId: String, config: JSONObject): String? = passLock.withLock {
        val coordinator = Coordinator(context)
        open(context, coordinator, toolInstanceId, config, attemptsOf(coordinator, toolInstanceId), System.currentTimeMillis())
    }

    /**
     * An attempt of [toolInstanceId] opened at [start], ending at [start] plus the goal's
     * duration, without an end of its own otherwise: any start — its schedule's or a launch —
     * sends the attempt open to be validated, its period ending there. Its id; null when it was
     * not written, which the log says.
     */
    private suspend fun open(context: Context, coordinator: Coordinator, toolInstanceId: String, config: JSONObject, attempts: List<AttemptState>, start: Long): String? {
        val s = Strings.`for`(tool = "goal", context = context)
        attempts.filter { it.state.optString(GoalToolType.STATUS) == GoalToolType.Status.ACTIVE }.forEach {
            toValidate(context, coordinator, toolInstanceId, config, it, start)
        }
        val end = (config.opt("duration") as? Number)?.toLong()?.let { app.treelune.core.selection.TimeResolver.at(start).after(start, it) }
        // As the scheduler: the copy of the definition is the app's to write
        val created = coordinator.processScheduledTask("tool_data.create", mapOf(
            "tool_instance_id" to toolInstanceId,
            "name" to (config.optString("name").ifEmpty { s.tool("display_name") }),
            "timestamp" to start,
            "data" to mapOf(GoalToolType.DEFINITION to GoalDefinition.copyOf(config).toString()),
            "state" to buildMap {
                put(GoalToolType.STATUS, GoalToolType.Status.ACTIVE)
                end?.let { put(GoalToolType.PERIOD_END, it) }
            }
        ))
        if (!created.isSuccess) {
            LogManager.service("GoalScheduler: attempt of goal $toolInstanceId not opened: ${created.error}", "ERROR")
            return null
        }
        return created.data?.get("id") as? String
    }

    /**
     * [attempt] sent to be validated, its period ending at [cutAt] when a start cuts it short
     * (null: at its own end): its event emitted, carrying it, told or not, and its notification
     * sent once unless the goal says not to.
     */
    private suspend fun toValidate(context: Context, coordinator: Coordinator, toolInstanceId: String, config: JSONObject, attempt: AttemptState, cutAt: Long?) {
        val s = Strings.`for`(tool = "goal", context = context)
        val end = (attempt.state.opt(GoalToolType.PERIOD_END) as? Number)?.toLong()
        setState(coordinator, attempt.id, buildMap {
            put(GoalToolType.STATUS, GoalToolType.Status.TO_VALIDATE)
            if (cutAt != null && (end == null || end > cutAt)) put(GoalToolType.PERIOD_END, cutAt)
        })
        app.treelune.core.events.EventPoints.emit(context, config, attempt.id)
        if (config.optBoolean("notify", true) && !attempt.state.optBoolean(GoalToolType.NOTIFIED)) {
            coordinator.processUserAction("notifications.send", mapOf(
                "title" to (config.optString("name").ifEmpty { s.tool("display_name") }),
                "content" to s.tool("notification_to_validate"),
                "priority" to "default",
                "icon_name" to config.optString("icon_name").ifEmpty { GoalToolType.getDefaultIconName() },
                "tool_instance_id" to toolInstanceId
            ))
            setState(coordinator, attempt.id, mapOf(GoalToolType.NOTIFIED to true))
        }
    }

    private data class AttemptState(val id: String, val start: Long, val state: JSONObject)

    private suspend fun attemptsOf(coordinator: Coordinator, toolInstanceId: String): List<AttemptState> {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) throw IllegalStateException(result.error ?: "tool_data.get")
        return (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { e ->
            @Suppress("UNCHECKED_CAST")
            AttemptState(e["id"] as String, (e["timestamp"] as Number).toLong(), (e["state"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) } ?: JSONObject())
        }
    }

    private suspend fun setState(coordinator: Coordinator, id: String, state: Map<String, Any>) {
        val result = coordinator.processUserAction("tool_data.update", mapOf("id" to id, "state" to state))
        if (!result.isSuccess) LogManager.service("GoalScheduler: attempt $id not updated: ${result.error}", "ERROR")
    }
}
