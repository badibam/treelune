package app.treelune.core.variables

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.PeriodType
import app.treelune.core.terms.Term
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a variable is, as `definition_json` stores it (docs/DATA.md, « Variables »): a list of
 * [values], one by default, each a constant or a formula on the [terms] they share — a selection
 * written once, read once per instant for all of them (« Apports du jour » : calories, protéines,
 * glucides). Never two kinds of variables: one value is a list of one.
 *
 * It may declare a [series] (docs/design/variable-series.md): its values per step of that unit,
 * computed at the step's end, which a Reading selects as it selects a tool's entries, each value
 * under `data.<key>` (VariableSeries). Without one, the variable has only its values at the
 * instant read.
 */
data class VariableDefinition(val terms: Map<String, Term>, val values: List<Value>, val series: PeriodType? = null) {

    /**
     * One value: its [key], unique in its variable and never shown, by which everything that reads
     * it names it (`{var:ID.protein}`, `{"variable": ID, "value": "protein"}`); its [label], shown
     * when the variable has several (« Apports › Protéines »); its [field], NUMERIC, SCALE or
     * DURATION, a formula reading numbers; what it is; and whether its tile shows it ([onTile]).
     */
    data class Value(val key: String, val label: String?, val field: FieldDefinition, val expression: Expression, val onTile: Boolean = true)

    /** What a value is: a [Constant] in the stored form of its field (milliseconds for a DURATION), or a formula on the variable's terms, another variable's value written `{var:ID.key}`. */
    sealed interface Expression {
        data class Constant(val value: Double) : Expression
        data class Computed(val formula: String) : Expression
    }

    /** The value of [key]; null when the variable has none of that key. */
    fun value(key: String): Value? = values.firstOrNull { it.key == key }

    fun toJson(): JSONObject = JSONObject().apply {
        if (terms.isNotEmpty()) put(TERMS, JSONObject().apply { terms.forEach { (name, term) -> put(name, term.toJson()) } })
        put(VALUES, JSONArray(values.map { value ->
            JSONObject().put(KEY, value.key).apply {
                value.label?.let { put(LABEL, it) }
                put(FIELD, fieldJson(value.field))
                when (val e = value.expression) {
                    is Expression.Constant -> put(CONSTANT, e.value)
                    is Expression.Computed -> put(FORMULA, e.formula)
                }
                if (!value.onTile) put(ON_TILE, false)
            }
        }))
        series?.let { put(SERIES, JSONObject().put(UNIT, it.name)) }
    }

    companion object {
        const val TERMS = "terms"
        const val VALUES = "values"
        const val KEY = "key"
        const val LABEL = "label"
        const val FIELD = "field"
        const val CONSTANT = "constant"
        const val FORMULA = "formula"
        const val ON_TILE = "on_tile"
        const val SERIES = "series"
        const val UNIT = "unit"

        /** The key of a variable's value until another is given: the one each variable got when values came (v124). */
        const val FIRST_KEY = "value"

        /** The types a variable's value takes. */
        val VALUE_TYPES = setOf(FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION)

        /** A value's key: lower-case ASCII letters, digits and `_`, a letter first. */
        val VALUE_KEY = Regex("^[a-z][a-z0-9_]*$")

        /**
         * @param name The variable's name, which its values' fields carry
         * @throws IllegalArgumentException naming what does not read
         */
        fun fromJson(json: JSONObject, name: String, text: (String) -> String): VariableDefinition {
            val terms = json.optJSONObject(TERMS)?.let { terms ->
                terms.keys().asSequence().associateWith { Term.fromJson(terms.getJSONObject(it), it, text) }
            } ?: emptyMap()
            val array = json.optJSONArray(VALUES)?.takeIf { it.length() > 0 } ?: throw IllegalArgumentException(text("variable_error_values"))
            val values = (0 until array.length()).map { i ->
                val value = array.optJSONObject(i) ?: throw IllegalArgumentException(text("variable_error_values"))
                val key = value.optString(KEY).takeIf { VALUE_KEY.matches(it) } ?: throw IllegalArgumentException(text("variable_error_value_key").format(value.optString(KEY)))
                val field = value.optJSONObject(FIELD)?.let { fieldOf(it, name, text) }
                val expression = when {
                    value.has(CONSTANT) -> Expression.Constant((value.opt(CONSTANT) as? Number)?.toDouble() ?: throw IllegalArgumentException(text("variable_error_constant_value")))
                    value.has(FORMULA) -> Expression.Computed(value.optString(FORMULA).takeIf { it.isNotBlank() } ?: throw IllegalArgumentException(text("variable_error_formula_missing")))
                    else -> throw IllegalArgumentException(text("variable_error_value_kind").format(key))
                }
                Value(
                    key = key,
                    label = value.optString(LABEL).trim().takeIf { it.isNotEmpty() },
                    // A constant says what it is; a formula's field the service deduces when it is not given
                    field = field ?: if (expression is Expression.Constant) throw IllegalArgumentException(text("variable_error_field").format(name)) else bareField(name),
                    expression = expression,
                    onTile = value.optBoolean(ON_TILE, true)
                )
            }
            values.groupBy { it.key }.entries.firstOrNull { it.value.size > 1 }?.let { throw IllegalArgumentException(text("variable_error_value_key_twice").format(it.key)) }
            // Each value named apart once there are several
            if (values.size > 1) values.firstOrNull { it.label == null }?.let { throw IllegalArgumentException(text("variable_error_value_label").format(it.key)) }
            return VariableDefinition(terms, values, seriesOf(json, text))
        }

        /** The unit of [json]'s series, null without one; any unit of the calendar. */
        private fun seriesOf(json: JSONObject, text: (String) -> String): PeriodType? {
            if (!json.has(SERIES) || json.isNull(SERIES)) return null
            val unit = json.optJSONObject(SERIES)?.optString(UNIT).orEmpty()
            return PeriodType.entries.firstOrNull { it.name == unit }
                ?: throw IllegalArgumentException(text("variable_error_series_unit").format(unit, PeriodType.entries.joinToString(", ") { it.name }))
        }

        /** Stands for a formula's field until the service deduces it: a bare number. */
        fun bareField(name: String) = FieldDefinition(name, name, null, FormulaType.BARE.type, false, FormulaType.BARE.config)

        fun fieldJson(field: FieldDefinition): JSONObject = JSONObject().put("type", field.type.name).apply {
            field.config?.let { put("config", JsonUtils.toJSONObject(it)) }
        }

        private fun fieldOf(json: JSONObject, name: String, text: (String) -> String): FieldDefinition {
            val type = FieldType.entries.firstOrNull { it.name == json.optString("type") }?.takeIf { it in VALUE_TYPES }
                ?: throw IllegalArgumentException(text("variable_error_type").format(json.optString("type"), VALUE_TYPES.joinToString(", ") { it.name }))
            return FieldDefinition(name, name, null, type, false, json.optJSONObject("config")?.let { JsonUtils.toMap(it) }?.mapValues { it.value!! })
        }

        /** How [value] of the variable named [variable] is said: its name alone for a variable of one value, « Apports › Protéines » for one of several. */
        fun spoken(variable: String, definition: VariableDefinition, value: Value): String =
            if (definition.values.size == 1) variable else "$variable › ${value.label ?: value.key}"
    }
}
