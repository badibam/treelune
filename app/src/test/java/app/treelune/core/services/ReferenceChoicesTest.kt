package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The browser's first level (references.choices at the app): the zones with the home screen's
 * groups and their places, which it shows them by; for an event alone, only the zones that have
 * one, with their events, chosen flat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReferenceChoicesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val coordinator get() = Coordinator(context)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data ?: emptyMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun rows(result: Map<String, Any?>, key: String) = result[key] as List<Map<String, Any?>>

    @Test
    fun `the zones come with the home screen's groups, and an event's with their events alone`() = runBlocking {
        app.treelune.core.grid.HomeGroups { action, params -> ok(action, JsonUtils.toMap(params)) }.add(listOf("Health"))
        val running = ok("zones.create", mapOf("name" to "Running", "group" to "Health"))["zone_id"] as String
        val kitchen = ok("zones.create", mapOf("name" to "Kitchen"))["zone_id"] as String
        val ready = ok("events.create", mapOf("zone_id" to kitchen, "name" to "Dinner ready"))["event_id"] as String

        val variables = ok("references.choices", mapOf("kinds" to listOf("ZONE")))
        assertTrue("Health" in (variables["groups"] as List<*>))
        assertEquals("Health", rows(variables, "zones").single { it["id"] == running }["group"])
        assertEquals(null, rows(variables, "zones").single { it["id"] == kitchen }["group"])

        val events = ok("references.choices", mapOf("kinds" to listOf("EVENT")))
        val zones = rows(events, "zones").map { it["id"] }
        assertTrue(kitchen in zones)
        assertTrue(running !in zones)
        assertEquals(kitchen, rows(events, "events").single { it["id"] == ready }["zone_id"])
    }
}
