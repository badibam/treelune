package app.treelune.core.access

import android.content.Context
import app.treelune.core.ai.data.AutomationMessage
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.ExecutableCommand
import app.treelune.core.ai.data.SystemMessageType
import app.treelune.core.ai.prompts.CommandExecutor
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
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
 * An automation's run held to its access (AccessScope.ofRun, read from the session's row, as the
 * AI's commands are executed): what goes beyond it is refused, and the refusal is in the message
 * the run's history shows (SystemMessage.commandResults).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunAccessTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var zoneId: String
    private lateinit var otherZoneId: String
    private lateinit var weightId: String

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        zoneId = zone("Health")
        otherZoneId = zone("Garden")
        val weight = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zoneId, "tooltype" to "tracking",
            "config" to mapOf("name" to "Weight", "type" to "numeric", "value" to mapOf("decimals" to 1), "units" to emptyList<String>())
        ))
        assertTrue(weight.error, weight.isSuccess)
        weightId = weight.data!!["tool_instance_id"] as String
    }

    private suspend fun zone(name: String): String {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to name))
        assertTrue(zone.error, zone.isSuccess)
        return zone.data!!["zone_id"] as String
    }

    private fun grant(kind: String, id: String, level: String) = mapOf("target" to mapOf("kind" to kind, "id" to id), "level" to level)

    private fun routine(name: String, access: List<Map<String, Any>>) = mapOf(
        "zone_id" to zoneId, "name" to name, "identity_id" to null, "provider_id" to null, "is_enabled" to false,
        "message" to listOf(mapOf("type" to AutomationMessage.TEXT, "text" to "hi")),
        "access" to access
    )

    /** A run of a routine given [access]: its session, as the scheduler makes it. */
    private suspend fun run(access: List<Map<String, Any>>): String {
        val automation = coordinator.processUserAction("automations.create", routine("Weekly", access))
        assertTrue(automation.error, automation.isSuccess)
        val session = coordinator.processUserAction("ai_sessions.create_session", mapOf(
            "name" to "Weekly", "type" to "AUTOMATION", "automation_id" to automation.data!!["automation_id"]
        ))
        assertTrue(session.error, session.isSuccess)
        return session.data!!["session_id"] as String
    }

    /**
     * The result [command] gives in the run [sessionId], under its access, as the AI's actions are
     * executed; a read waiting on its tool's schema gives that schema, after the first level of
     * its type's entry (EntryDescription), which is left aside here.
     */
    private suspend fun execute(sessionId: String, command: ExecutableCommand) =
        withContext(AccessScope.ofRun(context, sessionId)) {
            CommandExecutor(context).executeCommands(listOf(command), SystemMessageType.ACTIONS_EXECUTED, Source.AI, "ai_actions", sessionId)
        }.systemMessage.commandResults
            .filterNot { (it.data?.get(app.treelune.core.ai.prompts.SessionReads.TARGET) as? String)?.startsWith("entry:") == true }
            .single()

    @Test
    fun `a routine given a tool to read is refused a write to it, the refusal in its run`() = runBlocking {
        val sessionId = run(listOf(grant("TOOL_INSTANCE", weightId, "read")))
        val write = execute(sessionId, ExecutableCommand("tool_data", "create",
            mapOf("tool_instance_id" to weightId, "name" to "Weight", "data" to mapOf("value" to 72.4)), isActionCommand = true))
        assertEquals(CommandStatus.FAILED, write.status)
        assertTrue(write.error, write.error!!.contains("Weekly"))

        val read = execute(sessionId, ExecutableCommand("tool_data", "get", mapOf("tool_instance_id" to weightId)))
        assertEquals(read.error, CommandStatus.SUCCESS, read.status)
    }

    @Test
    fun `a routine given a tool to read reaches its export, the folder its first refusal`() = runBlocking {
        val sessionId = run(listOf(grant("TOOL_INSTANCE", weightId, "read")))
        val command = app.treelune.core.ai.processing.AICommandProcessor(context).processActionCommands(listOf(
            app.treelune.core.ai.data.DataCommand(id = "export", type = "EXPORT_DATA", params = mapOf("tool_instance_id" to weightId))
        )).executableCommands.single()
        val export = execute(sessionId, command)
        // No folder is chosen in a test: the export went past the access to the service, which asks for one
        assertEquals(CommandStatus.FAILED, export.status)
        assertEquals(app.treelune.core.strings.Strings.`for`(context = context).shared("export_error_no_folder"), export.error)

        val elsewhere = run(listOf(grant("ZONE", otherZoneId, "full")))
        val refused = execute(elsewhere, command)
        assertEquals(CommandStatus.FAILED, refused.status)
        assertTrue(refused.error, refused.error!!.contains("Weekly"))
    }

    @Test
    fun `a routine with a zone in full creates a routine within its access, never one wider or open`() = runBlocking {
        val sessionId = run(listOf(grant("ZONE", zoneId, "full")))
        val create: (List<Map<String, Any>>) -> ExecutableCommand = { access ->
            ExecutableCommand("automations", "create", routine("Child", access), isActionCommand = true)
        }

        val within = execute(sessionId, create(listOf(grant("TOOL_INSTANCE", weightId, "use"))))
        assertEquals(within.error, CommandStatus.SUCCESS, within.status)

        val open = execute(sessionId, create(emptyList()))
        assertEquals(CommandStatus.FAILED, open.status)
        assertTrue(open.error, open.error!!.contains("Weekly"))

        val wider = execute(sessionId, create(listOf(grant("ZONE", otherZoneId, "read"))))
        assertEquals(CommandStatus.FAILED, wider.status)
        assertTrue(wider.error, wider.error!!.contains("Weekly"))
    }
}
