package app.treelune.core.events

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskRun
import app.treelune.core.tasks.TaskRunEntity
import app.treelune.core.triggers.Triggers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
 * The events of a zone (docs/CORE.md, « Events »): a name unique in its zone with the values it
 * declares; emitted with each of them, of its type, by a Task's step, it starts what listens to
 * it, which receives the values; deleted only once nothing uses it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventsTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String
    private lateinit var notesId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = ok("zones.create", mapOf("name" to "Sessions"))["zone_id"] as String
        notesId = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Notes")))["tool_instance_id"] as String
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data!!
    }

    /** « session ready », carrying a text `title`: its id. */
    private suspend fun ready(): String = ok("events.create", mapOf("zone_id" to zoneId, "name" to "Session ready",
        "values" to listOf(mapOf("name" to "title", "type" to "text"))))["event_id"] as String

    private fun listening(eventId: String) = listOf(mapOf("kind" to Triggers.EVENT, "event" to mapOf("kind" to "EVENT", "id" to eventId)))

    private suspend fun ended(taskId: String): List<TaskRunEntity> {
        val dao = AppDatabase.getDatabase(context).taskDao()
        repeat(100) {
            val runs = dao.getRuns(taskId, 100)
            if (runs.isNotEmpty() && runs.all { TaskRun.State.of(it.state).ended }) return runs
            delay(50)
        }
        return dao.getRuns(taskId, 100)
    }

    @Test
    fun aDescription_isKept_andEmptiedByNull() = runBlocking {
        val id = ok("events.create", mapOf("zone_id" to zoneId, "name" to "Ready", "description" to "The day's sheet can be played"))["event_id"] as String
        fun described() = runBlocking { (ok("events.get", mapOf("event_id" to id))["event"] as Map<*, *>)["description"] }
        assertEquals("The day's sheet can be played", described())
        ok("events.update", mapOf("event_id" to id, "name" to "Ready now"))
        assertEquals("The day's sheet can be played", described())
        ok("events.update", mapOf("event_id" to id, "description" to null))
        assertEquals(null, described())
    }

    @Test
    fun aName_isUniqueInItsZone() = runBlocking {
        ready()
        assertFalse(coordinator.processUserAction("events.create", mapOf("zone_id" to zoneId, "name" to "session ready")).isSuccess)
    }

    @Test
    fun aStepEmits_andWhatListensStarts_receivingTheValues() = runBlocking {
        val eventId = ready()
        val listener = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Listener", "triggers" to listening(eventId), "steps" to listOf(mapOf(
            "kind" to "create_entry", "tool_instance_id" to notesId,
            "values" to mapOf("data.content" to mapOf("received" to mapOf("value" to "title")))
        ))))["task_id"] as String
        val emitter = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Emitter", "steps" to listOf(mapOf(
            "kind" to "emit_event", "event_id" to eventId, "inputs" to mapOf("title" to mapOf("constant" to "Morning breath"))
        ))))["task_id"] as String
        ok("tasks.run", mapOf("task_id" to emitter))

        val run = ended(listener).single()
        assertEquals(TaskRun.Trigger.EVENT.key, run.trigger)
        assertEquals(TaskRun.State.DONE.key, run.state)
        val notes = ok("tool_data.get", mapOf("tool_instance_id" to notesId))["entries"] as List<*>
        assertEquals(listOf("Morning breath"), notes.map { ((it as Map<*, *>)["data"] as Map<*, *>)["content"] })
    }

    @Test
    fun anEmission_givesEveryValue_ofItsType() = runBlocking {
        val eventId = ready()
        assertFalse(coordinator.processUserAction("events.emit", mapOf("event_id" to eventId, "values" to emptyMap<String, Any>())).isSuccess)
        assertFalse(coordinator.processUserAction("events.emit", mapOf("event_id" to eventId, "values" to mapOf("title" to 3))).isSuccess)
        assertFalse(coordinator.processUserAction("events.emit", mapOf("event_id" to eventId, "values" to mapOf("title" to "x", "other" to "y"))).isSuccess)
        assertTrue(coordinator.processUserAction("events.emit", mapOf("event_id" to eventId, "values" to mapOf("title" to "x"))).isSuccess)
        // A step that leaves a value out is refused when the Task is written
        assertFalse(coordinator.processUserAction("tasks.create", mapOf("zone_id" to zoneId, "name" to "Mute", "steps" to listOf(mapOf(
            "kind" to "emit_event", "event_id" to eventId)))).isSuccess)
    }

    @Test
    fun anEventListenedTo_isNotDeleted() = runBlocking {
        val eventId = ready()
        val listener = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Listener", "triggers" to listening(eventId)))["task_id"] as String

        assertFalse(coordinator.processUserAction("events.delete", mapOf("event_id" to eventId)).isSuccess)
        ok("tasks.delete", mapOf("task_id" to listener))
        assertTrue(AppDatabase.getDatabase(context).eventDao().get(ok("events.delete", mapOf("event_id" to eventId))["event_id"] as String) == null)
    }

    @Test
    fun aToolEmits_onlyAnEventCarryingNothingOrOneOfItsEntries() = runBlocking {
        // A List tells of a due date passed: it may emit there
        val list = ok("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "list", "config" to mapOf("name" to "Errands")))["tool_instance_id"] as String
        suspend fun carrying(name: String, tool: String) = ok("events.create", mapOf("zone_id" to zoneId, "name" to name, "values" to listOf(
            mapOf("name" to "item", "type" to "entry", "tool_instance_id" to mapOf("kind" to "TOOL_INSTANCE", "id" to tool)))))["event_id"] as String
        val ofNotes = carrying("Of notes", notesId)
        val ofList = carrying("Of the list", list)
        val config = (ok("tools.get", mapOf("tool_instance_id" to list))["tool_instance"] as Map<*, *>)["config"] as Map<*, *>
        fun withEvent(id: String) = mapOf("tool_instance_id" to list, "config" to config + (EventPoints.KEY to mapOf("kind" to "EVENT", "id" to id)))

        assertFalse(coordinator.processUserAction("tools.update", withEvent(ofNotes)).isSuccess)
        val accepted = coordinator.processUserAction("tools.update", withEvent(ofList))
        assertTrue(accepted.error, accepted.isSuccess)
    }

    @Test
    fun aPlaceEmits_carryingTheEntryConcerned() = runBlocking {
        val ofNotes = ok("events.create", mapOf("zone_id" to zoneId, "name" to "Noted", "values" to listOf(
            mapOf("name" to "note", "type" to "entry", "tool_instance_id" to mapOf("kind" to "TOOL_INSTANCE", "id" to notesId)))))["event_id"] as String
        val listener = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Listener", "triggers" to listening(ofNotes)))["task_id"] as String
        val entry = ok("tool_data.create", mapOf("tool_instance_id" to notesId, "data" to mapOf("content" to "hello")))["id"] as String

        EventPoints.emit(context, EventPoints.stored(ofNotes), entry)

        assertEquals(TaskRun.Received.Event(ofNotes, mapOf("note" to entry)), TaskRun.Received.of(ended(listener).single().receivedJson!!))
    }

    @Test
    fun aTaskThatEndsWell_emits_inItsChain() = runBlocking {
        val eventId = ok("events.create", mapOf("zone_id" to zoneId, "name" to "Done"))["event_id"] as String
        val listener = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Listener", "triggers" to listening(eventId)))["task_id"] as String
        val first = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "First", EventPoints.KEY to mapOf("kind" to "EVENT", "id" to eventId)))["task_id"] as String
        val runId = ok("tasks.run", mapOf("task_id" to first))["run_id"] as String

        assertEquals(runId, ended(listener).single().chainId)
    }
}
