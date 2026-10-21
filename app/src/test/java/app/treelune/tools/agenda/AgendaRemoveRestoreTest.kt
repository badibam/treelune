package app.treelune.tools.agenda

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
 * An occurrence of a series is removed, not deleted (AgendaService): it comes back under
 * « Retirées », and restore plans it again; an event of its own refuses the removal, it is deleted
 * instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgendaRemoveRestoreTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calendar"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"],
            "tooltype" to "agenda",
            "config" to mapOf("name" to "Calendar")
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
    }

    private suspend fun state(id: String): JSONObject {
        val entry = AppDatabase.getDatabase(context).toolDataDao().getById(id)!!
        return JSONObject(entry.state ?: "{}")
    }

    /** An event seeded as the series generation writes it: its status, and the series for an occurrence. */
    private suspend fun event(name: String, series: String?): String {
        val id = java.util.UUID.randomUUID().toString()
        val state = JSONObject().put(AgendaToolType.STATUS, AgendaToolType.Status.PLANNED).apply {
            if (series != null) { put(AgendaToolType.SERIES, series); put(AgendaToolType.SERIES_AT, System.currentTimeMillis()) }
        }
        AppDatabase.getDatabase(context).toolDataDao().insert(ToolDataEntity(
            id = id, toolInstanceId = toolId, tooltype = "agenda", timestamp = System.currentTimeMillis(),
            name = name, data = "{}", createdAt = 0, updatedAt = 0, state = state.toString()
        ))
        return id
    }

    @Test
    fun `an occurrence is removed and restored, a single event refuses removal`() = runBlocking {
        val occurrence = event("Dentist", "s_x")
        val removed = coordinator.processUserAction("agenda.remove", mapOf("id" to occurrence))
        assertTrue(removed.error, removed.isSuccess)
        assertEquals(AgendaToolType.Status.REMOVED, state(occurrence).getString(AgendaToolType.STATUS))

        val restored = coordinator.processUserAction("agenda.restore", mapOf("id" to occurrence))
        assertTrue(restored.error, restored.isSuccess)
        assertEquals(AgendaToolType.Status.PLANNED, state(occurrence).getString(AgendaToolType.STATUS))

        val single = event("Lunch", null)
        val refused = coordinator.processUserAction("agenda.remove", mapOf("id" to single))
        assertFalse(refused.isSuccess)
    }
}
