package app.treelune.tools.agenda

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * A calendar's operations, for the screen and the AI alike, beside the ordinary writes of its
 * events — the status is theirs alone (AgendaToolType.START):
 * - cancel: `id` — an event cancelled, shown struck through, without reminders
 * - remove: `id` — an occurrence of a series taken off the calendar, kept so that its series does
 *   not give it again; an event of its own is deleted instead
 * - restore: `id` — an event cancelled or removed planned again
 */
class AgendaService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "agenda", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
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

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "agenda", context = context)
        return s.tool("verbalize_$operation").takeIf { operation in setOf("cancel", "remove", "restore") } ?: s.shared("action_verbalize_unknown")
    }
}
