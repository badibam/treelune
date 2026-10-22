package app.treelune.core.fields

import android.content.Context
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.ExecutableCommand
import app.treelune.core.ai.data.SystemMessageType
import app.treelune.core.ai.prompts.CommandExecutor
import app.treelune.core.ai.prompts.SessionReads
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.tools.BaseSchemas
import app.treelune.core.tools.ToolTypeManager
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
 * The first level of a tool type's entry (docs/design/entry-meanings.md): what each part every
 * entry has means in the type, written once by the type, said once to the AI. The entries schema
 * no longer says it; a schema of a type, its config's or a tool's entries', comes after it, once
 * in a batch or a session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryDescriptionTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
    }

    private fun level1(tooltype: String) = EntryDescription.text(ToolTypeManager.getToolType(tooltype)!!, context)

    @Test
    fun `a tracking's entry says what its name, moment and extra fields are in a tracking`() {
        val text = level1("tracking")!!
        val s = app.treelune.core.strings.Strings.`for`(tool = "tracking", context = context)
        assertTrue(text, text.contains("- `name`: " + s.tool("entry_name")))
        assertTrue(text, text.contains("- `timestamp`: " + s.tool("entry_timestamp")))
        assertTrue(text, text.contains("- `extra`: " + s.tool("entry_extra")))
    }

    @Test
    fun `a part the type does not use is not described, unless the type says when it is there`() {
        // A table's records have no moment; a questionnaire's sittings no name
        assertFalse(level1("structured")!!.contains("`timestamp`"))
        assertFalse(level1("questionnaire")!!.contains("`name`"))
        // A note's title is there only while the titles are on: the type says so
        assertTrue(level1("notes")!!.contains("`name`"))
        // A deck's reviews take no fields of the user's
        assertFalse(level1("flashcards")!!.contains("`extra`"))
        // A chart keeps no entries
        assertNull(level1("chart"))
    }

    @Test
    fun `how an entry starts is said under its state`() {
        val messages = level1("messages")!!
        assertTrue(messages, messages.lines().single { it.startsWith("- `state`") }.contains("pending"))
        val goal = level1("goal")!!
        val refusal = app.treelune.core.strings.Strings.`for`(tool = "goal", context = context).tool("start_refused")
        assertTrue(goal, goal.lines().single { it.startsWith("- `state`") }.endsWith(refusal))
    }

    @Test
    fun `the entries schema leaves the parts' meaning to the first level`() {
        val tracking = ToolTypeManager.getToolType("tracking")!!
        val config = JSONObject().put("name", "Pulse").put("type", "numeric").put("value", JSONObject().put("decimals", 0))
        val properties = JSONObject(BaseSchemas.getEntrySchema(tracking, config, context)).getJSONObject("properties")
        for (part in listOf("name", "timestamp", "data", "extra", "state")) {
            assertFalse("$part keeps a description", properties.getJSONObject(part).has("description"))
        }
        // The fields inside keep theirs
        assertTrue(properties.getJSONObject("data").getJSONObject("properties").getJSONObject("value").has("description"))
    }

    private fun schema(params: Map<String, Any?>) = ExecutableCommand("schemas", "get", params)

    @Test
    fun `a schema of a type comes after its entry's first level, said once in a batch`() = runBlocking {
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Health"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "tracking",
            "config" to mapOf("name" to "Pulse", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to listOf("bpm"))
        ))
        assertTrue(tool.error, tool.isSuccess)

        val executed = CommandExecutor(context).executeCommands(
            commands = listOf(schema(mapOf("tooltype" to "tracking")), schema(mapOf("tool_instance_id" to tool.data!!["tool_instance_id"]))),
            messageType = SystemMessageType.DATA_ADDED,
            origin = Source.EXTERNAL
        )
        val target = EntryDescription.target("tracking")
        val entryReads = executed.systemMessage.commandResults.filter { it.data?.get(SessionReads.TARGET) == target }
        assertEquals(1, entryReads.size)
        assertEquals(CommandStatus.SUCCESS, entryReads.single().status)
        // Before the config's schema it explains
        assertEquals(target, executed.systemMessage.commandResults.first().data?.get(SessionReads.TARGET))
        assertEquals(level1("tracking"), executed.promptResults.first().formattedData)
        assertNotNull(executed.promptResults.drop(1).firstOrNull { it.formattedData.contains("tracking_config") })
    }
}
