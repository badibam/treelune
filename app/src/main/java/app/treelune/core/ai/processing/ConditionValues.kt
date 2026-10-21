package app.treelune.core.ai.processing

import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.conditions.ConditionCheck
import app.treelune.core.conditions.ConfigConditions
import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.TimePoint
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject
import java.time.ZoneId

/**
 * The written values of the conditions in a config the AI writes, put in their stored form on the
 * way in (docs/BRICKS.md, « Condition »), as every date and duration from the AI is: what a
 * value is read as is the type it faces (ConditionCheck.compared), known from declarations,
 * never the look of the value.
 *
 * Facing a DURATION, ISO 8601 ("PT7H") becomes milliseconds; facing a DATETIME, an ISO 8601
 * date-time becomes milliseconds, a relative date being kept, resolved when the condition is
 * judged. A bare number facing either is refused: 7 may be hours, 420 minutes, and taking it for
 * milliseconds would make "sleep ≥ 7 ms" met every night, without an error. Anything else is
 * written by the AI as it is stored. A condition whose sides do not read is left as it is, for
 * the service to refuse it and say why.
 */
class ConditionValues(private val sources: ConfigConditions.Sources, private val zone: ZoneId, private val text: (String) -> String) {

    /**
     * [config], declared by [nodes], with its conditions' written values stored.
     *
     * @throws IllegalArgumentException on a value that does not read in the type it faces, naming it
     */
    suspend fun toStored(nodes: List<SettingNode>, config: JSONObject): JSONObject {
        val check = ConditionCheck(sources, text)
        return ConfigConditions(sources).map(nodes, config) { found ->
            val field = check.compared(found)?.takeIf { it.type == FieldType.DURATION || it.type == FieldType.DATETIME }
                ?: return@map found.condition
            val stored = Conditions.withValues(found.condition) { stored(found.label, field, JsonUtils.toValue(it)) }
            // A written value on the left, facing what is read on the right
            stored.optJSONObject(Conditions.LEFT)?.takeIf { it.has(Conditions.CONSTANT) && !it.isNull(Conditions.CONSTANT) }?.let { left ->
                stored.put(Conditions.LEFT, JSONObject().put(Conditions.CONSTANT, stored(found.label, field, JsonUtils.toValue(left.get(Conditions.CONSTANT)))))
            }
            stored
        }
    }

    /**
     * [config], declared by [nodes], with its conditions' stored values in the form the model
     * writes them: the way back of [toStored], on a config the AI reads. A duration faced is
     * ISO 8601 ("PT7H"), an instant an ISO 8601 date-time; a relative date stays as it is.
     */
    suspend fun toModel(nodes: List<SettingNode>, config: JSONObject): JSONObject {
        val check = ConditionCheck(sources, text)
        return ConfigConditions(sources).map(nodes, config) { found ->
            val field = check.compared(found)?.takeIf { it.type == FieldType.DURATION || it.type == FieldType.DATETIME }
                ?: return@map found.condition
            val shown = Conditions.withValues(found.condition) { forModel(field, JsonUtils.toValue(it)) }
            shown.optJSONObject(Conditions.LEFT)?.takeIf { it.has(Conditions.CONSTANT) && !it.isNull(Conditions.CONSTANT) }?.let { left ->
                shown.put(Conditions.LEFT, JSONObject().put(Conditions.CONSTANT, forModel(field, JsonUtils.toValue(left.get(Conditions.CONSTANT)))))
            }
            shown
        }
    }

    /** A stored [value] facing [field], a DURATION or a DATETIME, as the model writes it. */
    private fun forModel(field: FieldDefinition, value: Any?): Any? = when {
        value !is Number -> value
        field.type == FieldType.DATETIME -> DateTimeConverter.timestampToISO(value.toLong(), zone)
        else -> ModelValues.toModel(value, FieldValueSchema.of(field), zone)
    }

    /** [value] facing [field], a DURATION or a DATETIME, in its stored form. */
    private fun stored(label: String, field: FieldDefinition, value: Any?): Any? = when {
        value == null -> null
        value is Number -> throw IllegalArgumentException(text("ai_error_condition_bare_number").format(label, value.toString(), field.displayName,
            text(if (field.type == FieldType.DURATION) "ai_condition_write_duration" else "ai_condition_write_datetime")))
        field.type == FieldType.DATETIME && TimePoint.isRelative(value) -> value
        field.type == FieldType.DATETIME && value is String -> try {
            DateTimeConverter.isoToTimestamp(value, zone)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(text("ai_error_period_invalid_iso").format(value), e)
        }
        field.type == FieldType.DURATION && value is String -> ModelValues.fromModel(value, FieldValueSchema.of(field), zone)
        // Anything else does not read: the service names it
        else -> value
    }
}
