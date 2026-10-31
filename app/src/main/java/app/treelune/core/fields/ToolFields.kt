package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.JsonUtils

/** The fields of a tool instance's entries, read from its current config through tools.get. */
object ToolFields {

    /**
     * The fields the entries of [toolInstanceId] can be filtered and read on, by path
     * (EntryFilters.filterableFields).
     *
     * @throws IllegalStateException when the tool cannot be read
     */
    /**
     * The fields of the entries a selection's [target] holds, by path: a tool instance's, for one
     * entry its tool's, for a variable with a series its steps' (VariableSeries).
     *
     * @throws IllegalStateException when the target is none of them, its entry is gone, or the
     * variable has no series
     */
    suspend fun ofTarget(target: app.treelune.core.selection.Reference, context: Context, s: StringsContext): Map<String, FieldDefinition> = when (target.kind) {
        app.treelune.core.selection.ReferenceKind.TOOL_INSTANCE -> filterable(target.id!!, context, s)
        app.treelune.core.selection.ReferenceKind.ENTRY -> filterable(
            app.treelune.core.database.AppDatabase.getDatabase(context).toolDataDao().getById(target.id!!)?.toolInstanceId
                ?: throw IllegalStateException(s.shared("service_error_entry_not_found").format(target.id)), context, s)
        app.treelune.core.selection.ReferenceKind.VARIABLE -> series(target.id!!, context, s).second
        else -> throw IllegalStateException(s.shared("service_error_reading_source").format(target.kind.name))
    }

    /**
     * The unit of the series of the variable [variableId] and the fields of its steps' entries.
     *
     * @throws IllegalStateException when the variable is gone, does not read, or has no series
     */
    suspend fun series(variableId: String, context: Context, s: StringsContext): Pair<app.treelune.core.selection.PeriodType, Map<String, FieldDefinition>> {
        val variable = app.treelune.core.database.AppDatabase.getDatabase(context).variableDao().getById(variableId)
            ?: throw IllegalStateException(s.shared("variable_error_not_found").format(variableId))
        val definition = try {
            app.treelune.core.variables.VariableDefinition.fromJson(org.json.JSONObject(variable.definitionJson), variable.name) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(e.message ?: "")
        }
        val unit = definition.series ?: throw IllegalStateException(s.shared("variable_error_no_series").format(variable.label))
        return unit to app.treelune.core.variables.VariableSeries.fields(definition, variable.label) { s.shared(it) }
    }

    suspend fun filterable(toolInstanceId: String, context: Context, s: StringsContext): Map<String, FieldDefinition> {
        val result = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
        }
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        return EntryFilters.filterableFields(toolType.getEntryFields(config, context), extra) { s.shared(it) }
    }
}
