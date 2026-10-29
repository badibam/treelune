package app.treelune.core.ai.validation

import android.app.NotificationManager
import android.content.Context
import app.treelune.core.ai.data.RoutineMessage
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.DataCommand
import app.treelune.core.ai.data.SessionValidation
import app.treelune.core.ai.data.SystemMessageType
import app.treelune.core.ai.processing.AICommandProcessor
import app.treelune.core.ai.prompts.CommandExecutor
import app.treelune.core.access.AccessScope
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.mcp.AppMcpBackend
import app.treelune.core.mcp.McpApprovals
import app.treelune.core.mcp.McpCaller
import app.treelune.core.mcp.OAuthClient
import app.treelune.core.mcp.RoomOAuthStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * An AI's config change that touches recorded values, the past-data protection on (docs/DATA.md,
 * « Config change »): asked with its counts even in a zone not protected; through the connector,
 * asked by the approval's notification; from a routine, which no one is asked for, let through
 * when its access reaches the tool, refused otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PastDataValidationTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String
    private lateinit var toolId: String
    private val database get() = AppDatabase.getDatabase(context)

    /** A weight from 0 to 10 holding an 8, in a zone not protected, the protections as by default. */
    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Health"))
        assertTrue(zone.error, zone.isSuccess)
        zoneId = zone.data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("min" to 0, "max" to 10, "decimals" to 0), "units" to listOf("kg"))
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
        val entry = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "name" to "Weighing", "data" to mapOf("value" to 8, "unit" to "kg")))
        assertTrue(entry.error, entry.isSuccess)
        val set = coordinator.processUserAction("app_config.set", mapOf("category" to "validation_config", "settings" to mapOf("validate_app" to false, "validate_past_data" to true)))
        assertTrue(set.error, set.isSuccess)
    }

    /** The scale tightened by the AI: 8 falls out of the new bounds. */
    private fun tightening() = DataCommand(id = "tighten", type = "UPDATE_TOOL", params = mapOf(
        "tool_instance_id" to toolId,
        "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("min" to 0, "max" to 5, "decimals" to 0), "units" to listOf("kg"))
    ))

    private suspend fun entries() = database.toolDataDao().getByToolInstance(toolId).size

    @Test
    fun `asked with its counts, the zone not protected`() = runBlocking {
        val reason = ValidationResolver(context).reasons(listOf(tightening()), SessionValidation()).single()
        assertTrue("asked: $reason", reason != null && reason.contains("1"))
    }

    @Test
    fun `through the connector, the approval's notification asks first`() = runBlocking {
        val id = UUID.randomUUID().toString()
        RoomOAuthStore(context).addClient(OAuthClient(id, "Claude", listOf("https://claude.ai/cb"), "hash-of-secret", 1L, null))
        val caller = McpCaller(id, "Claude")
        val arguments = JSONObject(app.treelune.core.utils.JsonUtils.toJSONObject(tightening().params).toString())

        val call = async { AppMcpBackend(context).call("update_tool", arguments, caller) }
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val asked = withTimeout(10_000) {
            while (manager.allNotifications.isEmpty()) delay(20)
            manager.allNotifications.single()
        }
        assertEquals("nothing changed before the answer", 1, entries())
        McpApprovals.answer(shadowOf(asked.actions.last().actionIntent).savedIntent.getStringExtra(McpApprovals.EXTRA_ID)!!, true)
        val result = call.await()
        assertFalse(result.text, result.isError)
        assertEquals(0, entries())
    }

    /** The results of the tightening run by a routine given [access], as its AI's actions run. */
    private suspend fun byRoutine(access: List<Map<String, Any>>): app.treelune.core.ai.data.CommandResult {
        val created = coordinator.processUserAction("routines.create", mapOf(
            "zone_id" to zoneId, "name" to "Weekly", "identity_id" to null, "provider_id" to null, "is_enabled" to false,
            "message" to listOf(mapOf("type" to RoutineMessage.TEXT, "text" to "hi")), "access" to access
        ))
        assertTrue(created.error, created.isSuccess)
        val session = coordinator.processUserAction("ai_sessions.create_session", mapOf("name" to "Weekly", "type" to "ROUTINE_RUN", "routine_id" to created.data!!["routine_id"]))
        assertTrue(session.error, session.isSuccess)
        val sessionId = session.data!!["session_id"] as String
        val command = AICommandProcessor(context).processActionCommands(listOf(tightening())).executableCommands.single()
        return withContext(AccessScope.ofRun(context, sessionId)) {
            CommandExecutor(context).executeCommands(listOf(command), SystemMessageType.ACTIONS_EXECUTED, Source.AI, "ai_actions", sessionId)
        }.systemMessage.commandResults.single()
    }

    @Test
    fun `a routine whose access reaches the tool changes it, one that only reads it is refused`() = runBlocking {
        val reads = byRoutine(listOf(mapOf("target" to mapOf("kind" to "TOOL_INSTANCE", "id" to toolId), "level" to "read")))
        assertEquals(CommandStatus.FAILED, reads.status)
        assertEquals(1, entries())

        val uses = byRoutine(listOf(mapOf("target" to mapOf("kind" to "ZONE", "id" to zoneId), "level" to "use")))
        assertEquals(uses.error, CommandStatus.SUCCESS, uses.status)
        assertEquals(0, entries())
    }
}
