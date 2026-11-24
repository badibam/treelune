package app.treelune.core.grid

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A page arranged by the AI (docs/design/ai-page-layout.md): the plan it reads, a whole page
 * written in one step, placed by the app, and every refusal leaving the page as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageArrangeTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    private suspend fun plan(zone: String) = ok("zones.page_plan", mapOf("zone_id" to zone))["plan"] as String

    private fun print(plan: String) = plan.lineSequence().first().removePrefix("plan ")

    /** The mark the line of the tile [name] gives it, in upper case as a command may write it. */
    private fun mark(plan: String, name: String) =
        Regex("""^([Tt]\d+) \w+ «${Regex.escape(name)}»""", RegexOption.MULTILINE).find(plan)!!.groupValues[1].uppercase()

    private suspend fun tool(zone: String, name: String, group: String?, size: String = "2x1") = ok("tools.create", mapOf(
        "zone_id" to zone, "tooltype" to "notes",
        "config" to buildMap { put("name", name); put("tile_size", size); group?.let { put("group", it) } }
    ))["tool_instance_id"] as String

    @Test
    fun thePlan_tellsAHalfTileFromAStripOfIcons() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Plan", "tool_groups" to listOf("A")))["zone_id"] as String
        tool(zone, "Half", "A")
        tool(zone, "Strip", "A", "icons_2x1")

        val read = plan(zone)
        assertTrue(read, Regex("""^T1 tool «Half» 2x1 """, RegexOption.MULTILINE).containsMatchIn(read))
        assertTrue(read, Regex("""^t2 tool «Strip» icons_2x1 """, RegexOption.MULTILINE).containsMatchIn(read))
        assertTrue(read, read.contains(" T1  T1  t2  t2") || read.contains(" T1  T1   .   .\n t2  t2"))
    }

    @Test
    fun aPage_isArrangedInOrder_itsLooksChanged_aSectionCreated_andUndoneInOneStep() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Arranged", "tool_groups" to listOf("A", "B")))["zone_id"] as String
        tool(zone, "First", "A")
        tool(zone, "Second", "A")
        tool(zone, "Third", "B", "4x1")
        val before = plan(zone)

        val after = ok("zones.arrange", mapOf(
            "zone_id" to zone, "plan" to print(before),
            "section_order" to listOf("New", "A", "B"),
            "sections" to listOf(
                mapOf("name" to "New", "tiles" to listOf(mapOf("tile" to mark(before, "Second"), "tile_size" to "icons_2x1", "icon_color" to "RED"))),
                mapOf("name" to "A", "tiles" to listOf(mark(before, "First")))
            )
        ))["plan"] as String

        assertEquals("New", sectionOf(after, "Second"))
        assertEquals("A", sectionOf(after, "First"))
        assertEquals("B", sectionOf(after, "Third"))
        assertTrue(after, Regex("""^t\d+ tool «Second» icons_2x1 \S+ RED""", RegexOption.MULTILINE).containsMatchIn(after))
        assertEquals(listOf("New", "A", "B"), Regex("""^## (.+)$""", RegexOption.MULTILINE).findAll(after).map { it.groupValues[1] }.toList())

        val step = (ok("history.list", mapOf("limit" to 1))["steps"] as List<*>).single() as Map<*, *>
        ok("history.undo", mapOf("id" to step["id"]))
        assertEquals(before, plan(zone))
    }

    /** The section the plan shows the tile [name] in. */
    private fun sectionOf(plan: String, name: String): String? {
        var section: String? = null
        for (line in plan.lineSequence()) {
            if (line.startsWith("## ")) section = line.removePrefix("## ")
            if ("«$name»" in line) return section
        }
        return null
    }

    @Test
    fun aRefusal_leavesThePageAsItWas() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Refused", "tool_groups" to listOf("A", "B")))["zone_id"] as String
        tool(zone, "One", "A")
        tool(zone, "Two", "A")
        tool(zone, "Other", "B")
        val before = plan(zone)
        val print = print(before)
        val one = mark(before, "One")
        val two = mark(before, "Two")
        val other = mark(before, "Other")
        fun section(name: String?, vararg tiles: Any) = mapOf("name" to name, "tiles" to tiles.toList())

        val refused = listOf<Map<String, Any?>>(
            // Its page changed since
            mapOf("plan" to "000000", "sections" to listOf(section("A", two, one))),
            // A listed section given without one of its tiles
            mapOf("plan" to print, "sections" to listOf(section("A", one))),
            // A tile leaving a section that is not listed
            mapOf("plan" to print, "sections" to listOf(section("A", one, two, other))),
            // A tile named twice, a mark that names none
            mapOf("plan" to print, "sections" to listOf(section("A", one, one, two))),
            mapOf("plan" to print, "sections" to listOf(section("A", one, two, "T99"))),
            // A strip longer than a pair, which only a zone takes
            mapOf("plan" to print, "sections" to listOf(section("A", mapOf("tile" to one, "tile_size" to "icons_4x1"), two))),
            // A colour that does not exist, after a change that would have been valid
            mapOf("plan" to print, "sections" to listOf(section("A", mapOf("tile" to two, "icon_color" to "RED"), mapOf("tile" to one, "icon_color" to "NOPE")))),
            // An order that forgets a section
            mapOf("plan" to print, "section_order" to listOf("A"))
        )
        for (params in refused) {
            val result = coordinator.processUserAction("zones.arrange", params + ("zone_id" to zone))
            assertFalse("accepted: $params", result.isSuccess)
            assertEquals(before, plan(zone))
        }
    }

    @Test
    fun aSectionEmptied_isRemoved_itsTilesGivenElsewhere() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Emptied", "tool_groups" to listOf("A", "B")))["zone_id"] as String
        tool(zone, "Moved", "A")
        tool(zone, "Stays", "B")
        val before = plan(zone)

        val after = ok("zones.arrange", mapOf("zone_id" to zone, "plan" to print(before), "sections" to listOf(
            mapOf("name" to "A", "tiles" to emptyList<Any>()),
            mapOf("name" to "B", "tiles" to listOf(mark(before, "Stays"), mark(before, "Moved")))
        )))["plan"] as String

        assertEquals(listOf("B"), Regex("""^## (.+)$""", RegexOption.MULTILINE).findAll(after).map { it.groupValues[1] }.toList())
        assertEquals("B", sectionOf(after, "Moved"))
    }

    @Test
    fun theHomeScreen_isArranged_itsZonesInANewGroup() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Homed"))["zone_id"] as String
        val before = ok("zones.page_plan", mapOf("home" to true))["plan"] as String
        val ungrouped = before.substringAfter("## (ungrouped)\n").lineSequence()
            .mapNotNull { Regex("""^([Tt]\d+) zone «""").find(it)?.groupValues?.get(1)?.uppercase() }.toList()
        val homed = mark(before, "Homed")

        val after = ok("zones.arrange", mapOf("home" to true, "plan" to print(before), "sections" to listOf(
            mapOf("name" to "Arranged home", "tiles" to listOf(mapOf("tile" to homed, "tile_size" to "icons_4x1"))),
            mapOf("name" to null, "tiles" to ungrouped.filter { it != homed })
        )))["plan"] as String

        assertEquals("Arranged home", sectionOf(after, "Homed"))
        assertTrue(after, Regex("""^t\d+ zone «Homed» icons_4x1""", RegexOption.MULTILINE).containsMatchIn(after))
        assertEquals("Arranged home", (ok("zones.get", mapOf("zone_id" to zone))["zone"] as Map<*, *>)["group"])
    }
}
