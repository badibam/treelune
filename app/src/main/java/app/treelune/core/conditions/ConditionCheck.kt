package app.treelune.core.conditions

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.EntryFilters
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.TimePoint
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermFields
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * The check every config holding conditions passes when it is written, whoever writes it
 * (docs/BRICKS.md, « Condition »): each condition found (ConfigConditions) has sides of known
 * types, read from declarations and never from data, and is refused, named, when
 * 1. its operator is not one the type takes (EntryFilters.operatorsFor);
 * 2. a written value does not read in the type it faces (a number facing a number, milliseconds
 *    facing a duration, a relative date or its stored form facing a date);
 * 3. two sides that are not written are not of one type (a variable in kcal facing a duration);
 * 4. a value it cites for a choice is not among the field's options, open choice included: it
 *    would never find anything, and it is almost always a typo.
 * What a side may be at all follows how the condition is judged (ConfigConditions.Kind).
 */
class ConditionCheck(private val sources: ConfigConditions.Sources, private val text: (String) -> String) {

    /** Why the conditions of [config], declared by [nodes], cannot be stored; null when they can. */
    suspend fun refuse(nodes: List<SettingNode>, config: JSONObject): String? {
        var refusal: String? = null
        try {
            ConfigConditions(sources, text).each(nodes, config) { found ->
                if (refusal == null) refusal = refuse(found)
            }
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        return refusal
    }

    /** Why [found] cannot be stored; null when it can. */
    suspend fun refuse(found: ConfigConditions.Found): String? {
        val label = found.label
        val condition = try {
            Condition.fromJson(found.condition, label, text)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        val sides = listOf(condition.left) + condition.right
        val fields = try {
            sides.mapIndexed { i, side -> fieldOf(found, i, side) }
        } catch (e: IllegalArgumentException) {
            return e.message
        }

        // The type compared is the first side's that has one
        val compared = fields.firstNotNullOfOrNull { it } ?: return text("condition_error_untyped").format(label)
        val operators = EntryFilters.operatorsFor(compared)
        if (condition.op !in operators) {
            return text("condition_error_operator").format(label, condition.op.key, compared.displayName, compared.type.name, operators.joinToString(", ") { it.key })
        }
        fields.filterNotNull().firstOrNull { !comparable(it, compared) }?.let { other ->
            return text("condition_error_types").format(label, compared.displayName, compared.type.name, other.displayName, other.type.name)
        }
        sides.forEach { side ->
            val value = ((side as? Condition.Side.Of)?.term as? Term.Constant)?.value ?: return@forEach
            refuseValue(label, compared, condition.op, value)?.let { return it }
        }
        return null
    }

    /**
     * The field the written values of [found] are read as: its first side's that has one; null
     * when none has, or when a side does not read, which [refuse] names.
     */
    suspend fun compared(found: ConfigConditions.Found): FieldDefinition? = try {
        val condition = Condition.fromJson(found.condition, found.label, text)
        (listOf(condition.left) + condition.right).withIndex().firstNotNullOfOrNull { (i, side) -> fieldOf(found, i, side) }
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * The field side [index] of [found] is a value of; null for a written value.
     *
     * @throws IllegalArgumentException naming a side that may not stand there, or does not read
     */
    private suspend fun fieldOf(found: ConfigConditions.Found, index: Int, side: Condition.Side): FieldDefinition? {
        val label = found.label
        return when (found.kind) {
            ConfigConditions.Kind.ONCE -> when {
                // The value entered stands on the left, whatever is stored there
                index == 0 && found.entered != null -> found.entered
                side is Condition.Side.Field -> throw IllegalArgumentException(text("condition_error_field_side").format(label, side.path))
                else -> termField((side as Condition.Side.Of).term, label)
            }
            ConfigConditions.Kind.ON_ENTRY -> when {
                index == 0 -> (side as? Condition.Side.Field)?.let { namedField(found, it.path) }
                    ?: throw IllegalArgumentException(text("condition_error_left_field").format(label))
                side is Condition.Side.Of && side.term is Term.Constant -> null
                else -> throw IllegalArgumentException(text("service_error_filter_not_written").format(label, raw(side)))
            }
            ConfigConditions.Kind.ON_ROW -> when {
                side is Condition.Side.Field -> namedField(found, side.path)
                index == 0 -> throw IllegalArgumentException(text("condition_error_left_field").format(label))
                (side as Condition.Side.Of).term is Term.Constant -> null
                else -> throw IllegalArgumentException(text("condition_error_row_written").format(label))
            }
        }
    }

    private fun namedField(found: ConfigConditions.Found, path: String): FieldDefinition =
        found.fields[path] ?: throw IllegalArgumentException(text("condition_error_field").format(found.label, path, found.fields.keys.joinToString(", ")))

    /** The field a term judged once gives values of: a formula per entry is a variable's alone. */
    private suspend fun termField(term: Term, label: String): FieldDefinition? {
        if (term is Term.Reading && term.perEntry != null) throw IllegalArgumentException(text("condition_error_per_entry").format(label))
        return TermFields.of(term, label, text, sources::targetFields, sources::variableField)
    }

    /** Two sides compare when they are of one type, a number and a scale both being numbers. */
    private fun comparable(a: FieldDefinition, b: FieldDefinition): Boolean =
        a.type == b.type || (a.type in NUMBERS && b.type in NUMBERS)

    /** Why the written [value] does not read facing [field] with [op]; null when it does. */
    private fun refuseValue(label: String, field: FieldDefinition, op: FilterOperator, value: Any): String? {
        val values = list(value)
        val reads = when (op) {
            FilterOperator.ABSENT, FilterOperator.PRESENT -> false
            FilterOperator.IN -> values != null && values.isNotEmpty() &&
                values.all { if (field.type == FieldType.REFERENCE) EntryFilters.single(field.type, it) != null else it is String }
            FilterOperator.CONTAINS -> value is String && value.isNotEmpty()
            // A relative date is resolved when the condition is judged: it only has to read
            else -> if ((field.type == FieldType.DATE || field.type == FieldType.DATETIME) && TimePoint.isRelative(value)) {
                try { TimePoint.read(value, text); true } catch (e: IllegalArgumentException) { false }
            } else EntryFilters.single(field.type, value) != null
        }
        if (!reads) {
            return text("condition_error_value").format(label, shown(value), field.displayName, text(EntryFilters.expectedValueKey(field.type, op)))
        }
        if (field.type == FieldType.CHOICE) {
            val options = ChoiceSettings.fromConfig(field.config).options
            values?.firstOrNull { it !in options }?.let { absent ->
                return text("condition_error_option").format(label, absent, field.displayName, options.joinToString(", "))
            }
        }
        return null
    }

    /** The values of a list, as JSON or Kotlin holds it; null for a single value. */
    private fun list(value: Any): List<Any?>? = when (value) {
        is List<*> -> value
        is JSONArray -> (0 until value.length()).map { value.opt(it) }
        else -> null
    }

    /** A written value as JSON writes it, a list included. */
    private fun shown(value: Any): String = when (value) {
        is List<*> -> JsonUtils.toJSONArray(value).toString()
        is Map<*, *> -> JsonUtils.toJSONObject(value.entries.associate { it.key.toString() to it.value }).toString()
        is String -> "\"$value\""
        else -> value.toString()
    }

    private fun raw(side: Condition.Side): String = when (side) {
        is Condition.Side.Field -> JSONObject().put(Conditions.FIELD, side.path).toString()
        is Condition.Side.Of -> side.term.toJson().toString()
    }

    private companion object {
        val NUMBERS = setOf(FieldType.NUMERIC, FieldType.SCALE)
    }
}
