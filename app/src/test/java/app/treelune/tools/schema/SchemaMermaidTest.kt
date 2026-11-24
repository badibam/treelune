package app.treelune.tools.schema

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a Mermaid text becomes, and what the conversion promises to say or to keep. */
class SchemaMermaidTest {

    /** The key of a text, with its values, in place of its words. */
    private val own: (String) -> String = { key ->
        key + (1..(COUNTS[key] ?: 1)).joinToString("") { " %$it\$s" }
    }

    private val mermaid = SchemaMermaid(own)

    private fun read(text: String, existing: JSONObject = JSONObject()) = mermaid.read(text, existing) { null }

    private fun spec(text: String, existing: JSONObject = JSONObject()) = SchemaSpec.of(read(text, existing).applyTo(JSONObject()), own)

    private fun refusal(text: String) = assertThrows(MermaidError::class.java) { read(text) }.message.orEmpty()

    @Test
    fun `every shape a schema has is read`() {
        val s = spec("""
            flowchart TD
                A[rect] --> B(rounded)
                B --> C([pill])
                C --> D((circle))
                D --> E{diamond}
                E --> F@{ shape: stadium, label: "extended" }
        """.trimIndent())
        assertEquals(
            listOf(SchemaShape.RECT, SchemaShape.ROUNDED, SchemaShape.STADIUM, SchemaShape.CIRCLE, SchemaShape.DIAMOND, SchemaShape.STADIUM),
            s.nodes.map { it.shape }
        )
        assertEquals(listOf("rect", "rounded", "pill", "circle", "diamond", "extended"), s.nodes.map { it.label })
    }

    @Test
    fun `a box named without text says its id`() {
        assertEquals("Alone", spec("graph TD\nAlone --> B").nodes[0].label)
    }

    @Test
    fun `every way of writing a link is read`() {
        val s = spec("""
            flowchart TD
                A --> B
                A --- C
                A -.-> D
                A -.- E
                A ==> F
                A === G
                A -- text --> H
                A -. dotted .-> I
                A == thick ==> J
                A -->|piped| K
        """.trimIndent())
        val edge = { to: String -> s.edges.single { it.to == to } }
        assertTrue(edge("B").head && edge("B").line == SchemaLine.NORMAL)
        assertFalse(edge("C").head)
        assertEquals(SchemaLine.DOTTED, edge("D").line)
        assertFalse(edge("E").head)
        assertEquals(SchemaLine.THICK, edge("F").line)
        assertFalse(edge("G").head)
        assertEquals("text", edge("H").label)
        assertEquals("dotted", edge("I").label)
        assertEquals(SchemaLine.DOTTED, edge("I").line)
        assertEquals("thick", edge("J").label)
        assertEquals("piped", edge("K").label)
    }

    @Test
    fun `a chain and several ends make every arrow`() {
        val s = spec("flowchart TD\nA & B --> C & D --> E")
        assertEquals(6, s.edges.size)
    }

    @Test
    fun `an arrow both ways is two arrows`() {
        assertEquals(2, spec("flowchart TD\nA <--> B").edges.size)
    }

    @Test
    fun `nested subgraphs become boxes inside boxes`() {
        val s = spec("""
            flowchart TD
                subgraph out [Outside]
                    A
                    subgraph in["Inside"]
                        B --> C
                    end
                end
                A --> B
        """.trimIndent())
        val parent = s.nodes.associate { it.id to it.parent }
        assertEquals("Outside", s.nodes.first { it.id == "out" }.label)
        assertEquals("Inside", s.nodes.first { it.id == "in" }.label)
        assertEquals(mapOf("out" to null, "A" to "out", "in" to "out", "B" to "in", "C" to "in"), parent)
    }

    @Test
    fun `a sideways direction is rewritten, and said`() {
        val read = read("flowchart LR\nA --> B")
        assertEquals("TD", read.direction)
        assertTrue(read.notes.any { it.startsWith("mermaid_note_direction LR") })
    }

    @Test
    fun `bottom to top stays`() {
        assertEquals("BT", read("flowchart BT\nA --> B").direction)
    }

    @Test
    fun `styling is ignored, and counted`() {
        val read = read("""
            flowchart TD
                A:::hot --> B
                style A fill:#f00
                classDef hot fill:#f00
                linkStyle 0 stroke:#000
        """.trimIndent())
        assertEquals(2, read.nodes.length())
        assertTrue(read.notes.any { it.startsWith("mermaid_note_ignored 4") })
    }

    @Test
    fun `a shape a schema does not have is a rectangle, and said`() {
        val read = read("flowchart TD\nA{{hexagon}} --> B@{ shape: cyl, label: \"db\" }")
        assertEquals("rect", read.nodes.getJSONObject(0).getString("shape"))
        assertEquals("rect", read.nodes.getJSONObject(1).getString("shape"))
        assertTrue(read.notes.any { it.startsWith("mermaid_note_shapes 2") })
    }

    @Test
    fun `an arrow to a subgraph reaches it as written`() {
        val read = read("""
            flowchart TD
                A --> G
                subgraph G [Group]
                    B --> C
                end
        """.trimIndent())
        val s = SchemaSpec.of(read.applyTo(JSONObject()), own)
        assertEquals("G", s.edges.first { it.from == "A" }.to)
        assertEquals(4, s.nodes.size)
        assertEquals(1, read.holders)
        assertEquals(null, s.nodes.first { it.id == "G" }.parent)
        assertTrue(read.notes.isEmpty())
    }

    @Test
    fun `comments, a fence and a front matter are left out`() {
        val s = spec("---\ntitle: x\n---\n```mermaid\nflowchart TD\n%% a comment\nA --> B; B --> C\n```")
        assertEquals(3, s.nodes.size)
    }

    @Test
    fun `a text with brackets inside its quotes reads`() {
        assertEquals("a [b] (c)", spec("flowchart TD\nA[\"a [b] (c)\"] --> B").nodes[0].label)
    }

    @Test
    fun `a line that does not read is named`() {
        assertTrue(refusal("flowchart TD\nA --> B\nA -->").startsWith("mermaid_error_syntax 3"))
    }

    @Test
    fun `another kind of diagram is refused`() {
        assertTrue(refusal("sequenceDiagram\nA->>B: hi").startsWith("mermaid_error_header 1"))
    }

    @Test
    fun `a subgraph left open is named`() {
        assertTrue(refusal("flowchart TD\nsubgraph G\nA\n").startsWith("mermaid_error_unclosed 2 G"))
    }

    @Test
    fun `a text too long is named`() {
        assertTrue(refusal("flowchart TD\nA[\"${"x".repeat(251)}\"]").startsWith("mermaid_error_long"))
    }

    @Test
    fun `a box whose id stays keeps what Mermaid cannot say`() {
        val existing = JSONObject("""{
            "nodes": [{"id": "A", "label": "old", "color": "RED", "icon": "leaf", "thing": {"kind": "ZONE", "id": "z"}, "parent": "G"},
                {"id": "G", "label": "old", "color": "BLUE", "folded": true, "shape": "diamond"}]
        }""")
        val s = spec("flowchart TD\nsubgraph G [New]\nA[new]\nend", existing)
        val box = s.nodes.first { it.id == "A" }
        assertEquals("new", box.label)
        assertEquals("RED", box.color?.name)
        assertEquals("leaf", box.icon)
        assertEquals("z", box.thing?.id)
        val group = s.nodes.first { it.id == "G" }
        assertEquals("New", group.label)
        assertEquals("BLUE", group.color?.name)
        assertTrue(group.folded)
        assertEquals(SchemaShape.DIAMOND, group.shape)
    }

    @Test
    fun `a linked box written by its thing's name keeps following it`() {
        val existing = JSONObject("""{"nodes": [{"id": "A", "thing": {"kind": "ZONE", "id": "z"}}]}""")
        val read = mermaid.read("flowchart TD\nA[\"Garden\"]", existing) { "Garden" }
        assertFalse(read.nodes.getJSONObject(0).has("label"))
    }

    @Test
    fun `a schema written then read again is the same`() {
        val original = spec("""
            flowchart BT
                A["first box"] -->|"goes"| B(second)
                B -.-> C([third])
                C === D((fourth))
                D ==> E{"fifth \"quoted\""}
                subgraph out [Outside]
                    B
                    subgraph in [Inside]
                        C
                    end
                end
                A --> out
        """.trimIndent().replace("\\\"", "#quot;"))
        val again = spec(mermaid.write(original) { null })
        assertEquals(original.direction, again.direction)
        assertEquals(original.nodes.sortedBy { it.id }, again.nodes.sortedBy { it.id })
        assertEquals(original.edges.toSet(), again.edges.toSet())
    }

    private companion object {
        val COUNTS = mapOf(
            "error_shape" to 2, "error_color" to 2, "error_parent_unknown" to 2, "error_edge_inside" to 2,
            "error_line" to 3,
            "error_id_missing" to 0, "error_edge_end" to 0,
            "mermaid_error_syntax" to 2, "mermaid_error_unclosed" to 2,
            "mermaid_error_long" to 2, "mermaid_error_long_line" to 3, "mermaid_note_direction" to 2
        )
    }
}
