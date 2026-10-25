package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.delay
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
 * What an active tool does now, beside its schedule, for a Task's step to call (docs/CORE.md, « Triggers »):
 * a questionnaire invites (an entry to fill, dated now), a sequence plans a session (an entry
 * planned, dated now) — the « when? » being the Task's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveToolsNowTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Body"))["zone_id"] as String
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    private suspend fun status(entryId: String): String = JSONObject(AppDatabase.getDatabase(context).toolDataDao().getById(entryId)!!.state!!).getString("status")

    @Test
    fun aQuestionnaire_invitesNow() = runBlocking {
        val toolId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "questionnaire",
            "config" to mapOf("name" to "After yoga", "notify" to false)))["tool_instance_id"] as String
        val before = System.currentTimeMillis()
        val id = ok("questionnaire.invite", mapOf("tool_instance_id" to toolId))["id"] as String

        assertEquals("to_fill", status(id))
        assertTrue(AppDatabase.getDatabase(context).toolDataDao().getById(id)!!.timestamp!! >= before)
    }

    @Test
    fun aSequence_plansASessionNow() = runBlocking {
        val toolId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "sequence",
            "config" to mapOf("name" to "Yoga", "notify" to false, "steps" to listOf(mapOf("kind" to "step", "name" to "Tree", "length" to "none", "auto_next" to false)))))["tool_instance_id"] as String
        val id = ok("sequence.plan", mapOf("tool_instance_id" to toolId))["id"] as String

        assertEquals("planned", status(id))
    }

    @Test
    fun aTasksStep_invitesThroughTheToolsOperation() = runBlocking {
        val toolId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "questionnaire",
            "config" to mapOf("name" to "After yoga", "notify" to false)))["tool_instance_id"] as String
        val taskId = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Invite", "steps" to listOf(mapOf(
            TaskSteps.KIND to TaskSteps.TOOL_OPERATION, TaskSteps.TOOL_INSTANCE_ID to toolId, TaskSteps.OPERATION to "invite", TaskSteps.PARAMS to emptyMap<String, Any>()
        ))))["task_id"] as String
        ok("tasks.run", mapOf("task_id" to taskId))

        repeat(100) {
            if (AppDatabase.getDatabase(context).toolDataDao().getByToolInstance(toolId).isNotEmpty()) return@runBlocking
            delay(50)
        }
        throw AssertionError("no invitation written: ${AppDatabase.getDatabase(context).taskDao().getRuns(taskId, 1).firstOrNull()?.resultsJson}")
    }
}
