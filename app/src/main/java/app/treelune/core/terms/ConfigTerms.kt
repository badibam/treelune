package app.treelune.core.terms

import android.content.Context
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FileField
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import org.json.JSONObject

/**
 * The terms a config holds (SettingNode.Term), found through its declaration: down its groups,
 * its lists of groups and every case of its variants (a case not chosen still holds what it
 * holds). A file's term is checked here when the config is written: its reading follows one
 * entry's file field of a single file, of the kinds the term takes (docs/BRICKS.md, « Term »).
 */
object ConfigTerms {

    /** A term found: its declaration, and where it stands, the object holding it under its name. */
    data class Found(val node: SettingNode.Term, val holder: JSONObject) {
        val term: JSONObject? get() = holder.optJSONObject(node.name)
    }

    /** The terms [nodes] declare in [config]. */
    fun all(nodes: List<SettingNode>, config: JSONObject): List<Found> = nodes.flatMap { node ->
        when (node) {
            is SettingNode.Term -> listOf(Found(node, config))
            is SettingNode.Group -> config.optJSONObject(node.name)?.let { all(node.nodes, it) }.orEmpty()
            is SettingNode.ListOf -> (node.item as? SettingNode.Item.Of)?.let { item ->
                config.optJSONArray(node.name)?.let { list -> (0 until list.length()).mapNotNull { list.optJSONObject(it) }.flatMap { all(item.nodes, it) } }
            }.orEmpty()
            is SettingNode.Variant -> node.cases.values.flatMap { all(it, config) }
            is SettingNode.Section -> all(node.nodes, config)
            is SettingNode.Field, is SettingNode.Condition, is SettingNode.Selection, is SettingNode.Period, is SettingNode.Instants -> emptyList()
        }
    }

    /**
     * Why a file's term of [config] cannot be stored; null when each can. Its constant is the
     * owner's write to check (OwnedFiles); its reading must follow a field of one entry that holds
     * a single file of a kind the term takes.
     */
    suspend fun refuseFiles(nodes: List<SettingNode>, config: JSONObject, context: Context, s: StringsContext): String? {
        for (found in all(nodes, config)) {
            val value = found.node.value?.takeIf { it.type == FieldType.FILE } ?: continue
            val reading = found.term?.optJSONObject(Term.Kind.READING.key) ?: continue
            val label = found.node.label
            val term = try {
                Term.fromJson(JSONObject().put(Term.Kind.READING.key, reading), label) { s.shared(it) } as Term.Reading
            } catch (e: IllegalArgumentException) {
                return e.message
            }
            if (term.selection.target.kind != ReferenceKind.ENTRY || term.field == null) return s.shared("term_error_file_follow").format(label)
            val fields = try {
                ToolFields.ofTarget(term.selection.target, context, s)
            } catch (e: IllegalStateException) {
                return s.shared("term_error_file_source").format(label, e.message ?: "")
            }
            val followed = fields[term.field] ?: return s.shared("service_error_filter_unknown_field").format(term.field, fields.keys.joinToString(", "))
            val takes = FileField.kinds(value.config)
            if (followed.type != FieldType.FILE || term.reduction !in Reduction.forField(followed) || FileField.kinds(followed.config).any { it !in takes }) {
                return s.shared("term_error_file_field").format(label, followed.displayName, takes.joinToString(", ") { it.name })
            }
        }
        return null
    }
}
