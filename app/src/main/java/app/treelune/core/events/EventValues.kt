package app.treelune.core.events

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * An event's declaration (docs/CORE.md, « Events »): its name and the values it carries, each a
 * name and a type — a text, a number, a yes or no, an entry of one tool. Stored as
 * `[{"name": "fiche", "type": "entry", "tool_instance_id": {"kind": "TOOL_INSTANCE", "id"}}]`,
 * declared with the settings nodes ([nodes]): the form of its editor and its schema.
 */
object EventValues {

    const val NAME = "name"
    const val VALUES = "values"
    const val TYPE = "type"
    const val TOOL = "tool_instance_id"

    enum class Type(val key: String) {
        TEXT("text"), NUMBER("number"), YES_NO("yes_no"), ENTRY("entry");

        companion object {
            fun of(key: String) = entries.first { it.key == key }
        }
    }

    /** One value an event carries: an entry's of [toolInstanceId]. */
    data class Value(val name: String, val type: Type, val toolInstanceId: String? = null)

    /** An event as its editor and its service take it: its name and its values. */
    fun nodes(text: (String) -> String): List<SettingNode> = listOf(
        SettingNode.Field(FieldDefinition(NAME, text("label_name"), text("event_schema_name"), FieldType.TEXT, true, mapOf("length" to TextLength.SHORT.name))),
        SettingNode.ListOf(
            name = VALUES,
            label = text("event_values"),
            item = SettingNode.Item.Of(listOf(
                SettingNode.Field(FieldDefinition(NAME, text("label_name"), text("event_schema_value_name"), FieldType.TEXT, true, mapOf("length" to TextLength.SHORT.name))),
                SettingNode.Variant(
                    selector = SettingNode.Field(FieldDefinition(TYPE, text("event_value_type"), text("event_schema_value_type"), FieldType.CHOICE, true,
                        mapOf("options" to ChoiceSettings.storedOptions(Type.entries.map { it.key }, Type.entries.associate { it.key to text("event_value_type_${it.key}") })))),
                    cases = Type.entries.associate { type ->
                        type.key to if (type == Type.ENTRY) listOf(SettingNode.Field(FieldDefinition(TOOL, text("event_value_tool"), text("event_schema_value_tool"), FieldType.REFERENCE, true,
                            mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.TOOL_INSTANCE.name)))))) else emptyList()
                    }
                )
            ), description = text("event_schema_value")),
            distinct = false,
            summary = listOf(NAME, TYPE),
            description = text("event_schema_values")
        )
    )

    /**
     * The values [json] declares, in their order.
     *
     * @throws IllegalArgumentException on one that does not read
     */
    fun read(json: String): List<Value> = JSONArray(json).let { list ->
        (0 until list.length()).map { index ->
            val value = list.getJSONObject(index)
            val type = Type.entries.firstOrNull { it.key == value.optString(TYPE) } ?: throw IllegalArgumentException("Unknown value type '${value.optString(TYPE)}'")
            Value(value.getString(NAME), type, if (type == Type.ENTRY) ReferenceTarget.referenceOf(value.opt(TOOL))?.id
                ?: throw IllegalArgumentException("The entry value '${value.optString(NAME)}' names no tool") else null)
        }
    }

    /** The field a value [value] is entered as, for a step that emits it: an entry of its tool designated. */
    fun field(value: Value, label: String = value.name): FieldDefinition = when (value.type) {
        Type.TEXT -> FieldDefinition(value.name, label, null, FieldType.TEXT, false, mapOf("length" to TextLength.UNLIMITED.name))
        Type.NUMBER -> FieldDefinition(value.name, label, null, FieldType.NUMERIC, false, null)
        Type.YES_NO -> FieldDefinition(value.name, label, null, FieldType.BOOLEAN, false, null)
        Type.ENTRY -> FieldDefinition(value.name, label, null, FieldType.REFERENCE, false,
            mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name), ReferenceTarget.TOOL_INSTANCES to listOf(value.toolInstanceId))))
    }

    /** Why [given] cannot be what an event declaring [declared] carries; null when it can: each value declared given, of its type, nothing else. */
    fun refusal(declared: List<Value>, given: JSONObject, text: (String) -> String): String? {
        val names = declared.map { it.name }.toSet()
        given.keys().asSequence().firstOrNull { it !in names }?.let { return text("event_error_value_unknown").format(it, names.joinToString(", ").ifEmpty { "-" }) }
        for (value in declared) {
            if (!given.has(value.name) || given.isNull(value.name)) return text("event_error_value_missing").format(value.name)
            val v = given.get(value.name)
            val fits = when (value.type) {
                Type.TEXT -> v is String
                Type.NUMBER -> v is Number
                Type.YES_NO -> v is Boolean
                Type.ENTRY -> v is String
            }
            if (!fits) return text("event_error_value_type").format(value.name, text("event_value_type_${value.type.key}"))
        }
        return null
    }
}
