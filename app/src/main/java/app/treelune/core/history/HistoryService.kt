package app.treelune.core.history

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.currentOrigin
import app.treelune.core.database.AppDatabase
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DataChangeNotifier
import org.json.JSONObject

/**
 * The history of writes as the screen and the AI read and act on it (docs/DATA.md, « History of writes »): the same three operations for both.
 *
 * Operations:
 * - list: the steps, the most recent first; tool_instance_id or zone_id narrows them to those
 *   that touched it, from and to (milliseconds) to a period, limit to how many (100 when not given)
 * - undo: id, a step done
 * - redo: id, a step undone
 * - summary: the room the history takes (size_bytes) and when its oldest step was made (timestamp)
 * - rows: id, the rows a step touched, each with its table and its copies before and after
 *
 * A step is given by its origin, its time (timestamp), the AI's session it answered (session_id),
 * its phrase, its state, and once undone or redone, who did it and when (state_by, updated_at).
 */
class HistoryService(private val context: Context) : ExecutableService {
    private val s = Strings.`for`(context = context)
    private val dao = AppDatabase.getDatabase(context).historyDao()

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "list" -> list(params)
            "undo", "redo" -> act(operation, params)
            "summary" -> OperationResult.success(buildMap {
                put("size_bytes", dao.totalSize())
                dao.oldestStep()?.let { put("timestamp", it) }
            })
            "rows" -> rows(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun list(params: JSONObject): OperationResult {
        val steps = dao.listSteps(
            toolInstanceId = params.text("tool_instance_id"),
            zoneId = params.text("zone_id"),
            from = if (params.has("from")) params.getLong("from") else 0L,
            to = if (params.has("to")) params.getLong("to") else Long.MAX_VALUE,
            limit = if (params.has("limit")) params.getInt("limit") else DEFAULT_LIMIT
        )
        return OperationResult.success(mapOf("steps" to steps.map { step ->
            buildMap {
                put("id", step.id)
                put("origin", step.source)
                put("timestamp", step.at)
                step.sessionId?.let { put("session_id", it) }
                put("phrase", step.phrase)
                put("state", step.state)
                step.stateBy?.let { put("state_by", it) }
                step.stateAt?.let { put("updated_at", it) }
            }
        }))
    }

    private suspend fun act(operation: String, params: JSONObject): OperationResult {
        val id = params.text("id") ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val undo = Undo(context)
        val outcome = if (operation == "undo") undo.undo(id, currentOrigin()) else undo.redo(id, currentOrigin())
        return when (outcome) {
            is Undo.Outcome.Refused -> OperationResult.error(outcome.reason)
            Undo.Outcome.Done -> {
                tell(id)
                // Undone or redone, its entries are written again: what listens to them starts
                app.treelune.core.triggers.EntryTriggers.dispatch(context) { app.treelune.core.triggers.EntryWrites.ofStep(context, id, undone = operation == "undo") }
                val step = dao.getStep(id)!!
                OperationResult.success(mapOf("id" to id, "state" to step.state, "phrase" to (step.phrase ?: "")))
            }
        }
    }

    private suspend fun rows(params: JSONObject): OperationResult {
        val id = params.text("id") ?: return OperationResult.error(s.shared("service_error_missing_id"))
        dao.getStep(id)?.takeIf { it.isStep } ?: return OperationResult.error(s.shared("history_error_step_not_found").format(id))
        return OperationResult.success(mapOf("rows" to dao.getRows(id).map { row ->
            buildMap {
                put("table", row.tableName)
                put("row_id", row.rowId)
                row.before?.let { put("before", app.treelune.core.utils.JsonUtils.toMap(JSONObject(it))) }
                row.after?.let { put("after", app.treelune.core.utils.JsonUtils.toMap(JSONObject(it))) }
            }
        }))
    }

    /** What the step [id] touched said to the screens, as its own writes said it. */
    private suspend fun tell(id: String) {
        val places = dao.placesOf(id)
        val tables = places.map { it.tableName }.toSet()
        if (CoveredTable.APP_SETTINGS.tableName in tables) {
            AppConfigManager.refresh(context)
            DataChangeNotifier.notifyAppConfigChanged()
        }
        if (CoveredTable.IDENTITIES.tableName in tables) DataChangeNotifier.notifyIdentitiesChanged()
        if (CoveredTable.VARIABLES.tableName in tables) DataChangeNotifier.notifyVariablesChanged()
        if (CoveredTable.ROUTINES.tableName in tables || CoveredTable.AI_SESSIONS.tableName in tables) DataChangeNotifier.notifyAISessionsChanged()
        places.mapNotNull { p -> p.zoneId?.let { zone -> p.toolInstanceId?.let { it to zone } } }.toSet()
            .forEach { (tool, zone) -> DataChangeNotifier.notifyToolDataChanged(tool, zone) }
        places.mapNotNull { it.zoneId }.toSet().forEach { DataChangeNotifier.notifyToolsChanged(it) }
        DataChangeNotifier.notifyZonesChanged()
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val phrase = params.text("id")?.let { dao.getStep(it)?.phrase } ?: params.optString("id")
        return when (operation) {
            "undo" -> s.shared("action_verbalize_history_undo").format(phrase)
            "redo" -> s.shared("action_verbalize_history_redo").format(phrase)
            else -> s.shared("action_verbalize_history_list")
        }
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private companion object {
        /** How many steps a list gives when not told */
        const val DEFAULT_LIMIT = 100
    }
}
