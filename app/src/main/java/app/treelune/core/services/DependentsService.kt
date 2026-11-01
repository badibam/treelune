package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * What depends on a thing, for a screen to say it before deleting it (Dependents,
 * docs/design/dependents.md):
 * - of: `target`, `{"kind": "TOOL_INSTANCE" | "VARIABLE" | "ENTRY", "id"}` or
 *   `{"kind": "FIELD", "tool_instance_id", "path"}` — `dependents`, each `{"kind", "id", "name"}`,
 *   with `count` for the entries of a tool that designate an entry; `said`, the same in words
 */
class DependentsService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        if (operation != "of") return OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        val target = params.optJSONObject("target") ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("target"))
        val read = when (target.optString("kind")) {
            "TOOL_INSTANCE" -> Dependents.Target.Tool(target.optString("id"))
            "VARIABLE" -> Dependents.Target.Variable(target.optString("id"))
            "ENTRY" -> Dependents.Target.Entry(target.optString("id"))
            "FIELD" -> Dependents.Target.Field(target.optString("tool_instance_id"), target.optString("path"))
            else -> return OperationResult.error(s.shared("service_error_missing_required_params").format("target.kind"))
        }
        val dependents = Dependents.of(context, read)
        return OperationResult.success(mapOf("dependents" to dependents.map { it.toMap() }, "said" to Dependents.said(dependents, s)))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String =
        Strings.`for`(context = context).shared("action_verbalize_unknown")
}
