package app.treelune.core.grid

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A tile crossed into another section in edit mode, written by zones.place_section and zones.place
 * with `sections`: its group becomes that section's, none for the ungrouped one, each section laid
 * out at once; a placement that leaves a section's tile unnamed writes nothing; one undo puts it
 * all back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SectionCrossingServiceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)
    private val database get() = AppDatabase.getDatabase(context)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    private fun place(x: Int, y: Int) = mapOf("grid_x" to x, "grid_y" to y)

    private suspend fun lastStep(): String {
        @Suppress("UNCHECKED_CAST")
        return ((ok("history.list", mapOf("limit" to 1))["steps"] as List<Map<String, Any?>>).single()["id"] as String)
    }

    @Test
    fun `a tool crosses into the next group, a variable into the ungrouped section, undone at once`() = runBlocking<Unit> {
        val zone = ok("zones.create", mapOf("name" to "Crossing", "tool_groups" to listOf("A", "B")))["zone_id"] as String
        val note = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Note", "group" to "A", "display_mode" to "ICON")))["tool_instance_id"] as String
        val other = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "notes", "config" to mapOf("name" to "Other", "group" to "B", "display_mode" to "ICON")))["tool_instance_id"] as String
        val variable = ok("variables.create", mapOf("zone_id" to zone, "name" to "crossing_count", "label" to "Count", "group" to "B", "display_mode" to "ICON",
            "definition" to mapOf("kind" to "CONSTANT", "value" to 2, "field" to mapOf("type" to "NUMERIC"))))["variable_id"] as String

        // The note down from A into the top of B, the variable down from B into the ungrouped section
        ok("zones.place_section", mapOf("zone_id" to zone, "sections" to listOf(
            mapOf("group" to "A", "places" to emptyMap<String, Any>()),
            mapOf("group" to "B", "places" to mapOf(note to place(0, 0), other to place(0, 1))),
            mapOf("group" to "", "places" to mapOf(variable to place(0, 0)))
        )))
        val moved = database.toolInstanceDao().getToolInstanceById(note)!!
        assertEquals("B", JSONObject(moved.config_json).getString("group"))
        assertEquals(0 to 0, moved.grid_x to moved.grid_y)
        assertEquals(0 to 1, database.toolInstanceDao().getToolInstanceById(other)!!.let { it.grid_x to it.grid_y })
        assertNull(database.variableDao().getById(variable)!!.group)

        // One undo puts both back in their groups
        assertTrue(coordinator.processUserAction("history.undo", mapOf("id" to lastStep())).isSuccess)
        assertEquals("A", JSONObject(database.toolInstanceDao().getToolInstanceById(note)!!.config_json).getString("group"))
        assertEquals("B", database.variableDao().getById(variable)!!.group)

        // A section whose places leave out one of its tiles: nothing written, its tile not regrouped
        val refused = coordinator.processUserAction("zones.place_section", mapOf("zone_id" to zone, "sections" to listOf(
            mapOf("group" to "A", "places" to emptyMap<String, Any>()),
            mapOf("group" to "B", "places" to mapOf(note to place(0, 0)))
        )))
        assertFalse(refused.isSuccess)
        assertEquals("A", JSONObject(database.toolInstanceDao().getToolInstanceById(note)!!.config_json).getString("group"))
    }

    @Test
    fun `a zone crosses into another group of the home screen`() = runBlocking<Unit> {
        val held = ((ok("app_config.get", mapOf("category" to "main_screen"))["settings"] as Map<*, *>)["zone_groups"] as? List<*>).orEmpty()
            .map { it as String }.filter { it != "Near" && it != "Far" }
        ok("app_config.set", mapOf("category" to "main_screen", "settings" to mapOf("zone_groups" to held + listOf("Near", "Far"))))
        val near = ok("zones.create", mapOf("name" to "Near one", "group" to "Near"))["zone_id"] as String
        val farZones = database.zoneDao().getAllZones().filter { it.group == "Far" }.map { it.id }

        ok("zones.place", mapOf("sections" to listOf(
            mapOf("group" to "Near", "places" to database.zoneDao().getAllZones().filter { it.group == "Near" && it.id != near }
                .let { rest -> app.treelune.core.grid.Grid.leave(rest.map { ZonePositions.tile(it) }, near) }.associate { it.id to place(it.column, it.row) }),
            mapOf("group" to "Far", "places" to (farZones.associateWith { id -> database.zoneDao().getZoneById(id)!!.let { place(it.grid_x, it.grid_y) } } +
                (near to place(0, farZones.maxOfOrNull { id -> database.zoneDao().getZoneById(id)!!.let { it.grid_y + 1 } } ?: 0))))
        )))
        assertEquals("Far", database.zoneDao().getZoneById(near)!!.group)
    }
}
