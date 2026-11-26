package app.treelune.tools.schema

import app.treelune.core.drawing.diagram.*
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

    private val text = object : DiagramText {
        override fun width(text: String) = text.length * 7f
        override val height = 16f
    }

    private val own: (String) -> String = { key ->
        when (key) {
            "group_folded" -> "%1\$s (%2\$d)"
            "thing_deleted" -> "deleted"
            "cut_more" -> "more"
            else -> key
        }
    }

    private fun spec(json: String) = SchemaConfig.read(JSONObject(json), own)

    private fun scene(json: String, folded: Set<String> = emptySet(), names: (Reference) -> String? = { null }) =
        DiagramSceneBuilder(1f, 1f, text, own).build(spec(json), names, folded, 340f)

    private fun DiagramScene.labels() = drawing.shapes.filterIsInstance<DrawShape.Label>().map { it.text }

    private fun DiagramScene.heads() = drawing.shapes.filterIsInstance<DrawShape.Arrowhead>()

    private val chain = """"nodes": [{"id": "A", "label": "first"}, {"id": "B", "label": "second"}], "edges": [{"from": "A", "to": "B"}]"""

    @Test
    fun `a box is painted by its fill, every part of it lighter when faded`() {
        fun painted(box: String): Pair<Float, Float> {
            val paths = scene("""{"nodes": [{"id": "A", "label": "a", "color": "GREEN"$box}]}""").drawing.shapes.filterIsInstance<DrawShape.Path>()
            return paths.single { it.filled }.opacity to paths.single { !it.filled }.opacity
        }
        assertEquals(DiagramSceneBuilder.SURFACE_OPACITY to 1f, painted(""))
        assertEquals(DiagramSceneBuilder.FULL_OPACITY to 1f, painted(""", "fill": "full""""))
        val (fill, border) = painted(""", "fill": "full", "faded": true""")
        assertEquals(DiagramSceneBuilder.FULL_OPACITY * DiagramSceneBuilder.FADED_OPACITY, fill, 0.001f)
        assertEquals(DiagramSceneBuilder.FADED_OPACITY, border, 0.001f)
    }

    @Test
    fun `a box's icon stands above its text, the box no wider, beside it in a drawing running sideways`() {
        val plain = scene("""{"nodes": [{"id": "A", "label": "a long enough text"}]}""")
        val json = """{"nodes": [{"id": "A", "label": "a long enough text", "icon": "leaf"}]}"""
        val above = scene(json)
        val icon = above.drawing.shapes.filterIsInstance<DrawShape.Icon>().single()
        val label = above.drawing.shapes.filterIsInstance<DrawShape.Label>().single()
        assertEquals("leaf", icon.name)
        assertTrue(icon.center.y + icon.size / 2 <= label.at.y)
        assertEquals(plain.drawing.width, above.drawing.width, 0.01f)
        assertTrue(above.drawing.height > plain.drawing.height)
        assertTrue(plain.drawing.shapes.none { it is DrawShape.Icon })

        val beside = DiagramSceneBuilder(1f, 1f, text, own).build(spec(json), { null }, emptySet(), 340f, across = true)
        val sideIcon = beside.drawing.shapes.filterIsInstance<DrawShape.Icon>().single()
        val sideLabel = beside.drawing.shapes.filterIsInstance<DrawShape.Label>().single()
        assertTrue(sideIcon.center.x + sideIcon.size / 2 < sideLabel.at.x - text.width(sideLabel.text) / 2)
    }

    @Test
    fun `a linked box without an icon shows its thing's, its own icon first`() {
        val thing = """{"kind": "ZONE", "id": "z"}"""
        fun iconsOf(json: String) = DiagramSceneBuilder(1f, 1f, text, own)
            .build(spec(json), { "Balcony" }, emptySet(), 340f, iconOf = { "flower" })
            .drawing.shapes.filterIsInstance<DrawShape.Icon>().map { it.name }
        assertEquals(listOf("flower"), iconsOf("""{"nodes": [{"id": "A", "thing": $thing}]}"""))
        assertEquals(listOf("leaf"), iconsOf("""{"nodes": [{"id": "A", "thing": $thing, "icon": "leaf"}]}"""))
    }

    @Test
    fun `a schema running across puts the target right of its start, its head a side triangle`() {
        val scene = DiagramSceneBuilder(1f, 1f, text, own).build(spec("""{"direction": "TD", $chain}"""), { null }, emptySet(), 340f, across = true)
        val first = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "first" }
        val second = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "second" }
        assertTrue(second.at.x > first.at.x)
        assertTrue(scene.heads().isEmpty())
    }

    private val levels = """{"nodes": [{"id": "A", "label": "with an icon", "icon": "leaf", "parent": "G"}, {"id": "B", "label": "without", "shape": "diamond", "parent": "G"},
        {"id": "G", "label": "a group"}], "edges": [{"from": "A", "to": "B", "label": "feeds"}]}"""

    private fun at(detail: DiagramDetail) = DiagramSceneBuilder(1f, 1f, text, own).build(spec(levels), { null }, emptySet(), 340f, detail = detail)

    @Test
    fun `below its text a drawing writes nothing, its arrows' texts and groups' titles included`() {
        assertTrue(at(DiagramDetail.TEXT).labels().containsAll(listOf("with an icon", "without", "feeds", "a group")))
        listOf(DiagramDetail.ICON, DiagramDetail.SHAPE, DiagramDetail.DOT).forEach { detail ->
            assertTrue("$detail writes", at(detail).labels().isEmpty())
        }
    }

    @Test
    fun `compact, a box writes one short line and a group its title, an arrow nothing`() {
        val labels = at(DiagramDetail.COMPACT).labels()
        assertTrue(labels.containsAll(listOf("without", "a group")))
        // A text longer than the short line is cut on it
        assertTrue(labels.any { it.startsWith("with an") && it.endsWith("…") })
        assertFalse("feeds" in labels)
        assertEquals(listOf("leaf"), at(DiagramDetail.COMPACT).drawing.shapes.filterIsInstance<DrawShape.Icon>().map { it.name })
    }

    @Test
    fun `at the icon level a box shows its icon alone, a box without one its shape alone`() {
        val icons = at(DiagramDetail.ICON).drawing.shapes.filterIsInstance<DrawShape.Icon>()
        assertEquals(listOf("leaf"), icons.map { it.name })
        assertTrue(at(DiagramDetail.SHAPE).drawing.shapes.none { it is DrawShape.Icon })
    }

    @Test
    fun `the room the boxes take shrinks from one level to the next`() {
        val builder = DiagramSceneBuilder(1f, 1f, text, own)
        val areas = DiagramDetail.entries.map { builder.boxesArea(spec(levels), { null }, emptySet(), 340f, it) }
        assertEquals(areas.sortedDescending(), areas)
        assertTrue(areas.first() > areas.last())
    }

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
        val nodes = DiagramShape.entries.joinToString(",") { """{"id": "${it.key}", "label": "${it.key}", "shape": "${it.key}"}""" }
        val scene = scene("""{"nodes": [$nodes], "edges": [{"from": "rect", "to": "rect"}, {"from": "rect", "to": "circle"}]}""")
        assertTrue(scene.labels().containsAll(DiagramShape.entries.map { it.key }))
    }

    private val grouped = """{
        "nodes": [{"id": "A", "label": "outside"}, {"id": "B", "label": "inner one", "parent": "G"}, {"id": "C", "label": "inner two", "parent": "G"}, {"id": "G", "label": "Group"}],
        "edges": [{"from": "A", "to": "B"}, {"from": "A", "to": "C"}, {"from": "B", "to": "C"}]
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
            "nodes": [{"id": "A", "label": "a", "parent": "I"}, {"id": "B", "label": "b", "parent": "I"}, {"id": "O", "label": "Out"}, {"id": "I", "label": "In", "parent": "O"}]
        }""", folded = setOf("O", "I"))
        assertTrue("Out (2)" in scene.labels())
        assertFalse("In" in scene.labels())
    }

    @Test
    fun `a touch on a group's title finds the group`() {
        val scene = scene(grouped)
        val title = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "Group" }
        assertEquals(DiagramTouch.Group("G", folded = false), scene.hitAt(title.at.x + 2f, title.at.y + 2f))
    }

    @Test
    fun `a long text is cut on three lines, and the touch gives it whole`() {
        val long = List(40) { "word" }.joinToString(" ")
        val scene = scene("""{"nodes": [{"id": "A", "label": "$long"}]}""")
        assertEquals(listOf("more"), scene.labels().takeLast(1))
        assertEquals(4, scene.labels().size)
        assertTrue(scene.labels()[2].endsWith("…"))
        assertEquals(1, scene.drawing.shapes.filterIsInstance<DrawShape.Segment>().size)
        val line = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first()
        val touch = scene.hitAt(line.at.x, line.at.y + 2f) as? DiagramTouch.Box
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
        val diamond = scene.hits.first { (it.second as? DiagramTouch.Box)?.node?.id == "D" }.first
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
            "nodes": [{"id": "end", "label": "last"}, {"id": "start", "label": "start", "parent": "G"}, {"id": "next", "label": "next", "parent": "G"}, {"id": "G", "label": "Group"}],
            "edges": [{"from": "start", "to": "next"}, {"from": "next", "to": "end"}, {"from": "end", "to": "next"}]
        }""")
        val y = { name: String -> scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == name }.at.y }
        assertTrue(y("start") < y("next"))
        assertTrue(y("next") < y("last"))
    }

    @Test
    fun `folding the group that holds the start keeps it at the top`() {
        val scene = scene("""{
            "nodes": [{"id": "end", "label": "last"}, {"id": "start", "label": "start", "parent": "G"}, {"id": "next", "label": "next", "parent": "G"}, {"id": "G", "label": "Group"}],
            "edges": [{"from": "start", "to": "next"}, {"from": "next", "to": "end"}, {"from": "end", "to": "next"}]
        }""", folded = setOf("G"))
        val y = { name: String -> scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == name }.at.y }
        assertTrue(y("Group (2)") < y("last"))
    }

    @Test
    fun `lines leave a diamond near its point, not at its corners`() {
        val scene = scene("""{"nodes": [{"id": "D", "label": "a rather wide decision", "shape": "diamond"}, {"id": "A", "label": "left answer"}, {"id": "B", "label": "right answer"}],
            "edges": [{"from": "D", "to": "A"}, {"from": "D", "to": "B"}]}""")
        val diamond = scene.hits.first { (it.second as? DiagramTouch.Box)?.node?.id == "D" }.first
        val middle = (diamond.left + diamond.right) / 2
        val reach = (diamond.right - diamond.left) * 0.35f / 2 + 0.5f
        scene.drawing.shapes.filterIsInstance<DrawShape.Path>().filter { !it.filled }.mapNotNull { it.steps.firstOrNull() as? PathStep.MoveTo }
            .filter { kotlin.math.abs(it.to.y - (diamond.top + diamond.bottom) / 2) < (diamond.bottom - diamond.top) / 2 + 1f && it.to.y > (diamond.top + diamond.bottom) / 2 }
            .forEach { assertTrue(kotlin.math.abs(it.to.x - middle) <= reach) }
    }

    @Test
    fun `only what a touch acts on is underlined`() {
        val plain = scene("""{"nodes": [{"id": "A", "label": "plain"}]}""")
        assertTrue(plain.drawing.shapes.none { it is DrawShape.Segment })
        val linked = scene("""{"nodes": [{"id": "A", "label": "linked", "thing": {"kind": "ZONE", "id": "z"}}]}""")
        assertTrue(linked.drawing.shapes.none { it is DrawShape.Segment })
        val found = scene("""{"nodes": [{"id": "A", "label": "linked", "thing": {"kind": "ZONE", "id": "z"}}]}""", names = { "Zone" })
        assertEquals(1, found.drawing.shapes.count { it is DrawShape.Segment })
    }

    @Test
    fun `folding a group leaves its neighbours on the same side`() {
        val json = """{
            "nodes": [{"id": "work", "label": "work"}, {"id": "lunch", "label": "lunch"}, {"id": "link", "label": "neighbour"},
                {"id": "nap", "label": "a long nap here", "parent": "G"}, {"id": "read", "label": "a long read here", "parent": "G"}, {"id": "gone", "label": "after the neighbour"}, {"id": "end", "label": "end"},
                {"id": "G", "label": "Group"}],
            "edges": [{"from": "work", "to": "lunch"}, {"from": "work", "to": "link"}, {"from": "link", "to": "gone"},
                {"from": "lunch", "to": "nap"}, {"from": "lunch", "to": "read"}, {"from": "nap", "to": "end"}, {"from": "read", "to": "end"}]
        }"""
        fun x(scene: DiagramScene, name: String) = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == name }.at.x
        val open = scene(json)
        val closed = scene(json, folded = setOf("G"))
        assertEquals(x(open, "lunch") < x(open, "neighbour"), x(closed, "lunch") < x(closed, "neighbour"))
    }

    private val wide = """{
        "nodes": [{"id": "P", "label": "parent"}, {"id": "A", "label": "member one", "parent": "G"}, {"id": "B", "label": "member two", "parent": "G"},
            {"id": "C", "label": "member three", "parent": "G"}, {"id": "D", "label": "member four", "parent": "G"}, {"id": "G", "label": "Group"}],
        "edges": [{"from": "P", "to": "A", "label": "a"}, {"from": "P", "to": "B", "label": "b"}, {"from": "P", "to": "C", "label": "c"}, {"from": "P", "to": "D", "label": "d"}]
    }"""

    private fun fitted(width: Float, keptOpen: Set<String> = emptySet(), unit: Float = 1f) =
        DiagramSceneBuilder(1f, unit, text, own).build(spec(wide), { null }, emptySet(), width, keptOpen, fit = true)

    @Test
    fun `a schema too wide folds a group to fit, and says which`() {
        // Narrower than a member inside its group's padding: no arrangement of the rows can hold it open
        val scene = fitted(110f)
        assertEquals(setOf("G"), scene.autoFolded)
        assertTrue(scene.drawing.width <= 110.5f)
        assertEquals(DiagramTouch.Group("G", folded = true), scene.hits.last { it.second is DiagramTouch.Group }.second)
    }

    @Test
    fun `a group opened by hand is never folded to make room`() {
        val scene = fitted(110f, keptOpen = setOf("G"))
        assertTrue(scene.autoFolded.isEmpty())
        assertTrue("member one" in scene.labels())
    }

    @Test
    fun `a group set never folded stays open however narrow, shown smaller or left to scroll`() {
        val never = spec(wide).let { spec -> spec.copy(nodes = spec.nodes.map { if (it.id == "G") it.copy(fold = app.treelune.core.drawing.diagram.DiagramFold.OPEN) else it }) }
        val scene = DiagramSceneBuilder(1f, 1f, text, own).build(never, { null }, emptySet(), 110f, fit = true)
        assertTrue(scene.autoFolded.isEmpty())
        assertTrue("member one" in scene.labels())
    }

    @Test
    fun `what is shown smaller stays readable, and fits, or is left to scroll`() {
        val open = DiagramSceneBuilder(1f, 1f, text, own).build(spec(wide), { null }, emptySet(), 10000f).drawing.width
        val scene = fitted(open * 0.7f, keptOpen = setOf("G"))
        assertTrue(scene.scale >= 0.75f)
        assertTrue(scene.scale == 1f || scene.drawing.width * scene.scale <= open * 0.7f + 1f)
    }

    @Test
    fun `a theme in whole pixels is never shown between two sizes`() {
        assertEquals(1f, fitted(110f, keptOpen = setOf("G"), unit = 3f).scale)
    }

    private val frames = """{
        "nodes": [{"id": "A", "label": "before"}, {"id": "G", "label": "Phase", "icon": "hammer", "shape": "diamond"},
            {"id": "B", "label": "inner one", "parent": "G"}, {"id": "C", "label": "inner two", "parent": "G"}, {"id": "Z", "label": "after"}],
        "edges": [{"from": "A", "to": "G"}, {"from": "B", "to": "C"}, {"from": "G", "to": "Z"}]
    }"""

    @Test
    fun `an arrow to or from a group stops at its frame`() {
        val scene = scene(frames)
        val frame = scene.groupArea("G")!!
        val heads = scene.heads()
        assertEquals(3, heads.size)
        // Onto the frame's top, not into it: one head on its border, the other inside on the second box
        assertEquals(1, heads.count { kotlin.math.abs(it.at.y + it.size / 2 - frame.top) < 1f })
        val after = scene.hits.first { (it.second as? DiagramTouch.Box)?.node?.id == "Z" }.first
        assertTrue(after.top > frame.bottom)
    }

    @Test
    fun `a group's title carries its icon, and folded the group takes its own shape`() {
        val open = scene(frames)
        assertTrue("Phase" in open.labels())
        assertEquals(listOf("hammer"), open.drawing.shapes.filterIsInstance<DrawShape.Icon>().map { it.name })
        val closed = scene(frames, folded = setOf("G"))
        assertTrue("Phase (2)" in closed.labels())
        val box = closed.hits.first { it.second == DiagramTouch.Group("G", folded = true) }.first
        // A diamond's outline starts at the middle of its top
        val outline = closed.drawing.shapes.filterIsInstance<DrawShape.Path>().first { path ->
            (path.steps.firstOrNull() as? PathStep.MoveTo)?.to?.let { kotlin.math.abs(it.y - box.top) < 0.5f && kotlin.math.abs(it.x - (box.left + box.right) / 2) < 0.5f } == true
        }
        assertNotNull(outline)
    }

    @Test
    fun `a linked group's title opens its thing, the rest of its band folds it`() {
        val json = """{"nodes": [{"id": "G", "thing": {"kind": "ZONE", "id": "z"}}, {"id": "B", "label": "inside", "parent": "G"}]}"""
        val scene = scene(json, names = { "Balcony" })
        val title = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().first { it.text == "Balcony" }
        val touch = scene.hitAt(title.at.x + 2f, title.at.y + 2f) as? DiagramTouch.Box
        assertEquals("G", touch?.node?.id)
        assertNotNull(touch?.open)
        val band = scene.groupArea("G")!!
        assertEquals(DiagramTouch.Group("G", folded = false), scene.hitAt(band.right - 2f, title.at.y + 2f))
    }

    /**
     * The house (docs/design/progression.md): the top level in parts, written out of order; the
     * phases told by the order, Walls to Joinery starting inside a part, Pause to Finishing skipping one.
     */
    private val house = """{
        "parts": true,
        "nodes": [
            {"id": "end", "label": "Handover"},
            {"id": "second", "label": "Second work"}, {"id": "joinery", "label": "Joinery", "parent": "second"}, {"id": "wiring", "label": "Wiring", "parent": "second"},
            {"id": "shell", "label": "Shell"}, {"id": "base", "label": "Foundations", "parent": "shell"}, {"id": "walls", "label": "Walls", "parent": "shell"}, {"id": "roof", "label": "Roof", "parent": "shell"},
            {"id": "pause", "label": "Winter pause"},
            {"id": "finish", "label": "Finishing"}, {"id": "paint", "label": "Paint", "parent": "finish"}
        ],
        "edges": [
            {"from": "base", "to": "walls"}, {"from": "walls", "to": "roof"}, {"from": "joinery", "to": "wiring"},
            {"from": "shell", "to": "pause"}, {"from": "pause", "to": "second"}, {"from": "second", "to": "finish"}, {"from": "finish", "to": "end"},
            {"from": "walls", "to": "joinery", "label": "openings"}, {"from": "pause", "to": "finish", "line": "dotted"}
        ]
    }"""

    private fun DiagramScene.top(id: String) = hits.first { (it.second as? DiagramTouch.Box)?.node?.id == id || (it.second as? DiagramTouch.Group)?.id == id }.first.top

    @Test
    fun `parts stack in the arrows' order, the next part told by the order alone`() {
        val scene = scene(house)
        val tops = listOf("shell", "pause", "second", "finish", "end").map { scene.top(it) }
        assertEquals(tops.sorted(), tops)
        // Three arrows inside the phases, each cut one drawn as two halves, and the four told ones a short line each
        assertEquals(3 + 2 * 2 + 4, scene.heads().size)
    }

    @Test
    fun `the short line to the next part keeps clear of a letter hanging in the gap`() {
        val scene = scene(house)
        val pause = scene.hits.first { (it.second as? DiagramTouch.Box)?.node?.id == "pause" }.first
        val second = scene.groupArea("second")!!
        val letter = scene.hits.first { (it.second as? DiagramTouch.Ref)?.let { r -> scene.refArea(r.id)!!.top > pause.bottom && scene.refArea(r.id)!!.bottom < second.top } == true }.first
        // The head landing on Second work's top stands beside the letter, not under it
        val head = scene.heads().first { kotlin.math.abs(it.at.y + it.size / 2 - second.top) < 1f }
        assertTrue(head.at.x < letter.left || head.at.x > letter.right)
    }

    @Test
    fun `an arrow the order does not tell becomes the same letter at both ends, each touch giving the other`() {
        val scene = scene(house)
        val refs = scene.hits.mapNotNull { it.second as? DiagramTouch.Ref }
        assertEquals(listOf("A", "A", "B", "B"), refs.map { it.letter }.sorted())
        refs.forEach { ref -> assertEquals(ref.id, refs.single { it.id == ref.pair }.pair) }
        // The arrow's text stays on its first half, by Walls
        assertTrue("openings" in scene.labels())
        // Walls' letter inside the Shell's frame, Joinery's inside Second work's
        val walls = refs.first { it.letter == refs.first { r -> scene.refArea(r.id)!!.top < scene.top("pause") }.letter }
        assertTrue(scene.refArea(walls.id)!!.top < scene.top("pause"))
        assertTrue(scene.refArea(walls.pair)!!.top > scene.top("second"))
    }

    @Test
    fun `an arrow from outside into a part is cut, one to the stacked box itself is drawn`() {
        val scene = scene("""{
            "nodes": [{"id": "A", "label": "outside"}, {"id": "S", "label": "Stack", "parts": true},
                {"id": "P", "label": "first", "parent": "S"}, {"id": "Q", "label": "second", "parent": "S"}],
            "edges": [{"from": "A", "to": "S"}, {"from": "A", "to": "Q"}, {"from": "P", "to": "Q"}]
        }""")
        assertEquals(2, scene.hits.count { it.second is DiagramTouch.Ref })
        // A to the stack's frame, the cut arrow's two halves, and P to Q told by the order, a short line
        assertEquals(4, scene.heads().size)
        assertTrue(scene.top("P") < scene.top("Q"))
    }

    @Test
    fun `parts folded away leave their arrows to the folded box`() {
        val scene = scene(house, folded = setOf("shell", "second"))
        // Walls to Joinery now runs from the folded Shell to the folded Second work, skipping the pause: still cut
        assertEquals(4, scene.hits.count { it.second is DiagramTouch.Ref })
    }

    @Test
    fun `letters follow reading order, by the part each arrow starts in, whatever the config's order`() {
        // Crafting comes first on the page, though written last; its three arrows out take A, B, C
        val scene = scene("""{"parts": true,
            "nodes": [{"id": "magic", "label": "Magic"}, {"id": "focus", "label": "Focus", "parent": "magic"}, {"id": "bolt", "label": "Bolt", "parent": "magic"},
                {"id": "craft", "label": "Crafting"}, {"id": "smith", "label": "Smithing", "parent": "craft"}, {"id": "potions", "label": "Potions", "parent": "craft"},
                {"id": "sword", "label": "Sword"}],
            "edges": [{"from": "focus", "to": "bolt"}, {"from": "bolt", "to": "sword"}, {"from": "smith", "to": "sword"}, {"from": "potions", "to": "focus"}]}""")
        val outOf = { id: String ->
            val box = scene.hits.first { (it.second as? DiagramTouch.Box)?.node?.id == id }.first
            scene.hits.mapNotNull { it.second as? DiagramTouch.Ref }.filter { scene.refArea(it.id)!!.top > box.top }
                .minBy { scene.refArea(it.id)!!.top - box.bottom + kotlin.math.abs(scene.refArea(it.id)!!.left - box.left) }.letter
        }
        assertEquals(listOf("A", "B", "C"), listOf(outOf("smith"), outOf("potions"), outOf("bolt")))
    }

    @Test
    fun `on a wide tile a top level in parts fills it as a grid`() {
        val builder = DiagramSceneBuilder(1f, 1f, text, own)
        val column = builder.build(spec(house), { null }, emptySet(), 340f)
        val grid = builder.build(spec(house), { null }, emptySet(), 340f, aspect = 2f)
        assertTrue(grid.drawing.height < column.drawing.height)
        assertTrue(grid.drawing.width > column.drawing.width)
    }

    @Test
    fun `an empty schema draws nothing, without failing`() {
        assertTrue(scene("{}").drawing.shapes.isEmpty())
    }
}
