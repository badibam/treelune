package app.treelune.core.fields

import app.treelune.core.coordinator.Source
import org.json.JSONArray
import org.json.JSONObject

/**
 * A field the AI only reads (FieldDefinition.aiReadOnly), which a person enters: the AI never
 * writes it, in a chat, by a routine or come through the MCP server. The rest of the app writes
 * it as any field: a person's import, the demo, a value cleared by a config change.
 *
 * ToolDataService refuses the whole write that holds one, by the origin of the call, so the AI has
 * no way around it: an import it starts writes its lines as the AI. Marking a field is anyone's,
 * unmarking it a person's (ToolInstanceService): the AI could otherwise unmark, write, mark again.
 */
object AiReadOnly {

    /** Whether a call of [source] is refused the fields the AI only reads. */
    fun refuses(source: Source): Boolean = source == Source.AI || source == Source.EXTERNAL

    /**
     * The fields among [fields] that [sent] holds and the AI only reads, a key sent as null (a
     * value cleared) included.
     */
    fun held(sent: JSONObject?, fields: List<FieldDefinition>): List<FieldDefinition> =
        if (sent == null) emptyList() else fields.filter { it.aiReadOnly && sent.has(it.name) }

    /** The fields [before] marks that [after] keeps under the same name, no longer marked. */
    fun unmarked(before: List<FieldDefinition>, after: List<FieldDefinition>): List<FieldDefinition> {
        val now = after.associateBy { it.name }
        return before.filter { it.aiReadOnly && now[it.name]?.aiReadOnly == false }
    }

    /**
     * The ids of the definitions among [definitions] that are marked. [id] is the key that names a
     * definition; [locate] finds the object that holds its mark: the definition itself, or a goal's
     * criterion's entered field.
     */
    fun markedIds(definitions: List<JSONObject>, id: String = "name", locate: (JSONObject) -> JSONObject? = { it }): Set<String> =
        definitions.filter { locate(it)?.optBoolean(AI_READ_ONLY, false) == true }.map { it.optString(id) }.toSet()

    /**
     * Marks again, in place, each of [definitions] whose id is in [marked] and that was sent without
     * the key: a config the AI rewrites whole to change something else keeps what it left out. A
     * mark sent false stays false, for unmarked to refuse.
     */
    fun keepMarks(definitions: List<JSONObject>, marked: Set<String>, id: String = "name", locate: (JSONObject) -> JSONObject? = { it }) {
        definitions.filter { it.optString(id) in marked }
            .forEach { definition -> locate(definition)?.takeIf { !it.has(AI_READ_ONLY) }?.put(AI_READ_ONLY, true) }
    }

    /** The objects of [array], the ones it holds and not copies. */
    fun objects(array: JSONArray?): List<JSONObject> = array?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

    /** [fields] named for a message, each between the quotes of the app's language ([text], the shared strings). */
    fun named(fields: List<FieldDefinition>, text: (String) -> String): String = fields.joinToString(", ") { text("quoted").format(it.displayName) }
}
