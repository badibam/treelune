package app.treelune.tools.path

import app.treelune.core.conditions.ConfigConditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.terms.Term
import org.json.JSONObject

/**
 * What a Path's rules name, for the core's check of its config (ConditionCheck) and the AI's
 * values (ConditionValues): `self`, typed from the kind the rule belongs to (its place in the
 * config, `kinds[i]`); the Path's own views, read through the tool "self" before the tool exists,
 * their fields from the grammar being written; anything else, the app's.
 */
class PathConditionSources(
    private val base: ConfigConditions.Sources,
    config: JSONObject,
    private val own: (String) -> String
) : ConfigConditions.Sources by base {

    private val grammar = PathGrammar.fromJson(config)

    override suspend fun targetFields(target: Reference, view: String?): Map<String, FieldDefinition> =
        if (target.kind == ReferenceKind.TOOL_INSTANCE && target.id == Term.SELF_TOOL) when (view) {
            PathRows.STAGES -> PathRows.stageFields(grammar)
            PathRows.MOVES -> PathRows.moveFields(grammar)
            else -> throw IllegalStateException(own("path_error_self_view").format(view ?: "-"))
        } else base.targetFields(target, view)

    override suspend fun selfField(root: JSONObject, path: List<Any>, self: Term.Self): FieldDefinition? {
        // A rule of a kind stands at kinds[i], in a state, a mark or an effect
        if (path.size < 2 || path[0] != PathGrammar.KINDS) return null
        val kind = grammar.kinds.getOrNull(path[1] as? Int ?: return null) ?: return null
        return PathSelf.fieldOf(self.key, kind, own)
    }
}
