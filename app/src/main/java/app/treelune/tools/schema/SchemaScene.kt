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
import app.treelune.core.drawing.layout.GraphLayout
import app.treelune.core.drawing.layout.LayoutBox
import app.treelune.core.drawing.layout.LayoutEdge
import app.treelune.core.drawing.layout.LayoutGraph
import app.treelune.core.drawing.layout.LayoutGroup
import app.treelune.core.drawing.layout.LayoutOptions
import app.treelune.core.drawing.layout.PlacedPoint
import app.treelune.core.drawing.layout.PlacedRect
import app.treelune.core.drawing.layout.Placement
import app.treelune.core.selection.Reference
import app.treelune.core.themes.TagColor
import kotlin.math.max
import kotlin.math.min

/** The texts of a schema as they measure in the theme's style for drawings. */
interface SchemaText {
    fun width(text: String): Float
    val height: Float
}

/**
 * How much a box says, from the richest to the poorest, for a tile short of room
 * (docs/design/progression.md): its text; compact, its icon and one short line, an arrow's text
 * left out and a group's title cut short; its icon alone, its shape alone, a dot, where nothing
 * is written any more.
 */
enum class SchemaDetail { TEXT, COMPACT, ICON, SHAPE, DOT }

/** What a touch on a schema found. */
sealed interface SchemaTouch {
    /**
     * A box: its [node], its whole [text], whether the drawing [cut] it, and the thing it can
     * [open]: its link, unless what it designates no longer exists.
     */
    data class Box(val node: SchemaNode, val text: String, val cut: Boolean, val open: Reference?) : SchemaTouch

    /** A group's title, or the box a [folded] group is drawn as. */
    data class Group(val id: String, val folded: Boolean) : SchemaTouch

    /** One end of an arrow cut between parts, its [letter] in a circle ([id]), whose other end is [pair]. */
    data class Ref(val id: String, val letter: String, val pair: String) : SchemaTouch
}

/**
 * A schema laid out: the drawing the theme draws, and what a touch finds on it, the topmost last.
 *
 * @property autoFolded The groups the app folded for the schema to fit the width
 * @property scale What the drawing is shown at for the same reason, 1 for its full size
 */
data class SchemaScene(
    val drawing: Drawing,
    val hits: List<Pair<DrawRect, SchemaTouch>>,
    val autoFolded: Set<String> = emptySet(),
    val scale: Float = 1f
) {
    fun hitAt(x: Float, y: Float): SchemaTouch? = hits.lastOrNull { it.first.contains(x, y) }?.second

    /** Where the end [id] of a cut arrow stands. */
    fun refArea(id: String): DrawRect? = hits.lastOrNull { (it.second as? SchemaTouch.Ref)?.id == id }?.first

    /** Where the group [id] stands, open by its title's band, folded by its box; null when a folded group hides it. */
    fun groupArea(id: String): DrawRect? = hits.lastOrNull { (it.second as? SchemaTouch.Group)?.id == id }?.first
}

/** A box that holds others, drawn open: the [boxes] it holds drawn as boxes, the [frames] as frames. */
internal data class SchemaFrame(val node: SchemaNode, val boxes: List<String>, val frames: List<String>) {
    val id get() = node.id
}

/**
 * What stays on screen with some boxes folded: a folded box is drawn as a box again, what it holds
 * goes, and the arrows that reached it reach that box. Arrows made identical by folding are drawn once.
 */
internal class SchemaVisible(spec: SchemaSpec, private val folded: Set<String>) {
    private val holders = spec.holders
    private val parentOf = spec.nodes.mapNotNull { node -> node.parent?.let { node.id to it } }.toMap()
    private val childrenOf = spec.nodes.filter { it.parent != null }.groupBy({ it.parent!! }, { it.id })

    /** The outermost folded box among [id] and those holding it; null when none is folded. */
    private fun outermostFolded(id: String): String? =
        generateSequence(id) { parentOf[it] }.filter { it in folded && it in holders }.lastOrNull()

    /** The id [id] is drawn under: its own, or the outermost folded box holding it. */
    fun shownAs(id: String): String = outermostFolded(id) ?: id

    /** Whether [id] holds others and is drawn folded, as a box. */
    fun isFolded(id: String): Boolean = id in holders && outermostFolded(id) == id

    /** What is drawn as a box: the boxes that hold nothing, and the folded ones, nothing folded around them. */
    val nodes: List<SchemaNode> = spec.nodes.filter { node -> if (node.id in holders) isFolded(node.id) else outermostFolded(node.id) == null }

    private fun isOpen(id: String) = id in holders && outermostFolded(id) == null

    /** The boxes drawn open, as frames. */
    val frames: List<SchemaFrame> = spec.nodes.filter { isOpen(it.id) }.map { node ->
        val children = childrenOf[node.id].orEmpty()
        SchemaFrame(node, children.filter { !isOpen(it) }, children.filter { isOpen(it) })
    }

    val edges: List<SchemaEdge> = spec.edges.mapNotNull { edge ->
        val from = shownAs(edge.from)
        val to = shownAs(edge.to)
        // An arrow inside a folded box goes with it; a box's arrow to itself stays
        if (from == to && edge.from != edge.to) null else edge.copy(from = from, to = to)
    }.distinct()

    /** How many boxes [id] holds that hold nothing themselves, at any depth: what folding hides. */
    fun boxCount(id: String): Int = childrenOf[id].orEmpty().sumOf { if (it in holders) boxCount(it) else 1 }
}

/**
 * Lays a schema out at a width, in pixels: measures its boxes, has them placed (GraphLayout), and
 * writes the shapes the theme draws. A schema running upward is placed with its arrows turned, so
 * that titles stay at the top of their groups, and each head is drawn at the line's other end.
 * On a wide tile, it runs from left to right instead (GraphLayout, `across`), its width the limit
 * then bounding its height.
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
        val node: SchemaNode,
        val folded: Boolean,
        val open: Reference?,
        val icon: String?,
        val iconBeside: Boolean,
        val iconSize: Float,
        val width: Float,
        val height: Float,
        /** For one end of a cut arrow, the id of its other end */
        val pair: String? = null
    )

    /**
     * @param nameOf The current name of a thing, null when it no longer exists
     * @param folded The groups folded for this visit
     * @param keptOpen The groups the person opened: the app never folds them to make room
     * @param fit Whether a schema too wide is made to fit: tighter, then groups folded, then
     *   shown smaller down to [MIN_SCALE]; what still does not fit is left to scroll sideways
     * @param iconOf The icon of a thing, which a linked box without its own shows; null for none
     * @param across Whether the drawing runs sideways (a wide tile): a box's icon then stands
     *   beside its text, where height is short, and above it otherwise, where width is
     * @param detail How much each box says
     * @param aspect The width over height of the room the drawing is fitted to (a tile): a schema
     *   whose top level is in parts lays them as the grid that shows biggest there
     */
    fun build(spec: SchemaSpec, nameOf: (Reference) -> String?, folded: Set<String>, width: Float,
              keptOpen: Set<String> = emptySet(), fit: Boolean = false, iconOf: (Reference) -> String? = { null }, across: Boolean = false, detail: SchemaDetail = SchemaDetail.TEXT, aspect: Float? = null): SchemaScene {
        val mode = Mode(across, detail, aspect)
        // Every state, folded or tightened, is placed against one drawing, the schema all open at
        // its usual spacing, so that its parts keep their sides from one state to the other
        val allOpen by lazy { prepare(spec, nameOf, iconOf, mode, emptySet(), width, emptyMap(), Spacing.NORMAL) }
        // Where each part stands across the arrows: its x in a drawing running down, its y across
        val reference by lazy { (allOpen.placement.boxes + allOpen.placement.groups).mapValues { (_, rect) -> if (across) (rect.top + rect.bottom) / 2 else (rect.left + rect.right) / 2 } }
        fun placed(shut: Set<String>, spacing: Spacing): Prepared =
            if (shut.isEmpty() && spacing == Spacing.NORMAL) allOpen else prepare(spec, nameOf, iconOf, mode, shut, width, reference, spacing)
        // The limit bounds the drawing across its arrows: its width running down, its height across
        fun Prepared.extent() = if (across) placement.height else placement.width
        fun Prepared.fits() = extent() <= width + 0.5f

        val normal = placed(folded, Spacing.NORMAL)
        if (!fit || normal.fits()) return draw(normal)
        val tight = placed(folded, Spacing.TIGHT)
        if (tight.fits()) return draw(tight)

        // Groups folded one at a time, the widest first; one whose folding gains nothing stays open.
        // Each try is a whole placement, slow on a phone: never every group at every round
        var best = tight
        var auto = emptySet<String>()
        val useless = mutableSetOf<String>()
        while (!best.fits()) {
            val widest = best.visible.frames.filter { it.id !in keptOpen && it.id !in useless }
                .maxByOrNull { best.placement.groups[it.id]?.let { rect -> if (across) rect.height else rect.width } ?: 0f } ?: break
            val next = placed(folded + auto + widest.id, Spacing.TIGHT)
            if (next.extent() >= best.extent() - 0.5f) useless += widest.id
            else {
                auto = auto + widest.id
                best = next
            }
        }
        if (auto.isNotEmpty()) placed(folded + auto, Spacing.NORMAL).takeIf { it.fits() }?.let { best = it }

        // A theme drawing in whole pixels is never shown at a size between two of them
        val wanted = width / best.extent()
        val scale = if (!best.fits() && unit <= 1f && wanted >= MIN_SCALE) wanted else 1f
        return draw(best, auto, scale)
    }

    /** How much room the text and the gaps take: tighter before anything is hidden. */
    private enum class Spacing(val text: Float, val gap: Float) { NORMAL(1f, 1f), TIGHT(0.8f, 0.65f) }

    private data class Prepared(
        val visible: SchemaVisible,
        val boxes: List<Measured>,
        val byId: Map<String, Measured>,
        /** The arrows drawn: the visible ones the parts leave whole, and the two halves of each they cut */
        val lines: List<SchemaEdge>,
        val labels: List<String?>,
        val titles: Map<String, Title>,
        val upward: Boolean,
        val mode: Mode,
        val placement: Placement,
        /** The arrows from a part to the next one, drawn as a short straight line across the gap between them */
        val told: List<SchemaEdge> = emptyList()
    )

    /** Which way the drawing runs and how much its boxes say. */
    private data class Mode(val across: Boolean, val detail: SchemaDetail, val aspect: Float? = null) {
        /** Whether an arrow's text is written. */
        val written get() = detail == SchemaDetail.TEXT
        /** Whether a group's title is written: cut short when compact. */
        val titled get() = detail == SchemaDetail.TEXT || detail == SchemaDetail.COMPACT
        /** How close things stand: closer as boxes shrink. */
        val gap get() = when (detail) {
            SchemaDetail.TEXT -> 1f; SchemaDetail.COMPACT -> 0.85f; SchemaDetail.ICON -> 0.75f; SchemaDetail.SHAPE -> 0.5f; SchemaDetail.DOT -> 0.45f
        }
    }

    /**
     * A frame's title as written: its [text], cut to fit from the [whole] one, the [icon] before it,
     * and the thing it can [open].
     */
    private data class Title(val text: String, val whole: String, val icon: String?, val open: Reference?) {
        val cut get() = text != whole
    }

    /** What a box says: its own text, or its thing's current name. */
    private fun wholeText(node: SchemaNode, nameOf: (Reference) -> String?): String = node.label ?: node.thing?.let(nameOf) ?: own("thing_deleted")

    /** The thing a box can open: its link, unless what it designates no longer exists. */
    private fun opens(node: SchemaNode, nameOf: (Reference) -> String?): Reference? = node.thing?.takeIf { nameOf(it) != null }

    /** The boxes of what stays visible with [folded] shut, measured as [mode] says. */
    private fun measured(spec: SchemaSpec, visible: SchemaVisible, nameOf: (Reference) -> String?, iconOf: (Reference) -> String?, mode: Mode, width: Float, spacing: Spacing): List<Measured> {
        val textLimit = max(min(width * 0.42f, dp(150f)) * spacing.text, dp(88f))
        return visible.nodes.map { node ->
            val folded = visible.isFolded(node.id)
            val text = wholeText(node, nameOf)
            val whole = if (folded) own("group_folded").format(text, visible.boxCount(node.id)) else text
            val open = opens(node, nameOf)
            measure(node.id, whole, node.shape, node.color, node, folded, open, node.icon ?: open?.let(iconOf), mode, textLimit)
        }
    }

    /**
     * The room the boxes of [spec] take at [detail], placed nowhere: a measure alone, cheap, which
     * tells beforehand at what level a tile's drawing should hold.
     */
    fun boxesArea(spec: SchemaSpec, nameOf: (Reference) -> String?, folded: Set<String>, width: Float, detail: SchemaDetail,
                  iconOf: (Reference) -> String? = { null }, across: Boolean = false): Float =
        measured(spec, SchemaVisible(spec, folded), nameOf, iconOf, Mode(across, detail), width, Spacing.NORMAL).sumOf { (it.width * it.height).toDouble() }.toFloat()

    /** The boxes of what stays visible, measured, and placed. */
    private fun prepare(spec: SchemaSpec, nameOf: (Reference) -> String?, iconOf: (Reference) -> String?, mode: Mode, folded: Set<String>, width: Float, reference: Map<String, Float>, spacing: Spacing): Prepared {
        val visible = SchemaVisible(spec, folded)
        val upward = spec.direction == SchemaDirection.BOTTOM_UP
        val parts = SchemaParts(spec, visible, upward)

        // An arrow cut between parts: a letter beside each end, in the end's own group, joined to it
        // by the arrow's first or last stretch, its text on the first
        val refs = mutableListOf<Measured>()
        val refParent = mutableMapOf<String, String>()
        val lines = visible.edges.withIndex().flatMap { (i, edge) ->
            if (i in parts.told) return@flatMap emptyList()
            val letter = parts.cut[i] ?: return@flatMap listOf(edge)
            val out = "$REF_OUT$i"
            val into = "$REF_IN$i"
            refs += measureRef(out, letter, into, mode)
            refs += measureRef(into, letter, out, mode)
            spec.nodes.firstOrNull { it.id == edge.from }?.parent?.let { refParent[out] = it }
            spec.nodes.firstOrNull { it.id == edge.to }?.parent?.let { refParent[into] = it }
            listOf(edge.copy(to = out), edge.copy(from = into, label = null))
        }
        val boxes = measured(spec, visible, nameOf, iconOf, mode, width, spacing) + refs
        val byId = boxes.associateBy { it.id }
        val labels = lines.map { edge -> edge.label?.takeIf { mode.written }?.let { ellipsize(it, dp(120f)) } }
        val titles = visible.frames.associate { frame ->
            val whole = wholeText(frame.node, nameOf)
            val open = opens(frame.node, nameOf)
            frame.id to Title(when {
                mode.written -> ellipsize(whole, width * 0.6f)
                mode.titled -> ellipsize(whole, dp(COMPACT_LINE))
                else -> ""
            }, whole, (frame.node.icon ?: open?.let(iconOf)).takeIf { mode.titled }, open)
        }
        val across = mode.across
        val placement = GraphLayout(LayoutOptions(
            across = across, stackAspect = mode.aspect, maxWidth = width, boxGap = dp(16f) * spacing.gap * mode.gap, rowGap = dp(24f) * mode.gap, trackGap = dp(7f) * mode.gap, portGap = dp(9f) * spacing.gap * mode.gap,
            groupPadding = GROUP_PADDING * density, blockPadding = dp(6f), blockGap = dp(6f), loopSize = dp(12f)
        )).place(LayoutGraph(
            boxes = boxes.map { box -> portSpan(box.shape)?.let { LayoutBox(box.id, box.width, box.height, it) } ?: LayoutBox(box.id, box.width, box.height) },
            edges = lines.mapIndexed { i, edge ->
                val label = labels[i]
                LayoutEdge(
                    from = if (upward) edge.to else edge.from,
                    to = if (upward) edge.from else edge.to,
                    labelWidth = label?.let { text.width(it) } ?: 0f,
                    labelHeight = if (label != null) text.height else 0f,
                    look = "${edge.label.orEmpty()}|${edge.line.key}|${edge.head}"
                )
            },
            groups = visible.frames.map { frame ->
                val title = titles.getValue(frame.id)
                LayoutGroup(frame.id, titleWidth(title), if (title.text.isEmpty()) 0f else text.height,
                    frame.boxes + refParent.filterValues { it == frame.id }.keys, frame.frames, parts.stacks[frame.id])
            },
            starts = starts(spec, visible, upward),
            reference = reference,
            stack = parts.stacks[null]
        ))
        return Prepared(visible, boxes, byId, lines, labels, titles, upward, mode, placement, parts.told.sorted().map { visible.edges[it] })
    }

    /** The shapes the theme draws, and what a touch finds on them. */
    private fun draw(prepared: Prepared, autoFolded: Set<String> = emptySet(), scale: Float = 1f): SchemaScene {
        val (visible, boxes, byId, lines, labels, titles, upward, mode, placement, told) = prepared
        val across = mode.across
        // Small shapes take their color whole, a faint fill vanishing at their size
        val solid = mode.detail == SchemaDetail.SHAPE || mode.detail == SchemaDetail.DOT
        val shapes = mutableListOf<DrawShape>()
        val hits = mutableListOf<Pair<DrawRect, SchemaTouch>>()

        // Frames first, the outer ones under the inner ones
        visible.frames.mapNotNull { f -> placement.groups[f.id]?.let { f to it } }
            .sortedByDescending { (_, rect) -> rect.width * rect.height }
            .forEach { (frame, rect) ->
                val color = frame.node.color
                val outline = rounded(rect.draw(), dp(8f), dp(8f))
                shapes += DrawShape.Path(outline, fill(color), SURFACE_OPACITY / 2, 0f, null, true)
                shapes += DrawShape.Path(outline, stroke(color, InkLevel.FAINT), 1f, thin(), null, false)
                val pad = GROUP_PADDING * density
                val title = titles.getValue(frame.id)
                // Its title's band: on its top, or on its left side in a drawing running across
                val band = if (across) DrawRect(rect.left, rect.top, rect.left + 2 * pad + titleWidth(title), rect.bottom)
                    else DrawRect(rect.left, rect.top, rect.right, rect.top + 2 * pad + text.height)
                hits += band to SchemaTouch.Group(frame.id, folded = false)
                if (mode.titled) {
                    val ink = DrawColor.Ink(InkLevel.MEDIUM)
                    var x = rect.left + pad
                    title.icon?.let { icon ->
                        shapes += DrawShape.Icon(icon, DrawPoint(x + iconSize() / 2, rect.top + pad + text.height / 2), iconSize(), stroke(color, InkLevel.STRONG))
                        x += iconRoom()
                    }
                    shapes += DrawShape.Label(title.text, DrawPoint(x, rect.top + pad), TextAnchor.START, TextBaseline.TOP, ink)
                    // A linked or cut title is underlined, and its text opens what it says, the rest of its band folding
                    if (title.open != null || title.cut) {
                        val textRight = x + text.width(title.text)
                        val y = rect.top + pad + text.height - dp(1f)
                        shapes += DrawShape.Segment(DrawPoint(x, y), DrawPoint(textRight, y), ink, max(unit, dp(1f)), null)
                        hits += DrawRect(x, rect.top, textRight, rect.top + 2 * pad + text.height) to SchemaTouch.Box(frame.node, title.whole, title.cut, title.open)
                    }
                }
            }

        placement.blocks.forEach { block ->
            shapes += DrawShape.Path(rounded(block.rect.draw(), dp(6f), dp(6f)), DrawColor.Ink(InkLevel.FAINT), 1f, thin(), listOf(dp(2f), dp(3f)), false)
        }

        placement.routes.forEach { route ->
            val edge = lines[route.edges.first()]
            // The points run along the arrow as it was placed: turned back for a schema running upward
            val placed = if (upward) route.points.reversed() else route.points
            val points = reachingOutlines(placed, byId[edge.from], byId[edge.to], placement.boxes)
            val strokeWidth = (if (edge.line == SchemaLine.THICK) max(unit * 2, dp(3f)) else thin()).let { if (solid) max(unit, it * 0.7f) else it }
            val dash = if (edge.line == SchemaLine.DOTTED) listOf(dp(4f), dp(3f)) else null
            val ink = DrawColor.Ink(InkLevel.MEDIUM)
            val headSize = if (solid) dp(5f) else dp(8f)
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
            if (box.pair != null) {
                drawRef(box, rect, solid, shapes)
                hits += rect to SchemaTouch.Ref(box.id, box.whole, box.pair)
                return@forEach
            }
            val outline = outline(box.shape, rect)
            if (solid) shapes += DrawShape.Path(outline, box.color?.let { DrawColor.Palette(it) } ?: DrawColor.Ink(InkLevel.MEDIUM), 1f, 0f, null, true)
            else shapes += DrawShape.Path(outline, fill(box.color), SURFACE_OPACITY, 0f, null, true)
            shapes += DrawShape.Path(outline, stroke(box.color, InkLevel.MEDIUM), 1f, if (box.folded) max(unit * 2, dp(2.5f)) else thin(), null, false)
            // The icon and the text centered together in the box, the icon above or beside
            val centerX = (rect.left + rect.right) / 2
            val middleY = (rect.top + rect.bottom) / 2
            val textHeight = box.lines.size * text.height
            val iconColor = stroke(box.color, InkLevel.STRONG)
            when {
                box.icon == null -> writeLines(box, centerX, middleY - textHeight / 2, shapes)
                // The icon alone, at its own size
                box.lines.isEmpty() -> shapes += DrawShape.Icon(box.icon, DrawPoint(centerX, middleY), box.iconSize, iconColor)
                box.iconBeside -> {
                    val textWidth = textWidth(box.lines, box.cut)
                    val startX = centerX - (iconRoom() + textWidth) / 2
                    shapes += DrawShape.Icon(box.icon, DrawPoint(startX + iconSize() / 2, middleY), iconSize(), iconColor)
                    writeLines(box, startX + iconRoom() + textWidth / 2, middleY - textHeight / 2, shapes)
                }
                else -> {
                    val startY = middleY - (iconRoom() + textHeight) / 2
                    shapes += DrawShape.Icon(box.icon, DrawPoint(centerX, startY + iconSize() / 2), iconSize(), iconColor)
                    writeLines(box, centerX, startY + iconRoom(), shapes)
                }
            }
            hits += rect to (if (box.folded) SchemaTouch.Group(box.id, folded = true) else SchemaTouch.Box(box.node, box.whole, box.cut, box.open))
        }
        told.forEach { edge -> shapes += toldLine(edge, placement, solid, mode) }
        return SchemaScene(Drawing(placement.width, placement.height, shapes), hits, autoFolded, scale)
    }

    /**
     * The boxes by where a loop should be entered, as the whole schema says it: folding a group
     * never moves the schema's start. Placed upward, the arrows are turned, and so is what starts.
     */
    private fun starts(spec: SchemaSpec, visible: SchemaVisible, upward: Boolean): List<String> {
        val reached = spec.edges.filter { it.from != it.to }.map { if (upward) it.from else it.to }.toSet()
        return spec.nodes.filter { it.id !in spec.holders }.sortedBy { it.id in reached }.map { visible.shownAs(it.id) }.distinct()
    }

    /**
     * An arrow from a part to the next, which the order says: a short straight line from the one's
     * bottom to the other's top, its head at its target, its look and its text kept. It stands on
     * the parts' common axis, or beside it when a letter hangs there, never reading as leading on
     * from that letter.
     */
    private fun toldLine(edge: SchemaEdge, placement: Placement, solid: Boolean, mode: Mode): List<DrawShape> {
        fun rectOf(id: String) = placement.boxes[id] ?: placement.groups[id]
        val source = rectOf(edge.from) ?: return emptyList()
        val target = rectOf(edge.to) ?: return emptyList()
        val (upper, lower) = if (source.top <= target.top) Pair(source, target) else Pair(target, source)
        val top = upper.bottom
        val bottom = lower.top
        // What stands in the gap: the letters hanging below the one and above the other
        val between = (placement.boxes.values + placement.groups.values).filter { it.top >= top - 0.5f && it.bottom <= bottom + 0.5f }
        val margin = dp(10f)
        val low = max(upper.left, lower.left) + margin
        val high = min(upper.right, lower.right) - margin
        val center = ((upper.left + upper.right) / 2).coerceIn(min(low, high), max(low, high))
        val clear = dp(4f)
        fun free(x: Float) = between.none { x >= it.left - clear && x <= it.right + clear }
        val step = dp(8f)
        val x = generateSequence(1) { it + 1 }.take(40).flatMap { k -> sequenceOf(center + k * step, center - k * step) }
            .let { sequenceOf(center) + it }.firstOrNull { it in low..high && free(it) }
        // No room beside the letters: the line keeps to the gap below them
        val start = if (x != null) top else (between.maxOfOrNull { it.bottom } ?: top) + clear
        val lineX = x ?: center
        val downward = source === upper
        val a = PlacedPoint(lineX, if (downward) start else bottom)
        val b = PlacedPoint(lineX, if (downward) bottom else start)
        val ink = DrawColor.Ink(InkLevel.MEDIUM)
        val strokeWidth = (if (edge.line == SchemaLine.THICK) max(unit * 2, dp(3f)) else thin()).let { if (solid) max(unit, it * 0.7f) else it }
        val dash = if (edge.line == SchemaLine.DOTTED) listOf(dp(4f), dp(3f)) else null
        val headSize = if (solid) dp(5f) else dp(8f)
        val points = listOf(a, b)
        val line = if (edge.head) shortened(points, headSize * 0.7f) else points
        return buildList {
            add(DrawShape.Path(steps(line, null), ink, 1f, strokeWidth, dash, false))
            if (edge.head) add(head(a, b, headSize, ink))
            edge.label?.takeIf { mode.written }?.let { ellipsize(it, dp(120f)) }?.let { label ->
                add(DrawShape.Label(label, DrawPoint(lineX + dp(6f), (a.y + b.y) / 2 - text.height / 2), TextAnchor.START, TextBaseline.TOP, ink))
            }
        }
    }

    /** One end of a cut arrow: a small circle with its letter, from the compact level up. */
    private fun measureRef(id: String, letter: String, pair: String, mode: Mode): Measured {
        val side = when (mode.detail) {
            SchemaDetail.TEXT, SchemaDetail.COMPACT -> max(text.width(letter), text.height) + dp(6f)
            SchemaDetail.ICON -> dp(12f)
            SchemaDetail.SHAPE, SchemaDetail.DOT -> dp(8f)
        }
        val lines = if (mode.titled) listOf(letter) else emptyList()
        val node = SchemaNode(id, letter, SchemaShape.CIRCLE, null, null)
        return Measured(id, lines, letter, false, SchemaShape.CIRCLE, null, node, false, null, null, false, 0f, side, side, pair)
    }

    private fun drawRef(box: Measured, rect: DrawRect, solid: Boolean, shapes: MutableList<DrawShape>) {
        val outline = outline(SchemaShape.CIRCLE, rect)
        if (solid) shapes += DrawShape.Path(outline, DrawColor.Ink(InkLevel.MEDIUM), 1f, 0f, null, true)
        else shapes += DrawShape.Path(outline, stroke(null, InkLevel.MEDIUM), 1f, thin(), null, false)
        box.lines.firstOrNull()?.let { letter ->
            shapes += DrawShape.Label(letter, DrawPoint((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2 - text.height / 2), TextAnchor.MIDDLE, TextBaseline.TOP, DrawColor.Ink(InkLevel.STRONG))
        }
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

    /** The width a frame's title takes, its icon included. */
    private fun titleWidth(title: Title): Float = when {
        title.text.isEmpty() -> 0f
        title.icon != null -> iconRoom() + text.width(title.text)
        else -> text.width(title.text)
    }

    /** An icon before a box's text, as tall as a line of it. */
    private fun iconSize() = text.height

    /** The room an icon takes above or beside the text: itself and a gap. */
    private fun iconRoom() = iconSize() + dp(6f)

    /** The width of a box's widest line, the word closing a [cut] text included. */
    private fun textWidth(lines: List<String>, cut: Boolean): Float =
        lines.mapIndexed { i, line -> text.width(if (cut && i == lines.lastIndex) "$line ${own("cut_more")}" else line) }.maxOrNull() ?: 0f

    private fun measure(id: String, whole: String, shape: SchemaShape, color: TagColor?, node: SchemaNode, folded: Boolean, open: Reference?, icon: String?, mode: Mode, limit: Float): Measured {
        when (mode.detail) {
            SchemaDetail.TEXT, SchemaDetail.COMPACT -> Unit
            // The icon alone in its shape, a box without one its shape alone
            SchemaDetail.ICON -> if (icon != null) {
                val size = iconSize() * ICON_ALONE
                val side = size + 2 * dp(5f)
                val (width, height) = when (shape) {
                    SchemaShape.RECT, SchemaShape.ROUNDED, SchemaShape.CIRCLE -> side to side
                    SchemaShape.STADIUM -> side * 1.4f to side
                    SchemaShape.DIAMOND -> side * 1.45f to side * 1.45f
                }
                return Measured(id, emptyList(), whole, false, shape, color, node, folded, open, icon, false, size, width, height)
            } else return measure(id, whole, shape, color, node, folded, open, null, mode.copy(detail = SchemaDetail.SHAPE), limit)
            SchemaDetail.SHAPE -> {
                val (width, height) = when (shape) {
                    SchemaShape.RECT, SchemaShape.ROUNDED -> dp(20f) to dp(13f)
                    SchemaShape.STADIUM -> dp(24f) to dp(13f)
                    SchemaShape.CIRCLE -> dp(13f) to dp(13f)
                    SchemaShape.DIAMOND -> dp(17f) to dp(17f)
                }
                return Measured(id, emptyList(), whole, false, shape, color, node, folded, open, null, false, 0f, width, height)
            }
            SchemaDetail.DOT -> return Measured(id, emptyList(), whole, false, SchemaShape.CIRCLE, color, node, folded, open, null, false, 0f, dp(8f), dp(8f))
        }
        // Compact, one short line: nothing on a tile opens the whole text
        val (lines, cut) = if (mode.detail == SchemaDetail.COMPACT) listOf(ellipsize(whole.trim(), dp(COMPACT_LINE))) to false else wrap(whole, limit)
        val across = mode.across
        val beside = icon != null && across
        val above = icon != null && !across
        val w = (if (above) max(textWidth(lines, cut), iconSize()) else textWidth(lines, cut)) + (if (beside) iconRoom() else 0f) + 2 * dp(10f)
        val h = lines.size * text.height + (if (above) iconRoom() else 0f) + 2 * dp(6f)
        val (width, height) = when (shape) {
            SchemaShape.RECT, SchemaShape.ROUNDED -> max(w, dp(56f)) to h
            SchemaShape.STADIUM -> max(w + h / 2, dp(56f)) to h
            SchemaShape.CIRCLE -> max(w, h).let { it * 1.1f to it * 1.1f }
            SchemaShape.DIAMOND -> w * 1.6f to h * 1.7f
        }
        return Measured(id, lines, whole, cut, shape, color, node, folded, open, icon, across, iconSize(), width, height)
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

    /** [p], on a side of [r], moved in to where [shape] really starts; any other point as it is. */
    private fun onOutline(p: PlacedPoint, shape: SchemaShape, r: PlacedRect): PlacedPoint {
        val halfW = r.width / 2
        val halfH = r.height / 2
        val dx = min(kotlin.math.abs(p.x - (r.left + halfW)), halfW)
        val dy = min(kotlin.math.abs(p.y - (r.top + halfH)), halfH)
        return when {
            kotlin.math.abs(p.y - r.top) < ON_SIDE -> PlacedPoint(p.x, r.top + inset(shape, dx, halfW, halfH))
            kotlin.math.abs(p.y - r.bottom) < ON_SIDE -> PlacedPoint(p.x, r.bottom - inset(shape, dx, halfW, halfH))
            kotlin.math.abs(p.x - r.right) < ON_SIDE -> PlacedPoint(r.right - sideInset(shape, dy, halfW, halfH), p.y)
            kotlin.math.abs(p.x - r.left) < ON_SIDE -> PlacedPoint(r.left + sideInset(shape, dy, halfW, halfH), p.y)
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

    /** How far inside the rectangle's left or right side the shape starts, [dy] from its middle. */
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
        // The ids of a cut arrow's two ends, by the arrow's index: a control character, which no box's id can be typed with
        const val REF_OUT = "\u0001out"
        const val REF_IN = "\u0001in"
        // An icon drawn alone, against the size of one written before a text
        const val ICON_ALONE = 1.5f
        // The longest line a compact box or group title writes, in dp
        const val COMPACT_LINE = 80f
        // The smallest a drawing is shown at for its text to stay readable; to judge on the phone
        const val MIN_SCALE = 0.75f
        // How close to a side of its box a line's end counts as standing on it, in pixels
        const val ON_SIDE = 1f
        const val ELLIPSIS = "…"
        const val GROUP_PADDING = 8f
        const val SURFACE_OPACITY = 0.22f
        // A cubic's control point that makes a quarter of a circle
        const val CIRCLE_CONTROL = 0.5523f
    }
}
