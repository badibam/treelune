package app.treelune.tools.path

import app.treelune.core.drawing.diagram.DiagramFill
import app.treelune.core.drawing.diagram.DiagramLine
import app.treelune.core.themes.TagColor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A run drawn as the shared diagram (PathDiagram, docs/design/progression.md, « L'apparence d'une
 * brique »): the root is the drawing, never a box; a box hangs in the stage that holds it, bears
 * its kind's icon and is painted by its state, a mark colouring it where the state does not and
 * fading it; a link is an arrow in its type's line, from its source to the stage it arrives on,
 * those that cannot be drawn left out; a kind in parts reads its content in parts.
 */
class PathDiagramTest {

    private val grammar = PathGrammar.fromJson(JSONObject("""{"root_kind": "year", "link_types": [
        {"key": "required", "name": "Required", "stroke": "normal"}, {"key": "see", "name": "See also", "stroke": "dotted"}],
        "kinds": [
        {"key": "year", "contains": ["month"], "layout": "parts", "states": [{"key": "ongoing"}]},
        {"key": "month", "icon": "calendar", "contains": ["session"], "states": [{"key": "open"}],
         "marks": [{"key": "dim", "when": {}, "paint": {"faded": true}}]},
        {"key": "session", "icon": "brush", "states": [
            {"key": "done", "manual": true, "paint": {"color": "GREEN", "fill": "full"}},
            {"key": "todo", "paint": {"fill": "outline"}}],
         "marks": [{"key": "late", "when": {}, "paint": {"color": "RED"}}]}]}"""))

    private val stages = listOf(
        Stage("y", "y", "year", null, 0, "2026"),
        Stage("m", "y", "month", "y", 0, "July"),
        Stage("a", "y", "session", "m", 0, "Session 1"),
        Stage("b", "y", "session", "m", 1, "Session 2", links = listOf(Link("a", "required"), Link("m", "see"), Link("gone", "see"))),
        Stage("c", "y", "session", "m", 2, "Session 3", links = listOf(Link("b", "see")))
    )

    private val states = mapOf("a" to "done", "b" to "todo", "c" to "todo")
    private val marks = mapOf("c" to listOf("late"), "m" to listOf("dim"))

    private val spec = PathDiagram.of(grammar, stages, { states[it.id] }, { marks[it.id].orEmpty() })
    private fun node(id: String) = spec.nodes.first { it.id == id }

    @Test
    fun `the root is the drawing, its first level hanging from nothing, in parts as its kind says`() {
        assertEquals(listOf("m", "a", "b", "c"), spec.nodes.map { it.id })
        assertNull(node("m").parent)
        assertEquals("m", node("a").parent)
        assertTrue(spec.parts)
    }

    @Test
    fun `a box bears its kind's icon and its state's paint`() {
        assertEquals("brush", node("a").icon)
        assertEquals(TagColor.GREEN, node("a").color)
        assertEquals(DiagramFill.FULL, node("a").fill)
        assertEquals(DiagramFill.OUTLINE, node("b").fill)
        assertNull(node("b").color)
    }

    @Test
    fun `a mark colours a box its state leaves neutral, and fades it`() {
        assertEquals(TagColor.RED, node("c").color)
        assertTrue(node("m").faded)
        assertFalse(node("a").faded)
    }

    @Test
    fun `a link is an arrow from its source in its type's line, one to what holds it or from a stage gone left out`() {
        assertEquals(listOf(Triple("a", "b", DiagramLine.NORMAL), Triple("b", "c", DiagramLine.DOTTED)),
            spec.edges.map { Triple(it.from, it.to, it.line) })
    }
}
