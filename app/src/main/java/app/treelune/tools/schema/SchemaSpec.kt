package app.treelune.tools.schema

import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.Reference
import app.treelune.core.themes.TagColor
import app.treelune.core.themes.Texture
import org.json.JSONArray
import org.json.JSONObject

/** The keys of a schema's config, in the vocabulary of Mermaid's flowcharts. */
object SchemaKeys {
    const val DIRECTION = "direction"
    const val NODES = "nodes"
    const val EDGES = "edges"
    const val GROUPS = "groups"
    const val ID = "id"
    const val LABEL = "label"
    const val SHAPE = "shape"
    const val THING = "thing"
    const val COLOR = "color"
    const val FROM = "from"
    const val TO = "to"
    const val LINE = "line"
    const val HEAD = "head"
    const val TITLE = "title"
    const val FOLDED = "folded"
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
 * it designates; with both, its label is shown.
 */
data class SchemaNode(
    val id: String,
    val label: String?,
    val shape: SchemaShape,
    val thing: Reference?,
    val color: TagColor?,
    val texture: Texture?
)

/** An arrow between two boxes; without [head], a plain line. */
data class SchemaEdge(val from: String, val to: String, val label: String?, val line: SchemaLine, val head: Boolean)

/** A group of boxes ([nodes]) and of other groups ([groups]), each in one group at most. */
data class SchemaGroup(
    val id: String,
    val title: String,
    val nodes: List<String>,
    val groups: List<String>,
    val folded: Boolean,
    val color: TagColor?,
    val texture: Texture?
)

/**
 * What a schema draws, read from its config (docs/design/schema.md): boxes, arrows and nested groups.
 * Reading it refuses what its placement could not draw, naming the id at fault.
 */
data class SchemaSpec(
    val direction: SchemaDirection,
    val nodes: List<SchemaNode>,
    val edges: List<SchemaEdge>,
    val groups: List<SchemaGroup>
) {
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
            val groups = config.objects(SchemaKeys.GROUPS).map { group(it, own) }
            return SchemaSpec(direction, nodes, edges, groups).also { it.check(own) }
        }

        private fun refuse(message: String): Nothing = throw IllegalArgumentException(message)

        private fun JSONObject.objects(key: String): List<JSONObject> =
            optJSONArray(key)?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }.orEmpty()

        private fun JSONObject.strings(key: String): List<String> =
            (optJSONArray(key) ?: JSONArray()).let { array -> (0 until array.length()).map { array.getString(it) } }

        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

        private fun color(json: JSONObject, id: String, own: (String) -> String): TagColor? = json.text(SchemaKeys.COLOR)?.let { name ->
            TagColor.entries.firstOrNull { it.name == name } ?: refuse(own("error_color").format(id, name))
        }

        private fun texture(json: JSONObject, id: String, own: (String) -> String): Texture? = json.text(Texture.KEY)?.let { name ->
            // A box or a group has no icon of its own to make a texture of
            Texture.entries.firstOrNull { it.name == name && it.hasCell } ?: refuse(own("error_texture").format(id, name))
        }

        private fun node(json: JSONObject, own: (String) -> String): SchemaNode {
            val id = json.text(SchemaKeys.ID) ?: refuse(own("error_id_missing"))
            val shape = json.text(SchemaKeys.SHAPE)?.let { SchemaShape.of(it) ?: refuse(own("error_shape").format(id, it)) } ?: SchemaShape.ROUNDED
            val thing = if (json.isNull(SchemaKeys.THING)) null
                else ReferenceTarget.referenceOf(json.opt(SchemaKeys.THING)) ?: refuse(own("error_thing").format(id))
            val label = json.text(SchemaKeys.LABEL)
            if (label == null && thing == null) refuse(own("error_node_empty").format(id))
            return SchemaNode(id, label, shape, thing, color(json, id, own), texture(json, id, own))
        }

        private fun edge(json: JSONObject, own: (String) -> String): SchemaEdge {
            val from = json.text(SchemaKeys.FROM) ?: refuse(own("error_edge_end"))
            val to = json.text(SchemaKeys.TO) ?: refuse(own("error_edge_end"))
            val line = json.text(SchemaKeys.LINE)?.let { SchemaLine.of(it) ?: refuse(own("error_line").format(from, to, it)) } ?: SchemaLine.NORMAL
            return SchemaEdge(from, to, json.text(SchemaKeys.LABEL), line, json.optBoolean(SchemaKeys.HEAD, true))
        }

        private fun group(json: JSONObject, own: (String) -> String): SchemaGroup {
            val id = json.text(SchemaKeys.ID) ?: refuse(own("error_id_missing"))
            val title = json.text(SchemaKeys.TITLE) ?: refuse(own("error_group_title").format(id))
            return SchemaGroup(id, title, json.strings(SchemaKeys.NODES), json.strings(SchemaKeys.GROUPS),
                json.optBoolean(SchemaKeys.FOLDED, false), color(json, id, own), texture(json, id, own))
        }
    }

    /** What the placement counts on: one id per thing, arrows and groups naming what exists, a tree of groups. */
    private fun check(own: (String) -> String) {
        val ids = nodes.map { it.id } + groups.map { it.id }
        ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let { refuse(own("error_id_twice").format(it.key)) }

        val boxes = nodes.map { it.id }.toSet()
        edges.forEach { edge ->
            listOf(edge.from, edge.to).firstOrNull { it !in boxes }?.let { refuse(own("error_edge_unknown").format(it)) }
        }

        val known = groups.map { it.id }.toSet()
        val groupOfBox = mutableMapOf<String, String>()
        val parent = mutableMapOf<String, String>()
        groups.forEach { group ->
            group.nodes.forEach { box ->
                if (box !in boxes) refuse(own("error_group_unknown_node").format(group.id, box))
                groupOfBox.put(box, group.id)?.let { refuse(own("error_node_two_groups").format(box, it, group.id)) }
            }
            group.groups.forEach { child ->
                if (child !in known) refuse(own("error_group_unknown_group").format(group.id, child))
                parent.put(child, group.id)?.let { refuse(own("error_group_two_parents").format(child, it, group.id)) }
            }
        }
        // A group inside itself would hang from nothing, and vanish from the drawing
        known.forEach { start ->
            val seen = mutableSetOf(start)
            var current = parent[start]
            while (current != null) {
                if (!seen.add(current)) refuse(own("error_group_loop").format(start))
                current = parent[current]
            }
        }
    }
}
