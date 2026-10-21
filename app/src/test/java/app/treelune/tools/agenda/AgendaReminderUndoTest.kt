package app.treelune.tools.agenda

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A reminder already sent is not sent again: undoing the rename of the event brings the old name
 * back while the reminder's mark stays — the mark is the app's own write, which an undo leaves as
 * it is (docs/DATA.md, « History of writes »).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgendaReminderUndoTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calendar"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "agenda", "config" to mapOf("name" to "Calendar")
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
    }

    private suspend fun state(id: String): JSONObject {
        val entry = database.toolDataDao().getById(id)!!
        return JSONObject(entry.state ?: "{}")
    }

    private suspend fun seed(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        database.toolDataDao().insert(ToolDataEntity(
            id = id, toolInstanceId = toolId, tooltype = "agenda", timestamp = System.currentTimeMillis(),
            name = name, data = "{}", createdAt = 0, updatedAt = 0,
            state = """{"status": "planned"}"""
        ))
        return id
    }

    private suspend fun lastStep(): String {
        val listed = coordinator.processUserAction("history.list", mapOf("limit" to 1))
        assertTrue(listed.error, listed.isSuccess)
        @Suppress("UNCHECKED_CAST")
        return ((listed.data!!["steps"] as List<Map<String, Any?>>).single()["id"] as String)
    }

    @Test
    fun `undoing the rename brings the old name back and the reminder is not sent again`() = runBlocking {
        val id = seed("Dentist")

        assertTrue(coordinator.processUserAction("tool_data.update", mapOf("id" to id, "name" to "Doctor")).isSuccess)

        val now = System.currentTimeMillis()
        val entry = database.toolDataDao().getById(id)!!
        database.toolDataDao().update(entry.copy(state = JSONObject(entry.state ?: "{}")
            .put(AgendaToolType.REMINDED_FOR, now).put(AgendaToolType.REMINDED_UNTIL, now).toString()))
        assertTrue("the mark is written before the undo", state(id).has(AgendaToolType.REMINDED_FOR))

        assertTrue(coordinator.processUserAction("history.undo", mapOf("id" to lastStep())).isSuccess)

        assertEquals("Dentist", database.toolDataDao().getById(id)!!.name)
        assertTrue("the reminder is still marked sent", state(id).has(AgendaToolType.REMINDED_FOR))
    }
}
