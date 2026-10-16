package app.treelune.core.fields

import app.treelune.core.coordinator.Source
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

    /** [fields] named for a message, each between quotes. */
    fun named(fields: List<FieldDefinition>): String = fields.joinToString(", ") { "« ${it.displayName} »" }
}
