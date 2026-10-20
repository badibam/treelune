package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.access.AccessMask
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Protections
import app.treelune.core.database.AppDatabase
import app.treelune.core.history.Writes
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * The connector's clients as the user manages them in the external access screen: what each
 * may reach (its access mask) and its revocation. A person's gestures alone: the AI and the
 * clients never call it. Through the coordinator, each is a step of the history of writes, which
 * an undo takes back — a revoked client back without its secret nor its tokens, which the history
 * never copies, and so to be connected again.
 *
 * Operations:
 * - set_access: client_id, access (the mask, as AccessMask.toJson writes it)
 * - delete: client_id, its tokens going with it
 */
class McpClientService(private val context: Context) : ExecutableService {
    private val s = Strings.`for`(context = context)
    private val dao = AppDatabase.getDatabase(context).mcpDao()
    private val writes = Writes(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        if (!Protections.byAPerson()) return OperationResult.error(s.shared("service_error_mcp_clients_user_only"))
        val clientId = params.optString("client_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val client = dao.client(clientId) ?: return OperationResult.error(s.shared("service_error_mcp_client_not_found").format(clientId))
        return when (operation) {
            "set_access" -> {
                val access = params.optJSONArray("access")?.toString()
                    ?: return OperationResult.error(s.shared("service_error_mcp_client_access_missing"))
                // Read as the server reads it: a mask it could not read is refused here
                try {
                    AccessMask.fromJson(access)
                } catch (e: Exception) {
                    return OperationResult.error(s.shared("service_error_mcp_client_access_unreadable").format(e.message ?: ""))
                }
                writes.setClientAccess(clientId, access)
                OperationResult.success(mapOf("client_id" to clientId, "name" to client.name))
            }
            "delete" -> {
                writes.deleteClient(clientId)
                OperationResult.success(mapOf("client_id" to clientId, "name" to client.name))
            }
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val name = params.optString("name").ifEmpty { params.optString("client_id").let { id -> dao.client(id)?.name ?: id } }
        return when (operation) {
            "set_access" -> s.shared("action_verbalize_mcp_client_access").format(name)
            "delete" -> s.shared("action_verbalize_mcp_client_delete").format(name)
            else -> "mcp_clients.$operation"
        }
    }
}
