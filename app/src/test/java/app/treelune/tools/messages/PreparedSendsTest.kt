package app.treelune.tools.messages

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.ScheduleConfig
import app.treelune.core.utils.SchedulePattern
import app.treelune.tools.messages.scheduler.MessageScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A send prepared by hand (something written for that day) that a change of the recurrence
 * leaves outside its times is deleted only once the user agrees, told how many go and how many
 * stay; an empty one, which the recurrence makes again, goes unsaid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreparedSendsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val horizon = 2 * 86_400_000L

    private fun config(times: List<String>, enabled: Boolean = true) = mapOf(
        "name" to "Watering",
        "enabled" to enabled,
        "priority" to "default",
        "external_notifications" to true,
        "creation_horizon" to horizon,
        "schedule" to mapOf("pattern" to mapOf("type" to "DailyMultiple", "times" to times))
    )

    /**
     * A tool due daily at 09:00, its next two sends pending: the first written for, the second
     * empty. The tool is made suspended, so that no pass of its scheduler, which its creation runs
     * in the background, makes sends of its own; the two are made as the scheduler makes them, by
     * the app, and the changes below switch it back on.
     */
    private fun prepared(coordinator: Coordinator): Pair<String, List<String>> = runBlocking {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Balcony")).data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone, "tooltype" to "messages", "config" to config(listOf("09:00"), enabled = false)
        )).data!!["tool_instance_id"] as String
        val now = System.currentTimeMillis()
        val due = MessageScheduler.expectedOccurrences(ScheduleConfig(SchedulePattern.DailyMultiple(listOf("09:00"))), now, now + horizon, tool)
        assertEquals(2, due.size)
        val ids = due.map { at ->
            val created = coordinator.processScheduledTask("tool_data.create", mapOf(
                "tool_instance_id" to tool, "tooltype" to "messages", "name" to "Watering", "timestamp" to at,
                "data" to emptyMap<String, Any>(), "state" to mapOf("status" to "pending", "triggered_by" to "SCHEDULE")
            ))
            assertTrue(created.error, created.isSuccess)
            created.data!!["id"] as String
        }
        val written = coordinator.processUserAction("tool_data.update", mapOf("id" to ids[0], "data" to mapOf("content" to "The basil first")))
        assertTrue(written.error, written.isSuccess)
        tool to ids
    }

    @Test
    fun `a prepared send off the new times waits for agreement, then goes`() = runBlocking {
        val coordinator = Coordinator(context)
        val (tool, ids) = prepared(coordinator)

        val asked = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to tool, "config" to config(listOf("10:00"))))
        assertFalse(asked.isSuccess)
        val removals = ((asked.data!!["migration"] as Map<*, *>)["removals"] as List<*>).map { it.toString() }
        assertEquals(1, removals.size)
        // One prepared deleted, none kept: the empty send is not counted
        assertTrue(removals.single(), removals.single().startsWith("1 "))
        assertTrue(removals.single(), removals.single().endsWith("; 0 prepared kept"))
        assertTrue(coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to ids[0])).isSuccess)

        val agreed = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to tool, "config" to config(listOf("10:00")), "confirm_migration" to true))
        assertTrue(agreed.error, agreed.isSuccess)
        assertFalse(coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to ids[0])).isSuccess)
    }

    @Test
    fun `a change that keeps the prepared send's time asks nothing`() = runBlocking {
        val coordinator = Coordinator(context)
        val (tool, _) = prepared(coordinator)

        val kept = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to tool, "config" to config(listOf("09:00", "18:00"))))
        assertTrue(kept.error, kept.isSuccess)
    }
}
