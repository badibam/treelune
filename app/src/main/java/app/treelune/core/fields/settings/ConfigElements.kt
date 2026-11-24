package app.treelune.core.fields.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * One element of a list of a config, written alone by its id (docs/DATA.md, « Settings »): a
 * schema's box, a session's program. The rest of the config is left as it is, so that a config
 * holding many elements is never sent back whole to change one; what is written is then checked
 * as a whole config is.
 *
 * Its stored form, `element` of a tool's update: `{"list", "id", "value"}`. With `value`, the
 * element of that id is replaced, or added at the end when there is none — without `id`, added,
 * the tool giving it its id (ToolTypeContract.completeConfig). Without `value`, or `null`, the
 * element of that id is taken out.
 */
object ConfigElements {

    const val LIST = "list"
    const val ID = "id"
    const val VALUE = "value"

    /** The config written, or why it is not. */
    sealed interface Written {
        data class Config(val config: JSONObject) : Written
        data class Refused(val message: String) : Written
    }

    /**
     * [config] with [element] written in it; [nodes] declare the config, where the list must be a
     * list of groups whose elements store an `id`; [shared] gives the app's texts by their key.
     */
    fun write(nodes: List<SettingNode>, config: JSONObject, element: JSONObject, shared: (String) -> String): Written {
        val name = element.optString(LIST)
        if (list(nodes, name) == null) {
            return Written.Refused(shared("service_error_config_element_list").format(name, keyedLists(nodes).joinToString(", ").ifEmpty { "-" }))
        }
        val id = element.optString(ID).takeIf { it.isNotBlank() }
        val value = element.optJSONObject(VALUE)
        if (value == null && !element.isNull(VALUE) && element.has(VALUE)) return Written.Refused(shared("service_error_config_element_value"))
        val written = JSONObject(config.toString())
        val items = ElementKeys.objects(written.optJSONArray(name)).toMutableList()
        val at = id?.let { wanted -> items.indexOfFirst { it.optString(ID) == wanted } } ?: -1
        when {
            value == null -> {
                if (id == null || at < 0) return Written.Refused(shared("service_error_config_element_missing").format(id ?: "", name))
                items.removeAt(at)
            }
            // An element given with its id keeps it; one replaced keeps the id it is found by
            else -> {
                val placed = JSONObject(value.toString()).also { if (id != null) it.put(ID, id) else it.remove(ID) }
                if (at >= 0) items[at] = placed else items.add(placed)
            }
        }
        written.put(name, JSONArray(items))
        return Written.Config(written)
    }

    /** The list [name] of [nodes] whose elements store an id; null when there is none. */
    private fun list(nodes: List<SettingNode>, name: String): SettingNode.ListOf? = lists(nodes).firstOrNull { it.name == name }

    /** The names of the lists of [nodes] that can be written element by element. */
    fun keyedLists(nodes: List<SettingNode>): List<String> = lists(nodes).map { it.name }.distinct()

    /** The lists stored at the config's first level — its sections' and its variants' cases' included — whose elements store an id. */
    private fun lists(nodes: List<SettingNode>): List<SettingNode.ListOf> = nodes.flatMap { node ->
        when (node) {
            is SettingNode.ListOf -> listOfNotNull(node.takeIf { (it.item as? SettingNode.Item.Of)?.nodes?.storedField(ID) != null })
            is SettingNode.Section -> lists(node.nodes)
            is SettingNode.Variant -> node.cases.values.flatMap { lists(it) }
            else -> emptyList()
        }
    }
}
