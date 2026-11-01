package app.treelune.core.mcp

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * A connector client empties a variable's colour, texture and description, and a zone's, with
 * `null`: the schema it reads takes `null` for these parameters, and the call clears them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpEmptyingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `null empties a variable's and a zone's look through the connector`() = runBlocking<Unit> {
        val coordinator = Coordinator(context)
        val backend = AppMcpBackend(context)

        // What a client reads: string or null
        val schema = backend.tools().first { it.name == "update_variable" }.inputSchema.getJSONObject("properties")
        assertEquals(JSONArray(listOf("string", "null")).toString(), schema.getJSONObject("icon_color").get("type").toString())

        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Trunk", "description" to "Body", "icon_color" to "BLUE", "texture" to "DOTS"))
        assertTrue(zone.error, zone.isSuccess)
        val zoneId = zone.data!!["zone_id"] as String
        val variable = coordinator.processUserAction("variables.create", mapOf(
            "zone_id" to zoneId, "name" to "fast", "label" to "Fast", "description" to "Hours", "icon_color" to "RED", "texture" to "DOTS",
            "definition" to mapOf("kind" to "CONSTANT", "value" to 2, "field" to mapOf("type" to "NUMERIC"))
        ))
        assertTrue(variable.error, variable.isSuccess)
        val variableId = variable.data!!["variable_id"] as String

        val clientId = UUID.randomUUID().toString()
        RoomOAuthStore(context).addClient(OAuthClient(clientId, "Claude", listOf("https://claude.ai/cb"), "hash-of-secret", 1L, null))
        val access = coordinator.processUserAction("mcp_clients.set_access", mapOf(
            "client_id" to clientId, "access" to listOf(mapOf("target" to mapOf("kind" to "ZONE", "id" to zoneId), "level" to "full"))
        ))
        assertTrue(access.error, access.isSuccess)
        val caller = McpCaller(clientId, "Claude")

        val cleared = backend.call("update_variable", JSONObject().put("variable_id", variableId)
            .put("icon_color", JSONObject.NULL).put("texture", JSONObject.NULL).put("description", JSONObject.NULL), caller)
        assertFalse(cleared.text, cleared.isError)
        val after = AppDatabase.getDatabase(context).variableDao().getById(variableId)!!
        assertNull(after.iconColor)
        assertNull(after.texture)
        assertNull(after.description)

        val zoneCleared = backend.call("update_zone", JSONObject().put("zone_id", zoneId)
            .put("icon_color", JSONObject.NULL).put("texture", JSONObject.NULL).put("description", JSONObject.NULL), caller)
        assertFalse(zoneCleared.text, zoneCleared.isError)
        val zoneAfter = AppDatabase.getDatabase(context).zoneDao().getZoneById(zoneId)!!
        assertNull(zoneAfter.icon_color)
        assertNull(zoneAfter.texture)
        assertNull(zoneAfter.description)
    }
}
