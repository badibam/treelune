package app.treelune.core.fields

import android.content.Context
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * An entry written out as text, to be copied away from the app: the parts its tool gives (a date,
 * a title, a content), then the user's fields, a block apart from each other.
 */
object EntryText {

    /**
     * [parts], the blank ones left out, then the fields of [config] that [extra] fills, one a line,
     * "Name : value" in the order the config lists them, each value written whole (describeValue),
     * a reference by the name of what it designates, those of a list joined as a multiple choice's.
     */
    suspend fun of(parts: List<String?>, config: JSONObject?, extra: Map<String, Any?>, context: Context): String {
        val fields = config?.let { fieldLines(it, extra, context) }.orEmpty()
        return (parts.filterNot { it.isNullOrBlank() }.map { it!!.trim() } + listOfNotNull(fields.takeIf { it.isNotEmpty() }?.joinToString("\n")))
            .joinToString("\n\n")
    }

    /** The lines of the user's fields that hold a value. */
    private suspend fun fieldLines(config: JSONObject, extra: Map<String, Any?>, context: Context): List<String> {
        val s = Strings.`for`(context = context)
        val fields = config.optJSONArray("extra_fields")?.toFieldDefinitions().orEmpty()
            .filter { filled(extra[it.name]) }
        val references = fields.filter { it.type == FieldType.REFERENCE }.flatMap { ReferenceTarget.referencesOf(extra[it.name]) }
        val names = try {
            loadReferenceNames(references, context)
        } catch (e: Exception) {
            LogManager.ui("Entry text: reference names not read: ${e.message}", "ERROR", e)
            null
        }
        return fields.map { field ->
            val value = extra[field.name]
            val text = if (field.type == FieldType.REFERENCE) {
                ReferenceTarget.referencesOf(value).ifEmpty { listOf(null) }.joinToString(", ") { reference ->
                    when (val name = reference?.let { names?.get(it) }) {
                        is ReferenceName.Named -> name.name
                        ReferenceName.Deleted -> s.shared("pointer_target_deleted")
                        null -> s.shared("field_reference_unreadable")
                    }
                }
            } else field.describeValue(value, context)
            s.shared("entry_text_field").format(field.displayName, text)
        }
    }

    /** Whether a stored value says something: not absent, not an empty text or list. */
    private fun filled(value: Any?): Boolean = when (value) {
        null -> false
        is String -> value.isNotBlank()
        is Collection<*> -> value.isNotEmpty()
        else -> true
    }
}
