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
     * The fields of the entries a selection's [target] holds, by path, or of the rows of its
     * [view] (core/views): a tool instance's, for one entry its tool's, a view's own (a variable's
     * series, its steps').
     *
     * @throws IllegalStateException when the target is none of them, its entry is gone, a variable
     * is named without its series, or the view cannot be opened
     */
    suspend fun ofTarget(target: app.treelune.core.selection.Reference, view: String?, context: Context, s: StringsContext): Map<String, FieldDefinition> = when {
        view != null -> app.treelune.core.views.Views.of(target, view, context, s).fields
        target.kind == app.treelune.core.selection.ReferenceKind.TOOL_INSTANCE -> filterable(target.id!!, context, s)
        target.kind == app.treelune.core.selection.ReferenceKind.ENTRY -> filterable(
            app.treelune.core.database.AppDatabase.getDatabase(context).toolDataDao().getById(target.id!!)?.toolInstanceId
                ?: throw IllegalStateException(s.shared("service_error_entry_not_found").format(target.id)), context, s)
        target.kind == app.treelune.core.selection.ReferenceKind.VARIABLE -> throw IllegalStateException(s.shared("selection_problem_variable"))
        else -> throw IllegalStateException(s.shared("service_error_reading_source").format(target.kind.name))
    }

    /**
     * The fields the entries of [toolInstanceId] can be filtered and read on, by path
     * (EntryFilters.filterableFields); with [sortKeys], also those offered as sort keys alone, a
     * manual position (EntryFilters.sortableFields).
     *
     * @throws IllegalStateException when the tool cannot be read
     */
    suspend fun filterable(toolInstanceId: String, context: Context, s: StringsContext, sortKeys: Boolean = false): Map<String, FieldDefinition> {
        val result = Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException(s.shared("service_error_tool_instance_not_found"))
        }
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        val declared = toolType.getEntryFields(config, context)
        return if (sortKeys) EntryFilters.sortableFields(declared, extra) { s.shared(it) } else EntryFilters.filterableFields(declared, extra) { s.shared(it) }
    }
}
