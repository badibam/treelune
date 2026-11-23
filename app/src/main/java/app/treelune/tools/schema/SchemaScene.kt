package app.treelune.tools.schema

import app.treelune.core.drawing.DrawColor
import app.treelune.core.drawing.DrawPoint
import app.treelune.core.drawing.DrawRect
import app.treelune.core.drawing.DrawShape
import app.treelune.core.drawing.Drawing
import app.treelune.core.drawing.InkLevel
import app.treelune.core.drawing.PathStep
import app.treelune.core.drawing.TextAnchor
import app.treelune.core.drawing.TextBaseline
import app.treelune.core.selection.Reference
import app.treelune.core.themes.TagColor
import app.treelune.tools.schema.layout.LayoutBox
import app.treelune.tools.schema.layout.LayoutEdge
import app.treelune.tools.schema.layout.LayoutGraph
import app.treelune.tools.schema.layout.LayoutGroup
import app.treelune.tools.schema.layout.LayoutOptions
import app.treelune.tools.schema.layout.PlacedPoint
import app.treelune.tools.schema.layout.PlacedRect
import app.treelune.tools.schema.layout.Placement
import app.treelune.tools.schema.layout.SchemaLayout
import kotlin.math.max
import kotlin.math.min

/** The texts of a schema as they measure in the theme's style for drawings. */
interface SchemaText {
    fun width(text: String): Float
    val height: Float
}

/** What a touch on a schema found. */
sealed interface SchemaTouch {
    /**
     * A box: its [node], its whole [text], whether the drawing [cut] it, and the thing it can
     * [open]: its link, unless what it designates no longer exists.
     */
    data class Box(val node: SchemaNode, val text: String, val cut: Boolean, val open: Reference?) : SchemaTouch

    /** A group's title, or the box a folded group is drawn as. */
    data class Group(val id: String) : SchemaTouch
}

/** A schema laid out: the drawing the theme draws, and what a touch finds on it, the topmost last. */
data class SchemaScene(val drawing: Drawing, val hits: List<Pair<DrawRect, SchemaTouch>>) {
    fun hitAt(x: Float, y: Float): SchemaTouch? = hits.lastOrNull { it.first.contains(x, y) }?.second

    /** Where the group [id] stands, open by its title's band, folded by its box; null when a folded group hides it. */
    fun groupArea(id: String): DrawRect? = hits.lastOrNull { it.second == SchemaTouch.Group(id) }?.first
}

/**
 * What stays on screen with some groups folded: a folded group is one box, what it holds goes, and
 * the arrows that entered it reach that box. Arrows made identical by folding are drawn once.
 */
internal class SchemaVisible(spec: SchemaSpec, private val folded: Set<String>) {
    private val groupsById = spec.groups.associateBy { it.id }
    private val parentOfGroup = spec.groups.flatMap { g -> g.groups.map { it to g.id } }.toMap()
    private val groupOfNode = spec.groups.flatMap { g -> g.nodes.map { it to g.id } }.toMap()

    /** The outermost folded group among [group] and those holding it; null when none is folded. */
    private fun outermostFolded(group: String?): String? =
        generateSequence(group) { parentOfGroup[it] }.filter { it in folded }.lastOrNull()

    /** The id [node] is drawn under: its own, or the outermost folded group holding it. */
    fun shownAs(node: String): String = outermostFolded(groupOfNode[node]) ?: node

    /** The groups drawn as one box. */
    val foldedGroups: List<SchemaGroup> = spec.groups.filter { it.id in folded && outermostFolded(it.id) == it.id }

    val nodes: List<SchemaNode> = spec.nodes.filter { outermostFolded(groupOfNode[it.id]) == null }

    /** The groups drawn open, holding what is still drawn, a folded group as a box. */
    val groups: List<SchemaGroup> = spec.groups.filter { outermostFolded(it.id) == null }.map { group ->
        group.copy(
            nodes = group.nodes + group.groups.filter { it in folded },
            groups = group.groups.filter { it !in folded }
        )
    }

    val edges: List<SchemaEdge> = spec.edges.mapNotNull { edge ->
        val from = shownAs(edge.from)
        val to = shownAs(edge.to)
        // An arrow inside a folded group goes with it; a box's arrow to itself stays
        if (from == to && edge.from != edge.to) null else edge.copy(from = from, to = to)
    }.distinct()

    /** How many boxes [group] holds, those of the groups inside it included. */
    fun boxCount(group: SchemaGroup): Int = group.nodes.size + group.groups.sumOf { id -> groupsById[id]?.let { boxCount(it) } ?: 0 }
}

/**
 * Lays a schema out at a width, in pixels: measures its boxes, has them placed (SchemaLayout), and
 * writes the shapes the theme draws. A schema running upward is placed with its arrows turned, so
 * that titles stay at the top of their groups, and each head is drawn at the line's other end.
 *
 * @param density Pixels per dp
 * @param unit The theme's smallest stroke, in pixels
 * @param own The schema tool's strings
 */
class SchemaSceneBuilder(private val density: Float, private val unit: Float, private val text: SchemaText, private val own: (String) -> String) {

    private fun dp(value: Float) = value * density

    private data class Measured(
        val id: String,
        val lines: List<String>,
        val whole: String,
        val cut: Boolean,
        val shape: SchemaShape,
        val color: TagColor?,
        val node: SchemaNode?,
        val group: SchemaGroup?,
        val open: Reference?,
        val width: Float,
        val height: Float
    )

    /**
     * @param nameOf The current name of a thing, null when it no longer exists
     * @param folded The groups folded for this visit
     */
    fun build(spec: SchemaSpec, nameOf: (Reference) -> String?, folded: Set<String>, width: Float): SchemaScene {
        // Every folding is placed against the schema all open, so that its parts keep their sides
        val reference = if (folded.isEmpty()) emptyMap() else prepare(spec, nameOf, emptySet(), width, emptyMap()).placement.let { open ->
            (open.boxes + open.groups).mapValues { (_, rect) -> (rect.left + rect.right) / 2 }
        }
        val (visible, boxes, byId, labels, titles, upward, placement) = prepare(spec, nameOf, folded, width, reference)
        return draw(visible, boxes, byId, labels, titles, upward, placement)
    }

    private data class Prepared(
        val visible: SchemaVisible,
        val boxes: List<Measured>,
        val byId: Map<String, Measured>,
        val labels: List<String?>,
        val titles: Map<String, String>,
        val upward: Boolean,
        val placement: Placement
    )

    /** The boxes of what stays visible, measured, and placed. */
    private fun prepare(spec: SchemaSpec, nameOf: (Reference) -> String?, folded: Set<String>, width: Float, reference: Map<String, Float>): Prepared {
        val visible = SchemaVisible(spec, folded)
        val textLimit = min(width * 0.42f, dp(150f))
        val boxes = visible.nodes.map { node ->
            val whole = node.label ?: node.thing?.let(nameOf) ?: own("thing_deleted")
            measure(node.id, whole, node.shape, node.color, node, null, node.thing?.takeIf { nameOf(it) != null }, textLimit)
        } + visible.foldedGroups.map { group ->
            val whole = own("group_folded").format(group.title, visible.boxCount(group))
            measure(group.id, whole, SchemaShape.RECT, group.color, null, group, null, textLimit)
        }
        val byId = boxes.associateBy { it.id }

        val upward = spec.direction == SchemaDirection.BOTTOM_UP
        val labels = visible.edges.map { edge -> edge.label?.let { ellipsize(it, dp(120f)) } }
        val titles = visible.groups.associate { it.id to ellipsize(it.title, width * 0.6f) }
        val placement = SchemaLayout(LayoutOptions(
            maxWidth = width, boxGap = dp(16f), rowGap = dp(24f), trackGap = dp(7f), portGap = dp(9f),
            groupPadding = GROUP_PADDING * density, blockPadding = dp(6f), blockGap = dp(6f), loopSize = dp(12f)
        )).place(LayoutGraph(
            boxes = boxes.map { box -> portSpan(box.shape)?.let { LayoutBox(box.id, box.width, box.height, it) } ?: LayoutBox(box.id, box.width, box.height) },
            edges = visible.edges.mapIndexed { i, edge ->
                val label = labels[i]
                LayoutEdge(
                    from = if (upward) edge.to else edge.from,
                    to = if (upward) edge.from else edge.to,
                    labelWidth = label?.let { text.width(it) } ?: 0f,
                    labelHeight = if (label != null) text.height else 0f,
                    look = "${edge.label.orEmpty()}|${edge.line.key}|${edge.head}"
                )
            },
            groups = visible.groups.map { LayoutGroup(it.id, text.width(titles.getValue(it.id)), text.height, it.nodes, it.groups) },
            starts = starts(spec, visible, upward),
            reference = reference
        ))
        return Prepared(visible, boxes, byId, labels, titles, upward, placement)
    }

    /** The shapes the theme draws, and what a touch finds on them. */
    private fun draw(visible: SchemaVisible, boxes: List<Measured>, byId: Map<String, Measured>, labels: List<String?>, titles: Map<String, String>,
                     upward: Boolean, placement: Placement): SchemaScene {
        val shapes = mutableListOf<DrawShape>()
        val hits = mutableListOf<Pair<DrawRect, SchemaTouch>>()

        // Groups first, the outer ones under the inner ones
        visible.groups.mapNotNull { g -> placement.groups[g.id]?.let { g to it } }
            .sortedByDescending { (_, rect) -> rect.width * rect.height }
            .forEach { (group, rect) ->
                val outline = rounded(rect.draw(), dp(8f), dp(8f))
                shapes += DrawShape.Path(outline, fill(group.color), SURFACE_OPACITY / 2, 0f, null, true)
                shapes += DrawShape.Path(outline, stroke(group.color, InkLevel.FAINT), 1f, thin(), null, false)
                val pad = GROUP_PADDING * density
                shapes += DrawShape.Label(titles.getValue(group.id), DrawPoint(rect.left + pad, rect.top + pad), TextAnchor.START, TextBaseline.TOP, DrawColor.Ink(InkLevel.MEDIUM))
                hits += DrawRect(rect.left, rect.top, rect.right, rect.top + 2 * pad + text.height) to SchemaTouch.Group(group.id)
            }

        placement.blocks.forEach { block ->
            shapes += DrawShape.Path(rounded(block.rect.draw(), dp(6f), dp(6f)), DrawColor.Ink(InkLevel.FAINT), 1f, thin(), listOf(dp(2f), dp(3f)), false)
        }

        placement.routes.forEach { route ->
            val edge = visible.edges[route.edges.first()]
            // The points run along the arrow as it was placed: turned back for a schema running upward
            val placed = if (upward) route.points.reversed() else route.points
            val points = reachingOutlines(placed, byId[edge.from], byId[edge.to], placement.boxes)
            val strokeWidth = if (edge.line == SchemaLine.THICK) max(unit * 2, dp(3f)) else thin()
            val dash = if (edge.line == SchemaLine.DOTTED) listOf(dp(4f), dp(3f)) else null
            val ink = DrawColor.Ink(InkLevel.MEDIUM)
            val headSize = dp(8f)
            val line = if (edge.head) shortened(points, headSize * 0.7f) else points
            val labelled = route.label.takeIf { labels[route.edges.first()] != null }
            shapes += DrawShape.Path(steps(line, labelled), ink, 1f, strokeWidth, dash, false)
            if (edge.head && points.size >= 2) shapes += head(points[points.size - 2], points.last(), headSize, ink)
            val label = labels[route.edges.first()]
            if (label != null && route.label != null) {
                shapes += DrawShape.Label(label, DrawPoint((route.label.left + route.label.right) / 2, (route.label.top + route.label.bottom) / 2),
                    TextAnchor.MIDDLE, TextBaseline.MIDDLE, ink)
            }
        }

        boxes.forEach { box ->
            val rect = placement.boxes.getValue(box.id).draw()
            val outline = outline(box.shape, rect)
            shapes += DrawShape.Path(outline, fill(box.color), SURFACE_OPACITY, 0f, null, true)
            shapes += DrawShape.Path(outline, stroke(box.color, InkLevel.MEDIUM), 1f, if (box.group != null) max(unit * 2, dp(2.5f)) else thin(), null, false)
            val centerX = (rect.left + rect.right) / 2
            val top = (rect.top + rect.bottom) / 2 - box.lines.size * text.height / 2
            writeLines(box, centerX, top, shapes)
            hits += rect to (box.group?.let { SchemaTouch.Group(it.id) } ?: SchemaTouch.Box(box.node!!, box.whole, box.cut, box.open))
        }
        return SchemaScene(Drawing(placement.width, placement.height, shapes), hits)
    }

    /**
     * The boxes by where a loop should be entered, as the whole schema says it: folding a group
     * never moves the schema's start. Placed upward, the arrows are turned, and so is what starts.
     */
    private fun starts(spec: SchemaSpec, visible: SchemaVisible, upward: Boolean): List<String> {
        val reached = spec.edges.filter { it.from != it.to }.map { if (upward) it.from else it.to }.toSet()
        return (spec.nodes.sortedBy { it.id in reached }.map { visible.shownAs(it.id) } + visible.foldedGroups.map { it.id }).distinct()
    }

    /** Where lines may touch a shape that narrows at its top and bottom; null for the usual width. */
    private fun portSpan(shape: SchemaShape): Float? = when (shape) {
        SchemaShape.DIAMOND -> 0.35f
        SchemaShape.CIRCLE -> 0.5f
        SchemaShape.RECT, SchemaShape.ROUNDED, SchemaShape.STADIUM -> null
    }

    /**
     * A box's text. What is underlined can be touched: the whole text of a linked box, and the
     * word that closes a cut text, which a touch shows whole.
     */
    private fun writeLines(box: Measured, centerX: Float, top: Float, shapes: MutableList<DrawShape>) {
        val ink = DrawColor.Ink(InkLevel.STRONG)
        val linked = box.open != null
        fun underline(from: Float, to: Float, lineTop: Float) {
            val y = lineTop + text.height - dp(1f)
            shapes += DrawShape.Segment(DrawPoint(from, y), DrawPoint(to, y), ink, max(unit, dp(1f)), null)
        }
        box.lines.forEachIndexed { i, line ->
            val lineTop = top + i * text.height
            if (box.cut && i == box.lines.lastIndex) {
                val more = own("cut_more")
                val start = centerX - text.width("$line $more") / 2
                val moreStart = start + text.width("$line ")
                shapes += DrawShape.Label(line, DrawPoint(start, lineTop), TextAnchor.START, TextBaseline.TOP, ink)
                shapes += DrawShape.Label(more, DrawPoint(moreStart, lineTop), TextAnchor.START, TextBaseline.TOP, ink)
                if (linked) underline(start, start + text.width(line), lineTop)
                underline(moreStart, moreStart + text.width(more), lineTop)
            } else {
                shapes += DrawShape.Label(line, DrawPoint(centerX, lineTop), TextAnchor.MIDDLE, TextBaseline.TOP, ink)
                if (linked) text.width(line).let { underline(centerX - it / 2, centerX + it / 2, lineTop) }
            }
        }
    }

    private fun thin() = max(unit, dp(1.5f))

    private fun fill(color: TagColor?): DrawColor = color?.let { DrawColor.Palette(it) } ?: DrawColor.Ink(InkLevel.FAINT)

    private fun stroke(color: TagColor?, level: InkLevel): DrawColor = color?.let { DrawColor.Palette(it) } ?: DrawColor.Ink(level)

    private fun measure(id: String, whole: String, shape: SchemaShape, color: TagColor?, node: SchemaNode?, group: SchemaGroup?, open: Reference?, limit: Float): Measured {
        val (lines, cut) = wrap(whole, limit)
        val textWidth = lines.mapIndexed { i, line -> text.width(if (cut && i == lines.lastIndex) "$line ${own("cut_more")}" else line) }.maxOrNull() ?: 0f
        val w = textWidth + 2 * dp(10f)
        val h = lines.size * text.height + 2 * dp(6f)
        val (width, height) = when (shape) {
            SchemaShape.RECT, SchemaShape.ROUNDED -> max(w, dp(56f)) to h
            SchemaShape.STADIUM -> max(w + h / 2, dp(56f)) to h
            SchemaShape.CIRCLE -> max(w, h).let { it * 1.1f to it * 1.1f }
            SchemaShape.DIAMOND -> w * 1.6f to h * 1.7f
        }
        return Measured(id, lines, whole, cut, shape, color, node, group, open, width, height)
    }

    /** [whole] on three lines at most, cut with an ellipsis beyond; whether it was cut. */
    private fun wrap(whole: String, limit: Float): Pair<List<String>, Boolean> {
        val lines = mutableListOf<String>()
        var current = ""
        whole.trim().split(Regex("\\s+")).flatMap { breakWord(it, limit) }.forEach { word ->
            val joined = if (current.isEmpty()) word else "$current $word"
            if (text.width(joined) <= limit) current = joined
            else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        if (lines.size <= MAX_LINES) return lines to false
        val kept = lines.take(MAX_LINES)
        // The last line leaves room for the word that says there is more
        return kept.dropLast(1) + ellipsize(kept.last() + ELLIPSIS, limit - text.width(" ${own("cut_more")}")) to true
    }

    /** A word wider than a line, in pieces that fit. */
    private fun breakWord(word: String, limit: Float): List<String> {
        if (text.width(word) <= limit) return listOf(word)
        val pieces = mutableListOf<String>()
        var piece = ""
        word.forEach { c ->
            if (piece.isNotEmpty() && text.width(piece + c) > limit) {
                pieces += piece
                piece = ""
            }
            piece += c
        }
        if (piece.isNotEmpty()) pieces += piece
        return pieces
    }

    private fun ellipsize(value: String, limit: Float): String {
        if (text.width(value) <= limit) return value
        var kept = value.removeSuffix(ELLIPSIS)
        while (kept.isNotEmpty() && text.width(kept + ELLIPSIS) > limit) kept = kept.dropLast(1)
        return kept.trimEnd() + ELLIPSIS
    }

    private fun outline(shape: SchemaShape, r: DrawRect): List<PathStep> {
        val w = r.right - r.left
        val h = r.bottom - r.top
        return when (shape) {
            SchemaShape.RECT -> rounded(r, 0f, 0f)
            SchemaShape.ROUNDED -> min(dp(8f), h / 2).let { rounded(r, it, it) }
            SchemaShape.STADIUM -> rounded(r, h / 2, h / 2)
            SchemaShape.CIRCLE -> rounded(r, w / 2, h / 2)
            SchemaShape.DIAMOND -> listOf(
                PathStep.MoveTo(DrawPoint(r.left + w / 2, r.top)),
                PathStep.LineTo(DrawPoint(r.right, r.top + h / 2)),
                PathStep.LineTo(DrawPoint(r.left + w / 2, r.bottom)),
                PathStep.LineTo(DrawPoint(r.left, r.top + h / 2)),
                PathStep.Close
            )
        }
    }

    /** A rectangle whose corners are quarter ellipses of radii [rx] and [ry]; square at zero. */
    private fun rounded(r: DrawRect, rx: Float, ry: Float): List<PathStep> {
        if (rx <= 0f || ry <= 0f) return listOf(
            PathStep.MoveTo(DrawPoint(r.left, r.top)), PathStep.LineTo(DrawPoint(r.right, r.top)),
            PathStep.LineTo(DrawPoint(r.right, r.bottom)), PathStep.LineTo(DrawPoint(r.left, r.bottom)), PathStep.Close
        )
        val kx = rx * (1 - CIRCLE_CONTROL)
        val ky = ry * (1 - CIRCLE_CONTROL)
        return listOf(
            PathStep.MoveTo(DrawPoint(r.left + rx, r.top)),
            PathStep.LineTo(DrawPoint(r.right - rx, r.top)),
            PathStep.CubicTo(DrawPoint(r.right - kx, r.top), DrawPoint(r.right, r.top + ky), DrawPoint(r.right, r.top + ry)),
            PathStep.LineTo(DrawPoint(r.right, r.bottom - ry)),
            PathStep.CubicTo(DrawPoint(r.right, r.bottom - ky), DrawPoint(r.right - kx, r.bottom), DrawPoint(r.right - rx, r.bottom)),
            PathStep.LineTo(DrawPoint(r.left + rx, r.bottom)),
            PathStep.CubicTo(DrawPoint(r.left + kx, r.bottom), DrawPoint(r.left, r.bottom - ky), DrawPoint(r.left, r.bottom - ry)),
            PathStep.LineTo(DrawPoint(r.left, r.top + ry)),
            PathStep.CubicTo(DrawPoint(r.left, r.top + ky), DrawPoint(r.left + kx, r.top), DrawPoint(r.left + rx, r.top)),
            PathStep.Close
        )
    }

    /** The line's two ends brought from the rectangle around their box to the box's real outline. */
    private fun reachingOutlines(points: List<PlacedPoint>, from: Measured?, to: Measured?, rects: Map<String, PlacedRect>): List<PlacedPoint> {
        if (points.size < 2) return points
        val first = from?.let { onOutline(points.first(), it.shape, rects.getValue(it.id)) } ?: points.first()
        val last = to?.let { onOutline(points.last(), it.shape, rects.getValue(it.id)) } ?: points.last()
        return listOf(first) + points.subList(1, points.size - 1) + last
    }

    /** [p], on the top, bottom or right side of [r], moved in to where [shape] really starts; any other point as it is. */
    private fun onOutline(p: PlacedPoint, shape: SchemaShape, r: PlacedRect): PlacedPoint {
        val halfW = r.width / 2
        val halfH = r.height / 2
        val dx = min(kotlin.math.abs(p.x - (r.left + halfW)), halfW)
        val dy = min(kotlin.math.abs(p.y - (r.top + halfH)), halfH)
        return when {
            kotlin.math.abs(p.y - r.top) < ON_SIDE -> PlacedPoint(p.x, r.top + inset(shape, dx, halfW, halfH))
            kotlin.math.abs(p.y - r.bottom) < ON_SIDE -> PlacedPoint(p.x, r.bottom - inset(shape, dx, halfW, halfH))
            kotlin.math.abs(p.x - r.right) < ON_SIDE -> PlacedPoint(r.right - sideInset(shape, dy, halfW, halfH), p.y)
            else -> p
        }
    }

    /** How far below the rectangle's top the shape starts, [dx] from its middle. */
    private fun inset(shape: SchemaShape, dx: Float, halfW: Float, halfH: Float): Float = when (shape) {
        SchemaShape.RECT -> 0f
        SchemaShape.DIAMOND -> halfH * dx / halfW
        SchemaShape.CIRCLE -> halfH * (1 - kotlin.math.sqrt(1 - (dx / halfW) * (dx / halfW)))
        SchemaShape.STADIUM -> corner(dx, halfW, halfH)
        SchemaShape.ROUNDED -> corner(dx, halfW, min(dp(8f), halfH))
    }

    /** How far inside the rectangle's right side the shape starts, [dy] from its middle. */
    private fun sideInset(shape: SchemaShape, dy: Float, halfW: Float, halfH: Float): Float = when (shape) {
        SchemaShape.RECT -> 0f
        SchemaShape.DIAMOND -> halfW * dy / halfH
        SchemaShape.CIRCLE -> halfW * (1 - kotlin.math.sqrt(1 - (dy / halfH) * (dy / halfH)))
        SchemaShape.STADIUM -> corner(dy, halfH, halfH)
        SchemaShape.ROUNDED -> corner(dy, halfH, min(dp(8f), halfH))
    }

    /** The depth of a round corner of radius [radius], [distance] from the middle of a side [half] long. */
    private fun corner(distance: Float, half: Float, radius: Float): Float {
        val past = distance - (half - radius)
        return if (past <= 0f) 0f else radius - kotlin.math.sqrt(max(0f, radius * radius - past * past))
    }

    /** The line as path steps, lifted over [label] so that it never runs through its text. */
    private fun steps(points: List<PlacedPoint>, label: PlacedRect?): List<PathStep> {
        val out = mutableListOf<PathStep>()
        points.firstOrNull()?.let { out += PathStep.MoveTo(it.draw()) }
        val margin = dp(2f)
        points.zipWithNext { a, b ->
            val vertical = a.x == b.x
            val crosses = label != null && if (vertical) a.x in label.left..label.right && min(a.y, b.y) < label.top && max(a.y, b.y) > label.bottom
                else a.y in label.top..label.bottom && min(a.x, b.x) < label.left && max(a.x, b.x) > label.right
            if (crosses && label != null) {
                val forward = if (vertical) b.y > a.y else b.x > a.x
                val (enter, leave) = when {
                    vertical && forward -> PlacedPoint(a.x, label.top - margin) to PlacedPoint(a.x, label.bottom + margin)
                    vertical -> PlacedPoint(a.x, label.bottom + margin) to PlacedPoint(a.x, label.top - margin)
                    forward -> PlacedPoint(label.left - margin, a.y) to PlacedPoint(label.right + margin, a.y)
                    else -> PlacedPoint(label.right + margin, a.y) to PlacedPoint(label.left - margin, a.y)
                }
                out += PathStep.LineTo(enter.draw())
                out += PathStep.MoveTo(leave.draw())
            }
            out += PathStep.LineTo(b.draw())
        }
        return out
    }

    /** The line stopped short of its end, for its tip not to pass through the head. */
    private fun shortened(points: List<PlacedPoint>, by: Float): List<PlacedPoint> {
        if (points.size < 2) return points
        val a = points[points.size - 2]
        val b = points.last()
        val length = kotlin.math.hypot(b.x - a.x, b.y - a.y)
        if (length <= by) return points
        val t = (length - by) / length
        return points.dropLast(1) + PlacedPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    /** The head at [to], coming from [from]: the theme's own when vertical, a small triangle sideways. */
    private fun head(from: PlacedPoint, to: PlacedPoint, size: Float, color: DrawColor): DrawShape {
        if (from.x == to.x) {
            val up = to.y < from.y
            return DrawShape.Arrowhead(DrawPoint(to.x, if (up) to.y + size / 2 else to.y - size / 2), up, size, color)
        }
        val direction = if (to.x > from.x) 1f else -1f
        return DrawShape.Path(listOf(
            PathStep.MoveTo(to.draw()),
            PathStep.LineTo(DrawPoint(to.x - direction * size, to.y - size / 2)),
            PathStep.LineTo(DrawPoint(to.x - direction * size, to.y + size / 2)),
            PathStep.Close
        ), color, 1f, 0f, null, true)
    }

    private fun PlacedPoint.draw() = DrawPoint(x, y)
    private fun PlacedRect.draw() = DrawRect(left, top, right, bottom)

    private companion object {
        const val MAX_LINES = 3
        // How close to a side of its box a line's end counts as standing on it, in pixels
        const val ON_SIDE = 1f
        const val ELLIPSIS = "…"
        const val GROUP_PADDING = 8f
        const val SURFACE_OPACITY = 0.22f
        // A cubic's control point that makes a quarter of a circle
        const val CIRCLE_CONTROL = 0.5523f
    }
}
