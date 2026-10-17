package app.treelune.core.conditions

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.settings.RowFields
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils
import app.treelune.core.variables.VariableDefinition
import org.json.JSONObject

/**
 * The app's answers to what a config's conditions name: a tool's fields and a variable's through
 * the coordinator, the rows' fields through the tool type that describes them.
 *
 * @param rows The tool type's RowFields (ToolTypeContract.getRowFields); null when it gives none
 */
class AppConditionSources(private val context: Context, private val rows: RowFields?) : ConfigConditions.Sources {

    private val s = Strings.`for`(context = context)

    override suspend fun toolFields(toolInstanceId: String): Map<String, FieldDefinition> = ToolFields.filterable(toolInstanceId, context, s)

    override suspend fun variableField(id: String): FieldDefinition? {
        val result = Coordinator(context).processUserAction("variables.get", mapOf("variable_id" to id))
        if (!result.isSuccess) return null
        val variable = result.data?.get("variable") as? Map<*, *> ?: return null
        val name = variable["name"] as? String ?: id
        val definition = JsonUtils.toJSONObject((variable["definition"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: return null)
        return VariableDefinition.fromJson(definition, name) { s.shared(it) }.field.copy(displayName = name)
    }

    override suspend fun rowFields(root: JSONObject, path: List<Any>): Map<String, FieldDefinition>? = rows?.at(root, path, context)
}
