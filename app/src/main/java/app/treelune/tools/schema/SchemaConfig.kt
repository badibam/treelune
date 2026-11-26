package app.treelune.tools.schema

import app.treelune.core.drawing.diagram.*
import app.treelune.core.drawing.diagram.DiagramDirection
import app.treelune.core.drawing.diagram.DiagramEdge
import app.treelune.core.drawing.diagram.DiagramFill
import app.treelune.core.drawing.diagram.DiagramLine
import app.treelune.core.drawing.diagram.DiagramNode
import app.treelune.core.drawing.diagram.DiagramShape
import app.treelune.core.drawing.diagram.DiagramSpec
import app.treelune.core.fields.ReferenceTarget
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
    const val FILL = "fill"
    const val FADED = "faded"
    const val FROM = "from"
    const val TO = "to"
    const val LINE = "line"
    const val HEAD = "head"
    const val PARENT = "parent"
    const val FOLDED = "folded"
    const val PARTS = "parts"
}

/**
 * A schema's config read as the diagram it draws (docs/design/schema.md), refusing what its
 * placement could not draw, naming the id at fault.
 */
object SchemaConfig {

    /**
     * The diagram [config] describes.
     *
     * @param own The schema tool's strings
     * @throws IllegalArgumentException naming what does not read
     */
    fun read(config: JSONObject, own: (String) -> String): DiagramSpec {
        val direction = config.optString(SchemaKeys.DIRECTION, DiagramDirection.TOP_DOWN.key).let { key ->
            DiagramDirection.of(key) ?: refuse(own("error_direction").format(key))
        }
        val nodes = config.objects(SchemaKeys.NODES).map { node(it, own) }
        val edges = config.objects(SchemaKeys.EDGES).map { edge(it, own) }
        return DiagramSpec(direction, nodes, edges, config.optBoolean(SchemaKeys.PARTS, false)).also { check(it, own) }
    }

    /** The lines a schema's arrows take: Mermaid's, which a schema is written and copied in. */
    val LINES = listOf(DiagramLine.NORMAL, DiagramLine.DOTTED, DiagramLine.THICK)

    private fun refuse(message: String): Nothing = throw IllegalArgumentException(message)

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }.orEmpty()

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun color(json: JSONObject, id: String, own: (String) -> String): TagColor? = json.text(SchemaKeys.COLOR)?.let { name ->
        TagColor.entries.firstOrNull { it.name == name } ?: refuse(own("error_color").format(id, name))
    }

    private fun node(json: JSONObject, own: (String) -> String): DiagramNode {
        val id = json.text(SchemaKeys.ID) ?: refuse(own("error_id_missing"))
        val shape = json.text(SchemaKeys.SHAPE)?.let { DiagramShape.of(it) ?: refuse(own("error_shape").format(id, it)) } ?: DiagramShape.ROUNDED
        val thing = if (json.isNull(SchemaKeys.THING)) null
            else ReferenceTarget.referenceOf(json.opt(SchemaKeys.THING)) ?: refuse(own("error_thing").format(id))
        val label = json.text(SchemaKeys.LABEL)
        if (label == null && thing == null) refuse(own("error_node_empty").format(id))
        val fill = json.text(SchemaKeys.FILL)?.let { DiagramFill.of(it) ?: refuse(own("error_fill").format(id, it)) } ?: DiagramFill.OUTLINE
        return DiagramNode(id, label, shape, thing, color(json, id, own), json.text(SchemaKeys.ICON),
            json.text(SchemaKeys.PARENT), json.optBoolean(SchemaKeys.FOLDED, false), json.optBoolean(SchemaKeys.PARTS, false),
            fill, json.optBoolean(SchemaKeys.FADED, false))
    }

    private fun edge(json: JSONObject, own: (String) -> String): DiagramEdge {
        val from = json.text(SchemaKeys.FROM) ?: refuse(own("error_edge_end"))
        val to = json.text(SchemaKeys.TO) ?: refuse(own("error_edge_end"))
        val line = json.text(SchemaKeys.LINE)?.let { key -> DiagramLine.of(key)?.takeIf { it in LINES } ?: refuse(own("error_line").format(from, to, key)) } ?: DiagramLine.NORMAL
        return DiagramEdge(from, to, json.text(SchemaKeys.LABEL), line, json.optBoolean(SchemaKeys.HEAD, true))
    }

    /**
     * What the placement counts on: one id per box, arrows and parents naming boxes that exist, no
     * box inside itself, and no arrow between a box and what it holds, which could not leave it.
     */
    private fun check(spec: DiagramSpec, own: (String) -> String) {
        // A box is said by its text, its id being one the form made up for a box drawn by hand
        val texts = spec.nodes.associate { it.id to (it.label ?: it.id) }
        fun said(id: String) = texts[id] ?: id
        spec.nodes.map { it.id }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let { refuse(own("error_id_twice").format(it.key)) }

        val boxes = spec.nodes.map { it.id }.toSet()
        spec.nodes.forEach { node ->
            node.parent?.takeIf { it !in boxes }?.let { refuse(own("error_parent_unknown").format(said(node.id), it)) }
        }
        // A box inside itself would hang from nothing, and vanish from the drawing
        spec.nodes.forEach { node ->
            val seen = mutableSetOf(node.id)
            spec.ancestors(node.id).forEach { if (!seen.add(it)) refuse(own("error_parent_loop").format(said(node.id))) }
        }
        spec.edges.forEach { edge ->
            listOf(edge.from, edge.to).firstOrNull { it !in boxes }?.let { refuse(own("error_edge_unknown").format(it)) }
            val inside = if (edge.from == edge.to) edge.from in spec.holders
                else edge.from in spec.ancestors(edge.to) || edge.to in spec.ancestors(edge.from)
            if (inside) refuse(own("error_edge_inside").format(said(edge.from), said(edge.to)))
        }
    }
}
