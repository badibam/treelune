package app.treelune.core.triggers

import android.content.Context
import app.treelune.core.coordinator.ChainLink
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskRun
import app.treelune.core.tasks.TaskRunEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A Task started by a condition become true (docs/CORE.md, « Triggers »): from false to true
 * only, its first judgement starting nothing; unknown keeps the verdict; a failure is noted
 * and keeps it too; a write in a tool it does not read judges nothing; the tick judges all; a
 * write inside a run continues its chain; the run receives the values read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConditionTriggersTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String
    private lateinit var weightId: String
    private lateinit var notesId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Health"))["zone_id"] as String
        weightId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("decimals" to 1), "units" to listOf("kg"))))["tool_instance_id"] as String
        notesId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Notes")))["tool_instance_id"] as String
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    /** "The last weight > 80". */
    private fun lastWeightAbove80() = mapOf(
        "kind" to Triggers.CONDITION,
        "condition" to mapOf(
            "left" to mapOf("reading" to mapOf(
                "selection" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to weightId)),
                "reduction" to "LAST", "field" to "data.value"
            )),
            "op" to ">", "right" to mapOf("constant" to 80)
        )
    )

    /** A Task without steps listening to "the last weight > 80": its id. */
    private suspend fun alert(): String =
        ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Too heavy", "triggers" to listOf(lastWeightAbove80()), "steps" to emptyList<Any>()))["task_id"] as String

    private suspend fun weigh(value: Number) {
        ok("tool_data.create", mapOf("tool_instance_id" to weightId, "name" to "Weighing", "data" to mapOf("value" to value, "unit" to "kg")))
    }

    private suspend fun judgements(taskId: String) =
        AppDatabase.getDatabase(context).conditionJudgementDao().getByOwner(ConditionTriggers.TASK, taskId)

    private suspend fun runs(taskId: String): List<TaskRunEntity> = AppDatabase.getDatabase(context).taskDao().getRuns(taskId, 100)

    /** The runs of [taskId] once each has ended, waited for a few seconds at most. */
    private suspend fun ended(taskId: String): List<TaskRunEntity> {
        repeat(100) {
            val runs = runs(taskId)
            if (runs.all { TaskRun.State.of(it.state).ended }) return runs
            delay(50)
        }
        return runs(taskId)
    }

    @Test
    fun fromFalseToTrue_startsOnce_theFirstJudgementNothing() = runBlocking {
        weigh(85)
        val taskId = alert()
        // Already true when it is first judged: noted, nothing starts
        weigh(86)
        assertEquals(true, judgements(taskId).single().verdict)
        weigh(70)
        weigh(82)
        // Still true: nothing more
        weigh(83)

        val run = ended(taskId).single()
        assertEquals(TaskRun.Trigger.CONDITION.key, run.trigger)
        assertEquals(TaskRun.State.DONE.key, run.state)
        val received = TaskRun.Received.of(run.receivedJson!!) as TaskRun.Received.Condition
        assertEquals(82.0, (received.values["left"] as Number).toDouble(), 0.0)
        assertEquals(80.0, (received.values["right"] as Number).toDouble(), 0.0)
        assertEquals("82 > 80", received.said)
    }

    @Test
    fun unknown_keepsTheVerdict_andIsNotFalse() = runBlocking {
        val taskId = alert()
        // No weight yet: unknown
        ConditionTriggers.dispatch(context, null)
        assertNull(judgements(taskId).single().verdict)
        // Unknown then true is no passage from false
        weigh(82)
        assertTrue(ended(taskId).isEmpty())
        weigh(70)
        weigh(81)
        assertEquals(1, ended(taskId).size)
    }

    @Test
    fun aWriteInAToolItDoesNotRead_judgesNothing() = runBlocking {
        val taskId = alert()
        ok("tool_data.create", mapOf("tool_instance_id" to notesId, "data" to mapOf("content" to "elsewhere")))
        assertTrue(judgements(taskId).isEmpty())
        weigh(70)
        assertEquals(false, judgements(taskId).single().verdict)
    }

    @Test
    fun aFailure_isNoted_andKeepsTheVerdict() = runBlocking {
        weigh(70)
        val taskId = alert()
        ConditionTriggers.dispatch(context, null)
        ok("tools.delete", mapOf("tool_instance_id" to weightId))
        ConditionTriggers.dispatch(context, null)

        val row = judgements(taskId).single()
        assertEquals(false, row.verdict)
        assertNotNull(row.failure)
        assertNotNull(row.failedSince)
        assertTrue(runs(taskId).isEmpty())
    }

    @Test
    fun theTick_dropsTheRowsOfWhatNoLongerListens() = runBlocking {
        weigh(70)
        val taskId = alert()
        ConditionTriggers.dispatch(context, null)
        assertEquals(1, judgements(taskId).size)
        ok("tasks.update", mapOf("task_id" to taskId, "is_enabled" to false))
        ConditionTriggers.dispatch(context, null)
        assertTrue(judgements(taskId).isEmpty())
    }

    @Test
    fun aWriteInsideARun_continuesItsChain() = runBlocking {
        weigh(70)
        val taskId = alert()
        ConditionTriggers.dispatch(context, null)
        withContext(ChainLink("earlier-run")) { weigh(90) }

        assertEquals("earlier-run", ended(taskId).single().chainId)
    }
}
