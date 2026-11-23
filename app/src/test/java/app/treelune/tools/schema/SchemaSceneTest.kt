package app.treelune.tools.schema

import app.treelune.core.drawing.DrawPoint
import app.treelune.core.drawing.DrawShape
import app.treelune.core.drawing.PathStep
import app.treelune.core.selection.Reference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a schema's drawing promises, without a phone: where heads point, what folding leaves, what is cut. */
class SchemaSceneTest {

    private val text = object : SchemaText {
        override fun width(text: String) = text.length * 7f
        override val height = 16f
    }

    private val own: (String) -> String = { key ->
        when (key) {
            "group_folded" -> "%1\$s (%2\$d)"
            "thing_deleted" -> "deleted"
            else -> key
        }
    }

    private fun spec(json: String) = SchemaSpec.of(JSONObject(json), own)

    private fun scene(json: String, folded: Set<String> = emptySet(), names: (Reference) -> String? = { null }) =
        SchemaSceneBuilder(1f, 1f, text, own).build(spec(json), names, folded, 340f)

    private fun SchemaScene.labels() = drawing.shapes.filterIsInstance<DrawShape.Label>().map { it.text }

    private fun SchemaScene.heads() = drawing.shapes.filterIsInstance<DrawShape.Arrowhead>()

    private val chain = """"nodes": [{"id": "A", "label": "first"}, {"id": "B", "label": "second"}], "edges": [{"from": "A", "to": "B"}]"""

    @Test
    fun `an arrow points down in a schema running down`() {
        val scene = scene("""{"direction": "TD", $chain}""")
        assertFalse(scene.heads().single().up)
    }

    @Test
    fun `an arrow points up in a schema running up, its target above`() {
        val scene = scene("""{"direction": "BT", $chain}""")
        assertTrue(scene.heads().single().up)
        val first = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "first" }
        val second = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "second" }
        assertTrue(second.at.y < first.at.y)
    }

    @Test
    fun `a line without a head has none`() {
        val scene = scene("""{"nodes": [{"id": "A", "label": "a"}, {"id": "B", "label": "b"}], "edges": [{"from": "A", "to": "B", "head": false}]}""")
        assertTrue(scene.heads().isEmpty())
    }

    @Test
    fun `every shape of box and a loop on a box are drawn`() {
        val nodes = SchemaShape.entries.joinToString(",") { """{"id": "${it.key}", "label": "${it.key}", "shape": "${it.key}"}""" }
        val scene = scene("""{"nodes": [$nodes], "edges": [{"from": "rect", "to": "rect"}, {"from": "rect", "to": "circle"}]}""")
        assertTrue(scene.labels().containsAll(SchemaShape.entries.map { it.key }))
    }

    private val grouped = """{
        "nodes": [{"id": "A", "label": "outside"}, {"id": "B", "label": "inner one"}, {"id": "C", "label": "inner two"}],
        "edges": [{"from": "A", "to": "B"}, {"from": "A", "to": "C"}, {"from": "B", "to": "C"}],
        "groups": [{"id": "G", "title": "Group", "members": ["B", "C"]}]
    }"""

    @Test
    fun `a folded group is one box, its arrows reaching it once`() {
        val scene = scene(grouped, folded = setOf("G"))
        assertTrue("Group (2)" in scene.labels())
        assertFalse("inner one" in scene.labels())
        assertEquals(1, scene.heads().size)
    }

    @Test
    fun `a group inside a folded group goes with it, its boxes counted`() {
        val scene = scene("""{
            "nodes": [{"id": "A", "label": "a"}, {"id": "B", "label": "b"}],
            "groups": [{"id": "O", "title": "Out", "members": ["I"]}, {"id": "I", "title": "In", "members": ["A", "B"]}]
        }""", folded = setOf("O", "I"))
        assertTrue("Out (2)" in scene.labels())
        assertFalse("In" in scene.labels())
    }

    @Test
    fun `a touch on a group's title finds the group`() {
        val scene = scene(grouped)
        val title = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "Group" }
        assertEquals(SchemaTouch.Group("G"), scene.hitAt(title.at.x + 2f, title.at.y + 2f))
    }

    @Test
    fun `a long text is cut on three lines, and the touch gives it whole`() {
        val long = List(40) { "word" }.joinToString(" ")
        val scene = scene("""{"nodes": [{"id": "A", "label": "$long"}]}""")
        assertEquals(3, scene.labels().size)
        assertTrue(scene.labels().last().endsWith("…"))
        val line = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first()
        val touch = scene.hitAt(line.at.x, line.at.y + 2f) as? SchemaTouch.Box
        assertNotNull(touch)
        assertTrue(touch!!.cut)
        assertEquals(long, touch.text)
    }

    @Test
    fun `a linked box shows its thing's name, or that it is deleted`() {
        val json = """{"nodes": [{"id": "A", "thing": {"kind": "TOOL_INSTANCE", "id": "t"}}]}"""
        assertTrue("Weight" in scene(json, names = { "Weight" }).labels())
        assertTrue("deleted" in scene(json).labels())
    }

    @Test
    fun `an arrow reaches the real outline of a diamond, beside its middle too`() {
        val scene = scene("""{"nodes": [{"id": "A", "label": "left one"}, {"id": "B", "label": "right one"}, {"id": "D", "label": "a wide decision", "shape": "diamond"}],
            "edges": [{"from": "A", "to": "D"}, {"from": "B", "to": "D"}]}""")
        val diamond = scene.hits.first { (it.second as? SchemaTouch.Box)?.node?.id == "D" }.first
        scene.heads().forEach { head ->
            val middle = (diamond.left + diamond.right) / 2
            val offCenter = kotlin.math.abs(head.at.x - middle) / ((diamond.right - diamond.left) / 2)
            val outline = diamond.top + (diamond.bottom - diamond.top) / 2 * offCenter
            assertEquals(outline, head.at.y + head.size / 2, 1f)
        }
    }

    @Test
    fun `a line never runs through the text of its arrow`() {
        val scene = scene("""{"nodes": [{"id": "A", "label": "a"}, {"id": "B", "label": "b"}], "edges": [{"from": "A", "to": "B", "label": "feeds"}]}""")
        val label = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "feeds" }
        val top = label.at.y - text.height / 2
        val bottom = label.at.y + text.height / 2
        val line = scene.drawing.shapes.filterIsInstance<DrawShape.Path>().first { path -> path.steps.count { it is PathStep.MoveTo } == 2 }
        var from: DrawPoint? = null
        line.steps.forEach { step ->
            when (step) {
                is PathStep.MoveTo -> from = step.to
                is PathStep.LineTo -> {
                    assertFalse(minOf(from!!.y, step.to.y) < bottom && maxOf(from!!.y, step.to.y) > top)
                    from = step.to
                }
                else -> Unit
            }
        }
    }

    @Test
    fun `a loop through a group starts at the box nothing reaches`() {
        val scene = scene("""{
            "nodes": [{"id": "end", "label": "last"}, {"id": "start", "label": "start"}, {"id": "next", "label": "next"}],
            "edges": [{"from": "start", "to": "next"}, {"from": "next", "to": "end"}, {"from": "end", "to": "next"}],
            "groups": [{"id": "G", "title": "Group", "members": ["start", "next"]}]
        }""")
        val y = { name: String -> scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == name }.at.y }
        assertTrue(y("start") < y("next"))
        assertTrue(y("next") < y("last"))
    }

    @Test
    fun `folding the group that holds the start keeps it at the top`() {
        val scene = scene("""{
            "nodes": [{"id": "end", "label": "last"}, {"id": "start", "label": "start"}, {"id": "next", "label": "next"}],
            "edges": [{"from": "start", "to": "next"}, {"from": "next", "to": "end"}, {"from": "end", "to": "next"}],
            "groups": [{"id": "G", "title": "Group", "members": ["start", "next"]}]
        }""", folded = setOf("G"))
        val y = { name: String -> scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == name }.at.y }
        assertTrue(y("Group (2)") < y("last"))
    }

    @Test
    fun `lines leave a diamond near its point, not at its corners`() {
        val scene = scene("""{"nodes": [{"id": "D", "label": "a rather wide decision", "shape": "diamond"}, {"id": "A", "label": "left answer"}, {"id": "B", "label": "right answer"}],
            "edges": [{"from": "D", "to": "A"}, {"from": "D", "to": "B"}]}""")
        val diamond = scene.hits.first { (it.second as? SchemaTouch.Box)?.node?.id == "D" }.first
        val middle = (diamond.left + diamond.right) / 2
        val reach = (diamond.right - diamond.left) * 0.35f / 2 + 0.5f
        scene.drawing.shapes.filterIsInstance<DrawShape.Path>().filter { !it.filled }.mapNotNull { it.steps.firstOrNull() as? PathStep.MoveTo }
            .filter { kotlin.math.abs(it.to.y - (diamond.top + diamond.bottom) / 2) < (diamond.bottom - diamond.top) / 2 + 1f && it.to.y > (diamond.top + diamond.bottom) / 2 }
            .forEach { assertTrue(kotlin.math.abs(it.to.x - middle) <= reach) }
    }

    @Test
    fun `an empty schema draws nothing, without failing`() {
        assertTrue(scene("{}").drawing.shapes.isEmpty())
    }
}
