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
 * The Tasks and the APIs in the history of writes (docs/DATA.md, « History of writes »): a Task's
 * change undone, a Task back with the zone deleted with it, without its runs; an API's change
 * undone with its key left as it is now, an API deleted back without its key, never copied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasksApisHistoryTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
    }

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        return result.data.orEmpty()
    }

    /** Undoes the most recent step. */
    private suspend fun undoLast() {
        @Suppress("UNCHECKED_CAST")
        val step = (ok("history.list", mapOf("limit" to 1))["steps"] as List<Map<String, Any?>>).single()["id"]
        ok("history.undo", mapOf("id" to step))
    }

    @Test
    fun `a Task's change is undone, and a Task deleted with its zone comes back with it`() = runBlocking {
        val zoneId = ok("zones.create", mapOf("name" to "Balcony"))["zone_id"] as String
        val taskId = ok("tasks.create", mapOf("zone_id" to zoneId, "name" to "Heatwave"))["task_id"] as String

        ok("tasks.update", mapOf("task_id" to taskId, "name" to "Cold snap"))
        undoLast()
        assertEquals("Heatwave", database.taskDao().get(taskId)!!.name)

        ok("zones.delete", mapOf("zone_id" to zoneId))
        assertNull(database.taskDao().get(taskId))
        undoLast()
        assertNotNull(database.zoneDao().getZoneById(zoneId))
        assertEquals("Heatwave", database.taskDao().get(taskId)!!.name)
    }

    @Test
    fun `an API's change is undone with its key as it is, a deletion undone brings it back without its key`() = runBlocking {
        // Made without a key: the tests have no keystore to seal one
        val config = mapOf("url" to "https://example.org/price", "method" to "GET", "key_in" to "query", "key_param" to "k", "response" to "immediate")
        val apiId = ok("apis.create", mapOf("name" to "Prices", "config" to config))["api_id"] as String
        fun stored() = JSONObject(runBlocking { database.apiDao().get(apiId)!!.configJson })
        // Its key put in place as a sealed one sits there, outside any step
        suspend fun withKey(key: String) = database.apiDao().get(apiId)!!.let { api ->
            database.apiDao().update(api.copy(configJson = JSONObject(api.configJson).put("key", key).toString()))
        }
        withKey("sealed-first")

        // Its address changed, then its key entered again; the address undone, the key left as it is now
        ok("apis.update", mapOf("api_id" to apiId, "name" to "Prices", "config" to config + ("url" to "https://example.org/other")))
        withKey("sealed-second")
        undoLast()
        assertEquals("https://example.org/price", stored().getString("url"))
        assertEquals("sealed-second", stored().getString("key"))

        // Deleted then back: its settings, never its key
        ok("apis.delete", mapOf("api_id" to apiId))
        undoLast()
        assertEquals("https://example.org/price", stored().getString("url"))
        assertFalse(stored().has("key"))
        // Nowhere in the history either
        database.historyDao().getRowsOf("apis", apiId).forEach { row ->
            listOfNotNull(row.before, row.after).forEach { copy -> assertFalse(copy, JSONObject(JSONObject(copy).getString("config_json")).has("key")) }
        }
    }
}
