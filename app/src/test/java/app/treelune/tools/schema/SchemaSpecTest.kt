package app.treelune.tools.schema

import app.treelune.core.selection.ReferenceKind
import app.treelune.tools.schema.layout.LayoutBox
import app.treelune.tools.schema.layout.LayoutEdge
import app.treelune.tools.schema.layout.LayoutGraph
import app.treelune.tools.schema.layout.LayoutGroup
import app.treelune.tools.schema.layout.SchemaLayout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a schema's config may hold: each refusal replaces a wrong config tried on the phone. */
class SchemaSpecTest {

    /** How many values a refusal names, where it is not one. */
    private val valuesOf = mapOf(
        "error_id_missing" to 0, "error_edge_end" to 0,
        "error_shape" to 2, "error_color" to 2,
        "error_group_unknown_member" to 2,
        "error_line" to 3, "error_node_two_groups" to 3, "error_group_two_parents" to 3
    )

    /** The key of the refusal, with its values, in place of its text. */
    private val own: (String) -> String = { key -> key + (1..(valuesOf[key] ?: 1)).joinToString("") { " %$it\$s" } }

    private fun read(json: String) = SchemaSpec.of(JSONObject(json), own)

    private fun refusal(json: String): String =
        assertThrows(IllegalArgumentException::class.java) { read(json) }.message.orEmpty()

    private val twoBoxes = """{"id": "A", "label": "a"}, {"id": "B", "label": "b"}"""

    @Test
    fun `a whole schema reads`() {
        val spec = read("""{
            "direction": "BT",
            "nodes": [
                {"id": "A", "shape": "diamond", "thing": {"kind": "TOOL_INSTANCE", "id": "t1"}, "color": "RED"},
                {"id": "B", "label": "Ce qu'il me reste"}
            ],
            "edges": [{"from": "A", "to": "B", "label": "nourrit", "line": "dotted", "head": false}],
            "groups": [
                {"id": "out", "title": "Dehors", "members": ["in"], "folded": true},
                {"id": "in", "title": "Dedans", "members": ["A", "B"]}
            ]
        }""")
        assertEquals(SchemaDirection.BOTTOM_UP, spec.direction)
        assertEquals(SchemaShape.DIAMOND, spec.nodes[0].shape)
        assertEquals(ReferenceKind.TOOL_INSTANCE, spec.nodes[0].thing?.kind)
        assertEquals(SchemaShape.ROUNDED, spec.nodes[1].shape)
        assertEquals(SchemaLine.DOTTED, spec.edges[0].line)
        assertEquals(false, spec.edges[0].head)
        assertEquals(listOf("in"), spec.groups[0].groups)
        assertTrue(spec.groups[0].folded)
    }

    @Test
    fun `an empty schema reads, top to bottom`() {
        val spec = read("{}")
        assertEquals(SchemaDirection.TOP_DOWN, spec.direction)
        assertTrue(spec.nodes.isEmpty())
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
    fun `an id serves once, among boxes and groups together`() {
        assertTrue(refusal("""{"nodes": [{"id": "A", "label": "a"}], "groups": [{"id": "A", "title": "t"}]}""").startsWith("error_id_twice A"))
    }

    @Test
    fun `an arrow names existing boxes, not a group`() {
        assertTrue(refusal("""{"nodes": [$twoBoxes], "edges": [{"from": "A", "to": "C"}]}""").startsWith("error_edge_unknown C"))
        assertTrue(refusal("""{"nodes": [$twoBoxes], "groups": [{"id": "G", "title": "t"}], "edges": [{"from": "A", "to": "G"}]}""")
            .startsWith("error_edge_unknown G"))
    }

    @Test
    fun `a group names what exists`() {
        assertTrue(refusal("""{"groups": [{"id": "G", "title": "t", "members": ["Z"]}]}""").startsWith("error_group_unknown_member G Z"))
    }

    @Test
    fun `a box or a group stands in one group`() {
        assertTrue(refusal("""{"nodes": [$twoBoxes], "groups": [{"id": "G", "title": "t", "members": ["A"]}, {"id": "H", "title": "t", "members": ["A"]}]}""")
            .startsWith("error_node_two_groups A G H"))
        assertTrue(refusal("""{"groups": [{"id": "G", "title": "t", "members": ["K"]}, {"id": "H", "title": "t", "members": ["K"]}, {"id": "K", "title": "t"}]}""")
            .startsWith("error_group_two_parents K G H"))
    }

    @Test
    fun `a group never ends up inside itself`() {
        assertTrue(refusal("""{"groups": [{"id": "G", "title": "t", "members": ["G"]}]}""").startsWith("error_group_loop G"))
        assertTrue(refusal("""{"groups": [{"id": "G", "title": "t", "members": ["H"]}, {"id": "H", "title": "t", "members": ["G"]}]}""")
            .startsWith("error_group_loop"))
    }

    @Test
    fun `an empty group, which the config accepts, is placed`() {
        val placement = SchemaLayout().place(LayoutGraph(
            boxes = listOf(LayoutBox("A", 80f, 40f), LayoutBox("B", 80f, 40f)),
            edges = listOf(LayoutEdge("A", "B")),
            groups = listOf(LayoutGroup("G", 40f, 16f, boxes = emptyList()))
        ))
        assertTrue("G" in placement.groups)
        assertEquals(2, placement.boxes.size)
    }
}
