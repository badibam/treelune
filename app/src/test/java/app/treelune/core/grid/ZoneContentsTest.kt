package app.treelune.core.grid

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What a zone holds, as the AI reads it (ZONE_CONTENTS): its sections in the zone's order, the
 * ungrouped last, every tile of each — tools and variables together — in reading order, with
 * its kind, size and look; its events; the zones in the home screen's order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoneContentsTest {

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

    @Test
    fun aZone_givesItsSectionsInOrder_eachTileInReadingOrder_andItsEvents() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Body", "tool_groups" to listOf("Today", "Library")))["zone_id"] as String
        ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Shelf", "group" to "Library", "icon_name" to "book", "icon_color" to "BLUE")))
        ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Plan", "group" to "Today")))
        ok("variables.create", mapOf("zone_id" to zone, "name" to "goal_contents", "label" to "Goal", "group" to "Today",
            "definition" to mapOf("kind" to "CONSTANT", "value" to 2, "field" to mapOf("type" to "NUMERIC"))))
        ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Loose")))
        ok("events.create", mapOf("zone_id" to zone, "name" to "Ready"))

        val read = (ok("zones.contents", mapOf("zone_id" to zone))["zones"] as List<*>).single() as Map<*, *>
        val sections = read["sections"] as List<*>
        assertEquals(listOf("Today", "Library", null), sections.map { (it as Map<*, *>)["group"] })
        fun tiles(i: Int) = ((sections[i] as Map<*, *>)["tiles"] as List<*>).map { it as Map<*, *> }
        assertEquals(listOf("tool" to "Plan", "variable" to "Goal"), tiles(0).map { it["kind"] to it["name"] })
        assertEquals("goal_contents", tiles(0)[1]["variable_name"])
        val shelf = tiles(1).single()
        assertEquals("notes", shelf["tooltype"])
        assertEquals("book", shelf["icon_name"])
        assertEquals("BLUE", shelf["icon_color"])
        assertEquals(listOf("Loose"), tiles(2).map { it["name"] })
        assertEquals(listOf("Ready"), (read["events"] as List<*>).map { (it as Map<*, *>)["name"] })
    }

    @Test
    fun theZones_comeInTheHomeScreensOrder() = runBlocking {
        ok("app_config.set", mapOf("category" to "main_screen", "settings" to mapOf("zone_groups" to listOf("First", "Second"))))
        ok("zones.create", mapOf("name" to "Loose"))
        ok("zones.create", mapOf("name" to "Second one", "group" to "Second"))
        ok("zones.create", mapOf("name" to "First one", "group" to "First"))
        // Among the zones the test database already holds, those made here
        val made = setOf("First one", "Second one", "Loose")
        val names = (ok("zones.contents", emptyMap())["zones"] as List<*>).map { (it as Map<*, *>)["name"] }.filter { it in made }
        assertEquals(listOf("First one", "Second one", "Loose"), names)
        assertEquals(names, (ok("zones.list", emptyMap())["zones"] as List<*>).map { (it as Map<*, *>)["name"] }.filter { it in made })
    }
}
