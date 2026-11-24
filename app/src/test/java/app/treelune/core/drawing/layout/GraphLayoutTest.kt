package app.treelune.core.drawing.layout

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a placement promises: running across, the order along its arrows, its height kept, a
 * group's title clear of what it holds; an arrow ending on a group stopping at its border.
 */
class GraphLayoutTest {

    private val across = GraphLayout(LayoutOptions(maxWidth = 200f, across = true))

    @Test
    fun `a chain runs from left to right, each box beside the one before`() {
        val placement = across.place(LayoutGraph(
            boxes = listOf(LayoutBox("A", 80f, 30f), LayoutBox("B", 80f, 30f), LayoutBox("C", 80f, 30f)),
            edges = listOf(LayoutEdge("A", "B"), LayoutEdge("B", "C"))
        ))
        val (a, b, c) = listOf("A", "B", "C").map { placement.boxes.getValue(it) }
        assertTrue(a.right <= b.left && b.right <= c.left)
        // Each box keeps its own measures once turned back
        assertTrue(kotlin.math.abs(a.width - 80f) < 0.5f && kotlin.math.abs(a.height - 30f) < 0.5f)
        // The line leaves a side of its box and reaches a side of the next
        val route = placement.routes.first { 0 in it.edges }
        assertTrue(kotlin.math.abs(route.points.first().x - a.right) < 0.5f)
        assertTrue(kotlin.math.abs(route.points.last().x - b.left) < 0.5f)
    }

    @Test
    fun `boxes no arrow joins are packed into the height the limit gives`() {
        val placement = across.place(LayoutGraph(boxes = (1..8).map { LayoutBox("B$it", 60f, 40f) }))
        assertTrue(placement.height <= 200f + 0.5f)
        assertTrue(placement.width > 60f)
    }

    @Test
    fun `a group's title takes a band on its left, what it holds beside it`() {
        val placement = across.place(LayoutGraph(
            boxes = listOf(LayoutBox("A", 80f, 30f)),
            groups = listOf(LayoutGroup("G", 70f, 14f, boxes = listOf("A")))
        ))
        val group = placement.groups.getValue("G")
        val box = placement.boxes.getValue("A")
        assertTrue(box.left >= group.left + 70f)
    }

    private val down = GraphLayout(LayoutOptions(maxWidth = 340f))

    /** A box before a group of two, the group before a box after: every end on the group itself. */
    private val throughGroup = LayoutGraph(
        boxes = listOf(LayoutBox("A", 80f, 30f), LayoutBox("B", 80f, 30f), LayoutBox("C", 80f, 30f), LayoutBox("Z", 80f, 30f)),
        edges = listOf(LayoutEdge("A", "G"), LayoutEdge("B", "C"), LayoutEdge("G", "Z")),
        groups = listOf(LayoutGroup("G", 40f, 14f, boxes = listOf("B", "C")))
    )

    @Test
    fun `an arrow to a group stops on its border, and one from it starts there`() {
        val placement = down.place(throughGroup)
        val group = placement.groups.getValue("G")
        val into = placement.routes.first { 0 in it.edges }.points
        val out = placement.routes.first { 2 in it.edges }.points
        assertTrue(kotlin.math.abs(into.last().y - group.top) < 0.5f)
        assertTrue(into.last().x in group.left..group.right)
        assertTrue(kotlin.math.abs(out.first().y - group.bottom) < 0.5f)
        // Nothing of either line runs inside the frame
        listOf(into, out).flatten().forEach { p -> assertTrue(p.y <= group.top + 0.5f || p.y >= group.bottom - 0.5f) }
        assertTrue(placement.boxes.getValue("A").bottom <= group.top)
        assertTrue(placement.boxes.getValue("Z").top >= group.bottom)
    }

    @Test
    fun `an arrow from inside a group to another group crosses one border and stops at the other`() {
        val placement = down.place(LayoutGraph(
            boxes = listOf(LayoutBox("B", 80f, 30f), LayoutBox("C", 80f, 30f), LayoutBox("D", 80f, 30f)),
            edges = listOf(LayoutEdge("B", "H")),
            groups = listOf(LayoutGroup("G", 40f, 14f, boxes = listOf("B")), LayoutGroup("H", 40f, 14f, boxes = listOf("C", "D")))
        ))
        val route = placement.routes.single().points
        assertTrue(kotlin.math.abs(route.first().y - placement.boxes.getValue("B").bottom) < 0.5f)
        assertTrue(kotlin.math.abs(route.last().y - placement.groups.getValue("H").top) < 0.5f)
    }
}
