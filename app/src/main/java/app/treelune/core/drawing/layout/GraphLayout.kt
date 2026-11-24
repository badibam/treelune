package app.treelune.core.drawing.layout

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
 * The placement of a drawing of boxes, arrows and nested groups, laid out from top to bottom for a
 * phone held upright; the Schema tool draws with it (tools/schema, SchemaScene). Plain Kotlin, with
 * no Android type: what it computes can be run and measured on a computer.
 *
 * The method is the layered one (Sugiyama), applied scope by scope:
 *  - a scope is the whole drawing or one group; a child group is one opaque node of its
 *    parent, so that no foreign arrow can run through it;
 *  - an arrow that enters or leaves a group is cut at the group's border, at a port on its top or
 *    bottom side, and continues inside as an arrow of the group's own layout;
 *  - in a scope: loops broken, nodes put in rows, each row ordered to limit crossings, positions
 *    computed, arrows drawn with right angles on tracks between the rows;
 *  - the parts of a scope that no arrow joins are packed in lines at the width of the screen
 *    rather than side by side, and so are the leaf children a box reaches by identical arrows.
 */

/** Where lines touch a box of straight sides, as a share of its width. */
const val USUAL_PORT_SPAN = 0.7f

/**
 * A box to place, measured by whoever draws it. [portSpan] is the share of its width, around its
 * middle, where lines may touch it: narrow for a shape whose top and bottom come to a point.
 */
data class LayoutBox(val id: String, val width: Float, val height: Float, val portSpan: Float = USUAL_PORT_SPAN)

/**
 * An arrow. [look] says what makes two arrows identical besides their label (their line, their
 * head): identical arrows from one box to leaf children are drawn as a single one reaching a block.
 */
data class LayoutEdge(
    val from: String,
    val to: String,
    val labelWidth: Float = 0f,
    val labelHeight: Float = 0f,
    val look: String = ""
)

/** A group, holding boxes and other groups; its title is measured by whoever draws it. */
data class LayoutGroup(
    val id: String,
    val titleWidth: Float,
    val titleHeight: Float,
    val boxes: List<String>,
    val groups: List<String> = emptyList()
)

/**
 * What to place. [starts] orders the boxes by where a loop should be entered, first the best; empty,
 * the boxes no arrow reaches, then the others as written. [reference] gives the x each box or group
 * stands at in another drawing of the same graph: among equally clean drawings, the one that leaves
 * neighbours on the same side is kept, so that folding a group does not swap what surrounds it.
 */
data class LayoutGraph(
    val boxes: List<LayoutBox>,
    val edges: List<LayoutEdge> = emptyList(),
    val groups: List<LayoutGroup> = emptyList(),
    val starts: List<String> = emptyList(),
    val reference: Map<String, Float> = emptyMap()
)

/**
 * Distances, in the unit of the boxes' measures.
 *
 * @property across Whether the drawing runs from left to right rather than down (a wide tile):
 *   placed down with widths and heights swapped, then turned, so that [maxWidth] bounds its
 *   height and each distance keeps its meaning along or across the arrows. A group's title, kept
 *   level, takes a band on the group's left side rather than on its top
 */
data class LayoutOptions(
    val maxWidth: Float = 340f,
    val boxGap: Float = 16f,
    val rowGap: Float = 24f,
    val trackGap: Float = 7f,
    val portGap: Float = 9f,
    val groupPadding: Float = 8f,
    val blockPadding: Float = 6f,
    val blockGap: Float = 6f,
    val blockMinimum: Int = 3,
    val loopSize: Float = 12f,
    val attempts: Int = 2,
    val across: Boolean = false
)

data class PlacedPoint(val x: Float, val y: Float)

data class PlacedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** A line drawn for one arrow, or for all the identical arrows that reach one block ([edges]). */
data class PlacedRoute(val edges: List<Int>, val points: List<PlacedPoint>, val label: PlacedRect?)

/** The discreet frame around leaf children reached by one arrow. */
data class PlacedBlock(val rect: PlacedRect, val boxes: List<String>)

/** The result, in coordinates from the drawing's top left corner. */
data class Placement(
    val width: Float,
    val height: Float,
    val boxes: Map<String, PlacedRect>,
    val groups: Map<String, PlacedRect>,
    val blocks: List<PlacedBlock>,
    val routes: List<PlacedRoute>
)

/** Places a graph; its ids are expected valid, whoever builds it having refused the others (the Schema's config check). */
class GraphLayout(private val options: LayoutOptions = LayoutOptions()) {
    /**
     * Ordering rows is a heuristic that depends on where it starts, and a group's ports can follow
     * what they face outside or what they reach inside. So a graph that is not clean at once is
     * drawn a few times, from fixed starts (the same graph always gives the same drawing), and the
     * drawing kept is the one that fits the width, then has the fewest crossings, bends and height.
     */
    fun place(graph: LayoutGraph): Placement = if (options.across) placeDown(graph.turned()).turned() else placeDown(graph)

    private fun placeDown(graph: LayoutGraph): Placement {
        val ranking = rankingFor(graph.reference)
        val first = Run(graph, options, portsFollowInside = false, start = 0, ranking).place()
        if (graph.groups.isEmpty() && graph.reference.isEmpty() && crossings(first) == 0 && first.width <= options.maxWidth) return first
        val others = (0 until options.attempts).flatMap { start ->
            buildList {
                if (start > 0) add(Run(graph, options, portsFollowInside = false, start = start, ranking).place())
                if (graph.groups.isNotEmpty()) add(Run(graph, options, portsFollowInside = true, start = start, ranking).place())
            }
        }
        return (listOf(first) + others).minWith(ranking)
    }

    /** The better drawing: it fits the width, then has the fewest crossings, neighbours swapped from [reference], bends and height. */
    private fun rankingFor(reference: Map<String, Float>): Comparator<Placement> {
        // Counting crossings is the slow part, and a drawing is compared many times: counted once
        val crossingsOf = java.util.IdentityHashMap<Placement, Int>()
        val swappedOf = java.util.IdentityHashMap<Placement, Int>()
        return compareBy<Placement> { it.width > options.maxWidth }
            .thenBy { p -> crossingsOf.getOrPut(p) { crossings(p) } }
            .thenBy { p -> swappedOf.getOrPut(p) { swapped(p, reference) } }
            .thenBy { bends(it) }.thenBy { it.height }
    }

    /** How many pairs side by side stand in the opposite order in [reference]; what holds the other is not beside it. */
    private fun swapped(placement: Placement, reference: Map<String, Float>): Int {
        if (reference.isEmpty()) return 0
        val items = (placement.boxes + placement.groups).entries.filter { it.key in reference }.map { it.key to it.value }
        var total = 0
        for (i in items.indices) for (j in i + 1 until items.size) {
            val (idA, a) = items[i]
            val (idB, b) = items[j]
            val beside = a.top < b.bottom && b.top < a.bottom && a.right.coerceAtMost(b.right) - a.left.coerceAtLeast(b.left) < min(a.width, b.width)
            if (beside && (a.left + a.right - b.left - b.right) * (reference.getValue(idA) - reference.getValue(idB)) < 0) total++
        }
        return total
    }

    private fun bends(placement: Placement) = placement.routes.sumOf { max(0, it.points.size - 2) }

    /** The places where a horizontal part of a line is cut by a vertical part of another. */
    private fun crossings(placement: Placement): Int {
        val parts = placement.routes.map { it.points.zipWithNext() }
        var total = 0
        for (i in parts.indices) for (j in i + 1 until parts.size) for (a in parts[i]) for (b in parts[j]) {
            if (cuts(a, b) || cuts(b, a)) total++
        }
        return total
    }

    private fun cuts(horizontal: Pair<PlacedPoint, PlacedPoint>, vertical: Pair<PlacedPoint, PlacedPoint>): Boolean {
        val (a, b) = horizontal
        val (c, d) = vertical
        if (a.y != b.y || c.x != d.x) return false
        return c.x > min(a.x, b.x) + 0.5f && c.x < max(a.x, b.x) - 0.5f && a.y > min(c.y, d.y) + 0.5f && a.y < max(c.y, d.y) - 0.5f
    }

    private companion object {
    }
}

/** The graph as placed down for a drawing running across: each measure's width and height swapped. */
private fun LayoutGraph.turned() = copy(
    boxes = boxes.map { it.copy(width = it.height, height = it.width) },
    edges = edges.map { it.copy(labelWidth = it.labelHeight, labelHeight = it.labelWidth) },
    groups = groups.map { it.copy(titleWidth = it.titleHeight, titleHeight = it.titleWidth) }
)

private fun PlacedPoint.turned() = PlacedPoint(y, x)
private fun PlacedRect.turned() = PlacedRect(top, left, bottom, right)

/** A placement made down, turned to run across: each x and y swapped. */
private fun Placement.turned() = Placement(
    width = height,
    height = width,
    boxes = boxes.mapValues { it.value.turned() },
    groups = groups.mapValues { it.value.turned() },
    blocks = blocks.map { it.copy(rect = it.rect.turned()) },
    routes = routes.map { route -> route.copy(points = route.points.map { it.turned() }, label = route.label?.turned()) }
)

private enum class Kind { BOX, GROUP, BLOCK, DUMMY, LABEL, TOP_PORT, BOTTOM_PORT }

private class Node(val id: String, val kind: Kind, var width: Float = 0f, var height: Float = 0f) {
    /** The node's left and top, relative to its scope. */
    var x = 0f
    var y = 0f
    var layer = 0
    var order = 0
    /** Room kept on the right for an arrow from the box to itself. */
    var rightExtra = 0f
    /** The scope this node opens, for a group. */
    var inner: Scope? = null
    /** Its boxes and their places inside it, for a block. */
    var members: List<Pair<Node, PlacedPoint>> = emptyList()
    /** Sorts a port among the others of its side, from the parent's order. */
    var portKey = 0f
    /** For a port, the x in its group of the line that reaches it from outside, once known. */
    var wantedX: Float? = null

    val isPort get() = kind == Kind.TOP_PORT || kind == Kind.BOTTOM_PORT
    val isThin get() = kind == Kind.DUMMY || isPort
    val center get() = x + width / 2
    /** For a box, the share of its width around its middle where lines may touch it. */
    var portSpan = 1f

    /** The leftmost and rightmost places a line may touch it, off its corners. */
    fun portLow(corner: Float) = max(x + min(corner, width / 2), center - width * portSpan / 2)
    fun portHigh(corner: Float) = min(x + width - min(corner, width / 2), center + width * portSpan / 2)

    /** The same for a line alone on its side, free to go straight: anywhere off the corners, unless the shape narrows. */
    fun slideLow(corner: Float) = if (portSpan < USUAL_PORT_SPAN) portLow(corner) else x + min(corner, width / 2)
    fun slideHigh(corner: Float) = if (portSpan < USUAL_PORT_SPAN) portHigh(corner) else x + width - min(corner, width / 2)
}

/** One arrow's stretch inside one scope, between two of its nodes, in the arrow's own direction. */
private class Link(val edges: List<Int>, val from: Node, val to: Node, val labelWidth: Float, val labelHeight: Float) {
    lateinit var top: Node
    lateinit var bottom: Node
    /** Its continuation inside the group it leaves, or enters. */
    var fromInner: Link? = null
    var toInner: Link? = null
    var label: Node? = null
    /** The nodes next to its two ends in its scope's rows: a thin node for a long link. */
    var belowTop: Node? = null
    var aboveBottom: Node? = null
    /** The line in the scope's coordinates, from top to bottom. */
    var points: List<PlacedPoint> = emptyList()
    val hasLabel get() = labelWidth > 0f
    val length get() = if (hasLabel) 2 else 1
    val isReversed get() = top === to
}

/**
 * Where a link stands in its node's row: the node's rank, and on a group a fraction of it saying
 * which of the group's ports the link uses, so that orders see the arrows inside a group apart.
 */
private fun Node.slot(link: Link): Float {
    val scope = inner ?: return order.toFloat()
    val stretch = (if (link.from === this) link.fromInner else link.toInner) ?: return order.toFloat()
    val port = if (stretch.from.isPort) stretch.from else stretch.to
    val count = scope.nodes.count { it.kind == port.kind }
    return order + (port.order + 1f) / (count + 1f) - 0.5f
}

/** A stretch of a link between two neighbouring rows. */
private class Segment(val up: Node, val down: Node, val link: Link) {
    var upX = 0f
    var downX = 0f
    var track = 0
}

private class Scope(val group: LayoutGroup?, val limit: Float) {
    val nodes = mutableListOf<Node>()
    val links = mutableListOf<Link>()
    val parts = mutableListOf<Layered>()
    var width = 0f
    var height = 0f
    var originX = 0f
    var originY = 0f
}

private class Run(private val graph: LayoutGraph, private val options: LayoutOptions, private val portsFollowInside: Boolean, private val start: Int, private val ranking: Comparator<Placement>) {

    private val root = Scope(null, options.maxWidth)
    private val boxNodes = mutableMapOf<String, Node>()
    private val scopeOfBox = mutableMapOf<String, Scope>()
    private val parentOfScope = mutableMapOf<Scope, Scope>()
    private val nodeOfScope = mutableMapOf<Scope, Node>()
    private val scopes = mutableMapOf<String, Scope>()
    private val topLinks = mutableListOf<Pair<Link, Scope>>()
    private val selfLoops = mutableListOf<Pair<Int, Node>>()
    private val blocks = mutableListOf<Pair<Node, Scope>>()
    private val startIndex: Map<String, Int> = graph.starts.ifEmpty {
        val reached = graph.edges.filter { it.from != it.to }.map { it.to }.toSet()
        graph.boxes.map { it.id }.sortedBy { it in reached }
    }.withIndex().associate { (i, id) -> id to i }

    fun place(): Placement {
        buildScopes()
        buildLinks()
        plan(root)
        repeat(REFINEMENTS) { refine(root) }
        measure(root)
        var best = result()
        // The inside of a group is laid out before its surroundings: laid out again knowing where
        // its arrows come from, its ports face them and the lines cross its border without a jog.
        // A round can also make things worse, so the best drawing met is the one kept.
        if (graph.groups.isNotEmpty()) repeat(ALIGNMENTS) {
            aimPorts(root)
            measure(root)
            val now = result()
            if (ranking.compare(now, best) < 0) best = now
        }
        return best
    }

    // ---------------------------------------------------------------------------------------------
    // The tree of scopes and the links
    // ---------------------------------------------------------------------------------------------

    private fun buildScopes() {
        val byId = graph.groups.associateBy { it.id }
        val nested = graph.groups.flatMap { it.groups }.toSet()
        val grouped = graph.groups.flatMap { it.boxes }.toSet()
        val boxes = graph.boxes.associateBy { it.id }

        fun fill(scope: Scope, boxIds: List<String>, groupIds: List<String>) {
            boxIds.forEach { id ->
                val box = boxes.getValue(id)
                val node = Node(id, Kind.BOX, box.width, box.height).also { it.portSpan = box.portSpan }
                boxNodes[id] = node
                scopeOfBox[id] = scope
                scope.nodes += node
            }
            groupIds.forEach { id ->
                val group = byId.getValue(id)
                val inner = Scope(group, scope.limit - 2 * options.groupPadding)
                val node = Node(id, Kind.GROUP).also { it.inner = inner }
                scopes[id] = inner
                parentOfScope[inner] = scope
                nodeOfScope[inner] = node
                scope.nodes += node
                fill(inner, group.boxes, group.groups)
            }
        }
        fill(root, graph.boxes.map { it.id }.filter { it !in grouped }, graph.groups.map { it.id }.filter { it !in nested })
    }

    private fun chain(scope: Scope): List<Scope> = generateSequence(scope) { parentOfScope[it] }.toList()

    /** The node of [scope] that holds the box: the box itself, or the child group it is inside. */
    private fun representative(boxId: String, scope: Scope): Node {
        var current = scopeOfBox.getValue(boxId)
        if (current === scope) return boxNodes.getValue(boxId)
        while (parentOfScope[current] !== scope) current = parentOfScope.getValue(current)
        return nodeOfScope.getValue(current)
    }

    private fun buildLinks() {
        val blocked = buildBlocks()
        graph.edges.forEachIndexed { index, edge ->
            if (index in blocked) return@forEachIndexed
            if (edge.from == edge.to) {
                val node = boxNodes.getValue(edge.from)
                val text = if (edge.labelWidth > 0f) edge.labelWidth + options.trackGap else 0f
                node.rightExtra = max(node.rightExtra, options.loopSize + options.portGap + text)
                selfLoops += index to node
                return@forEachIndexed
            }
            val fromChain = chain(scopeOfBox.getValue(edge.from))
            val common = chain(scopeOfBox.getValue(edge.to)).first { it in fromChain }
            val link = Link(listOf(index), representative(edge.from, common), representative(edge.to, common), edge.labelWidth, edge.labelHeight)
            common.links += link
            topLinks += link to common
        }
    }

    /**
     * Gathers the leaf children a box reaches by identical arrows into blocks, each reached by one
     * link. Returns the arrows the blocks took.
     */
    private fun buildBlocks(): Set<Int> {
        val degree = mutableMapOf<String, Int>()
        graph.edges.forEach { edge ->
            degree.merge(edge.from, 1, Int::plus)
            degree.merge(edge.to, 1, Int::plus)
        }
        val families = graph.edges.withIndex()
            .filter { (_, e) -> e.from != e.to && degree[e.to] == 1 && scopeOfBox[e.from] === scopeOfBox[e.to] }
            .groupBy { (_, e) -> listOf(e.from, e.look, e.labelWidth, e.labelHeight) }
            .values.filter { it.size >= options.blockMinimum }
        val taken = mutableSetOf<Int>()
        for (family in families) {
            val first = family.first().value
            val scope = scopeOfBox.getValue(first.from)
            val members = family.map { boxNodes.getValue(it.value.to) }
            val pad = options.blockPadding
            val packed = packLines(members.map { it.width to it.height }, scope.limit - 2 * pad, options.blockGap)
            val block = Node("block:" + first.from + ":" + family.first().index, Kind.BLOCK, packed.width + 2 * pad, packed.height + 2 * pad)
            block.members = members.mapIndexed { i, node -> node to PlacedPoint(packed.places[i].x + pad, packed.places[i].y + pad) }
            val at = scope.nodes.indexOf(members.first())
            scope.nodes.removeAll(members.toSet())
            scope.nodes.add(min(at, scope.nodes.size), block)
            blocks += block to scope
            val link = Link(family.map { it.index }, boxNodes.getValue(first.from), block, first.labelWidth, first.labelHeight)
            scope.links += link
            topLinks += link to scope
            taken += family.map { it.index }
        }
        return taken
    }

    private class Packed(val width: Float, val height: Float, val places: List<PlacedPoint>)

    /** Puts rectangles in lines no wider than [limit], each line centered under the widest. */
    private fun packLines(sizes: List<Pair<Float, Float>>, limit: Float, gap: Float): Packed {
        val lines = mutableListOf<MutableList<Int>>()
        var used = 0f
        sizes.forEachIndexed { i, (w, _) ->
            if (lines.isEmpty() || used + gap + w > limit) { lines += mutableListOf(i); used = w }
            else { lines.last() += i; used += gap + w }
        }
        val lineWidth = lines.map { line -> line.sumOf { sizes[it].first.toDouble() }.toFloat() + gap * (line.size - 1) }
        val width = lineWidth.maxOrNull() ?: 0f
        val places = arrayOfNulls<PlacedPoint>(sizes.size)
        var y = 0f
        lines.forEachIndexed { l, line ->
            val lineHeight = line.maxOf { sizes[it].second }
            var x = (width - lineWidth[l]) / 2
            line.forEach { i ->
                places[i] = PlacedPoint(x, y + (lineHeight - sizes[i].second) / 2)
                x += sizes[i].first + gap
            }
            y += lineHeight + gap
        }
        return Packed(width, max(0f, y - gap), places.map { it!! })
    }

    // ---------------------------------------------------------------------------------------------
    // Rows and orders, from the outermost scope in
    // ---------------------------------------------------------------------------------------------

    private fun plan(scope: Scope) {
        if (start > 0) scope.nodes.shuffle(Random(start))
        val hasPorts = scope.nodes.any { it.isPort }
        val components = if (hasPorts) listOf(scope.nodes.toList()) else components(scope)
        components.forEach { nodes ->
            val set = nodes.toSet()
            val part = Layered(nodes, scope.links.filter { it.from in set }, scope.limit, options, portsFollowInside, ::startRank)
            part.assignRows()
            part.orderRows()
            scope.parts += part
        }
        // Each arrow crossing a child group's border continues in the group, on the side it comes from
        scope.links.forEach { link ->
            (link.from.inner)?.let { inner -> link.fromInner = continueInside(link, inner, link.from, leaving = true) }
            (link.to.inner)?.let { inner -> link.toInner = continueInside(link, inner, link.to, leaving = false) }
        }
        scope.nodes.mapNotNull { it.inner }.forEach { plan(it) }
    }

    /**
     * Where a loop is entered, lowest first: by the best start a node holds, the drawing's real
     * start even when a loop passes through its group.
     */
    private fun startRank(node: Node): Int = boxesIn(node).minOfOrNull { startIndex[it] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE

    private fun boxesIn(node: Node): List<String> = when (node.kind) {
        Kind.BOX -> listOf(node.id)
        Kind.GROUP -> node.inner?.nodes.orEmpty().flatMap { boxesIn(it) }
        Kind.BLOCK -> node.members.flatMap { boxesIn(it.first) }
        else -> emptyList()
    }

    private fun continueInside(link: Link, inner: Scope, group: Node, leaving: Boolean): Link {
        val edge = graph.edges[link.edges.first()]
        val onTop = link.bottom === group
        val other = (if (onTop) link.aboveBottom else link.belowTop) ?: if (onTop) link.top else link.bottom
        val port = Node("port:${inner.group?.id}:${link.edges.first()}:$leaving", if (onTop) Kind.TOP_PORT else Kind.BOTTOM_PORT)
        port.portKey = other.layer * 1000f + other.slot(link)
        inner.nodes += port
        val boxId = if (leaving) edge.from else edge.to
        val node = representative(boxId, inner)
        val stretch = if (leaving) Link(link.edges, node, port, 0f, 0f) else Link(link.edges, port, node, 0f, 0f)
        inner.links += stretch
        return stretch
    }

    /**
     * Orders each scope again now that the ports exist: a row sees where each arrow really enters a
     * group, and each group's ports follow the new order outside.
     */
    private fun refine(scope: Scope) {
        scope.parts.forEach { it.orderRows() }
        scope.links.forEach { link ->
            listOf(link.from to link.fromInner, link.to to link.toInner).forEach { (group, stretch) ->
                if (stretch == null) return@forEach
                val onTop = link.bottom === group
                val other = (if (onTop) link.aboveBottom else link.belowTop) ?: return@forEach
                val port = if (stretch.from.isPort) stretch.from else stretch.to
                port.portKey = other.layer * 1000f + other.slot(link)
            }
        }
        scope.nodes.mapNotNull { it.inner }.forEach { refine(it) }
    }

    private fun aimPorts(scope: Scope) {
        scope.parts.forEach { part ->
            part.links.forEach { link ->
                listOf(link.from to link.fromInner, link.to to link.toInner).forEach { (group, stretch) ->
                    if (stretch == null) return@forEach
                    val port = if (stretch.from.isPort) stretch.from else stretch.to
                    part.facing(link, group)?.let {
                        val room = max(0f, group.width - 2 * options.groupPadding)
                        port.wantedX = (it - group.x - options.groupPadding).coerceIn(0f, room)
                    }
                }
            }
        }
        scope.nodes.mapNotNull { it.inner }.forEach { aimPorts(it) }
    }

    private fun components(scope: Scope): List<List<Node>> {
        val parent = scope.nodes.associateWith { it }.toMutableMap()
        fun find(n: Node): Node { var c = n; while (parent[c] !== c) c = parent.getValue(c); return c }
        scope.links.forEach { parent[find(it.from)] = find(it.to) }
        return scope.nodes.groupBy { find(it) }.values.toList()
    }

    // ---------------------------------------------------------------------------------------------
    // Sizes and positions, from the innermost scope out
    // ---------------------------------------------------------------------------------------------

    private fun measure(scope: Scope) {
        scope.nodes.forEach { node ->
            node.inner?.let { inner ->
                measure(inner)
                node.width = inner.width
                node.height = inner.height
            }
        }
        val title = scope.group?.let { it.titleHeight + options.groupPadding / 2 } ?: 0f
        val pad = if (scope.group != null) options.groupPadding else 0f
        val titleEnd = scope.group?.let { pad + it.titleWidth + options.portGap } ?: 0f
        scope.parts.forEach { it.place(titleEnd - pad) }

        // The parts in lines at the scope's width
        val ordered = scope.parts.sortedByDescending { it.nodes.size > 1 }
        val packed = packLines(ordered.map { it.width to it.height }, scope.limit - 2 * pad, options.boxGap)
        ordered.forEachIndexed { i, part -> part.translate(packed.places[i].x + pad, packed.places[i].y + pad + title) }
        val contentWidth = max(packed.width, scope.group?.titleWidth ?: 0f)
        scope.width = contentWidth + 2 * pad
        scope.height = packed.height + title + 2 * pad

        // A port stands on the group's border: its line goes on to the border itself
        scope.links.forEach { link ->
            var points = link.points
            if (link.top.kind == Kind.TOP_PORT) points = listOf(PlacedPoint(points.first().x, 0f)) + points
            if (link.bottom.kind == Kind.BOTTOM_PORT) points = points + PlacedPoint(points.last().x, scope.height)
            link.points = points
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The result
    // ---------------------------------------------------------------------------------------------

    private fun result(): Placement {
        val boxes = mutableMapOf<String, PlacedRect>()
        val groups = mutableMapOf<String, PlacedRect>()
        val placedBlocks = mutableListOf<PlacedBlock>()

        fun walk(scope: Scope, ox: Float, oy: Float) {
            scope.originX = ox
            scope.originY = oy
            scope.nodes.forEach { node ->
                val left = ox + node.x
                val top = oy + node.y
                val rect = PlacedRect(left, top, left + node.width, top + node.height)
                when (node.kind) {
                    Kind.BOX -> boxes[node.id] = rect
                    Kind.GROUP -> {
                        groups[node.id] = rect
                        walk(node.inner!!, left, top)
                    }
                    Kind.BLOCK -> {
                        node.members.forEach { (member, at) ->
                            boxes[member.id] = PlacedRect(left + at.x, top + at.y, left + at.x + member.width, top + at.y + member.height)
                        }
                        placedBlocks += PlacedBlock(rect, node.members.map { it.first.id })
                    }
                    else -> Unit
                }
            }
        }
        walk(root, 0f, 0f)

        val routes = topLinks.map { (link, scope) ->
            val label = link.label?.let { PlacedRect(scope.originX + it.x, scope.originY + it.y, scope.originX + it.x + it.width, scope.originY + it.y + it.height) }
            PlacedRoute(link.edges, simplify(path(link, scope)), label)
        } + selfLoops.map { (index, node) ->
            val rect = boxes.getValue(node.id)
            val mid = (rect.top + rect.bottom) / 2
            val half = min(options.loopSize / 2, rect.height / 3)
            val out = rect.right + options.loopSize
            val edge = graph.edges[index]
            val label = if (edge.labelWidth > 0f) PlacedRect(
                out + options.trackGap, mid - edge.labelHeight / 2,
                out + options.trackGap + edge.labelWidth, mid + edge.labelHeight / 2
            ) else null
            PlacedRoute(listOf(index), listOf(
                PlacedPoint(rect.right, mid - half), PlacedPoint(out, mid - half),
                PlacedPoint(out, mid + half), PlacedPoint(rect.right, mid + half)
            ), label)
        }
        return Placement(root.width, root.height, boxes, groups, placedBlocks, routes)
    }

    /** The whole line of a link in the arrow's direction, its stretches inside groups included. */
    private fun path(link: Link, scope: Scope): List<PlacedPoint> {
        val own = link.points.map { PlacedPoint(it.x + scope.originX, it.y + scope.originY) }
        val directed = if (link.isReversed) own.reversed() else own
        val before = link.fromInner?.let { path(it, link.from.inner!!) }.orEmpty()
        val after = link.toInner?.let { path(it, link.to.inner!!) }.orEmpty()
        return before + directed + after
    }

    private companion object {
        const val REFINEMENTS = 3
        const val ALIGNMENTS = 3
    }

    private fun simplify(points: List<PlacedPoint>): List<PlacedPoint> {
        val out = mutableListOf<PlacedPoint>()
        for (p in points) {
            if (out.isNotEmpty() && abs(out.last().x - p.x) < 0.01f && abs(out.last().y - p.y) < 0.01f) continue
            if (out.size >= 2) {
                val a = out[out.size - 2]
                val b = out.last()
                val sameX = abs(a.x - b.x) < 0.01f && abs(b.x - p.x) < 0.01f
                val sameY = abs(a.y - b.y) < 0.01f && abs(b.y - p.y) < 0.01f
                if (sameX || sameY) out.removeAt(out.size - 1)
            }
            out += p
        }
        return out
    }
}

/** The layered layout of one connected part of a scope. */
private class Layered(val nodes: List<Node>, val links: List<Link>, val limit: Float, val options: LayoutOptions, val portsFollowInside: Boolean, val startRank: (Node) -> Int) {

    private var rows: List<MutableList<Node>> = emptyList()
    private val segments = mutableListOf<Segment>()
    private val above = mutableMapOf<Node, MutableList<Segment>>()
    private val below = mutableMapOf<Node, MutableList<Segment>>()
    private val extras = mutableListOf<Node>()
    var width = 0f
    var height = 0f

    // ---------------------------------------------------------------------------------------------
    // Rows
    // ---------------------------------------------------------------------------------------------

    fun assignRows() {
        val inner = nodes.filter { !it.isPort }
        breakLoops(inner)
        val free = links.filter { !it.top.isPort && !it.bottom.isPort }
        val into = free.groupBy { it.bottom }
        val outOf = free.groupBy { it.top }

        // Longest path from the sources
        val waiting = inner.associateWith { into[it]?.size ?: 0 }.toMutableMap()
        val queue = ArrayDeque(inner.filter { waiting[it] == 0 })
        val sorted = mutableListOf<Node>()
        inner.forEach { it.layer = 0 }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            sorted += node
            outOf[node]?.forEach { link ->
                link.bottom.layer = max(link.bottom.layer, node.layer + link.length)
                waiting[link.bottom] = waiting.getValue(link.bottom) - 1
                if (waiting[link.bottom] == 0) queue += link.bottom
            }
        }
        // A source stands right above what it feeds, so that its arrows stay short
        sorted.asReversed().forEach { node ->
            if (into[node].isNullOrEmpty()) outOf[node]?.minOfOrNull { it.bottom.layer - it.length }?.let { node.layer = it }
        }
        spreadWideRows(inner, into, outOf)

        val lowest = inner.minOfOrNull { it.layer } ?: 0
        val base = if (nodes.any { it.kind == Kind.TOP_PORT }) 1 else 0
        inner.forEach { it.layer += base - lowest }
        val last = inner.maxOfOrNull { it.layer } ?: 0
        nodes.forEach { if (it.kind == Kind.TOP_PORT) it.layer = 0; if (it.kind == Kind.BOTTOM_PORT) it.layer = last + 1 }

        // Links longer than a row pass through thin nodes, the label taking the place of one
        links.forEach { link ->
            val chain = mutableListOf(link.top)
            val first = link.top.layer + 1
            val end = link.bottom.layer
            val labelRow = if (link.hasLabel) (first + end - 1) / 2 else -1
            for (l in first until end) {
                val node = if (l == labelRow) Node("label", Kind.LABEL, link.labelWidth, link.labelHeight).also { link.label = it }
                else Node("dummy", Kind.DUMMY)
                node.layer = l
                extras += node
                chain += node
            }
            chain += link.bottom
            link.belowTop = chain[1]
            link.aboveBottom = chain[chain.size - 2]
            chain.zipWithNext { up, down ->
                val segment = Segment(up, down, link)
                segments += segment
                below.getOrPut(up) { mutableListOf() } += segment
                above.getOrPut(down) { mutableListOf() } += segment
            }
        }
        val all = nodes + extras
        val count = (all.maxOfOrNull { it.layer } ?: 0) + 1
        rows = List(count) { l -> all.filter { it.layer == l }.toMutableList() }
    }

    /** Depth-first search: an arrow going back to a node still open is turned over. */
    private fun breakLoops(inner: List<Node>) {
        val free = links.filter { !it.from.isPort && !it.to.isPort }
        val outOf = free.groupBy { it.from }
        val hasInput = free.map { it.to }.toSet()
        val open = mutableSetOf<Node>()
        val done = mutableSetOf<Node>()
        fun visit(node: Node) {
            open += node
            outOf[node]?.forEach { link ->
                if (link.to in open) { link.top = link.to; link.bottom = link.from }
                else {
                    link.top = link.from
                    link.bottom = link.to
                    if (link.to !in done) visit(link.to)
                }
            }
            open -= node
            done += node
        }
        inner.filter { it !in hasInput }.forEach { if (it !in done) visit(it) }
        inner.sortedBy(startRank).forEach { if (it !in done) visit(it) }
        links.filter { it.from.isPort || it.to.isPort }.forEach { link ->
            val portOnTop = link.from.kind == Kind.TOP_PORT || link.to.kind == Kind.TOP_PORT
            val port = if (link.from.isPort) link.from else link.to
            val other = if (link.from.isPort) link.to else link.from
            link.top = if (portOnTop) port else other
            link.bottom = if (portOnTop) other else port
        }
    }

    /**
     * A row wider than the screen first hands the nodes free to move to a narrower row within their
     * arrows' reach; if none can, it is split: a node goes down to a row of its own making, what
     * ends there first and the widest first, everything below moving down by one.
     */
    private fun spreadWideRows(inner: List<Node>, into: Map<Node, List<Link>>, outOf: Map<Node, List<Link>>) {
        fun row(l: Int) = inner.filter { it.layer == l }
        fun rowWidth(l: Int) = row(l).let { nodes -> nodes.sumOf { it.width.toDouble() }.toFloat() + options.boxGap * max(0, nodes.size - 1) }
        var lastRow = inner.maxOfOrNull { it.layer } ?: return
        repeat(inner.size * 2) {
            val wide = (inner.minOf { it.layer }..lastRow).firstOrNull { rowWidth(it) > limit && row(it).size > 1 } ?: return
            val moved = row(wide).sortedBy { it.width }.firstOrNull { node ->
                val low = into[node]?.maxOfOrNull { it.top.layer + it.length } ?: inner.minOf { it.layer }
                val high = outOf[node]?.minOfOrNull { it.bottom.layer - it.length } ?: lastRow
                val target = (low..high).filter { it != wide && rowWidth(it) + options.boxGap + node.width <= limit }
                    .minByOrNull { abs(it - wide) } ?: return@firstOrNull false
                node.layer = target
                true
            }
            if (moved == null) {
                val node = row(wide).sortedWith(compareBy<Node> { !outOf[it].isNullOrEmpty() }.thenByDescending { it.width }).first()
                inner.forEach { if (it.layer > wide) it.layer++ }
                node.layer = wide + 1
                lastRow++
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The order in each row
    // ---------------------------------------------------------------------------------------------

    fun orderRows() {
        rows.forEach { row -> if (row.any { it.isPort }) row.sortBy { it.portKey } }
        renumber()
        var best = rows.map { it.toList() }
        var bestCrossings = crossings()
        repeat(SWEEPS) { sweep ->
            val down = sweep % 2 == 0
            val indices = if (down) rows.indices.drop(1) else rows.indices.reversed().drop(1)
            for (l in indices) {
                val row = rows[l]
                val keys = row.associateWith { node ->
                    val neighbours = if (down) above[node]?.map { it.up.slot(it.link) } else below[node]?.map { it.down.slot(it.link) }
                    if (neighbours.isNullOrEmpty()) node.order.toFloat() else neighbours.average().toFloat()
                }
                // Ports keep the order of what they face outside; those facing the same thing follow the inside
                if (portsFollowInside) row.sortBy { keys.getValue(it) }
                else row.sortWith(compareBy<Node> { it.portKey }.thenBy { keys.getValue(it) })
                row.forEachIndexed { i, node -> node.order = i }
            }
            transpose()
            val now = crossings()
            if (now < bestCrossings) {
                bestCrossings = now
                best = rows.map { it.toList() }
            }
        }
        rows.forEachIndexed { l, row -> row.clear(); row += best[l] }
        renumber()
    }

    private fun renumber() = rows.forEach { row -> row.forEachIndexed { i, node -> node.order = i } }

    private fun crossings(): Int {
        var total = 0
        val byRow = segments.groupBy { it.up.layer }
        byRow.values.forEach { list ->
            for (i in list.indices) for (j in i + 1 until list.size) if (crosses(list[i], list[j])) total++
        }
        return total
    }

    private fun crosses(a: Segment, b: Segment): Boolean =
        (a.up.slot(a.link) - b.up.slot(b.link)) * (a.down.slot(a.link) - b.down.slot(b.link)) < 0f

    /** Swaps neighbours in a row as long as it removes crossings. */
    private fun transpose() {
        var improved = true
        var rounds = 0
        while (improved && rounds++ < 6) {
            improved = false
            rows.forEach { row ->
                if (row.any { it.isPort }) return@forEach
                for (i in 0 until row.size - 1) {
                    val a = row[i]
                    val b = row[i + 1]
                    if (pairCrossings(b, a) < pairCrossings(a, b)) {
                        row[i] = b
                        row[i + 1] = a
                        a.order = i + 1
                        b.order = i
                        improved = true
                    }
                }
            }
        }
    }

    /** The crossings between the arrows of two neighbours, [left] standing before [right]. */
    private fun pairCrossings(left: Node, right: Node): Int {
        fun count(l: List<Float>, r: List<Float>) = l.sumOf { x -> r.count { y -> x > y } }
        return count(above[left].orEmpty().map { it.up.slot(it.link) }, above[right].orEmpty().map { it.up.slot(it.link) }) +
            count(below[left].orEmpty().map { it.down.slot(it.link) }, below[right].orEmpty().map { it.down.slot(it.link) })
    }

    // ---------------------------------------------------------------------------------------------
    // Positions
    // ---------------------------------------------------------------------------------------------

    /** Where the line of [link] stands on the far side of the gap next to [group]. */
    fun facing(link: Link, group: Node): Float? =
        above[group]?.firstOrNull { it.link === link }?.upX ?: below[group]?.firstOrNull { it.link === link }?.downX

    /** Computes the positions and the lines; [titleEnd] is where a top port may stand, past the group's title. */
    fun place(titleEnd: Float) {
        placeRows()
        val left = (nodes + extras).minOfOrNull { it.x } ?: 0f
        (nodes + extras).forEach { it.x -= left }
        val topPorts = rows.firstOrNull()?.filter { it.kind == Kind.TOP_PORT }.orEmpty()
        if (topPorts.isNotEmpty() && titleEnd > 0f) {
            var next = titleEnd
            topPorts.forEach { port ->
                port.x = max(port.x, next)
                next = port.x + options.portGap
            }
        }
        width = (nodes + extras).maxOfOrNull { it.x + it.width + it.rightExtra } ?: 0f
        attach()
        route()
    }

    private fun gap(a: Node, b: Node): Float = a.rightExtra + when {
        a.isThin && b.isThin -> options.portGap
        a.isThin || b.isThin -> options.boxGap / 2
        else -> options.boxGap
    }

    private fun placeRows() {
        rows.forEach { row ->
            var x = 0f
            row.forEach { node ->
                node.x = x
                x += node.width + (row.getOrNull(node.order + 1)?.let { gap(node, it) } ?: 0f)
            }
        }
        repeat(POSITION_SWEEPS) { sweep ->
            val down = sweep % 2 == 0
            val indices = if (down) rows.indices.toList() else rows.indices.reversed().toList()
            indices.forEach { l -> settleRow(rows[l], if (sweep < POSITION_SWEEPS - 2) null else down) }
        }
        holdWithinWidth()
        alignLongLines()
    }

    /** A line running over several rows stands at one x, wherever its neighbours leave it the room. */
    private fun alignLongLines() {
        fun fits(node: Node, center: Float): Boolean {
            val row = rows[node.layer]
            val i = row.indexOf(node)
            val x = center - node.width / 2
            val left = row.getOrNull(i - 1)
            val right = row.getOrNull(i + 1)
            return (left == null || left.x + left.width + gap(left, node) <= x + 0.01f) &&
                (right == null || x + node.width + gap(node, right) <= right.x + 0.01f)
        }
        links.forEach { link ->
            val inner = segments.filter { it.link === link }.map { it.down }.filter { it.kind == Kind.DUMMY || it.kind == Kind.LABEL }
            if (inner.size < 2) return@forEach
            val median = inner.map { it.center }.sorted()[inner.size / 2]
            // Best in front of what it reaches: a group's port, or the place on a box nearest the line
            fun reach(end: Node) = if (end.kind == Kind.GROUP) end.x + portX(link, end) else median.coerceIn(end.slideLow(CORNER), end.slideHigh(CORNER))
            val center = listOf(reach(link.top), reach(link.bottom), median).firstOrNull { c -> inner.all { fits(it, c) } } ?: return@forEach
            inner.forEach { it.x = center - it.width / 2 }
        }
    }

    /**
     * Rows that each fit the screen can still add up to a drawing too wide, by standing offset from
     * one another: they are brought into one window of the screen's width, and settle again inside it.
     */
    private fun holdWithinWidth() {
        fun left() = rows.flatten().minOfOrNull { it.x } ?: 0f
        fun right() = rows.flatten().maxOfOrNull { it.x + it.width + it.rightExtra } ?: 0f
        if (right() - left() <= limit) return
        val from = (left() + right() - limit) / 2
        repeat(WINDOW_SWEEPS) { sweep ->
            val indices = if (sweep % 2 == 0) rows.indices.toList() else rows.indices.reversed().toList()
            indices.forEach { l ->
                settleRow(rows[l], null)
                clampRow(rows[l], from, from + limit)
            }
        }
    }

    /** Pushes a row inside a window, keeping its order and gaps; a row wider than the window stays whole from its left. */
    private fun clampRow(row: List<Node>, from: Float, to: Float) {
        if (row.isEmpty()) return
        var next = to
        for (i in row.indices.reversed()) {
            val node = row[i]
            val end = node.x + node.width + node.rightExtra
            if (end > next) node.x -= end - next
            next = node.x - (row.getOrNull(i - 1)?.let { gap(it, node) - it.rightExtra } ?: 0f)
        }
        var least = from
        row.forEachIndexed { i, node ->
            if (node.x < least) node.x = least
            least = node.x + node.width + (row.getOrNull(i + 1)?.let { gap(node, it) } ?: 0f)
        }
    }

    /** Places a row as near as its order allows to what it is linked to, by least squares. */
    private fun settleRow(row: List<Node>, fromAbove: Boolean?) {
        if (row.isEmpty()) return
        val wanted = FloatArray(row.size)
        val weights = FloatArray(row.size)
        row.forEachIndexed { i, node ->
            val links = when (fromAbove) {
                true -> above[node].orEmpty().map { it.up to it }
                false -> below[node].orEmpty().map { it.down to it }
                null -> above[node].orEmpty().map { it.up to it } + below[node].orEmpty().map { it.down to it }
            }
            var sum = 0f
            var total = 0f
            links.forEach { (other, segment) ->
                val w = when {
                    node.isThin && other.isThin -> 8f
                    node.isThin || other.isThin -> 3f
                    else -> 1f
                }
                sum += (reach(other, segment.link) - (reach(node, segment.link) - node.center)) * w
                total += w
            }
            wanted[i] = if (total > 0f) sum / total else node.center
            weights[i] = max(total, 0.01f)
            node.wantedX?.let {
                wanted[i] = it
                weights[i] = PORT_WEIGHT
            }
        }
        // With each center shifted by what must stand before it, the order is a plain « not decreasing »
        val offsets = FloatArray(row.size)
        for (i in 1 until row.size) offsets[i] = offsets[i - 1] + row[i - 1].width / 2 + gap(row[i - 1], row[i]) + row[i].width / 2
        val solved = isotonic(FloatArray(row.size) { wanted[it] - offsets[it] }, weights)
        row.forEachIndexed { i, node -> node.x = solved[i] + offsets[i] - node.width / 2 }
    }

    /** Pool-adjacent-violators: the closest non-decreasing series, in weighted least squares. */
    private fun isotonic(values: FloatArray, weights: FloatArray): FloatArray {
        val mean = FloatArray(values.size)
        val weight = FloatArray(values.size)
        val count = IntArray(values.size)
        var blocks = 0
        for (i in values.indices) {
            mean[blocks] = values[i]
            weight[blocks] = weights[i]
            count[blocks] = 1
            blocks++
            while (blocks > 1 && mean[blocks - 2] > mean[blocks - 1]) {
                val w = weight[blocks - 2] + weight[blocks - 1]
                mean[blocks - 2] = (mean[blocks - 2] * weight[blocks - 2] + mean[blocks - 1] * weight[blocks - 1]) / w
                weight[blocks - 2] = w
                count[blocks - 2] += count[blocks - 1]
                blocks--
            }
        }
        val out = FloatArray(values.size)
        var at = 0
        for (b in 0 until blocks) repeat(count[b]) { out[at++] = mean[b] }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Where lines touch their nodes
    // ---------------------------------------------------------------------------------------------

    private fun attach() {
        segments.forEach {
            it.upX = it.up.center
            it.downX = it.down.center
        }
        (nodes + extras).forEach { node ->
            when (node.kind) {
                Kind.BOX, Kind.BLOCK -> {
                    spread(node, below[node].orEmpty(), { reach(it.down, it.link) }) { s, x -> s.upX = x }
                    spread(node, above[node].orEmpty(), { reach(it.up, it.link) }) { s, x -> s.downX = x }
                }
                Kind.GROUP -> {
                    below[node].orEmpty().forEach { it.upX = node.x + portX(it.link, node) }
                    above[node].orEmpty().forEach { it.downX = node.x + portX(it.link, node) }
                }
                else -> Unit
            }
        }
        // Once every end has a first place, each box's lines face the real ends of the others
        (nodes + extras).filter { it.kind == Kind.BOX || it.kind == Kind.BLOCK }.forEach { node ->
            spread(node, below[node].orEmpty(), { it.downX }) { s, x -> s.upX = x }
            spread(node, above[node].orEmpty(), { it.upX }) { s, x -> s.downX = x }
        }
        straighten()
    }

    /** Where a link really touches a neighbour: its port on a group, the center of anything else. */
    private fun reach(node: Node, link: Link): Float =
        if (node.kind == Kind.GROUP) node.x + portX(link, node) else node.center

    /**
     * A line goes straight whenever one of its ends can slide to face the other: an end alone on
     * its side slides freely along the box, as long as it stays off the corners.
     */
    private fun straighten() {
        fun slides(node: Node, count: Int) = (node.kind == Kind.BOX || node.kind == Kind.BLOCK) && count == 1
        fun within(node: Node, x: Float) = x >= node.slideLow(CORNER) && x <= node.slideHigh(CORNER)
        segments.forEach { s ->
            if (abs(s.upX - s.downX) <= 0.5f) return@forEach
            val upFree = slides(s.up, below[s.up]?.size ?: 0)
            val downFree = slides(s.down, above[s.down]?.size ?: 0)
            when {
                upFree && downFree -> {
                    val low = max(s.up.slideLow(CORNER), s.down.slideLow(CORNER))
                    val high = min(s.up.slideHigh(CORNER), s.down.slideHigh(CORNER))
                    if (low <= high) { val x = (low + high) / 2; s.upX = x; s.downX = x }
                }
                downFree && within(s.down, s.upX) -> s.downX = s.upX
                upFree && within(s.up, s.downX) -> s.upX = s.downX
            }
        }
    }

    /** The x of the port a link uses on a child group, inside that group. */
    private fun portX(link: Link, group: Node): Float {
        val inner = if (link.from === group) link.fromInner else link.toInner
        val port = inner?.let { if (it.from.isPort) it.from else it.to } ?: return group.width / 2
        return port.x + port.width / 2
    }

    /**
     * Places the lines on one side of a box, in the order of where they go: each as near as it can
     * be to facing its other end, off the corners and apart from its neighbours.
     */
    private fun spread(node: Node, side: List<Segment>, otherX: (Segment) -> Float, set: (Segment, Float) -> Unit) {
        if (side.isEmpty()) return
        val sorted = side.sortedBy(otherX)
        val low = node.portLow(CORNER)
        val high = node.portHigh(CORNER)
        val pitch = if (sorted.size > 1) min(options.portGap, (node.slideHigh(CORNER) - node.slideLow(CORNER)) / (sorted.size - 1)) else 0f
        // A line that can go straight does, anywhere off the corners; one that must bend leaves near the middle
        val places = FloatArray(sorted.size) { i ->
            val wanted = otherX(sorted[i])
            if (wanted >= node.slideLow(CORNER) && wanted <= node.slideHigh(CORNER)) wanted else wanted.coerceIn(low, high)
        }
        for (i in 1 until places.size) places[i] = max(places[i], places[i - 1] + pitch)
        val end = node.slideHigh(CORNER)
        if (places.last() > end) places[places.size - 1] = end
        for (i in places.size - 2 downTo 0) places[i] = min(places[i], places[i + 1] - pitch)
        sorted.forEachIndexed { i, segment -> set(segment, places[i]) }
    }

    // ---------------------------------------------------------------------------------------------
    // Lines
    // ---------------------------------------------------------------------------------------------

    /** Gives each bent stretch a track of the gap between its rows, then sets the rows' heights. */
    private fun route() {
        val byGap = segments.groupBy { it.up.layer }
        val tracks = IntArray(rows.size)
        byGap.forEach { (gap, list) -> tracks[gap] = assignTracks(list) }

        val heights = rows.map { row -> row.maxOfOrNull { it.height } ?: 0f }
        val rowTop = FloatArray(rows.size)
        var y = 0f
        rows.indices.forEach { l ->
            rowTop[l] = y
            y += heights[l]
            if (l < rows.size - 1) y += if (rows[l].all { it.isPort } || rows[l + 1].all { it.isPort }) options.rowGap * 0.6f + max(0, tracks[l] - 1) * options.trackGap
            else options.rowGap + max(0, tracks[l] - 1) * options.trackGap
        }
        height = y
        rows.forEachIndexed { l, row -> row.forEach { it.y = rowTop[l] + (heights[l] - it.height) / 2 } }

        links.forEach { link ->
            val chain = segments.filter { it.link === link }.sortedBy { it.up.layer }
            val points = mutableListOf<PlacedPoint>()
            chain.forEach { s ->
                val l = s.up.layer
                val startY = if (s.up.isThin || s.up.kind == Kind.LABEL) rowTop[l] + heights[l] else s.up.y + s.up.height
                val endY = if (s.down.isThin || s.down.kind == Kind.LABEL) rowTop[l + 1] else s.down.y
                if (s.up.isThin || s.up.kind == Kind.LABEL) points += PlacedPoint(s.upX, rowTop[l])
                points += PlacedPoint(s.upX, startY)
                if (abs(s.upX - s.downX) > 0.5f) {
                    val trackCount = tracks[l]
                    val gapTop = rowTop[l] + heights[l]
                    val gapHeight = rowTop[l + 1] - gapTop
                    val trackY = gapTop + gapHeight * (s.track + 1) / (trackCount + 1)
                    points += PlacedPoint(s.upX, trackY)
                    points += PlacedPoint(s.downX, trackY)
                }
                points += PlacedPoint(s.downX, endY)
            }
            link.points = points
        }
    }

    /**
     * Stacks the bent stretches of a gap on tracks. Two stretches side by side must not share a
     * track, and the one whose top leg stands inside the other's span goes first, so that its leg
     * does not cut the other's horizontal part.
     */
    private fun assignTracks(list: List<Segment>): Int {
        val bent = list.filter { abs(it.upX - it.downX) > 0.5f }
        if (bent.isEmpty()) return 0
        fun low(s: Segment) = min(s.upX, s.downX) - options.trackGap / 2
        fun high(s: Segment) = max(s.upX, s.downX) + options.trackGap / 2
        fun overlap(a: Segment, b: Segment) = low(a) <= high(b) && low(b) <= high(a)
        val before = bent.associateWith { mutableSetOf<Segment>() }
        for (i in bent.indices) for (j in i + 1 until bent.size) {
            val a = bent[i]
            val b = bent[j]
            if (!overlap(a, b)) continue
            val aFirst = (if (a.upX in low(b)..high(b)) 1 else 0) + (if (b.downX in low(a)..high(a)) 1 else 0)
            val bFirst = (if (b.upX in low(a)..high(a)) 1 else 0) + (if (a.downX in low(b)..high(b)) 1 else 0)
            if (aFirst >= bFirst) before.getValue(b) += a else before.getValue(a) += b
        }
        // Longest path over « goes before », a circle being cut where it closes
        val track = mutableMapOf<Segment, Int>()
        val open = mutableSetOf<Segment>()
        fun depth(s: Segment): Int {
            track[s]?.let { return it }
            open += s
            val value = before.getValue(s).filter { it !in open }.maxOfOrNull { depth(it) + 1 } ?: 0
            open -= s
            track[s] = value
            return value
        }
        bent.forEach { depth(it) }
        // A circle cut may leave two overlapping stretches on one track: the later one moves down
        bent.forEachIndexed { i, s ->
            var value = track.getValue(s)
            while (bent.take(i).any { overlap(it, s) && track[it] == value }) value++
            track[s] = value
            s.track = value
        }
        return bent.maxOf { it.track } + 1
    }

    fun translate(dx: Float, dy: Float) {
        (nodes + extras).forEach {
            it.x += dx
            it.y += dy
        }
        segments.forEach {
            it.upX += dx
            it.downX += dx
        }
        links.forEach { link -> link.points = link.points.map { PlacedPoint(it.x + dx, it.y + dy) } }
    }

    private companion object {
        const val SWEEPS = 24
        const val POSITION_SWEEPS = 12
        const val WINDOW_SWEEPS = 6
        /** How much more a port's place outside counts than what it reaches inside. */
        const val PORT_WEIGHT = 40f
        /** How far from a box's corner a line may touch it. */
        const val CORNER = 8f
    }
}
