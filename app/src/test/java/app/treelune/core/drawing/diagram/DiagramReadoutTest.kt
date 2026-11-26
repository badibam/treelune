package app.treelune.core.drawing.diagram

import app.treelune.core.drawing.DrawShape
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A box's readout in the diagram (docs/design/progression.md, « L'apparence d'une brique »): its
 * short form under its text at the richest level, gone when compact; its gauge a bar at the
 * bottom of a box with text, its full part as long as its ratio; a fill rising from the bottom of
 * a box without text.
 */
class DiagramReadoutTest {

    private val text = object : DiagramText {
        override fun width(text: String) = text.length * 7f
        override val height = 16f
    }

    private val spec = DiagramSpec(DiagramDirection.TOP_DOWN, listOf(
        DiagramNode("w", "Week 1", DiagramShape.ROUNDED, null, TagColor.GREEN, icon = "calendar", readout = "3 / 4", gauge = 0.75f)
    ), emptyList())

    private fun at(detail: DiagramDetail) = DiagramSceneBuilder(1f, 1f, text) { it }.build(spec, { null }, emptySet(), 340f, detail = detail)

    private fun DiagramScene.boxes() = drawing.shapes.filterIsInstance<DrawShape.Box>()

    @Test
    fun `with its text, the short form under the name and the gauge a bar its ratio long`() {
        val scene = at(DiagramDetail.TEXT)
        val labels = scene.drawing.shapes.filterIsInstance<DrawShape.Label>()
        assertEquals(listOf("3 / 4", "Week 1"), labels.map { it.text })
        assertTrue(labels[0].at.y > labels[1].at.y)
        val (empty, full) = scene.boxes()
        assertEquals(0.75f, (full.rect.right - full.rect.left) / (empty.rect.right - empty.rect.left), 0.001f)
        assertEquals(DiagramSceneBuilder.GAUGE_EMPTY_OPACITY, empty.opacity, 0.001f)
    }

    @Test
    fun `compact, the short form goes and the bar stays`() {
        val scene = at(DiagramDetail.COMPACT)
        assertEquals(listOf("Week 1"), scene.drawing.shapes.filterIsInstance<DrawShape.Label>().map { it.text })
        assertEquals(2, scene.boxes().size)
    }

    @Test
    fun `without text, the fill rises from the bottom`() {
        val scene = at(DiagramDetail.ICON)
        val fill = scene.boxes().single()
        val outline = scene.hits.single().first
        assertEquals(outline.bottom, fill.rect.bottom, 2f)
        assertTrue(fill.rect.top > outline.top + (outline.bottom - outline.top) * 0.2f)
    }
}
