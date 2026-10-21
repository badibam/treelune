package app.treelune.tools.sequence

import android.app.NotificationManager
import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.scheduling.SchedulersHeld
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.utils.AppConfigManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime

/**
 * A session planned every day (SequenceScheduler): back from an absence, one entry « planned » for
 * each time passed, dated from it, one notification and the tool waiting on its tile; « Done » on
 * one marks it done at its planned time, « Start » takes the latest one, « Ignore all » the others.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SequenceScheduleTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val database get() = AppDatabase.getDatabase(context)
    private val day = 86_400_000L

    /** A pass of the scheduler as the core scheduler's tick runs it, as the app (CoreScheduler.tick). */
    private suspend fun tick() = withContext(Origin(Source.SCHEDULER)) { SequenceScheduler.checkScheduled(context) }

    /**
     * Passes run until [done] holds: the base and the schedulers outlive a test class, and a pass
     * an earlier test's write started may still hold the scheduler's lock, the one run here then
     * leaving without doing anything.
     */
    private suspend fun tickUntil(done: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            tick()
            if (done()) break
            delay(50)
        }
    }

    private fun status(entry: ToolDataEntity) = JSONObject(entry.state!!).getString(SequenceToolType.STATUS)
    /** This test's notifications: an earlier test's tools, still in the base, may be told of by a pass of theirs. */
    private fun shown() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        .count { it.extras.getString(android.app.Notification.EXTRA_TITLE) == "Intervals" }

    @Test
    fun `back from an absence, a planned entry for each time passed, done at its time, started, the rest ignored`() = runBlocking<Unit> {
        // The tick a tool's write launches in the background (ToolInstanceService) held off: the
        // scheduler runs here alone, when the test says
        SchedulersHeld.during {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Course"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "sequence",
            "config" to mapOf(
                "name" to "Intervals", "enabled" to true,
                "steps" to listOf(mapOf("kind" to "step", "name" to "Run", "end" to "timed", "duration" to 60_000)),
                "schedule" to mapOf("pattern" to mapOf("type" to "DailyMultiple", "times" to listOf("09:00")))
            )
        ))
        assertTrue(tool.error, tool.isSuccess)
        val toolId = tool.data!!["tool_instance_id"] as String
        // Made four days ago, the app not run since
        val instance = database.toolInstanceDao().getToolInstanceById(toolId)!!
        database.toolInstanceDao().updateToolInstance(instance.copy(created_at = System.currentTimeMillis() - 4 * day))

        tickUntil { database.toolDataDao().getByToolInstance(toolId).isNotEmpty() }
        val entries = database.toolDataDao().getByToolInstance(toolId).sortedBy { it.timestamp }
        assertTrue("one entry for each day passed: ${entries.size}", entries.size in 3..4)
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        entries.forEach { entry ->
            assertEquals(SequenceToolType.Status.PLANNED, status(entry))
            assertEquals("dated from the time due", LocalTime.of(9, 0), Instant.ofEpochMilli(entry.timestamp!!).atZone(timezone).toLocalTime())
        }
        assertEquals("one notification for the absence", 1, shown())
        val waiting = coordinator.processUserAction("tools.waiting", emptyMap())
        assertEquals("the tile's dot", entries.size, (waiting.data!!["tools"] as Map<*, *>)[toolId])

        // « Done » on the oldest: done, at its planned time
        val oldest = entries.first()
        val done = coordinator.processUserAction("sequence.complete", mapOf("tool_instance_id" to toolId, "id" to oldest.id))
        assertTrue(done.error, done.isSuccess)
        val marked = database.toolDataDao().getById(oldest.id)!!
        assertEquals(SequenceToolType.Status.DONE, status(marked))
        assertEquals(oldest.timestamp, marked.timestamp)

        // « Start » takes the latest planned one
        val started = coordinator.processUserAction("sequence.start", mapOf("tool_instance_id" to toolId))
        assertTrue(started.error, started.isSuccess)
        assertEquals(entries.last().id, started.data!!["id"])

        // « Ignore all »: the ones still planned
        val ignored = coordinator.processUserAction("sequence.ignore_all", mapOf("tool_instance_id" to toolId))
        assertTrue(ignored.error, ignored.isSuccess)
        val after = database.toolDataDao().getByToolInstance(toolId).associate { it.id to status(it) }
        assertEquals(SequenceToolType.Status.DONE, after[oldest.id])
        assertEquals(SequenceToolType.Status.RUNNING, after[entries.last().id])
        entries.drop(1).dropLast(1).forEach { assertEquals(SequenceToolType.Status.IGNORED, after[it.id]) }
        }
    }
}
