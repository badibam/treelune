package app.treelune.core.apis

import android.content.Context
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.processing.AICommandProcessor
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
 * The APIs the AI reads and writes (APIS, CREATE_API, UPDATE_API): it never handles a key. A key
 * given is refused; one the user entered never reaches it, stays through a change, and is dropped
 * by a change of where it goes; an API that expects a key and holds none refuses its call before
 * it leaves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiApisTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private val database get() = AppDatabase.getDatabase(context)

    private val config = mapOf(
        "url" to "https://example.org/jobs", "method" to "POST", "key_in" to "header", "key_header" to "Authorization", "key_prefix" to "Bearer ",
        "inputs" to listOf(mapOf("name" to "context", "type" to "text")),
        "response" to "deferred", "id_path" to "job", "status_url" to "https://example.org/jobs?job={id}",
        "ready_path" to "state", "ready_value" to "ready", "wait_limit" to "PT1H"
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
    }

    /** [type] with [params] as the AI sends it, run as its executable command; the error when refused. */
    private suspend fun ai(type: String, params: Map<String, Any?>): Pair<Map<String, Any?>, String?> {
        val transformed = AICommandProcessor(context).processActionCommands(listOf(DataCommand("c", type, params)))
        transformed.errors.firstOrNull()?.let { return emptyMap<String, Any?>() to it }
        val command = transformed.executableCommands.single()
        val result = coordinator.processUserAction("${command.resource}.${command.operation}", command.params)
        return result.data.orEmpty() to result.error.takeIf { !result.isSuccess }
    }

    private suspend fun created(name: String): String {
        val (data, error) = ai("CREATE_API", mapOf("name" to name, "config" to config))
        assertEquals(null, error)
        return data["api_id"] as String
    }

    private fun stored(apiId: String) = JSONObject(runBlocking { database.apiDao().get(apiId)!!.configJson })

    /** A key entered by the user, put in place as a sealed one sits there: the tests have no keystore to seal one. */
    private suspend fun enterKey(apiId: String) = database.apiDao().get(apiId)!!.let { api ->
        database.apiDao().update(api.copy(configJson = JSONObject(api.configJson).put("key", "sealed-key").toString()))
    }

    @Test
    fun `a key given by the AI is refused`() = runBlocking {
        val (_, error) = ai("CREATE_API", mapOf("name" to "Refused", "config" to config + ("key" to "secret")))
        assertTrue(error, error!!.isNotEmpty())
        assertTrue(database.apiDao().getAll().none { it.name == "Refused" })
    }

    @Test
    fun `an API the AI declares waits for its key, its wait limit in milliseconds`() = runBlocking {
        val apiId = created("Waiting")
        assertFalse(stored(apiId).has("key"))
        assertEquals(3_600_000L, stored(apiId).getLong("wait_limit"))

        @Suppress("UNCHECKED_CAST")
        val listed = (coordinator.processUserAction("apis.list").data!!["apis"] as List<Map<String, Any?>>).single { it["id"] == apiId }
        assertEquals(true, listed["key_missing"])

        // Its call refused before it leaves, saying where the key is entered
        val call = coordinator.processUserAction("apis.call", mapOf("api_id" to apiId, "inputs" to mapOf("context" to "x")))
        assertFalse(call.isSuccess)
        assertTrue(call.error, call.error!!.contains("Settings › APIs"))
    }

    @Test
    fun `the AI reads every setting but the key, and whether it is entered`() = runBlocking {
        val apiId = created("Read")
        enterKey(apiId)
        @Suppress("UNCHECKED_CAST")
        val read = (coordinator.processUserAction("apis.configs").data!!["apis"] as List<Map<String, Any?>>).single { it["id"] == apiId }
        assertEquals(true, read["key_entered"])
        assertFalse((read["config"] as Map<*, *>).containsKey("key"))
        assertEquals("https://example.org/jobs", (read["config"] as Map<*, *>)["url"])
    }

    @Test
    fun `a change by the AI keeps the key, unless it changes where the key goes`() = runBlocking {
        val apiId = created("Changed")
        enterKey(apiId)

        // Its inputs changed: the key stays
        val (_, kept) = ai("UPDATE_API", mapOf("api_id" to apiId, "name" to "Changed", "config" to config +
            ("inputs" to listOf(mapOf("name" to "context", "type" to "text"), mapOf("name" to "voice", "type" to "text")))))
        assertEquals(null, kept)
        assertEquals("sealed-key", stored(apiId).getString("key"))

        // Its address changed: the key is dropped, for the user to enter again
        val (_, moved) = ai("UPDATE_API", mapOf("api_id" to apiId, "name" to "Changed", "config" to config + ("status_url" to "https://other.example/s?job={id}")))
        assertEquals(null, moved)
        assertFalse(stored(apiId).has("key"))
    }

    @Test
    fun `a change from the screen replaces the config as it is given`() = runBlocking {
        val apiId = created("Screen")
        enterKey(apiId)
        val screen = config + ("wait_limit" to 3_600_000L)
        assertTrue(coordinator.processUserAction("apis.update", mapOf("api_id" to apiId, "name" to "Screen", "config" to screen)).isSuccess)
        assertFalse(stored(apiId).has("key"))
    }
}
