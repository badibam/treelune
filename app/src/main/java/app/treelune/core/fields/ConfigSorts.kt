package app.treelune.core.fields

import android.content.Context
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.StringsContext
import org.json.JSONObject

/**
 * The sorts a config holds (SettingNode.Sort), checked when it is written, whoever writes it: each
 * key names a field with an order of the tool its selection beside it reads (EntrySort.parse), so
 * that the order a screen asks for later is the one stored. Down its groups, its lists of groups,
 * and the chosen case of its variants, the only one stored.
 */
object ConfigSorts {

    /** Why a sort of [config], declared by [nodes], cannot be stored; null when each can. */
    suspend fun refuse(nodes: List<SettingNode>, config: JSONObject, context: Context, s: StringsContext): String? {
        for (node in nodes) {
            val refusal = when (node) {
                is SettingNode.Sort -> refuse(node, config, context, s)
                is SettingNode.Group -> config.optJSONObject(node.name)?.let { refuse(node.nodes, it, context, s) }
                is SettingNode.ListOf -> (node.item as? SettingNode.Item.Of)?.let { item ->
                    val list = config.optJSONArray(node.name) ?: return@let null
                    (0 until list.length()).firstNotNullOfOrNull { i -> list.optJSONObject(i)?.let { refuse(item.nodes, it, context, s) } }
                }
                is SettingNode.Variant -> {
                    val chosen = config.opt(node.selector.definition.name)?.takeIf { it != JSONObject.NULL }?.toString() ?: node.selector.default?.toString()
                    node.cases[chosen]?.let { refuse(it, config, context, s) }
                }
                is SettingNode.Section -> refuse(node.nodes, config, context, s)
                is SettingNode.Field, is SettingNode.Condition, is SettingNode.Term, is SettingNode.Selection,
                is SettingNode.Period, is SettingNode.Instants -> null
            }
            if (refusal != null) return refusal
        }
        return null
    }

    private suspend fun refuse(node: SettingNode.Sort, holder: JSONObject, context: Context, s: StringsContext): String? {
        val sort = holder.optJSONArray(node.name)?.takeIf { it.length() > 0 } ?: return null
        val toolId = ReferenceTarget.referenceOf(holder.optJSONObject(node.selection)?.opt("target"))?.id
            ?: return s.shared("sort_error_without_selection").format(node.label)
        val fields = try {
            ToolFields.filterable(toolId, context, s)
        } catch (e: IllegalStateException) {
            return s.shared("sort_error_source").format(node.label, e.message ?: "")
        }
        return (EntrySort.parse(sort, fields) { s.shared(it) } as? EntrySort.Parsed.Refused)?.let { s.shared("settings_count").format(node.label, it.error) }
    }
}
