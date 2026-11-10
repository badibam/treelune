package app.treelune.core.fields.settings

import app.treelune.core.fields.FieldContainer
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.migration.EntryMigration
import app.treelune.core.fields.migration.OptionRenames
import org.json.JSONArray
import org.json.JSONObject

/**
 * The value each option of a CHOICE field had when a config screen read it, followed as the
 * config is edited: the only place an option renamed can be told from one removed beside one
 * added (OptionRenames). Each option of the config read takes its value a second time, under
 * [FORMER]; the form carries that key with the option wherever it moves, an option added has
 * none. Screen state, never stored: the config is sent [untagged], with the [renames] it gives.
 */
object OptionOrigins {

    /** The key an option holds its former value under, while the config is on screen. */
    private const val FORMER = "former_value"

    /** [config] with each option of a choice it holds marked with its value. */
    fun tagged(config: JSONObject): JSONObject = JSONObject(config.toString()).also { walk(it) { option -> option.put(FORMER, option.get("value")) } }

    /** [config] without the marks [tagged] put. */
    fun untagged(config: JSONObject): JSONObject = JSONObject(config.toString()).also { walk(it) { option -> option.remove(FORMER) } }

    /**
     * The renames the fields of a [tagged] config give, by the path of their field: each option
     * whose value is no longer the one it was read with. A field the config [loaded] did not hold
     * renames nothing: a new field, or one that took a new key (NewFieldInstead), starts empty.
     */
    fun renames(fields: EntryMigration.Fields, loaded: EntryMigration.Fields): OptionRenames {
        val held = loaded.data.map { OptionRenames.path(FieldContainer.DATA, it.definition.name) }.toSet() +
            loaded.extra.map { OptionRenames.path(FieldContainer.EXTRA, it.name) }
        fun of(container: FieldContainer, field: FieldDefinition): Pair<String, Map<String, String>>? {
            if (field.type != FieldType.CHOICE) return null
            val path = OptionRenames.path(container, field.name)
            if (path !in held) return null
            val renamed = (field.config?.get("options") as? List<*>).orEmpty().mapNotNull { option ->
                val map = option as? Map<*, *> ?: return@mapNotNull null
                val former = map[FORMER]?.toString() ?: return@mapNotNull null
                val value = map["value"]?.toString() ?: return@mapNotNull null
                if (former != value) former to value else null
            }.toMap()
            return renamed.takeIf { it.isNotEmpty() }?.let { path to it }
        }
        return OptionRenames((fields.data.mapNotNull { of(FieldContainer.DATA, it.definition) } + fields.extra.mapNotNull { of(FieldContainer.EXTRA, it) }).toMap())
    }

    /** Each option of [node]: an object of an array named "options" that holds a value. */
    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> node.keys().asSequence().toList().forEach { key ->
                val child = node.opt(key)
                if (key == "options" && child is JSONArray) {
                    for (i in 0 until child.length()) (child.opt(i) as? JSONObject)?.takeIf { it.opt("value") is String }?.let(visit)
                } else walk(child, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
