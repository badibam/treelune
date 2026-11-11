package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.ai.data.RoutineMessage
import app.treelune.core.ai.data.SessionEndReason
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs over and not seen yet (docs/BRICKS.md, « Waiting »), a Task's and a routine's: counted in
 * their zone's mark, seen one at a time without opening them, or all at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaitingRunsTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Health"))["zone_id"] as String
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data.orEmpty()
    }

    /** A Task of the zone with [waiting] runs over and not seen, and one seen. */
    private suspend fun taskWithRuns(waiting: Int): Pair<String, List<String>> {
        val notesId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Notes")))["tool_instance_id"] as String
        val taskId = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Note", "steps" to listOf(mapOf(
            "kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId,
            "values" to mapOf("data.content" to mapOf("constant" to "hi"))
        ))))["task_id"] as String
        val dao = AppDatabase.getDatabase(context).taskDao()
        val ids = (0..waiting).map { i ->
            val id = "run-$taskId-$i"
            dao.insertRun(TaskRunEntity(id, taskId, "manual", i.toLong(), i.toLong(), i.toLong() + 1, TaskRun.State.DONE.key, 1, "[]", null,
                seenAt = if (i == waiting) 1L else null))
            id
        }
        return taskId to ids.dropLast(1)
    }

    /** A routine of the zone with [waiting] runs over and not seen. */
    private suspend fun routineWithRuns(waiting: Int): String {
        val routineId = ok("routines.create", mapOf(
            "zone_id" to zoneId, "name" to "Weekly", "identity_id" to null, "provider_id" to null, "is_enabled" to false,
            "message" to listOf(mapOf("type" to RoutineMessage.TEXT, "text" to "hi"))
        ))["routine_id"] as String
        val dao = AppDatabase.getDatabase(context).aiDao()
        repeat(waiting) {
            val sessionId = ok("ai_sessions.create_session", mapOf("name" to "Weekly", "type" to "ROUTINE_RUN", "routine_id" to routineId))["session_id"] as String
            dao.updateSession(dao.getSession(sessionId)!!.copy(isActive = false, endReason = SessionEndReason.COMPLETED.name, seenAt = null))
        }
        return routineId
    }

    private suspend fun zoneCount(resource: String): Int =
        ((ok("$resource.waiting", mapOf("zone_id" to zoneId))["zones"] as Map<*, *>)[zoneId] as? Number)?.toInt() ?: 0

    @Test
    fun aTasksRunsWaiting_countInItsZone_andAreSeenOneOrAllAtOnce() = runBlocking {
        val (taskId, waiting) = taskWithRuns(3)
        assertEquals(3, zoneCount("tasks"))

        ok("tasks.seen", mapOf("run_id" to waiting.first()))
        assertEquals(2, zoneCount("tasks"))

        assertEquals(2, ok("tasks.seen_all", mapOf("task_id" to taskId))["seen"])
        assertEquals(0, zoneCount("tasks"))
    }

    @Test
    fun aRoutinesRunsWaiting_areCountedOnItsPage_andSeenAllAtOnce() = runBlocking {
        val routineId = routineWithRuns(3)
        assertEquals(3, zoneCount("routines"))
        assertEquals(3, ok("ai_sessions.list_sessions_for_routine", mapOf("routine_id" to routineId))["waiting"])

        assertEquals(3, ok("ai_sessions.seen_all", mapOf("routine_id" to routineId))["seen"])
        assertEquals(0, zoneCount("routines"))
        assertEquals(0, ok("ai_sessions.list_sessions_for_routine", mapOf("routine_id" to routineId))["waiting"])
    }
}
