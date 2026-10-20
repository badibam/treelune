package app.treelune.core.history

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * What docs/design/undo-history.md promised of an undo and a redo: a step undone whole and
 * redone whole; a deletion undone with all it took along; a row changed since refuses, naming
 * what changed it; an undo that would delete what a later step wrote refuses; what the app alone
 * writes is left as it is; the app's own writes are never undone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndoTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
    }

    /** The most recent step, as the history lists it. */
    private suspend fun lastStep(): String {
        val listed = coordinator.processUserAction("history.list", mapOf("limit" to 1))
        assertTrue(listed.error, listed.isSuccess)
        @Suppress("UNCHECKED_CAST")
        return ((listed.data!!["steps"] as List<Map<String, Any?>>).single()["id"] as String)
    }

    private suspend fun zone(name: String): String {
        val created = coordinator.processUserAction("zones.create", mapOf("name" to name))
        assertTrue(created.error, created.isSuccess)
        return created.data!!["zone_id"] as String
    }

    private suspend fun rename(zoneId: String, name: String): String {
        val renamed = coordinator.processUserAction("zones.update", mapOf("zone_id" to zoneId, "name" to name))
        assertTrue(renamed.error, renamed.isSuccess)
        return lastStep()
    }

    private suspend fun nameOf(zoneId: String) = database.zoneDao().getZoneById(zoneId)?.name

    @Test
    fun `a step undone is undone whole, and redone`() = runBlocking {
        val zoneId = zone("Garden")
        val step = rename(zoneId, "Orchard")

        val undone = coordinator.processUserAction("history.undo", mapOf("id" to step))
        assertTrue(undone.error, undone.isSuccess)
        assertEquals("Garden", nameOf(zoneId))
        assertEquals(HistoryState.UNDONE, database.historyDao().getStep(step)!!.state)
        assertEquals("USER", database.historyDao().getStep(step)!!.stateBy)

        val redone = coordinator.processUserAction("history.redo", mapOf("id" to step))
        assertTrue(redone.error, redone.isSuccess)
        assertEquals("Orchard", nameOf(zoneId))
        assertEquals(HistoryState.DONE, database.historyDao().getStep(step)!!.state)
    }

    @Test
    fun `a zone deleted comes back with its tools and their entries`() = runBlocking {
        val zoneId = zone("Garden")
        val toolId = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Plants")
        )).data!!["tool_instance_id"] as String
        val entry = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "data" to mapOf("content" to "Fern")))
        assertTrue(entry.error, entry.isSuccess)
        val entryId = entry.data!!["id"] as String
        assertTrue(coordinator.processUserAction("zones.delete", mapOf("zone_id" to zoneId)).isSuccess)

        val undone = coordinator.processUserAction("history.undo", mapOf("id" to lastStep()))
        assertTrue(undone.error, undone.isSuccess)
        assertEquals("Garden", nameOf(zoneId))
        assertEquals(zoneId, database.toolInstanceDao().getToolInstanceById(toolId)!!.zone_id)
        assertEquals(toolId, database.toolDataDao().getById(entryId)!!.toolInstanceId)
    }

    @Test
    fun `a row changed since refuses the undo, naming the step that changed it`() = runBlocking {
        val zoneId = zone("Garden")
        val first = rename(zoneId, "Orchard")
        rename(zoneId, "Meadow")

        val refused = coordinator.processUserAction("history.undo", mapOf("id" to first))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("Meadow"))
        assertEquals("Meadow", nameOf(zoneId))
        assertEquals(HistoryState.DONE, database.historyDao().getStep(first)!!.state)
    }

    @Test
    fun `an undo that would delete what a later step wrote refuses`() = runBlocking {
        val zoneId = zone("Garden")
        coordinator.processUserAction("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Plants")))
        val toolStep = lastStep()
        val toolId = database.historyDao().getRows(toolStep).first { it.tableName == "tool_instances" }.rowId
        assertTrue(coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "data" to mapOf("content" to "Fern"))).isSuccess)

        val refused = coordinator.processUserAction("history.undo", mapOf("id" to toolStep))
        assertFalse(refused.isSuccess)
        assertNotNull("the tool stays", database.toolInstanceDao().getToolInstanceById(toolId))
    }

    @Test
    fun `what the app alone writes is left as it is by an undo`() = runBlocking {
        suspend fun data(): Map<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            return coordinator.processUserAction("app_config.get", mapOf("category" to "data")).data!!["settings"] as Map<String, Any?>
        }
        val before = data()
        val turned = coordinator.processUserAction("app_config.set", mapOf("category" to "data", "settings" to before + ("auto_backup" to !(before["auto_backup"] as Boolean))))
        assertTrue(turned.error, turned.isSuccess)
        val step = lastStep()
        // The app marks the case seen, as the data screen does when opened
        val seen = coordinator.processScheduledTask("app_config.set", mapOf("category" to "data", "settings" to data() + ("seen" to "folder_missing")))
        assertTrue(seen.error, seen.isSuccess)

        val undone = coordinator.processUserAction("history.undo", mapOf("id" to step))
        assertTrue(undone.error, undone.isSuccess)
        assertEquals(before["auto_backup"], data()["auto_backup"])
        assertEquals("folder_missing", data()["seen"])
    }

    @Test
    fun `the app's own writes are never undone`() = runBlocking {
        assertTrue(coordinator.processScheduledTask("zones.create", mapOf("name" to "Garden")).isSuccess)
        val noted = database.historyDao().allSteps().last()
        assertFalse(noted.isStep)
        assertFalse(coordinator.processUserAction("history.undo", mapOf("id" to noted.id)).isSuccess)
        assertNull("nor listed", (coordinator.processUserAction("history.list", emptyMap()).data!!["steps"] as List<*>)
            .firstOrNull { (it as Map<*, *>)["id"] == noted.id })
    }
}
