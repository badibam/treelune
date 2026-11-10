package app.treelune.core.fields.migration

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldContainer
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.strings.StringsContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The options of CHOICE fields a config change renames: by the path a filter names the field
 * with (`extra.mood`, `data.value`), each former value to its new one. Only the editor of the
 * options knows a rename from an option removed beside one added: it hands its renames with the
 * config, under `renames.options` of `tools.update` (docs/DATA.md, « Config change »).
 *
 * A renamed option keeps what it holds: the entries that chose it, the values of the user's
 * fields a config holds, and the filters of other tools that compare the field with it, all take
 * the new value. The comparison of the old fields with the new then sees no option gone.
 */
class OptionRenames(val byPath: Map<String, Map<String, String>>) {

    fun isEmpty(): Boolean = byPath.isEmpty()

    /** [value], a value of the field at [path] (one option, or a list of them), under the new names. */
    fun value(path: String, value: Any?): Any? {
        val renames = byPath[path] ?: return value
        return when (value) {
            is String -> renames[value] ?: value
            is List<*> -> value.map { (it as? String)?.let { option -> renames[option] } ?: it }
            is JSONArray -> JSONArray((0 until value.length()).map { value.opt(it).let { option -> (option as? String)?.let { o -> renames[o] } ?: option } })
            else -> value
        }
    }

    /** The values of an entry's [container], each under the new names of its field's options. */
    fun values(container: FieldContainer, values: Map<String, Any?>): Map<String, Any?> =
        if (isEmpty()) values else values.mapValues { (name, value) -> value(path(container, name), value) }

    /** [fields] as the old config gave them, their renamed options under their new names. */
    fun fields(fields: EntryMigration.Fields): EntryMigration.Fields = if (isEmpty()) fields else EntryMigration.Fields(
        data = fields.data.map { it.copy(definition = renamed(FieldContainer.DATA, it.definition)) },
        extra = fields.extra.map { renamed(FieldContainer.EXTRA, it) }
    )

    /** The user's fields [fields], their renamed options under their new names. */
    fun extra(fields: List<FieldDefinition>): List<FieldDefinition> = if (isEmpty()) fields else fields.map { renamed(FieldContainer.EXTRA, it) }

    private fun renamed(container: FieldContainer, field: FieldDefinition): FieldDefinition {
        val renames = byPath[path(container, field.name)] ?: return field
        val options = (field.config?.get("options") as? List<*>) ?: return field
        val next = options.map { option ->
            val held = option as? Map<*, *> ?: return@map option
            val value = held["value"]?.toString()
            if (value != null && value in renames) held.entries.associate { it.key.toString() to it.value } + ("value" to renames.getValue(value)) else held
        }
        return field.copy(config = field.config + ("options" to next))
    }

    /**
     * Why these renames cannot go from the fields [old] to [new]; null when they can. Each names a
     * CHOICE field both have; each former value is an option of [old] that [new] no longer has,
     * each new one an option of [new] that [old] did not have, and no two share their new value.
     */
    fun refusal(old: EntryMigration.Fields, new: EntryMigration.Fields, s: StringsContext): String? {
        fun choices(fields: EntryMigration.Fields) = fields.data.map { path(FieldContainer.DATA, it.definition.name) to it.definition } +
            fields.extra.map { path(FieldContainer.EXTRA, it.name) to it }
        val before = choices(old).toMap()
        val after = choices(new).toMap()
        byPath.forEach { (path, renames) ->
            val was = before[path]?.takeIf { it.type == FieldType.CHOICE }
            val now = after[path]?.takeIf { it.type == FieldType.CHOICE }
            if (was == null || now == null) return s.shared("service_error_option_renames_field").format(path)
            val formerOptions = ChoiceSettings.fromConfig(was.config).options
            val newOptions = ChoiceSettings.fromConfig(now.config).options
            renames.forEach { (from, to) ->
                if (from !in formerOptions || from in newOptions || to !in newOptions || to in formerOptions)
                    return s.shared("service_error_option_renames_option").format(from, to, path)
            }
            if (renames.values.toSet().size != renames.size) return s.shared("service_error_option_renames_twice").format(path)
        }
        return null
    }

    /** [config] with the default of each user field that chose a renamed option under its new name. */
    fun defaults(config: JSONObject): JSONObject {
        val next = JSONObject(config.toString())
        val fields = next.optJSONArray("extra_fields") ?: return next
        for (i in 0 until fields.length()) {
            val field = fields.optJSONObject(i) ?: continue
            val path = path(FieldContainer.EXTRA, field.optString("name"))
            if (path !in byPath || !field.has("default_value") || field.isNull("default_value")) continue
            field.put("default_value", JSONObject.wrap(value(path, field.get("default_value"))))
        }
        return next
    }

    /** These renames as a write's params hold them: `{"options": {path: {former: new}}}`. */
    fun toParams(): Map<String, Any> = mapOf(KEY to byPath)

    companion object {
        /** Under `renames` of `tools.update`. */
        const val KEY = "options"

        val NONE = OptionRenames(emptyMap())

        /** The path a filter names the field [name] of [container] with. */
        fun path(container: FieldContainer, name: String): String = "${container.key}.$name"

        /** The renames a write's params hold under `renames.options`; none when absent. */
        fun read(params: JSONObject): OptionRenames {
            val given = params.optJSONObject("renames")?.optJSONObject(KEY) ?: return NONE
            return OptionRenames(given.keys().asSequence().associateWith { path ->
                val renames = given.getJSONObject(path)
                renames.keys().asSequence().associateWith { renames.getString(it) }
            }.filterValues { it.isNotEmpty() })
        }
    }
}
