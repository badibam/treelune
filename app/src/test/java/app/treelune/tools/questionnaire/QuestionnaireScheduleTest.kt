package app.treelune.tools.questionnaire

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
import org.junit.After
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
 * A questionnaire planned every day (QuestionnaireScheduler): back from an absence, one entry « to
 * fill » for each time passed, dated from it, and one notification; an answer given is kept while
 * the entry stays to fill; « Ignore all » makes every one ignored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionnaireScheduleTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val database get() = AppDatabase.getDatabase(context)
    private val day = 86_400_000L

    /** A pass of the scheduler as the core scheduler's tick runs it, as the app (CoreScheduler.tick). */
    /** The zone of this test, deleted after it with its tools: scheduled, a later test's tick would act on them. */
    private var zoneId: String? = null

    @After
    fun tearDown() = runBlocking<Unit> {
        zoneId?.let {
            val deleted = Coordinator(context).processUserAction("zones.delete", mapOf("zone_id" to it))
            assertTrue(deleted.error, deleted.isSuccess)
        }
    }

    private suspend fun tick() = withContext(Origin(Source.SCHEDULER)) { QuestionnaireScheduler.checkScheduled(context) }

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

    private fun status(entry: ToolDataEntity) = JSONObject(entry.state!!).getString(QuestionnaireToolType.STATUS)
    /** This test's notifications: an earlier test's tools, still in the base, may be told of by a pass of theirs. */
    private fun shown() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        .count { it.extras.getString(android.app.Notification.EXTRA_TITLE) == "Evening" }

    @Test
    fun `back from an absence, an entry to fill for each time passed, one notification, all ignored at once`() = runBlocking<Unit> {
        // The tick a tool's write launches in the background (ToolInstanceService) held off: the
        // scheduler runs here alone, when the test says
        SchedulersHeld.during {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Italian"))
        assertTrue(zone.error, zone.isSuccess)
        zoneId = zone.data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "questionnaire",
            "config" to mapOf(
                "name" to "Evening", "enabled" to true,
                "schedule" to mapOf("pattern" to mapOf("type" to "DailyMultiple", "times" to listOf("09:00"))),
                "extra_fields" to listOf(
                    mapOf("name" to "words", "display_name" to "Words", "type" to "NUMERIC", "always_visible" to false, "config" to mapOf("decimals" to 0)),
                    mapOf("name" to "phrase", "display_name" to "Phrase", "type" to "TEXT", "always_visible" to false)
                )
            )
        ))
        assertTrue(tool.error, tool.isSuccess)
        val toolId = tool.data!!["tool_instance_id"] as String
        // Made four days ago, the app not run since
        val instance = database.toolInstanceDao().getToolInstanceById(toolId)!!
        database.toolInstanceDao().updateToolInstance(instance.copy(created_at = System.currentTimeMillis() - 4 * day))

        tickUntil { database.toolDataDao().getByToolInstance(toolId).isNotEmpty() }
        val entries = database.toolDataDao().getByToolInstance(toolId)
        assertTrue("one entry for each day passed: ${entries.size}", entries.size in 3..4)
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()
        entries.forEach { entry ->
            assertEquals(QuestionnaireToolType.Status.TO_FILL, status(entry))
            assertEquals("dated from the time due", LocalTime.of(9, 0), Instant.ofEpochMilli(entry.timestamp!!).atZone(timezone).toLocalTime())
        }
        assertEquals("one notification for the absence", 1, shown())

        // Another pass creates nothing again
        tick()
        assertEquals(entries.size, database.toolDataDao().getByToolInstance(toolId).size)

        // The first question answered, then left: the answer stays, the entry still to fill
        val latest = entries.maxBy { it.timestamp!! }
        val answered = coordinator.processUserAction("tool_data.update", mapOf("id" to latest.id, "extra" to mapOf("words" to 12)))
        assertTrue(answered.error, answered.isSuccess)
        val kept = database.toolDataDao().getById(latest.id)!!
        assertEquals(12, JSONObject(kept.extra!!).getInt("words"))
        assertEquals(QuestionnaireToolType.Status.TO_FILL, status(kept))

        val ignored = coordinator.processUserAction("questionnaire.ignore_all", mapOf("tool_instance_id" to toolId))
        assertTrue(ignored.error, ignored.isSuccess)
        assertTrue(database.toolDataDao().getByToolInstance(toolId).all { status(it) == QuestionnaireToolType.Status.IGNORED })
        }
    }
}
