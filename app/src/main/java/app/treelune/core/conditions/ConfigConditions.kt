package app.treelune.core.conditions

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.toFieldConfig
import app.treelune.core.selection.ReferenceKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * The conditions a config holds, found through its declaration (docs/BRICKS.md, « Condition »):
 * the walk follows the SettingNodes along with the value, and hands each condition with what its
 * sides may name. A tool type declaring conditions has them found without writing anything.
 *
 * Found: a condition setting (SettingNode.Condition), judged once or put on each row; the filters
 * of a selection (SettingNode.Selection), of a term's reading (SettingNode.Term), and of a
 * reading standing on a side of a condition judged once.
 *
 * @param sources Where the fields its sides may name are read
 */
class ConfigConditions(private val sources: Sources) {

    /** Where the walk and the check read the fields of what a condition names, from declarations only. */
    interface Sources {
        /**
         * The fields of a tool instance's entries, by path (ToolFields.filterable).
         *
         * @throws IllegalStateException when the tool cannot be read
         */
        suspend fun toolFields(toolInstanceId: String): Map<String, FieldDefinition>

        /** The field of the variable [id] as its definition stores it; null when there is none. */
        suspend fun variableField(id: String): FieldDefinition?

        /**
         * The fields of the rows the config describes at [path] (RowFields); null when its
         * owner gives none.
         */
        suspend fun rowFields(root: JSONObject, path: List<Any>): Map<String, FieldDefinition>?
    }

    /** How a condition is judged, which says what its sides may be. */
    enum class Kind {
        /** Once: each side a term, its left the value [Found.entered] when one is declared. */
        ONCE,
        /** On each entry of a selection, in SQL: a field of the entry on the left, written values on the right. */
        ON_ENTRY,
        /** On each row the config describes: fields of the row or written values. */
        ON_ROW
    }

    /**
     * One condition found.
     *
     * @property label Where it stands, for an error to name it as the screen does
     * @property condition Its stored form
     * @property fields The fields a side may name, by path or name: the entries' for [Kind.ON_ENTRY], the row's for [Kind.ON_ROW]
     * @property entered For [Kind.ONCE], the value entered in each attempt that stands on its left whatever is stored there
     * @property tool For [Kind.ON_ENTRY], the tool instance whose entries it is put on; null when its selection names none
     */
    data class Found(
        val label: String,
        val condition: JSONObject,
        val kind: Kind,
        val fields: Map<String, FieldDefinition> = emptyMap(),
        val entered: FieldDefinition? = null,
        val tool: String? = null
    )

    /**
     * [config] with each condition it holds through [visit], in the order of its declaration
     * [nodes]: what [visit] returns takes its place. Inside a condition judged once, the readings
     * on its sides are walked once [visit] has turned it.
     *
     * @throws IllegalArgumentException naming a selection whose tool cannot be read
     */
    suspend fun map(nodes: List<SettingNode>, config: JSONObject, visit: suspend (Found) -> JSONObject): JSONObject {
        val root = JSONObject(config.toString())
        walk(nodes, root, root, emptyList(), "", visit)
        return root
    }

    /** Each condition of [config], for a check that only reads. */
    suspend fun each(nodes: List<SettingNode>, config: JSONObject, visit: suspend (Found) -> Unit) {
        map(nodes, config) { found -> visit(found); found.condition }
    }

    private suspend fun walk(nodes: List<SettingNode>, obj: JSONObject, root: JSONObject, path: List<Any>, label: String, visit: suspend (Found) -> JSONObject) {
        for (node in nodes) when (node) {
            is SettingNode.Field, is SettingNode.Period, is SettingNode.Instants -> Unit
            is SettingNode.Group -> obj.optJSONObject(node.name)?.let { walk(node.nodes, it, root, path + node.name, under(label, node.label), visit) }
            is SettingNode.ListOf -> {
                val item = node.item as? SettingNode.Item.Of ?: continue
                val elements = obj.optJSONArray(node.name) ?: continue
                for (i in 0 until elements.length()) {
                    val element = elements.optJSONObject(i) ?: continue
                    // An element is named by the first value of its summary, or by its position
                    val name = node.summary.firstNotNullOfOrNull { key -> element.optString(key).takeIf { it.isNotBlank() } } ?: "${i + 1}"
                    walk(item.nodes, element, root, path + node.name + i, under(under(label, node.label), name), visit)
                }
            }
            // Only the chosen case is stored, flat beside the selector
            is SettingNode.Variant -> {
                val chosen = obj.opt(node.selector.definition.name)?.takeIf { it != JSONObject.NULL }?.toString() ?: node.selector.default?.toString()
                node.cases[chosen]?.let { walk(it, obj, root, path, label, visit) }
            }
            is SettingNode.Section -> walk(node.nodes, obj, root, path, label, visit)
            is SettingNode.Condition -> {
                val condition = obj.optJSONObject(node.name) ?: continue
                val here = under(label, node.label)
                if (node.onRow) {
                    val fields = sources.rowFields(root, path) ?: error("The condition '${node.name}' is put on each row, and its config's owner gives no RowFields")
                    obj.put(node.name, visit(Found(here, condition, Kind.ON_ROW, fields)))
                } else {
                    val turned = visit(Found(here, condition, Kind.ONCE, entered = entered(node, obj)))
                    obj.put(node.name, turned)
                    sidesOf(turned).forEach { side -> reading(side, here, visit) }
                }
            }
            is SettingNode.Term -> obj.optJSONObject(node.name)?.let { reading(it, under(label, node.label), visit) }
            is SettingNode.Selection -> obj.optJSONObject(node.name)?.let { selection(it, under(label, node.label), visit) }
        }
    }

    /** The value declared beside [node] (SettingNode.Condition.enteredField), as a field; null when none is. */
    private fun entered(node: SettingNode.Condition, obj: JSONObject): FieldDefinition? {
        val declared = node.enteredField?.let { obj.optJSONObject(it) } ?: return null
        val type = FieldType.entries.firstOrNull { it.name == declared.optString("type") } ?: return null
        return FieldDefinition(node.name, node.label, null, type, false, declared.optJSONObject("config")?.toFieldConfig())
    }

    /** The sides of a condition as stored: its left, then its right, a pair for `between`. */
    private fun sidesOf(condition: JSONObject): List<JSONObject> = buildList {
        condition.optJSONObject(Conditions.LEFT)?.let { add(it) }
        when (val right = condition.opt(Conditions.RIGHT)) {
            is JSONObject -> add(right)
            is JSONArray -> (0 until right.length()).forEach { i -> right.optJSONObject(i)?.let { add(it) } }
        }
    }

    /** The filters of the reading [term] stands for, if it is one. */
    private suspend fun reading(term: JSONObject, label: String, visit: suspend (Found) -> JSONObject) {
        term.optJSONObject("reading")?.optJSONObject("selection")?.let { selection(it, label, visit) }
    }

    /** The filters of [selection], each put on an entry of its tool and naming its fields. */
    private suspend fun selection(selection: JSONObject, label: String, visit: suspend (Found) -> JSONObject) {
        val filters = selection.optJSONArray("filters")?.takeIf { it.length() > 0 } ?: return
        val target = selection.optJSONObject("target")
        val id = target?.optString("id")?.takeIf { target.optString("kind") == ReferenceKind.TOOL_INSTANCE.name && it.isNotEmpty() }
        // Filters on anything else than a tool name no field it has: each is refused as such
        val fields = id?.let {
            try {
                sources.toolFields(it)
            } catch (e: IllegalStateException) {
                throw IllegalArgumentException("$label : ${e.message}")
            }
        } ?: emptyMap()
        for (i in 0 until filters.length()) {
            val filter = filters.optJSONObject(i) ?: continue
            filters.put(i, visit(Found(label, filter, Kind.ON_ENTRY, fields, tool = id)))
        }
    }

    private fun under(label: String, step: String) = if (label.isEmpty()) step else "$label › $step"
}
