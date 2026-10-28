package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.ai.data.AutomationMessage
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * A connector client given a limited access (the « Access » button of its row, mcp_clients.set_access):
 * its calls held to it (AppMcpBackend.call, under AccessScope), its context saying it, and the
 * routines' reads answering within it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpClientAccessTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var caller: McpCaller
    private lateinit var healthId: String
    private lateinit var gardenId: String
    private lateinit var weightId: String
    private lateinit var seedsId: String
    private lateinit var routineId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        healthId = zone("Health")
        gardenId = zone("Garden")
        weightId = tool(healthId, "Weight")
        seedsId = tool(gardenId, "Seeds")
        val routine = coordinator.processUserAction("automations.create", mapOf(
            "zone_id" to healthId, "name" to "Weekly", "identity_id" to null, "provider_id" to null, "is_enabled" to false,
            "message" to listOf(mapOf("type" to AutomationMessage.TEXT, "text" to "hi"))
        ))
        assertTrue(routine.error, routine.isSuccess)
        routineId = routine.data!!["automation_id"] as String

        val clientId = UUID.randomUUID().toString()
        RoomOAuthStore(context).addClient(OAuthClient(clientId, "Claude", listOf("https://claude.ai/cb"), "hash-of-secret", 1L, null))
        val access = coordinator.processUserAction("mcp_clients.set_access", mapOf(
            "client_id" to clientId,
            "access" to listOf(mapOf("target" to mapOf("kind" to "ZONE", "id" to healthId), "level" to "full"))
        ))
        assertTrue(access.error, access.isSuccess)
        caller = McpCaller(clientId, "Claude")
    }

    private suspend fun zone(name: String): String {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to name))
        assertTrue(zone.error, zone.isSuccess)
        return zone.data!!["zone_id"] as String
    }

    private suspend fun tool(zoneId: String, name: String): String {
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to name, "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>())
        ))
        assertTrue(tool.error, tool.isSuccess)
        return tool.data!!["tool_instance_id"] as String
    }

    private fun call(name: String, arguments: JSONObject) = runBlocking { AppMcpBackend(context).call(name, arguments, caller) }

    @Test
    fun `a read outside the client's access is refused, one inside it answers`() {
        val outside = call("tool_data", JSONObject().put("id", seedsId).put("fields", JSONArray().put("value")))
        assertTrue(outside.text, outside.isError)
        assertTrue(outside.text, outside.text.contains("Claude"))

        val inside = call("tool_data", JSONObject().put("id", weightId).put("fields", JSONArray().put("value")))
        assertFalse(inside.text, inside.isError)
    }

    @Test
    fun `the context says the client's access`() = runBlocking {
        val text = AppMcpBackend(context).appContext(caller)
        val intro = app.treelune.core.strings.Strings.`for`(context = context).shared("ai_prompt_access_intro")
        assertTrue(text.contains(intro))
        assertTrue(text.contains(healthId))
        assertFalse("a zone out of reach is not listed in the access", text.substringAfter(intro).substringBefore("\n\n").contains(gardenId))
    }

    @Test
    fun `the routines' reads answer, and a routine is not created outside the access`() {
        val routines = call("routines", JSONObject().put("zone_id", healthId))
        assertFalse(routines.text, routines.isError)
        assertTrue(routines.text, routines.text.contains("Weekly"))

        val config = call("routine_config", JSONObject().put("id", routineId))
        assertFalse(config.text, config.isError)
        assertTrue(config.text, config.text.contains("Weekly"))

        val settings = JSONObject().put("name", "Elsewhere").put("is_enabled", false)
        val message = JSONArray().put(JSONObject().put("type", AutomationMessage.TEXT).put("text", "hi"))
        val outside = call("create_routine", JSONObject().put("zone_id", gardenId).put("settings", settings).put("message", message))
        assertTrue(outside.text, outside.isError)
        assertTrue(outside.text, outside.text.contains("Claude"))
    }
}
