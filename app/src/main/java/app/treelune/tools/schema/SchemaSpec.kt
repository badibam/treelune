package app.treelune.tools.schema

import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.Reference
import app.treelune.core.themes.TagColor
import org.json.JSONObject

/** The keys of a schema's config, in the vocabulary of Mermaid's flowcharts. */
object SchemaKeys {
    const val DIRECTION = "direction"
    const val NODES = "nodes"
    const val EDGES = "edges"
    const val ID = "id"
    const val LABEL = "label"
    const val SHAPE = "shape"
    const val THING = "thing"
    const val COLOR = "color"
    const val ICON = "icon"
    const val FROM = "from"
    const val TO = "to"
    const val LINE = "line"
    const val HEAD = "head"
    const val PARENT = "parent"
    const val FOLDED = "folded"
    const val PARTS = "parts"
}

/** Which way the arrows run; never sideways, which a phone held upright could not show. */
enum class SchemaDirection(val key: String) {
    TOP_DOWN("TD"), BOTTOM_UP("BT");

    companion object {
        fun of(key: String): SchemaDirection? = entries.firstOrNull { it.key == key }
    }
}

/** A box's shape, by its name in Mermaid 11's `@{ shape: … }`. */
enum class SchemaShape(val key: String) {
    RECT("rect"), ROUNDED("rounded"), STADIUM("stadium"), CIRCLE("circle"), DIAMOND("diamond");

    companion object {
        fun of(key: String): SchemaShape? = entries.firstOrNull { it.key == key }
    }
}

/** An arrow's line. */
enum class SchemaLine(val key: String) {
    NORMAL("normal"), DOTTED("dotted"), THICK("thick");

    companion object {
        fun of(key: String): SchemaLine? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A box. It says something by its own [label], or by the current name of the [thing] of the app
 * it designates; with both, its label is shown. Its [icon], a name of the vocabulary, stands
 * before its text; without one, a linked box shows its thing's icon.
 *
 * A box may stand inside another, its [parent]. One that holds others is drawn as a frame, its
 * text as its title; its [shape] then serves only when it is folded, drawn as a box again, as it
 * is at first when [folded]. In [parts], what it holds is read as parts stacked one under the
 * other rather than as one graph (docs/design/progression.md, « La linéarisation »).
 */
data class SchemaNode(
    val id: String,
    val label: String?,
    val shape: SchemaShape,
    val thing: Reference?,
    val color: TagColor?,
    val icon: String? = null,
    val parent: String? = null,
    val folded: Boolean = false,
    val parts: Boolean = false
)

/** An arrow between two boxes; without [head], a plain line. */
data class SchemaEdge(val from: String, val to: String, val label: String?, val line: SchemaLine, val head: Boolean)

/**
 * What a schema draws, read from its config (docs/design/schema.md): boxes, some inside others, and
 * arrows between any two of them; its top level in [parts], as a box's content can be. Reading it
 * refuses what its placement could not draw, naming the id at fault.
 */
data class SchemaSpec(
    val direction: SchemaDirection,
    val nodes: List<SchemaNode>,
    val edges: List<SchemaEdge>,
    val parts: Boolean = false
) {
    /** The boxes that hold others, drawn as frames unless folded. */
    val holders: Set<String> by lazy { nodes.mapNotNull { it.parent }.toSet() }

    /** The boxes holding [id], the nearest first. */
    fun ancestors(id: String): Sequence<String> {
        val parentOf = parents
        return generateSequence(parentOf[id]) { parentOf[it] }
    }

    private val parents: Map<String, String> by lazy { nodes.mapNotNull { node -> node.parent?.let { node.id to it } }.toMap() }

    companion object {
        /**
         * The schema [config] describes.
         *
         * @param own The schema tool's strings
         * @throws IllegalArgumentException naming what does not read
         */
        fun of(config: JSONObject, own: (String) -> String): SchemaSpec {
            val direction = config.optString(SchemaKeys.DIRECTION, SchemaDirection.TOP_DOWN.key).let { key ->
                SchemaDirection.of(key) ?: refuse(own("error_direction").format(key))
            }
            val nodes = config.objects(SchemaKeys.NODES).map { node(it, own) }
            val edges = config.objects(SchemaKeys.EDGES).map { edge(it, own) }
            return SchemaSpec(direction, nodes, edges, config.optBoolean(SchemaKeys.PARTS, false)).also { it.check(own) }
        }

        private fun refuse(message: String): Nothing = throw IllegalArgumentException(message)

        private fun JSONObject.objects(key: String): List<JSONObject> =
            optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }.orEmpty()

        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

        private fun color(json: JSONObject, id: String, own: (String) -> String): TagColor? = json.text(SchemaKeys.COLOR)?.let { name ->
            TagColor.entries.firstOrNull { it.name == name } ?: refuse(own("error_color").format(id, name))
        }

        private fun node(json: JSONObject, own: (String) -> String): SchemaNode {
            val id = json.text(SchemaKeys.ID) ?: refuse(own("error_id_missing"))
            val shape = json.text(SchemaKeys.SHAPE)?.let { SchemaShape.of(it) ?: refuse(own("error_shape").format(id, it)) } ?: SchemaShape.ROUNDED
            val thing = if (json.isNull(SchemaKeys.THING)) null
                else ReferenceTarget.referenceOf(json.opt(SchemaKeys.THING)) ?: refuse(own("error_thing").format(id))
            val label = json.text(SchemaKeys.LABEL)
            if (label == null && thing == null) refuse(own("error_node_empty").format(id))
            return SchemaNode(id, label, shape, thing, color(json, id, own), json.text(SchemaKeys.ICON),
                json.text(SchemaKeys.PARENT), json.optBoolean(SchemaKeys.FOLDED, false), json.optBoolean(SchemaKeys.PARTS, false))
        }

        private fun edge(json: JSONObject, own: (String) -> String): SchemaEdge {
            val from = json.text(SchemaKeys.FROM) ?: refuse(own("error_edge_end"))
            val to = json.text(SchemaKeys.TO) ?: refuse(own("error_edge_end"))
            val line = json.text(SchemaKeys.LINE)?.let { SchemaLine.of(it) ?: refuse(own("error_line").format(from, to, it)) } ?: SchemaLine.NORMAL
            return SchemaEdge(from, to, json.text(SchemaKeys.LABEL), line, json.optBoolean(SchemaKeys.HEAD, true))
        }
    }

    /**
     * What the placement counts on: one id per box, arrows and parents naming boxes that exist, no
     * box inside itself, and no arrow between a box and what it holds, which could not leave it.
     */
    private fun check(own: (String) -> String) {
        nodes.map { it.id }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let { refuse(own("error_id_twice").format(it.key)) }

        val boxes = nodes.map { it.id }.toSet()
        nodes.forEach { node ->
            node.parent?.takeIf { it !in boxes }?.let { refuse(own("error_parent_unknown").format(node.id, it)) }
        }
        // A box inside itself would hang from nothing, and vanish from the drawing
        nodes.forEach { node ->
            val seen = mutableSetOf(node.id)
            ancestors(node.id).forEach { if (!seen.add(it)) refuse(own("error_parent_loop").format(node.id)) }
        }
        edges.forEach { edge ->
            listOf(edge.from, edge.to).firstOrNull { it !in boxes }?.let { refuse(own("error_edge_unknown").format(it)) }
            val inside = if (edge.from == edge.to) edge.from in holders
                else edge.from in ancestors(edge.to) || edge.to in ancestors(edge.from)
            if (inside) refuse(own("error_edge_inside").format(edge.from, edge.to))
        }
    }
}
