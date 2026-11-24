package app.treelune.core.drawing.layout

import org.junit.Assert.assertTrue
import org.junit.Test

/** What a placement running across promises: the order along its arrows, its height kept, a group's title clear of what it holds. */
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
}
