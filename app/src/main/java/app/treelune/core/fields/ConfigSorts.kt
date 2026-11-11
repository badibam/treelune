package app.treelune.core.fields

import android.content.Context
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.strings.StringsContext
import org.json.JSONObject

/**
 * The order and the count of the selections a config holds (SettingNode.Selection, its `sort` and
 * `limit`), checked when it is written, whoever writes it: a selection that reads in order has a
 * sort, each key naming a field with an order of the tool it reads (EntrySort.parse), so that the
 * order a screen asks for later is the one stored; one that does not (a trigger's) has neither.
 * Down its groups, its lists of groups, and the chosen case of its variants, the only one stored.
 * A sort left out is written before this check, by its default (SelectionOrder.complete).
 */
object ConfigSorts {

    /** Why a selection of [config], declared by [nodes], cannot be stored; null when each can. */
    suspend fun refuse(nodes: List<SettingNode>, config: JSONObject, context: Context, s: StringsContext): String? {
        for (node in nodes) {
            val refusal = when (node) {
                is SettingNode.Selection -> config.optJSONObject(node.name)?.let { refuse(node, it, context, s) }
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
                is SettingNode.Field, is SettingNode.Condition, is SettingNode.Term,
                is SettingNode.Period, is SettingNode.Instants, is SettingNode.FieldValues -> null
            }
            if (refusal != null) return refusal
        }
        return null
    }

    /** The names of a tool config's top-level selections that read in order, which SelectionOrder.complete fills. */
    fun orderedKeys(nodes: List<SettingNode>): Set<String> = buildSet {
        fun visit(list: List<SettingNode>) {
            list.forEach { node ->
                when (node) {
                    is SettingNode.Selection -> if (node.ordered) add(node.name)
                    is SettingNode.Variant -> node.cases.values.forEach { visit(it) }
                    is SettingNode.Section -> visit(node.nodes)
                    else -> {}
                }
            }
        }
        visit(nodes)
    }

    private suspend fun refuse(node: SettingNode.Selection, json: JSONObject, context: Context, s: StringsContext): String? {
        val selection = try {
            app.treelune.core.selection.EntrySelection.fromJson(json) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            return s.shared("settings_count").format(node.label, e.message ?: "")
        }
        selection.problem(trigger = !node.ordered) { s.shared(it) }?.let { return s.shared("settings_count").format(node.label, it) }
        if (node.ordered) selection.unordered { s.shared(it) }?.let { return s.shared("settings_count").format(node.label, it) }
        if (selection.sort.isEmpty()) return null
        val toolId = selection.target.id ?: return s.shared("sort_error_without_selection").format(node.label)
        val fields = try {
            ToolFields.filterable(toolId, context, s, sortKeys = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            return s.shared("sort_error_source").format(node.label, e.message ?: "")
        }
        return (EntrySort.parse(EntrySort.toJson(selection.sort), fields) { s.shared(it) } as? EntrySort.Parsed.Refused)
            ?.let { s.shared("settings_count").format(node.label, it.error) }
    }
}
