package app.treelune.core.history

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
 * What docs/design/undo-history.md promised of the writes kept: a step per operation asked from
 * outside, its phrase said once it is done, each row whole before and after; a deletion keeping
 * what SQLite deletes with the row; the app's own writes noted, not steps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryWritesTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var earlier: Set<String>
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        // The database outlives a test: what the tests before wrote is not this one's
        earlier = database.historyDao().allSteps().map { it.id }.toSet()
    }

    /** The steps this test wrote, oldest first. */
    private suspend fun steps(): List<HistoryStepEntity> = database.historyDao().allSteps().filter { it.id !in earlier }

    @Test
    fun `a zone created is one step, its row kept without a copy before`() = runBlocking {
        val created = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        assertTrue(created.error, created.isSuccess)
        val zoneId = created.data!!["zone_id"] as String

        val step = steps().single()
        assertTrue(step.isStep)
        assertEquals("USER", step.source)
        assertEquals(HistoryState.DONE, step.state)
        assertNotNull("its phrase is written once it is done", step.phrase)
        val row = database.historyDao().getRows(step.id).single()
        assertEquals("zones", row.tableName)
        assertEquals(zoneId, row.rowId)
        assertEquals(zoneId, row.zoneId)
        assertNull(row.before)
        assertEquals("Garden", JSONObject(row.after!!).getString("name"))
    }

    @Test
    fun `a zone renamed keeps its row before and after`() = runBlocking {
        val zoneId = coordinator.processUserAction("zones.create", mapOf("name" to "Garden")).data!!["zone_id"] as String
        val renamed = coordinator.processUserAction("zones.update", mapOf("zone_id" to zoneId, "name" to "Orchard"))
        assertTrue(renamed.error, renamed.isSuccess)

        val step = steps().last()
        val row = database.historyDao().getRows(step.id).single()
        assertEquals("Garden", JSONObject(row.before!!).getString("name"))
        assertEquals("Orchard", JSONObject(row.after!!).getString("name"))
    }

    @Test
    fun `a zone deleted keeps its tools and their entries, read before SQLite deletes them`() = runBlocking {
        val zoneId = coordinator.processUserAction("zones.create", mapOf("name" to "Garden")).data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Plants")
        ))
        assertTrue(tool.error, tool.isSuccess)
        val toolId = tool.data!!["tool_instance_id"] as String
        val entry = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "data" to mapOf("content" to "Fern")))
        assertTrue(entry.error, entry.isSuccess)

        val deleted = coordinator.processUserAction("zones.delete", mapOf("zone_id" to zoneId))
        assertTrue(deleted.error, deleted.isSuccess)

        val rows = database.historyDao().getRows(steps().last().id)
        val gone = rows.filter { it.after == null }.map { it.tableName }
        assertEquals(listOf("zones", "tool_instances", "tool_data"), gone)
        rows.filter { it.after == null }.forEach { assertNotNull(it.before) }
        assertEquals("the entry kept with its tool and zone", toolId, rows.single { it.tableName == "tool_data" }.toolInstanceId)
        assertEquals(zoneId, rows.single { it.tableName == "tool_data" }.zoneId)
    }

    @Test
    fun `the app's own write is noted, never a step`() = runBlocking {
        val created = coordinator.processScheduledTask("zones.create", mapOf("name" to "Garden"))
        assertTrue(created.error, created.isSuccess)

        val noted = steps().single()
        assertFalse(noted.isStep)
        assertEquals("SCHEDULER", noted.source)
    }

    @Test
    fun `the demo installed is one step, its removal another that keeps what it deletes`() = runBlocking {
        val installed = coordinator.processUserAction("demo.install", emptyMap())
        assertTrue(installed.error, installed.isSuccess)
        val install = steps().filter { it.isStep }.single()
        assertTrue(database.historyDao().getRows(install.id).any { it.tableName == "zones" })

        val removed = coordinator.processUserAction("demo.remove", emptyMap())
        assertTrue(removed.error, removed.isSuccess)
        val removal = steps().filter { it.isStep }.last()
        val rows = database.historyDao().getRows(removal.id)
        assertTrue("its zones kept", rows.any { it.tableName == "zones" && it.after == null && it.before != null })
        assertTrue("its routines' messages kept", rows.any { it.tableName == "session_messages" && it.after == null })
        assertTrue("the home screen's groups in the same step", rows.any { it.tableName == "app_settings_categories" })
    }

    @Test
    fun `an operation that writes nothing leaves no step`() = runBlocking {
        val read = coordinator.processUserAction("zones.list", emptyMap())
        assertTrue(read.error, read.isSuccess)
        assertTrue(steps().isEmpty())
    }
}
