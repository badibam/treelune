package app.treelune.core.readouts

import android.content.Context
import app.treelune.core.conditions.AppConditionSources
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.RowFields
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermFields
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * A readout's values as the fields of a row (RowFields): what its colour conditions compare, each
 * by its name (`{"field": "done"}`), as a chart's conditions compare a row's columns. A value is
 * the field its term gives values of (TermFields); a constant, which has no type of its own, the
 * type of what is written — a number, a yes or no, a text.
 *
 * The config is being written: a value that does not read yet is left out rather than refused,
 * the service's check naming it at the save.
 */
object ReadoutRows : RowFields {

    override suspend fun at(root: JSONObject, path: List<Any>, context: Context): Map<String, FieldDefinition> {
        val s = Strings.`for`(context = context)
        val sources = AppConditionSources(context, null)
        val values = root.optJSONArray(ReadoutSettings.VALUES) ?: return emptyMap()
        return (0 until values.length()).mapNotNull { values.optJSONObject(it) }.mapNotNull { value ->
            val name = value.optString(ReadoutSettings.NAME).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val stored = value.optJSONObject(ReadoutSettings.VALUE) ?: return@mapNotNull null
            try {
                val term = Term.fromJson(stored, name) { s.shared(it) }
                val field = TermFields.of(term, name, { s.shared(it) }, sources::targetFields, sources::variableField)
                    ?: constantField(term, name)
                    ?: return@mapNotNull null
                name to field.copy(name = name, displayName = name)
            } catch (e: IllegalArgumentException) {
                LogManager.ui("ReadoutRows: value '$name' not read: ${e.message}", "WARN")
                null
            }
        }.toMap()
    }

    /** Only the values give the fields: read again when they change, not at every keystroke elsewhere. */
    override fun dependsOn(root: JSONObject, path: List<Any>): Any = root.optJSONArray(ReadoutSettings.VALUES)?.toString() ?: ""

    /** The field a constant is compared as, by what is written; null for a term that is not a constant or a value of no such type. */
    fun constantField(term: Term, name: String): FieldDefinition? {
        val value = (term as? Term.Constant)?.value ?: return null
        val type = when (value) {
            is Number -> FieldType.NUMERIC
            is Boolean -> FieldType.BOOLEAN
            is String -> FieldType.TEXT
            else -> return null
        }
        return FieldDefinition(name, name, null, type, false, null)
    }
}
