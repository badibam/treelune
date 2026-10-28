package app.treelune.core.fields

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext

/**
 * Whether a REFERENCE value designates something that exists, and for an entry, one of a tool
 * instance the field takes, down to one of its fields when it names one. The same check for a
 * value written in an entry (ToolDataService) and in a config (ConfigFieldValues).
 */
object ReferenceCheck {

    /** Why [reference], a value of [field], cannot be written; null when it holds. */
    suspend fun refusal(field: FieldDefinition, reference: Reference, context: Context, s: StringsContext): String? {
        val database = AppDatabase.getDatabase(context)
        val error = when (reference.kind) {
            ReferenceKind.APP -> null
            ReferenceKind.ZONE -> s.shared("field_value_reference_not_found").takeIf { database.zoneDao().getZoneById(reference.id!!) == null }
            ReferenceKind.TOOL_INSTANCE -> s.shared("field_value_reference_not_found").takeIf { database.toolInstanceDao().getToolInstanceById(reference.id!!) == null }
            ReferenceKind.VARIABLE -> s.shared("field_value_reference_not_found").takeIf { database.variableDao().getById(reference.id!!) == null }
            ReferenceKind.EVENT -> s.shared("field_value_reference_not_found").takeIf { database.eventDao().get(reference.id!!) == null }
            ReferenceKind.ENTRY -> when (val entry = database.toolDataDao().getById(reference.id!!)) {
                null -> s.shared("field_value_reference_not_found")
                else -> s.shared("field_value_reference_wrong_tool").takeIf { !ReferenceTarget.fromConfig(field.config).acceptsEntryOf(entry.toolInstanceId) }
                    ?: reference.field?.let { path ->
                        val fields = ToolFields.filterable(entry.toolInstanceId, context, s)
                        s.shared("field_value_reference_no_field").format(path, fields.keys.joinToString(", ")).takeIf { path !in fields }
                    }
            }
        } ?: return null
        return s.shared("error_custom_field_validation_failed").format(field.displayName, error.format(reference.kind.name, reference.id))
    }
}
