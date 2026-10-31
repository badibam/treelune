package app.treelune.core.variables

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.PeriodType
import app.treelune.core.terms.Term
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * What a variable is, as `definition_json` stores it (docs/DATA.md, « Variables »): a constant
 * (`objectif_calorique = 2100`), or a formula on named terms. Its [field] is the type and
 * settings of its value, NUMERIC, SCALE or DURATION: a formula reads numbers.
 *
 * Either may declare a [series] (docs/design/variable-series.md): a value per step of that unit,
 * computed at the step's end, which a Reading selects as it selects a tool's entries
 * (VariableSeries). Without one, the variable has only its value at the instant read.
 */
sealed interface VariableDefinition {
    val field: FieldDefinition
    val series: PeriodType?

    /** [value] in the stored form of [field]: a number, milliseconds for a DURATION. */
    data class Constant(val value: Double, override val field: FieldDefinition, override val series: PeriodType? = null) : VariableDefinition

    /**
     * [formula] as stored, a variable it names written `{var:ID}`; [terms], its local names.
     */
    data class Computed(val formula: String, val terms: Map<String, Term>, override val field: FieldDefinition, override val series: PeriodType? = null) : VariableDefinition

    fun toJson(): JSONObject = when (this) {
        is Constant -> JSONObject().put("kind", CONSTANT).put("value", value).put("field", fieldJson(field))
        is Computed -> JSONObject().put("kind", FORMULA).put("formula", formula)
            .put("terms", JSONObject().apply { terms.forEach { (name, term) -> put(name, term.toJson()) } })
            .put("field", fieldJson(field))
    }.apply { series?.let { put(SERIES, JSONObject().put(UNIT, it.name)) } }

    companion object {
        const val CONSTANT = "CONSTANT"
        const val FORMULA = "FORMULA"
        const val SERIES = "series"
        const val UNIT = "unit"

        /** The types a variable's value takes. */
        val VALUE_TYPES = setOf(FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION)

        /**
         * @param name The variable's name, which its field carries
         * @throws IllegalArgumentException naming what does not read
         */
        fun fromJson(json: JSONObject, name: String, text: (String) -> String): VariableDefinition {
            val field = json.optJSONObject("field")?.let { fieldOf(it, name, text) }
            val series = seriesOf(json, text)
            return when (json.optString("kind")) {
                CONSTANT -> Constant(
                    value = (json.opt("value") as? Number)?.toDouble() ?: throw IllegalArgumentException(text("variable_error_constant_value")),
                    field = field ?: throw IllegalArgumentException(text("variable_error_field").format(name)),
                    series = series
                )
                FORMULA -> Computed(
                    formula = json.optString("formula").takeIf { it.isNotBlank() } ?: throw IllegalArgumentException(text("variable_error_formula_missing")),
                    terms = json.optJSONObject("terms")?.let { terms ->
                        terms.keys().asSequence().associateWith { Term.fromJson(terms.getJSONObject(it), it, text) }
                    } ?: emptyMap(),
                    field = field ?: bareField(name),
                    series = series
                )
                else -> throw IllegalArgumentException(text("variable_error_kind").format(json.optString("kind")))
            }
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
    }
}
