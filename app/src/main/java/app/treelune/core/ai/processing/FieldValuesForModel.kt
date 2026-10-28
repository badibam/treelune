package app.treelune.core.ai.processing

import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.fields.ConfigFieldValues
import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject
import java.time.ZoneId

/**
 * The values of the user's fields a config holds (SettingNode.FieldValues: a tracking shortcut's
 * fields), in the form the model speaks and back, as an entry's are: an instant or a duration in
 * ISO 8601. The config's schema cannot mark where they sit, its fields being the tool's own: the
 * field each value is given for is read in the config itself (ConfigFieldValues.fieldsOf), and
 * its schema (FieldValueSchema.of) says what to convert, as in an entry. A key of no field is left
 * as it is, for the service to name it.
 */
object FieldValuesForModel {

    /** [config], declared by [nodes], with the values the model wrote in their stored form. */
    fun toStored(nodes: List<SettingNode>, config: JSONObject, zone: ZoneId): JSONObject =
        map(nodes, config) { value, schema -> ModelValues.fromModel(value, schema, zone) }

    /** [config], declared by [nodes], with its stored values in the form the model reads: the way back of [toStored]. */
    fun toModel(nodes: List<SettingNode>, config: JSONObject, zone: ZoneId): JSONObject =
        map(nodes, config) { value, schema -> ModelValues.toModel(value, schema, zone) }

    private fun map(nodes: List<SettingNode>, config: JSONObject, convert: (Any?, JSONObject) -> Any?): JSONObject {
        val next = JSONObject(config.toString())
        ConfigFieldValues.all(nodes, next).forEach { found ->
            val values = found.values ?: return@forEach
            val fields = ConfigFieldValues.fieldsOf(found.node, next).associateBy { it.name }
            values.keys().asSequence().toList().forEach { key ->
                val field = fields[key] ?: return@forEach
                values.put(key, JSONObject.wrap(convert(JsonUtils.toValue(values.get(key)), FieldValueSchema.of(field))))
            }
        }
        return next
    }
}
