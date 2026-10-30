package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.triggers.Triggers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A step's conditions (docs/CORE.md, « Tasks », StepConditions): all holding, the step runs;
 * otherwise it is skipped, said why, and the run goes on; a step reading what a skipped step gave
 * is skipped too; a condition that does not read, or compares two types, is refused when the
 * Task is written.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepConditionsTest {

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

    private val onWeight get() = mapOf(
        "kind" to Triggers.ENTRY_WRITTEN, "writes" to listOf("created"),
        "entries" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to weightId), "filters" to emptyList<Any>())
    )

    /** The weight received compared with [op] to [kg]. */
    private fun weight(op: String, kg: Number) = mapOf("left" to mapOf("received" to mapOf("field" to "data.value")), "op" to op, "right" to mapOf("constant" to kg))

    private fun note(content: String, vararg conditions: Map<String, Any?>) = mapOf(
        "kind" to TaskSteps.CREATE_ENTRY, "tool_instance_id" to notesId,
        "values" to mapOf("data.content" to mapOf("constant" to content))
    ) + (if (conditions.isEmpty()) emptyMap() else mapOf(StepConditions.WHEN to conditions.toList()))

    /** Steps 0 and 1 each run on one side of 80 kg; step 2 changes what step 0 created. */
    private val sorting get() = listOf(
        note("heavy", weight(">", 80)),
        note("light", weight("<=", 80)),
        mapOf("kind" to TaskSteps.UPDATE_ENTRY, "tool_instance_id" to notesId, "entry" to mapOf("result" to mapOf("step" to 0)),
            "values" to mapOf("data.content" to mapOf("constant" to "heavy, seen")))
    )

    private suspend fun weigh(value: Number) {
        ok("tool_data.create", mapOf("tool_instance_id" to weightId, "name" to "Weighing", "data" to mapOf("value" to value, "unit" to "kg")))
    }

    private suspend fun notes(): List<String> =
        (ok("tool_data.get", mapOf("tool_instance_id" to notesId))["entries"] as List<*>).map { ((it as Map<*, *>)["data"] as Map<*, *>)["content"] as String }

    /** The single run of [taskId] once ended, waited for a few seconds at most. */
    private suspend fun ended(taskId: String): TaskRunEntity {
        repeat(100) {
            val run = AppDatabase.getDatabase(context).taskDao().getRuns(taskId, 10).singleOrNull()
            if (run != null && TaskRun.State.of(run.state).ended) return run
            delay(50)
        }
        error("no run of $taskId ended")
    }

    private suspend fun task(steps: List<Map<String, Any?>>): String =
        ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Sort", "triggers" to listOf(onWeight), "steps" to steps))["task_id"] as String

    @Test
    fun aStep_runsWhenItsConditionsHold_andIsSkippedOtherwise() = runBlocking {
        val taskId = task(sorting)
        weigh(82)

        val run = ended(taskId)
        assertEquals(TaskRun.State.DONE.key, run.state)
        val results = TaskRun.results(run.resultsJson)
        assertNull(results[0].skipped)
        assertTrue(results[1].skipped!!.isNotEmpty())
        assertNull(results[2].skipped)
        assertEquals(listOf("heavy, seen"), notes())
    }

    @Test
    fun aStepReadingASkippedOne_isSkippedToo() = runBlocking {
        val taskId = task(sorting)
        weigh(70)

        val run = ended(taskId)
        assertEquals(TaskRun.State.DONE.key, run.state)
        val results = TaskRun.results(run.resultsJson)
        assertTrue(results[0].skipped != null)
        assertNull(results[1].skipped)
        assertTrue(results[2].skipped != null)
        assertEquals(listOf("light"), notes())
    }

    @Test
    fun aStepRuns_onlyWhenAllItsConditionsHold() = runBlocking {
        val taskId = task(listOf(note("between", weight(">", 60), weight("<", 80))))
        weigh(85)

        assertTrue(TaskRun.results(ended(taskId).resultsJson).single().skipped != null)
        assertTrue(notes().isEmpty())
    }

    @Test
    fun aCondition_comparingANumberWithAText_isRefused() = runBlocking {
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Sort", "triggers" to listOf(onWeight),
            "steps" to listOf(note("heavy", weight(">", 80).plus("right" to mapOf("constant" to "heavy"))))))
        assertFalse(result.isSuccess)
    }

    @Test
    fun aCondition_onWhatNoTriggerGives_isRefused() = runBlocking {
        val result = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Sort",
            "steps" to listOf(note("heavy", weight(">", 80)))))
        assertFalse(result.isSuccess)
    }

    @Test
    fun theResultsAStepReads_areFoundAnywhereInIt() {
        val step = JSONObject("""{"kind": "update_entry", "entry": {"result": {"step": 0}},
            "values": {"data.content": {"result": {"step": 2, "value": "text"}}},
            "when": [{"left": {"result": {"step": 1, "value": "rain"}}, "op": "present"}]}""")
        assertEquals(setOf(0, 1, 2), StepConditions.resultsRead(step))
    }
}
