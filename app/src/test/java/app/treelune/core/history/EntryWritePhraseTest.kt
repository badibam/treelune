package app.treelune.core.history

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The words of a step in the history of writes name the tool and the zone of an entry written by
 * its id alone, as a tool's history screen writes it: changed, deleted (gone by the time the step
 * is put into words), several deleted at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryWritePhraseTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private var zoneId: String? = null

    @After
    fun tearDown() = runBlocking<Unit> {
        zoneId?.let { Coordinator(context).processUserAction("zones.delete", mapOf("zone_id" to it)) }
    }

    /** The words of the latest step. */
    private suspend fun lastPhrase(coordinator: Coordinator): String {
        val listed = coordinator.processUserAction("history.list", mapOf("limit" to 1))
        assertTrue(listed.error, listed.isSuccess)
        @Suppress("UNCHECKED_CAST")
        return (listed.data!!["steps"] as List<Map<String, Any?>>).single()["phrase"] as String
    }

    @Test
    fun `an entry written by its id alone is named with its tool and zone`() = runBlocking<Unit> {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Trunk"))
        assertTrue(zone.error, zone.isSuccess)
        zoneId = zone.data!!["zone_id"] as String
        val tool = coordinator.processUserAction("tools.create", mapOf("zone_id" to zoneId, "tooltype" to "notes", "config" to mapOf("name" to "Meals")))
        assertTrue(tool.error, tool.isSuccess)
        val toolId = tool.data!!["tool_instance_id"] as String
        suspend fun entry(text: String): String {
            val created = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolId, "data" to mapOf("content" to text)))
            assertTrue(created.error, created.isSuccess)
            return created.data!!["id"] as String
        }
        fun named(phrase: String) = assertTrue(phrase, "Meals" in phrase && "Trunk" in phrase)

        val first = entry("Soup")
        val changed = coordinator.processUserAction("tool_data.update", mapOf("id" to first, "data" to mapOf("content" to "Soup and bread")))
        assertTrue(changed.error, changed.isSuccess)
        named(lastPhrase(coordinator))

        val deleted = coordinator.processUserAction("tool_data.delete", mapOf("id" to first))
        assertTrue(deleted.error, deleted.isSuccess)
        named(lastPhrase(coordinator))

        val both = listOf(entry("Rice"), entry("Fish"))
        val batch = coordinator.processUserAction("tool_data.batch_delete", mapOf("ids" to both))
        assertTrue(batch.error, batch.isSuccess)
        named(lastPhrase(coordinator))
    }
}
