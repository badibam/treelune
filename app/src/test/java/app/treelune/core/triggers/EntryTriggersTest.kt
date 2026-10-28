package app.treelune.core.triggers

import android.content.Context
import app.treelune.core.conditions.Conditions
import app.treelune.core.coordinator.ChainLink
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskRun
import app.treelune.core.tasks.TaskRunEntity
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A Task started by an entry written (docs/CORE.md, « Triggers »): one run per entry its
 * triggers hear, in the tool, the writes and the filters they name — a deleted entry judged as
 * it was —, the run receiving the entry; a write made inside a run continues its chain; an undo
 * writes again, and starts what listens; a step reads the entry received as it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryTriggersTest {

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

    /** A trigger on the weights written as [writes], with [filters]. */
    private fun onWeight(writes: List<String>, filters: List<Map<String, Any?>> = emptyList()) = mapOf(
        "kind" to Triggers.ENTRY_WRITTEN, "writes" to writes,
        "entries" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to weightId), "filters" to filters)
    )

    private val above80 = JsonUtils.toMap(Conditions.onField("data.value", ">", 80))

    /** A Task listening to [triggers], with [steps] (none by default): its id. */
    private suspend fun task(triggers: List<Map<String, Any?>>, steps: List<Map<String, Any?>> = emptyList()): String =
        ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Listener", "triggers" to triggers, "steps" to steps))["task_id"] as String

    private suspend fun weigh(value: Number): String =
        ok("tool_data.create", mapOf("tool_instance_id" to weightId, "name" to "Weighing", "data" to mapOf("value" to value, "unit" to "kg")))["id"] as String

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
    fun anEntryWritten_startsARun_receivingIt() = runBlocking {
        val taskId = task(listOf(onWeight(listOf("created"))))
        val entry = weigh(81)

        val run = ended(taskId).single()
        assertEquals(TaskRun.Trigger.ENTRY.key, run.trigger)
        assertEquals(TaskRun.Received.Entry(entry, weightId, Triggers.Write.CREATED), TaskRun.Received.of(run.receivedJson!!))
    }

    @Test
    fun theFilters_andTheWrites_sayWhichEntriesStartIt() = runBlocking {
        val taskId = task(listOf(onWeight(listOf("created"), listOf(above80))))
        weigh(79)
        val heavy = weigh(82)
        // A change is not a creation
        ok("tool_data.update", mapOf("id" to heavy, "data" to mapOf("value" to 83, "unit" to "kg")))
        ok("tool_data.create", mapOf("tool_instance_id" to notesId, "data" to mapOf("content" to "elsewhere")))

        assertEquals(listOf(heavy), ended(taskId).map { (TaskRun.Received.of(it.receivedJson!!) as TaskRun.Received.Entry).entryId })
    }

    @Test
    fun aDeletedEntry_isJudgedAsItWas() = runBlocking {
        val light = weigh(70)
        val heavy = weigh(90)
        val taskId = task(listOf(onWeight(listOf("deleted"), listOf(above80))))
        ok("tool_data.delete", mapOf("id" to light))
        ok("tool_data.delete", mapOf("id" to heavy))

        val run = ended(taskId).single()
        assertEquals(TaskRun.Received.Entry(heavy, weightId, Triggers.Write.DELETED), TaskRun.Received.of(run.receivedJson!!))
    }

    @Test
    fun anImport_startsNothing() = runBlocking {
        val taskId = task(listOf(mapOf("kind" to Triggers.ENTRY_WRITTEN, "writes" to listOf("created"),
            "entries" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to notesId), "filters" to emptyList<Any>()))))
        val csv = "content\nfirst\nsecond\n"
        val detected = ok("imports.detect", mapOf("tool_instance_id" to notesId, "csv" to csv))
        ok("imports.apply", mapOf("tool_instance_id" to notesId, "csv" to csv, "columns" to detected["columns"]))

        assertEquals(2, (ok("tool_data.get", mapOf("tool_instance_id" to notesId))["entries"] as List<*>).size)
        assertTrue(runs(taskId).isEmpty())
    }

    @Test
    fun aWriteInsideARun_continuesItsChain() = runBlocking {
        val taskId = task(listOf(onWeight(listOf("created"))))
        withContext(ChainLink("earlier-run")) {
            coordinator.processTaskStep("tool_data.create", mapOf("tool_instance_id" to weightId, "name" to "Weighing", "data" to mapOf("value" to 75, "unit" to "kg")))
        }

        assertEquals("earlier-run", ended(taskId).single().chainId)
    }

    @Test
    fun anUndo_startsWhatListensToTheWriteItMakes() = runBlocking {
        weigh(75)
        val stepId = AppDatabase.getDatabase(context).historyDao().allSteps().maxBy { it.at }.id
        val taskId = task(listOf(onWeight(listOf("deleted"))))
        ok("history.undo", mapOf("id" to stepId))

        assertEquals(Triggers.Write.DELETED, (TaskRun.Received.of(ended(taskId).single().receivedJson!!) as TaskRun.Received.Entry).write)
    }

    @Test
    fun aStep_readsTheEntryReceived_asItIs() = runBlocking {
        val taskId = task(listOf(onWeight(listOf("created"))), listOf(mapOf(
            "kind" to "create_entry", "tool_instance_id" to notesId,
            "values" to mapOf("data.content" to mapOf("received" to mapOf("field" to "name")))
        )))
        weigh(77)

        assertEquals(TaskRun.State.DONE.key, ended(taskId).single().state)
        val notes = ok("tool_data.get", mapOf("tool_instance_id" to notesId))["entries"] as List<*>
        assertEquals(listOf("Weighing"), notes.map { ((it as Map<*, *>)["data"] as Map<*, *>)["content"] })
    }

    @Test
    fun aReceivedInput_withoutATriggerGivingAnEntry_isRefused() = runBlocking {
        val refused = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Deaf", "steps" to listOf(mapOf(
            "kind" to "create_entry", "tool_instance_id" to notesId,
            "values" to mapOf("name" to mapOf("received" to mapOf("field" to "name")))
        ))))

        assertTrue(!refused.isSuccess)
    }

    @Test
    fun aRoutinesTriggers_areStored_andReadBack() = runBlocking {
        val created = ok("routines.create", mapOf(
            "zone_id" to zoneId, "name" to "Coach", "identity_id" to null, "provider_id" to null, "is_enabled" to false,
            "message" to listOf(mapOf("type" to "text", "text" to "Look at this weighing")),
            "triggers" to listOf(onWeight(listOf("created"), listOf(above80)))
        ))
        val read = ok("routines.get", mapOf("routine_id" to created["routine_id"]))["routine"] as Map<*, *>
        val trigger = (read["triggers"] as List<*>).single() as Map<*, *>

        assertEquals(listOf("created"), trigger["writes"])
        assertEquals(1, app.treelune.core.ai.data.Routine.fromResult(read).triggers)
    }

    @Test
    fun aTriggerOnAToolThatIsNotThere_isRefused() = runBlocking {
        val refused = coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Deaf", "triggers" to listOf(mapOf(
            "kind" to Triggers.ENTRY_WRITTEN, "writes" to listOf("created"),
            "entries" to mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to "nowhere"))
        ))))

        assertTrue(!refused.isSuccess)
    }
}
