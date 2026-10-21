package app.treelune.tools.messages

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.messages.scheduler.MessageScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A Messages template whose stored recurrence no longer reads (StoredSchedule.Unreadable, a
 * switch the schedule no longer has): the scheduler creates and deletes nothing, the occurrences
 * already pending are kept and go out at their time, and the config is not saved over it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessagesUnreadableScheduleTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val database get() = AppDatabase.getDatabase(context)

    private suspend fun occurrences(toolId: String) = database.toolDataDao().getByToolInstance(toolId)
    private fun status(entry: app.treelune.core.database.entities.ToolDataEntity) = JSONObject(entry.state!!).getString("status")

    /** A pass of the scheduler as the core scheduler's tick runs it, as the app (CoreScheduler.tick). */
    private suspend fun tick() = withContext(Origin(Source.SCHEDULER)) { MessageScheduler.checkScheduled(context) }

    @Test
    fun `an unreadable recurrence keeps what is pending, sends it at its time, and is not saved over`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Home"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "messages",
            "config" to mapOf(
                "name" to "Water", "common_title" to "Water the plants", "enabled" to true,
                "schedule" to mapOf("pattern" to mapOf("type" to "DailyMultiple", "times" to listOf("09:00", "18:00")))
            )
        ))
        assertTrue(tool.error, tool.isSuccess)
        val toolId = tool.data!!["tool_instance_id"] as String

        tick()
        val pending = occurrences(toolId)
        assertTrue("the recurrence created its occurrences", pending.isNotEmpty())
        assertTrue(pending.all { status(it) == "pending" })

        // The recurrence written unreadable in the base, as an older version could have left it
        val instance = database.toolInstanceDao().getToolInstanceById(toolId)!!
        val broken = JSONObject(instance.config_json).apply { getJSONObject("schedule").put("enabled", true) }
        database.toolInstanceDao().updateToolInstance(instance.copy(config_json = broken.toString()))

        tick()
        assertEquals("nothing created or deleted", pending.map { it.id }.toSet(), occurrences(toolId).map { it.id }.toSet())

        // One of them come due: it goes out
        val first = pending.minBy { it.timestamp!! }
        database.toolDataDao().update(first.copy(timestamp = System.currentTimeMillis() - 60_000))
        tick()
        assertEquals("sent", status(occurrences(toolId).single { it.id == first.id }))

        // Saved as it stands, the config is refused and stays as it was
        val saved = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to toolId, "config" to JsonUtils.toMap(broken)))
        assertFalse(saved.isSuccess)
        assertEquals(broken.toString(), database.toolInstanceDao().getToolInstanceById(toolId)!!.config_json)
    }
}
