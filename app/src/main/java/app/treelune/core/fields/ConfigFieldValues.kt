package app.treelune.core.fields

import android.content.Context
import app.treelune.core.fields.migration.FieldConfigComparator
import app.treelune.core.fields.migration.FieldDataMigrator
import app.treelune.core.fields.migration.MigrationPolicy
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import app.treelune.core.utils.JsonUtils
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import app.treelune.core.validation.SchemaValidator
import org.json.JSONObject

/**
 * The values of the user's fields a config holds (SettingNode.FieldValues, a tracking shortcut's
 * fields), found through its declaration: down its groups, its lists of groups and every case of
 * its variants. They are held to what an entry's values are held to, and follow their fields'
 * changes the way an entry's do (docs/DATA.md, « Config change »).
 */
object ConfigFieldValues {

    /**
     * Values found: their declaration, the object holding them under its name, and where they
     * stand, named as the screen names it, for an error.
     */
    data class Found(val node: SettingNode.FieldValues, val holder: JSONObject, val label: String) {
        val values: JSONObject? get() = holder.optJSONObject(node.name)
    }

    /** The values [nodes] declare in [config]. */
    fun all(nodes: List<SettingNode>, config: JSONObject): List<Found> = walk(nodes, config, "")

    private fun walk(nodes: List<SettingNode>, obj: JSONObject, label: String): List<Found> = nodes.flatMap { node ->
        when (node) {
            is SettingNode.FieldValues -> listOf(Found(node, obj, under(label, node.label)))
            is SettingNode.Group -> obj.optJSONObject(node.name)?.let { walk(node.nodes, it, under(label, node.label)) }.orEmpty()
            is SettingNode.ListOf -> (node.item as? SettingNode.Item.Of)?.let { item ->
                val elements = obj.optJSONArray(node.name) ?: return@let emptyList()
                (0 until elements.length()).mapNotNull { i -> elements.optJSONObject(i)?.let { i to it } }.flatMap { (i, element) ->
                    // An element is named by the first value of its summary, or by its position
                    val name = node.summary.firstNotNullOfOrNull { key -> element.optString(key).takeIf { it.isNotBlank() } } ?: "${i + 1}"
                    walk(item.nodes, element, under(under(label, node.label), name))
                }
            }.orEmpty()
            is SettingNode.Variant -> node.cases.values.flatMap { walk(it, obj, label) }
            is SettingNode.Section -> walk(node.nodes, obj, label)
            is SettingNode.Field, is SettingNode.Condition, is SettingNode.Term, is SettingNode.Selection,
            is SettingNode.Sort, is SettingNode.Period, is SettingNode.Instants -> emptyList()
        }
    }

    private fun under(label: String, step: String) = if (label.isEmpty()) step else "$label › $step"

    /**
     * The fields [node]'s values may be given for, as [root] defines them: all but a file field,
     * whose files belong to an entry.
     */
    fun fieldsOf(node: SettingNode.FieldValues, root: JSONObject): List<FieldDefinition> =
        root.optJSONArray(node.fields)?.toFieldDefinitions().orEmpty().filter { it.type != FieldType.FILE }

    /** The ids of the entries [config]'s values designate, for [migrated] to know their tools. */
    fun designatedEntries(nodes: List<SettingNode>, config: JSONObject): Set<String> =
        all(nodes, config).flatMap { found ->
            val values = found.values ?: return@flatMap emptyList()
            values.keys().asSequence().flatMap { ReferenceTarget.referencesOf(JsonUtils.toValue(values.opt(it))) }
                .filter { it.kind == ReferenceKind.ENTRY }.mapNotNull { it.id }.toList()
        }.toSet()

    /**
     * What [migrated] made of a config.
     *
     * @property config The config, its values following their fields
     * @property removed How many values left: their field gone, or changed so that they lost their meaning
     */
    data class Migrated(val config: JSONObject, val removed: Int)

    /**
     * [config] with its values following the change of their fields from [previous], as an
     * entry's follow it (EntryMigration): a field gone or of another type takes its value away, a
     * scale's bounds or a choice's shape convert it or take it away. Each number is rounded to its
     * field's decimals, as an entry's is.
     *
     * @param entryInstance The tool instance of an entry a reference designates, null once deleted
     */
    fun migrated(nodes: List<SettingNode>, previous: JSONObject?, config: JSONObject, entryInstance: (String) -> String?): Migrated {
        val next = JSONObject(config.toString())
        var removed = 0
        all(nodes, next).forEach { found ->
            val values = found.values ?: return@forEach
            val fields = next.optJSONArray(found.node.fields)?.toFieldDefinitions().orEmpty()
            val before = previous?.optJSONArray(found.node.fields)?.toFieldDefinitions().orEmpty()
            val changes = FieldConfigComparator.compare(before, fields)
            // A key of no field is left as it is: the config's check names it
            val held = JsonUtils.toMap(values)
            val kept = FieldDataMigrator.applyMigrationStrategies(held, changes, MigrationPolicy.getStrategies(changes), entryInstance)
            removed += held.keys.count { it !in kept }
            val rounded = JSONObject(NumericPrecision.roundAll(JsonUtils.toJSONObject(kept).toString(), fields)!!)
            if (rounded.length() > 0) found.holder.put(found.node.name, rounded) else found.holder.remove(found.node.name)
        }
        return Migrated(next, removed)
    }

    /**
     * Why [config]'s values cannot be stored; null when each can. A value is one of a field the
     * config defines, and holds as that field's value in an entry would: its schema, the rules a
     * schema cannot say, and a reference to something that exists. A reference [previous] already
     * held is not checked again: the thing it designates may have been deleted since, and the
     * config stays writable, as an entry holding it does.
     */
    suspend fun refusal(nodes: List<SettingNode>, config: JSONObject, previous: JSONObject?, context: Context, s: StringsContext): String? {
        val held = previous?.let { designatedBy(nodes, it) }.orEmpty()
        for (found in all(nodes, config)) {
            val values = found.values ?: continue
            val fields = fieldsOf(found.node, config).associateBy { it.name }
            for (key in values.keys()) {
                val field = fields[key]
                    ?: return s.shared("config_field_values_unknown").format(found.label, key, fields.keys.joinToString(", "))
                val value = JsonUtils.toValue(values.opt(key))
                val schema = Schema(
                    id = "field_values:$key", displayName = field.displayName, description = "",
                    category = SchemaCategory.TOOL_DATA,
                    content = JSONObject().put("type", "object")
                        .put("properties", JSONObject().put(key, FieldValueSchema.of(field)))
                        .toString()
                )
                val checked = SchemaValidator.validate(schema, mapOf(key to value), context)
                val refused = checked.errorMessage.takeIf { !checked.isValid }
                    ?: FieldValueValidator.validate(field, value, context).errorMessage
                if (refused != null) return s.shared("config_field_values_refused").format(found.label, s.shared("error_custom_field_validation_failed").format(field.displayName, refused))
                for (reference in ReferenceTarget.referencesOf(value)) {
                    if (reference in held[found.node.fields to key].orEmpty()) continue
                    ReferenceCheck.refusal(field, reference, context, s)?.let { return s.shared("config_field_values_refused").format(found.label, it) }
                }
            }
        }
        return null
    }

    /** The references [config]'s values hold, by the fields they are values of. */
    private fun designatedBy(nodes: List<SettingNode>, config: JSONObject): Map<Pair<String, String>, Set<app.treelune.core.selection.Reference>> {
        val held = mutableMapOf<Pair<String, String>, MutableSet<app.treelune.core.selection.Reference>>()
        all(nodes, config).forEach { found ->
            val values = found.values ?: return@forEach
            values.keys().forEach { key ->
                held.getOrPut(found.node.fields to key) { mutableSetOf() } += ReferenceTarget.referencesOf(JsonUtils.toValue(values.opt(key)))
            }
        }
        return held
    }

    /** [config] without any of its values, for a tool created before the entries they may designate (PartImport). */
    fun without(nodes: List<SettingNode>, config: JSONObject): JSONObject {
        val next = JSONObject(config.toString())
        all(nodes, next).forEach { it.holder.remove(it.node.name) }
        return next
    }
}
