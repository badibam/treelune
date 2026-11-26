package app.treelune.tools.schema

import app.treelune.core.drawing.diagram.*
import app.treelune.core.selection.ReferenceKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a schema's config may hold: each refusal replaces a wrong config tried on the phone. */
class SchemaSpecTest {

    /** How many values a refusal names, where it is not one. */
    private val valuesOf = mapOf(
        "error_id_missing" to 0, "error_edge_end" to 0,
        "error_shape" to 2, "error_color" to 2,
        "error_parent_unknown" to 2, "error_edge_inside" to 2,
        "error_line" to 3
    )

    /** The key of the refusal, with its values, in place of its text. */
    private val own: (String) -> String = { key -> key + (1..(valuesOf[key] ?: 1)).joinToString("") { " %$it\$s" } }

    private fun read(json: String) = SchemaConfig.read(JSONObject(json), own)

    private fun refusal(json: String): String =
        assertThrows(IllegalArgumentException::class.java) { read(json) }.message.orEmpty()

    private val twoBoxes = """{"id": "A", "label": "a"}, {"id": "B", "label": "b"}"""

    @Test
    fun `a whole schema reads`() {
        val spec = read("""{
            "direction": "BT",
            "nodes": [
                {"id": "A", "shape": "diamond", "thing": {"kind": "TOOL_INSTANCE", "id": "t1"}, "color": "RED", "parent": "in"},
                {"id": "B", "label": "Ce qu'il me reste", "parent": "in"},
                {"id": "out", "label": "Dehors", "folded": true},
                {"id": "in", "label": "Dedans", "parent": "out"}
            ],
            "edges": [{"from": "A", "to": "B", "label": "nourrit", "line": "dotted", "head": false}]
        }""")
        assertEquals(DiagramDirection.BOTTOM_UP, spec.direction)
        assertEquals(DiagramShape.DIAMOND, spec.nodes[0].shape)
        assertEquals(ReferenceKind.TOOL_INSTANCE, spec.nodes[0].thing?.kind)
        assertEquals(DiagramShape.ROUNDED, spec.nodes[1].shape)
        assertEquals(DiagramLine.DOTTED, spec.edges[0].line)
        assertEquals(false, spec.edges[0].head)
        assertEquals(setOf("out", "in"), spec.holders)
        assertEquals(listOf("in", "out"), spec.ancestors("A").toList())
        assertTrue(spec.nodes[2].folded)
    }

    @Test
    fun `an empty schema reads, top to bottom`() {
        val spec = read("{}")
        assertEquals(DiagramDirection.TOP_DOWN, spec.direction)
        assertTrue(spec.nodes.isEmpty())
    }

    @Test
    fun `a box is outlined and not faded unless it says otherwise, an unknown fill refused`() {
        val spec = read("""{"nodes": [{"id": "A", "label": "a"}, {"id": "B", "label": "b", "fill": "full", "faded": true}]}""")
        assertEquals(DiagramFill.OUTLINE, spec.nodes[0].fill)
        assertFalse(spec.nodes[0].faded)
        assertEquals(DiagramFill.FULL, spec.nodes[1].fill)
        assertTrue(spec.nodes[1].faded)
        assertTrue(refusal("""{"nodes": [{"id": "A", "label": "a", "fill": "solid"}]}""").startsWith("error_fill A"))
    }

    @Test
    fun `a sideways direction is refused`() {
        assertTrue(refusal("""{"direction": "LR"}""").startsWith("error_direction LR"))
    }

    @Test
    fun `a box says something`() {
        assertTrue(refusal("""{"nodes": [{"id": "A"}]}""").startsWith("error_node_empty A"))
    }

    @Test
    fun `an id serves once`() {
        assertTrue(refusal("""{"nodes": [{"id": "A", "label": "a"}, {"id": "A", "label": "b"}]}""").startsWith("error_id_twice A"))
    }

    @Test
    fun `an arrow names existing boxes, a box holding others included`() {
        assertTrue(refusal("""{"nodes": [$twoBoxes], "edges": [{"from": "A", "to": "C"}]}""").startsWith("error_edge_unknown C"))
        read("""{"nodes": [$twoBoxes, {"id": "G", "label": "g"}, {"id": "C", "label": "c", "parent": "G"}], "edges": [{"from": "A", "to": "G"}, {"from": "G", "to": "B"}]}""")
    }

    @Test
    fun `an arrow never joins a box and what it holds, at any depth, nor a holding box to itself`() {
        val nested = """{"id": "G", "label": "g"}, {"id": "H", "label": "h", "parent": "G"}, {"id": "A", "label": "a", "parent": "H"}"""
        assertTrue(refusal("""{"nodes": [$nested], "edges": [{"from": "G", "to": "A"}]}""").startsWith("error_edge_inside g a"))
        assertTrue(refusal("""{"nodes": [$nested], "edges": [{"from": "A", "to": "H"}]}""").startsWith("error_edge_inside a h"))
        assertTrue(refusal("""{"nodes": [$nested], "edges": [{"from": "G", "to": "G"}]}""").startsWith("error_edge_inside g g"))
        read("""{"nodes": [$nested], "edges": [{"from": "A", "to": "A"}]}""")
    }

    @Test
    fun `a box stands inside a box that exists`() {
        assertTrue(refusal("""{"nodes": [{"id": "A", "label": "a", "parent": "Z"}]}""").startsWith("error_parent_unknown a Z"))
    }

    @Test
    fun `a box never ends up inside itself`() {
        assertTrue(refusal("""{"nodes": [{"id": "G", "label": "g", "parent": "G"}]}""").startsWith("error_parent_loop g"))
        assertTrue(refusal("""{"nodes": [{"id": "G", "label": "g", "parent": "H"}, {"id": "H", "label": "h", "parent": "G"}]}""")
            .startsWith("error_parent_loop"))
    }

    @Test
    fun `a box gets an id from its text when it has none, one given kept, a new box of the form its own made up`() {
        val completed = SchemaToolType.completeConfig(JSONObject("""{"nodes": [
            {"label": "Ce qu'il me reste"}, {"label": "Ce qu’il me reste"}, {"thing": {"kind": "ZONE", "id": "z"}}, {"id": "A", "label": "a"}]}"""), null)
        val ids = (0 until 4).map { completed.getJSONArray("nodes").getJSONObject(it).getString("id") }
        assertEquals(listOf("ce_qu_il_me_reste", "ce_qu_il_me_reste_2", "box", "A"), ids)
        read(completed.toString())
        val made = { app.treelune.core.fields.settings.SettingDefaults.of(listOf(app.treelune.core.fields.settings.SettingNode.Field(
            app.treelune.core.fields.FieldDefinition("id", "Id", null, app.treelune.core.fields.FieldType.TEXT, false, null), systemWritten = true, newId = true))).getString("id") }
        assertTrue(made() != made())
    }
}
