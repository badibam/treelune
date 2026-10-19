package app.treelune.tools.flashcards

import android.content.Context
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.settings.RowFields
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * The fields a deck's form offers where a setting names one (RowFields): its groups' field and
 * its directions' faces, among the fields of its source's lines, wherever they stand. Until a
 * source is chosen there are none; a source that does not read is logged and offers none, the
 * config's check (FlashcardsToolType.refuseConfig) naming it at the save.
 */
object FlashcardsRowFields : RowFields {

    override suspend fun at(root: JSONObject, path: List<Any>, context: Context): Map<String, FieldDefinition> {
        val source = sourceId(root) ?: return emptyMap()
        return try {
            ToolFields.filterable(source, context, Strings.`for`(tool = "flashcards", context = context))
        } catch (e: IllegalStateException) {
            LogManager.ui("FlashcardsRowFields: fields of $source not read: ${e.message}", "WARN")
            emptyMap()
        }
    }

    /** Only the source gives the fields. */
    override fun dependsOn(root: JSONObject, path: List<Any>): Any = sourceId(root) ?: ""

    private fun sourceId(root: JSONObject): String? =
        root.optJSONObject(FlashcardsDeck.SOURCE)?.optJSONObject("target")?.optString("id")?.takeIf { it.isNotEmpty() }
}
