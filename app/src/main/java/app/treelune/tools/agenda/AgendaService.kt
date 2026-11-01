package app.treelune.tools.agenda

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import org.json.JSONArray
import org.json.JSONObject

/**
 * A calendar's operations, for the screen and the AI alike, beside the ordinary writes of its
 * events — the status is theirs alone (AgendaToolType.START):
 * - cancel: `id` — an event cancelled, shown struck through, without reminders
 * - remove: `id` — an occurrence of a series taken off the calendar, kept so that its series does
 *   not give it again; an event of its own is deleted instead
 * - restore: `id` — an event cancelled or removed planned again
 * - repeat: `id`, `schedule` — an event of its own made the first occurrence of a new series of
 *   its name, its duration or days, on that schedule, which gives the next ones after it
 */
class AgendaService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "agenda", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        if (operation == "repeat") return repeat(params)
        val status = when (operation) {
            "cancel" -> AgendaToolType.Status.CANCELLED
            "remove" -> AgendaToolType.Status.REMOVED
            "restore" -> AgendaToolType.Status.PLANNED
            else -> return OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val entry = coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to id)).data?.get("entry") as? Map<*, *>
            ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(id))
        if (entry["tooltype"] != "agenda") return OperationResult.error(s.tool("error_not_an_event").format(id))
        val event = AgendaEvent.of(entry)
        if (status == AgendaToolType.Status.REMOVED && event.series == null) return OperationResult.error(s.tool("error_remove_single"))

        val result = coordinator.processUserAction("tool_data.update", mapOf("id" to id, "state" to mapOf(AgendaToolType.STATUS to status)))
        return if (result.isSuccess) OperationResult.success(mapOf("id" to id, "status" to status)) else OperationResult.error(result.error ?: "")
    }

    /**
     * The event [params] names made the first occurrence of a series on the schedule given. The
     * event is marked first, its series not existing yet: no pass of AgendaScheduler can give its
     * time meanwhile; the series then written gives the times after it — after its last day for
     * a whole day, whose time of the schedule falls inside it. A series refused leaves the
     * event as it was.
     */
    private suspend fun repeat(params: JSONObject): OperationResult {
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val schedule = params.optJSONObject(app.treelune.core.fields.settings.ScheduleSettings.NAME)
            ?: return OperationResult.error(s.tool("error_repeat_without_schedule"))
        val entry = coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to id)).data?.get("entry") as? Map<*, *>
            ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(id))
        if (entry["tooltype"] != "agenda") return OperationResult.error(s.tool("error_not_an_event").format(id))
        val event = AgendaEvent.of(entry)
        if (event.series != null) return OperationResult.error(s.tool("error_repeat_occurrence"))
        val toolInstanceId = entry["tool_instance_id"] as String
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        val config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { app.treelune.core.utils.JsonUtils.toJSONObject(it) }
            ?: return OperationResult.error(tool.error ?: s.shared("service_error_tool_instance_not_found"))

        // The new series, its key given now as the config's writing would, to mark the event with it
        val series = JSONObject().put("name", event.name).put(app.treelune.core.fields.settings.ScheduleSettings.NAME, schedule).put(AgendaToolType.ALL_DAY, event.allDay)
        if (event.allDay) series.put(AgendaToolType.DAYS, event.days) else event.duration?.let { series.put(AgendaToolType.DURATION, it) }
        val next = JSONObject(config.toString()).put(AgendaToolType.SERIES_LIST, (config.optJSONArray(AgendaToolType.SERIES_LIST)?.let { JSONArray(it.toString()) } ?: JSONArray()).put(series))
        val completed = AgendaToolType.completeConfig(next, config)
        val list = completed.getJSONArray(AgendaToolType.SERIES_LIST)
        val key = list.getJSONObject(list.length() - 1).getString("key")
        val givenUntil = if (event.allDay) event.end(AgendaToolType.zone()) - 1 else event.start

        val marked = coordinator.processUserAction("tool_data.update", mapOf("id" to id,
            "state" to mapOf(AgendaToolType.SERIES to key, AgendaToolType.SERIES_AT to givenUntil)))
        if (!marked.isSuccess) return OperationResult.error(marked.error ?: "")
        val written = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to toolInstanceId, "config" to completed))
        if (!written.isSuccess) {
            val back = coordinator.processUserAction("tool_data.update", mapOf("id" to id,
                "state" to mapOf(AgendaToolType.SERIES to null, AgendaToolType.SERIES_AT to null)))
            if (!back.isSuccess) app.treelune.core.utils.LogManager.service("AgendaService: event $id left marked with series $key, its series refused: ${back.error}", "ERROR")
            return OperationResult.error(written.error ?: "")
        }
        return OperationResult.success(mapOf("id" to id, "series" to key))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "agenda", context = context)
        return s.tool("verbalize_$operation").takeIf { operation in setOf("cancel", "remove", "restore", "repeat") } ?: s.shared("action_verbalize_unknown")
    }
}
