package app.treelune.tools.goal

import android.app.NotificationManager
import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.scheduling.SchedulersHeld
import app.treelune.core.database.entities.ToolDataEntity
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

/**
 * A goal recurring every day (GoalScheduler): one attempt a day; its period over, it is to be
 * validated, told once by a notification, and the day's opens beside it; left past the delay, it
 * expires; the goal stopped, none opens. A day passing is the attempts moved a day of the
 * calendar back, the same hour the day before: a day changing the clock lasts 23 or 25 hours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalRecurringTest {

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

    private suspend fun tick() = withContext(Origin(Source.SCHEDULER)) { GoalScheduler.checkScheduled(context) }

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

    private fun status(entry: ToolDataEntity) = JSONObject(entry.state!!).getString(GoalToolType.STATUS)
    /** This test's notifications: an earlier test's tools, still in the base, may be told of by a pass of theirs. */
    private fun shown() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        .count { it.extras.getString(android.app.Notification.EXTRA_TITLE) == "Run daily" }

    /** [entry] as if time had passed: its start and its period's end moved by [back]. */
    private suspend fun aged(entry: ToolDataEntity, back: (Long) -> Long) {
        val state = JSONObject(entry.state!!)
        state.optLong(GoalToolType.PERIOD_END).takeIf { state.has(GoalToolType.PERIOD_END) }?.let { state.put(GoalToolType.PERIOD_END, back(it)) }
        database.toolDataDao().update(entry.copy(timestamp = back(entry.timestamp!!), state = state.toString()))
    }

    /** [at] the same hour the day before, in the app's time zone. */
    private fun dayBefore(at: Long): Long {
        val zone = app.treelune.core.utils.AppConfigManager.getDateTimeConfig().getZoneId()
        return java.time.Instant.ofEpochMilli(at).atZone(zone).minusDays(1).toInstant().toEpochMilli()
    }

    @Test
    fun `one attempt a day, the one over to validate and told once, expired past the delay, none once stopped`() = runBlocking<Unit> {
        // The tick a tool's write launches in the background (ToolInstanceService) held off: the
        // scheduler runs here alone, when the test says
        SchedulersHeld.during {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Course"))
        assertTrue(zone.error, zone.isSuccess)
        zoneId = zone.data!!["zone_id"] as String
        val runs = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "tracking",
            "config" to mapOf("name" to "Runs", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>())
        ))
        assertTrue(runs.error, runs.isSuccess)
        val config = mutableMapOf<String, Any?>(
            "name" to "Run daily", "enabled" to true, "expiry_delay" to day,
            "schedule" to mapOf("pattern" to mapOf("type" to "DailyMultiple", "times" to listOf("06:00"))),
            "criteria" to listOf(mapOf(
                "key" to "c_sum", "name" to "Sum",
                "condition" to mapOf(
                    "left" to mapOf("reading" to mapOf(
                        "selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to runs.data!!["tool_instance_id"])),
                        "reduction" to "SUM", "field" to "data.value"
                    )),
                    "op" to ">=", "right" to mapOf("constant" to 1)
                )
            ))
        )
        val goal = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "goal", "config" to config))
        assertTrue(goal.error, goal.isSuccess)
        val goalId = goal.data!!["tool_instance_id"] as String
        val instance = database.toolInstanceDao().getToolInstanceById(goalId)!!
        database.toolInstanceDao().updateToolInstance(instance.copy(created_at = System.currentTimeMillis() - 3 * day))
        suspend fun attempts() = database.toolDataDao().getByToolInstance(goalId).sortedBy { it.timestamp }

        // The last day's attempt alone, the days before it not caught up
        tickUntil { attempts().isNotEmpty() }
        val first = attempts().single()
        assertEquals(GoalToolType.Status.ACTIVE, status(first))

        // A day later: the one over to validate, the day's opened beside it, one notification
        aged(first, ::dayBefore)
        tickUntil { attempts().size == 2 }
        val (over, today) = attempts().also { assertEquals(2, it.size) }
        assertEquals(first.id, over.id)
        assertEquals(GoalToolType.Status.TO_VALIDATE, status(over))
        assertEquals(GoalToolType.Status.ACTIVE, status(today))
        assertEquals(1, shown())
        tick()
        assertEquals("told once", 1, shown())

        // Left past the delay, a duration: expired
        aged(database.toolDataDao().getById(over.id)!!) { it - day }
        tickUntil { status(database.toolDataDao().getById(over.id)!!) != GoalToolType.Status.TO_VALIDATE }
        assertEquals(GoalToolType.Status.EXPIRED, status(database.toolDataDao().getById(over.id)!!))

        // Stopped: a day later, none opens
        val stopped = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to goalId, "config" to app.treelune.core.utils.JsonUtils.toMap(
                JSONObject(database.toolInstanceDao().getToolInstanceById(goalId)!!.config_json).put("enabled", false))))
        assertTrue(stopped.error, stopped.isSuccess)
        aged(database.toolDataDao().getById(today.id)!!, ::dayBefore)
        tick()
        assertEquals(2, attempts().size)
        }
    }
}
