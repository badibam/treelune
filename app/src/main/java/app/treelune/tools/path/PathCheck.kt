package app.treelune.tools.path

import app.treelune.core.conditions.Conditions
import app.treelune.core.terms.Term
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a Path's grammar must hold beyond its schema, checked whenever its config is written
 * (PathToolType.refuseConfig), each refusal saying what to write instead:
 * 1. kinds with keys of their own, the root's among them, what a kind contains naming kinds;
 * 2. a field's name neither a fact of a stage (PathSelf.RESERVED) nor of two types across kinds,
 *    a stage's row holding one value per name;
 * 3. states with keys of their own, never `start` nor `end`; a `record` with its rule, a
 *    `note_if` on a manual state, `closes_run` on the root's kind;
 * 4. an effect giving a state some kind has; link types with keys of their own;
 * 5. a state's rule reading the state of stages (on `stages`) anchored on a relation of `self`
 *    computed before it: its descendants, its links of a type, the stage before it.
 */
object PathCheck {

    /** Why [config] cannot be a Path's grammar; null when it can. */
    fun refuse(config: JSONObject, own: (String) -> String): String? {
        val grammar = try { PathGrammar.fromJson(config) } catch (e: IllegalArgumentException) { return e.message }
        if (grammar.kinds.isEmpty()) return own("path_check_no_kind")
        duplicate(grammar.kinds.map { it.key })?.let { return own("path_check_kind_twice").format(it) }
        grammar.kinds.firstOrNull { it.key.isEmpty() }?.let { return own("path_check_kind_key").format(it.name) }
        val keys = grammar.kinds.map { it.key }.toSet()
        if (grammar.rootKind !in keys) return own("path_check_root_kind").format(grammar.rootKind, keys.joinToString(", "))
        duplicate(grammar.linkTypes.map { it.key })?.let { return own("path_check_link_type_twice").format(it) }
        if (grammar.opens == Opens.SCHEDULED && grammar.unit == null) return own("path_check_unit")

        val types = mutableMapOf<String, Pair<String, app.treelune.core.fields.FieldType>>()
        for (kind in grammar.kinds) {
            kind.contains.firstOrNull { it !in keys }?.let { return own("path_check_contains").format(kind.name, it, keys.joinToString(", ")) }
            duplicate(kind.fields.map { it.name })?.let { return own("path_check_field_twice").format(kind.name, it) }
            for (field in kind.fields) {
                if (field.name in PathSelf.RESERVED) return own("path_check_field_reserved").format(kind.name, field.name, PathSelf.RESERVED.joinToString(", "))
                val seen = types[field.name]
                if (seen != null && seen.second != field.type) return own("path_check_field_types").format(field.name, seen.first, kind.name)
                types[field.name] = kind.name to field.type
            }
            if (kind.states.isEmpty()) return own("path_check_no_state").format(kind.name)
            duplicate(kind.states.map { it.key })?.let { return own("path_check_state_twice").format(kind.name, it) }
            for (state in kind.states) {
                if (state.key.isEmpty()) return own("path_check_state_key").format(kind.name)
                if (state.key == Move.START || state.key == Move.END) return own("path_check_state_reserved").format(kind.name, state.key)
                if (state.record && state.whenRule == null) return own("path_check_record_rule").format(kind.name, state.name)
                if (state.record && state.manual) return own("path_check_record_manual").format(kind.name, state.name)
                if (state.noteIf != null && !state.manual) return own("path_check_note_if").format(kind.name, state.name)
                if (state.closesRun && kind.key != grammar.rootKind) return own("path_check_closes_run").format(kind.name, state.name)
                state.whenRule?.let { rule -> unanchored(rule)?.let { return own("path_check_unanchored").format(kind.name, state.name) } }
            }
            for (effect in kind.effects) {
                if (grammar.kinds.none { it.state(effect.setState) != null }) return own("path_check_effect_state").format(kind.name, effect.setState)
            }
        }
        return null
    }

    /**
     * The first reading of [rule] that reads the state of the Path's stages without a relation of
     * `self` among its filters; null when each is anchored, or reads facts only.
     */
    private fun unanchored(rule: JSONObject): JSONObject? = Conditions.comparisons(rule).flatMap { comparison ->
        listOfNotNull(comparison.opt(Conditions.LEFT)) + when (val right = comparison.opt(Conditions.RIGHT)) {
            is JSONArray -> (0 until right.length()).map { right.opt(it) }
            null -> emptyList()
            else -> listOf(right)
        }
    }.filterIsInstance<JSONObject>().mapNotNull { it.optJSONObject(Term.Kind.READING.key) }.firstOrNull { reading ->
        val selection = reading.optJSONObject("selection") ?: return@firstOrNull false
        val target = selection.optJSONObject("target")
        if (target?.optString("id") != Term.SELF_TOOL || selection.optString("view") != PathRows.STAGES) return@firstOrNull false
        val filters = objects(selection.optJSONArray("filters"))
        val readsState = reading.optString("field") in COMPUTED || reading.has("group") || filters.any { Conditions.fieldOf(it) in COMPUTED }
        readsState && filters.none { anchored(it) }
    }

    /** Whether [filter] narrows to a relation of `self` computed before it. */
    private fun anchored(filter: JSONObject): Boolean {
        val self = filter.optJSONObject(Conditions.RIGHT)?.takeIf { it.has(Term.SELF) } ?: return false
        return when (Conditions.fieldOf(filter)) {
            "data.${PathRows.ANCESTORS}", "data.${PathSelf.PARENT}" -> self.optString(Term.SELF) == PathSelf.ID
            "id" -> self.optString(Term.SELF) in setOf(PathSelf.LINKS, PathSelf.PREVIOUS, PathSelf.CONTENT)
            else -> false
        }
    }

    /** What a row of `stages` holds that the replay computes: its state, since when, its marks. */
    private val COMPUTED = setOf("data.${PathSelf.STATE}", "data.${PathSelf.SINCE}", "data.${PathRows.MARKS}")

    private fun duplicate(keys: List<String>): String? = keys.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key

    private fun objects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array!!.optJSONObject(it) }
}
