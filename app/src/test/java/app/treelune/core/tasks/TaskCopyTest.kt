package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A Task copied from a section's « Copy »: into the zone asked, « X (copy) », with its settings
 * and its steps, without its runs, and off — on, a copy with a schedule or a trigger would run
 * as a double of the original.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskCopyTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val coordinator = Coordinator(context)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    @Test
    fun aCopy_takesSettingsAndSteps_bornOff_withoutRuns() = runBlocking {
        val from = ok("zones.create", mapOf("name" to "Balcony"))["zone_id"] as String
        val to = ok("zones.create", mapOf("name" to "Work"))["zone_id"] as String
        val toolId = ok("tools.create", mapOf("zone_id" to from, "tooltype" to "questionnaire",
            "config" to mapOf("name" to "Feeling", "notify" to false)))["tool_instance_id"] as String
        val steps = listOf(mapOf(TaskSteps.KIND to TaskSteps.TOOL_OPERATION, TaskSteps.TOOL_INSTANCE_ID to toolId, TaskSteps.OPERATION to "invite", TaskSteps.PARAMS to emptyMap<String, Any>()))
        val source = ok("tasks.create", mapOf("zone_id" to from, "name" to "Heatwave", "notify_end" to "always", "steps" to steps))["task_id"] as String

        val copyId = ok("tasks.duplicate", mapOf("task_id" to source, "target_zone_id" to to, "target_group" to ""))["task_id"] as String

        val dao = AppDatabase.getDatabase(context).taskDao()
        val original = dao.get(source)!!
        val copy = dao.get(copyId)!!
        assertEquals(to, copy.zoneId)
        assertTrue(copy.name, copy.name.contains("Heatwave") && copy.name != "Heatwave")
        assertEquals("always", copy.notifyEnd)
        assertEquals(JSONArray(original.stepsJson).toString(), JSONArray(copy.stepsJson).toString())
        assertTrue(original.isEnabled)
        assertFalse(copy.isEnabled)
        assertTrue(dao.getRuns(copyId, 1).isEmpty())
    }
}
