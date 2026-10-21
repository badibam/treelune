package app.treelune.core.fields

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
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
 * A field the AI only reads (FieldDefinition.aiReadOnly), at the write itself: a write of the AI, a
 * routine or an AI through the MCP server that holds the field is refused whole — nothing is
 * written, not even the rest of the entry — while the app writes it, and a write that does not
 * hold it passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiReadOnlyWriteTest {

    private lateinit var context: Context
    private lateinit var coordinator: Coordinator
    private lateinit var toolId: String
    private val database get() = AppDatabase.getDatabase(context)

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"],
            "tooltype" to "notes",
            "config" to mapOf(
                "name" to "Plants",
                "extra_fields" to listOf(
                    mapOf("display_name" to "Mood", "type" to "TEXT", "ai_read_only" to true),
                    mapOf("display_name" to "Note", "type" to "TEXT")
                )
            )
        ))
        assertTrue(tool.error, tool.isSuccess)
        toolId = tool.data!!["tool_instance_id"] as String
    }

    private fun write(data: Map<String, Any?>, extra: Map<String, Any?>) = mapOf(
        "tool_instance_id" to toolId, "data" to data, "extra" to extra
    )

    @Test
    fun `a write of the AI holding the field is refused whole, a person's and one without it pass`() = runBlocking {
        val ai = coordinator.processAICommand("tool_data.create", write(mapOf("content" to "X"), mapOf("mood" to "calm", "note" to "hi")))
        assertFalse(ai.isSuccess)
        assertTrue(ai.error!!.contains("Mood"))
        assertEquals(0, database.toolDataDao().countByToolInstance(toolId))

        val external = coordinator.processExternalCommand("tool_data.create", write(mapOf("content" to "X"), mapOf("mood" to "calm")))
        assertFalse(external.isSuccess)

        val aiNote = coordinator.processAICommand("tool_data.create", write(mapOf("content" to "X"), mapOf("note" to "hi")))
        assertTrue(aiNote.error, aiNote.isSuccess)

        val user = coordinator.processUserAction("tool_data.create", write(mapOf("content" to "X"), mapOf("mood" to "calm")))
        assertTrue(user.error, user.isSuccess)
        assertEquals(2, database.toolDataDao().countByToolInstance(toolId))
    }
}
