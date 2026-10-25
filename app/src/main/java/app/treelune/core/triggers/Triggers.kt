package app.treelune.core.triggers

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * The « when? » of what runs by itself, beside its schedule (docs/CORE.md, « Triggers »): a list
 * of what makes it start, each one kind. Today one kind, an entry written in a tool — created,
 * changed or deleted, passing the entry's filters when it has some.
 *
 * Stored under `triggers`: `[{"kind": "entry_written", "writes": ["created", "updated"],
 * "entries": {"target": {"kind": "TOOL_INSTANCE", "id"}, "filters": [...]}}]`. Declared with the settings
 * nodes ([nodes]): its form, its schema and the check of its filters come from there.
 */
object Triggers {

    const val KEY = "triggers"
    const val KIND = "kind"
    const val ENTRY_WRITTEN = "entry_written"
    const val WRITES = "writes"
    const val ENTRIES = "entries"

    /** What happens to an entry. */
    enum class Write(val key: String) {
        CREATED("created"), UPDATED("updated"), DELETED("deleted");

        companion object {
            fun of(key: String) = entries.first { it.key == key }
        }
    }

    /** One trigger, read. */
    sealed interface Trigger {
        /** An entry of [toolInstanceId] written in one of [writes], passing [filters] (Conditions put on the entry). */
        data class EntryWritten(val toolInstanceId: String, val writes: Set<Write>, val filters: JSONArray) : Trigger
    }

    /** The list's declaration, [text] the shared string of a key. */
    fun node(text: (String) -> String): SettingNode = SettingNode.ListOf(
        name = KEY,
        label = text("trigger_list"),
        item = SettingNode.Item.Of(listOf(
            SettingNode.Variant(
                selector = SettingNode.Field(FieldDefinition(KIND, text("trigger_kind"), text("trigger_schema_kind"), FieldType.CHOICE, true,
                    mapOf("options" to ChoiceSettings.storedOptions(listOf(ENTRY_WRITTEN), mapOf(ENTRY_WRITTEN to text("trigger_kind_entry_written")))))),
                cases = mapOf(ENTRY_WRITTEN to listOf(
                    SettingNode.Field(FieldDefinition(WRITES, text("trigger_writes"), text("trigger_schema_writes"), FieldType.CHOICE, true,
                        mapOf("options" to ChoiceSettings.storedOptions(Write.entries.map { it.key }, Write.entries.associate { it.key to text("trigger_write_${it.key}") })) + ("multiple" to true))),
                    SettingNode.Selection(ENTRIES, text("trigger_entries"), text("trigger_reference"), required = true)
                )),
                descriptions = mapOf(ENTRY_WRITTEN to text("trigger_schema_entry_written"))
            )
        ), description = text("trigger_schema_item")),
        summary = listOf(KIND, WRITES),
        description = text("trigger_schema_list")
    )

    /**
     * The triggers [list] stores, in its order.
     *
     * @throws IllegalArgumentException on one that does not read: the settings were checked when written
     */
    fun read(list: JSONArray?): List<Trigger> = (0 until (list?.length() ?: 0)).map { index ->
        val trigger = list!!.getJSONObject(index)
        when (val kind = trigger.optString(KIND)) {
            ENTRY_WRITTEN -> {
                val entries = trigger.getJSONObject(ENTRIES)
                val target = entries.getJSONObject("target")
                require(target.optString("kind") == app.treelune.core.selection.ReferenceKind.TOOL_INSTANCE.name) { "A trigger's entries are a tool's, not ${target.optString("kind")}" }
                val writes = trigger.getJSONArray(WRITES)
                Trigger.EntryWritten(
                    toolInstanceId = target.getString("id"),
                    writes = (0 until writes.length()).map { Write.of(writes.getString(it)) }.toSet(),
                    filters = entries.optJSONArray("filters") ?: JSONArray()
                )
            }
            else -> throw IllegalArgumentException("Unknown trigger kind '$kind'")
        }
    }

    /** The triggers stored in [json], a Task's or a routine's column; none when null. */
    fun read(json: String?): List<Trigger> = read(json?.let { JSONArray(it) })

    /** Whether [settings] hold triggers: what keeps them as their column. */
    fun of(settings: JSONObject): JSONArray? = settings.optJSONArray(KEY)?.takeIf { it.length() > 0 }
}
