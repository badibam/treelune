package app.treelune.core.drawing.diagram

import app.treelune.core.selection.Reference
import app.treelune.core.themes.TagColor

/** Which way the arrows run; never sideways, which a phone held upright could not show. */
enum class DiagramDirection(val key: String) {
    TOP_DOWN("TD"), BOTTOM_UP("BT");

    companion object {
        fun of(key: String): DiagramDirection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How a box is painted, as a Path brick's state paints it (docs/design/progression.md, « L'apparence
 * d'une brique »): [OUTLINE], its colour as a pale fill under its border, as a box always was;
 * [FULL], a strong fill, the text still in the theme's ink.
 */
enum class DiagramFill(val key: String) {
    OUTLINE("outline"), FULL("full");

    companion object {
        fun of(key: String): DiagramFill? = entries.firstOrNull { it.key == key }
    }
}

/** A box's shape, by its name in Mermaid 11's `@{ shape: … }`. */
enum class DiagramShape(val key: String) {
    RECT("rect"), ROUNDED("rounded"), STADIUM("stadium"), CIRCLE("circle"), DIAMOND("diamond");

    companion object {
        fun of(key: String): DiagramShape? = entries.firstOrNull { it.key == key }
    }
}

/** An arrow's line. Dashed is not Mermaid's, which a schema writes in: a Path's link types alone draw it. */
enum class DiagramLine(val key: String) {
    NORMAL("normal"), DOTTED("dotted"), DASHED("dashed"), THICK("thick");

    companion object {
        fun of(key: String): DiagramLine? = entries.firstOrNull { it.key == key }
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
 * other rather than as one graph (docs/design/progression.md, « La linéarisation »). Its [fill]
 * and whether it is [faded] say how it is painted, set by hand (not this season, planned). A box
 * may carry a [readout], a short form written under its text, and a [gauge] from 0 to 1, a bar
 * at its bottom while it has text, its fill rising from the bottom once it has none (a Path's
 * stage, its kind's readout).
 */
data class DiagramNode(
    val id: String,
    val label: String?,
    val shape: DiagramShape,
    val thing: Reference?,
    val color: TagColor?,
    val icon: String? = null,
    val parent: String? = null,
    val folded: Boolean = false,
    val parts: Boolean = false,
    val fill: DiagramFill = DiagramFill.OUTLINE,
    val faded: Boolean = false,
    val readout: String? = null,
    val gauge: Float? = null
)

/** An arrow between two boxes; without [head], a plain line; in [color], a name of the palette, or the theme's ink without one. */
data class DiagramEdge(val from: String, val to: String, val label: String?, val line: DiagramLine, val head: Boolean, val color: TagColor? = null)

/**
 * A diagram (docs/design/schema.md): boxes, some inside others, and arrows between any two of
 * them; its top level in [parts], as a box's content can be. The drawing the Schema tool shows
 * (its config read by SchemaConfig) and a Path's run (docs/design/progression.md).
 */
data class DiagramSpec(
    val direction: DiagramDirection,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
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
}
