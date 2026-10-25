package app.treelune.tools.goal

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
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
 * « Launch now » (docs/TOOLS.md, « Goal »): an attempt opened at once, lasting the goal's
 * duration, without an end of its own when it has none; a launch while one is open sends that
 * one to be validated, its period ending there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalLaunchTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Health"))
        assertTrue(zone.error, zone.isSuccess)
        zoneId = zone.data!!["zone_id"] as String
    }

    /** A goal without a schedule, lasting [duration] when given: its id. */
    private suspend fun goal(duration: Long?): String {
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "goal",
            "config" to buildMap {
                put("name", "No sugar")
                put("criteria", listOf(mapOf(
                    "key" to "c_no_sugar", "name" to "No sugar",
                    "field" to mapOf("type" to "BOOLEAN"),
                    "condition" to mapOf("op" to "=", "right" to mapOf("constant" to true))
                )))
                duration?.let { put("duration", it) }
            }
        ))
        assertTrue(tool.error, tool.isSuccess)
        return tool.data!!["tool_instance_id"] as String
    }

    private suspend fun launch(toolId: String): String {
        val result = coordinator.processUserAction("goal.launch", mapOf("tool_instance_id" to toolId))
        assertTrue(result.error, result.isSuccess)
        return result.data!!["id"] as String
    }

    private suspend fun state(id: String) = JSONObject(AppDatabase.getDatabase(context).toolDataDao().getById(id)!!.state ?: "{}")

    @Test
    fun aLaunch_opensAnAttemptLastingTheDuration() = runBlocking {
        val week = 7 * 86_400_000L
        val before = System.currentTimeMillis()
        val id = launch(goal(week))

        assertEquals(GoalToolType.Status.ACTIVE, state(id).getString(GoalToolType.STATUS))
        // Its days counted on the calendar: a week on, give or take a change of the clock
        val end = state(id).getLong(GoalToolType.PERIOD_END)
        assertTrue(end - before in (week - 3_600_000)..(week + 3_600_000 + 60_000))
    }

    @Test
    fun withoutADuration_itHasNoEndOfItsOwn() = runBlocking {
        val id = launch(goal(null))

        assertEquals(GoalToolType.Status.ACTIVE, state(id).getString(GoalToolType.STATUS))
        assertFalse(state(id).has(GoalToolType.PERIOD_END))
    }

    @Test
    fun aLaunchWhileOneIsOpen_sendsItToBeValidated_itsPeriodEndingThere() = runBlocking {
        val toolId = goal(null)
        val first = launch(toolId)
        val second = launch(toolId)

        assertEquals(GoalToolType.Status.TO_VALIDATE, state(first).getString(GoalToolType.STATUS))
        val secondStart = AppDatabase.getDatabase(context).toolDataDao().getById(second)!!.timestamp!!
        assertEquals(secondStart, state(first).getLong(GoalToolType.PERIOD_END))
        assertEquals(GoalToolType.Status.ACTIVE, state(second).getString(GoalToolType.STATUS))
    }
}
