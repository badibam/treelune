package app.treelune.core.terms

import app.treelune.core.reading.ReadingGroup
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.EntrySelection
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The Terme brick (docs/BRICKS.md): a value a formula or a condition reads, written once, taken
 * from a variable, or read from entries. Stored as `{"constant": …}`, `{"variable": "<id>"}` or
 * `{"reading": {…}}`.
 */
sealed interface Term {

    /** The kind of a term, which a setting offers among (SettingNode.Term): its stored form's key. */
    enum class Kind(val key: String) { CONSTANT("constant"), VARIABLE("variable"), READING("reading") }


    /**
     * A reading of the core without a test: a field reduced across the selected entries, or,
     * with [perEntry], a formula computed in each entry then reduced ("quantité × aliment.kcal_100g
     * / 100", summed over the meals). The selection's period is relative to the instant the
     * term is read at. With [group], each group of entries is reduced, then the groups kept are
     * (ReadingGroup).
     */
    data class Reading(val selection: EntrySelection, val field: String?, val reduction: Reduction, val perEntry: String? = null, val group: ReadingGroup? = null) : Term {

        /** What readings.read takes to read it, but the instant: its stored form's keys. */
        fun params(): Map<String, Any> = buildMap {
            put("selection", JsonUtils.toMap(selection.toJson()))
            field?.let { put("field", it) }
            put("reduction", reduction.name)
            group?.let { put("group", JsonUtils.toMap(it.toJson())) }
        }

        /**
         * The terms its groups are compared with (ReadingGroup.keep), and theirs in turn: what it
         * reads beside its own entries. None when the keep does not read, which its check names.
         */
        fun keepTerms(): List<Term> {
            val keep = group?.keepCondition() ?: return emptyList()
            val terms = try {
                app.treelune.core.conditions.Condition.fromJson(keep, "", { it }).right
                    .mapNotNull { (it as? app.treelune.core.conditions.Condition.Side.Of)?.term }
            } catch (e: IllegalArgumentException) {
                emptyList()
            }
            return terms + terms.filterIsInstance<Reading>().flatMap { it.keepTerms() }
        }
    }

    /**
     * A value written once, in the stored form of what it is read as, in its Kotlin form (a list
     * for several options). It has no type of its own: a formula reads it as a number, a condition
     * as the field on its other side.
     */
    data class Constant(val value: Any) : Term

    /** A variable, by its id. */
    data class Variable(val id: String) : Term

    fun toJson(): JSONObject = when (this) {
        is Reading -> JSONObject().put("reading", JSONObject().apply {
            put("selection", selection.toJson())
            field?.let { put("field", it) }
            perEntry?.let { put("per_entry", it) }
            put("reduction", reduction.name)
            group?.let { put("group", it.toJson()) }
        })
        is Constant -> JSONObject().put("constant", when (value) {
            is List<*> -> JsonUtils.toJSONArray(value)
            is Map<*, *> -> JsonUtils.toJSONObject(value.entries.associate { it.key.toString() to it.value })
            else -> value
        })
        is Variable -> JSONObject().put("variable", id)
    }

    companion object {
        /**
         * @param name What the term is called where it is used, for the error
         * @throws IllegalArgumentException naming what does not read
         */
        fun fromJson(json: JSONObject, name: String, text: (String) -> String): Term = when {
            json.has("reading") -> json.getJSONObject("reading").let { reading ->
                Reading(
                    selection = EntrySelection.fromJson(reading.optJSONObject("selection")
                        ?: throw IllegalArgumentException(text("variable_error_term").format(name)), text),
                    field = reading.optString("field").takeIf { it.isNotEmpty() },
                    reduction = Reduction.entries.firstOrNull { it.name == reading.optString("reduction") }
                        ?: throw IllegalArgumentException(text("service_error_reading_reduction").format(reading.optString("reduction"), Reduction.entries.joinToString(", ") { it.name })),
                    perEntry = reading.optString("per_entry").takeIf { it.isNotBlank() },
                    group = reading.optJSONObject("group")?.let { ReadingGroup.fromJson(it, text) }
                )
            }
            json.has("constant") && !json.isNull("constant") -> Constant(JsonUtils.toValue(json.get("constant"))!!)
            json.optString("variable").isNotEmpty() -> Variable(json.getString("variable"))
            else -> throw IllegalArgumentException(text("variable_error_term").format(name))
        }
    }
}
