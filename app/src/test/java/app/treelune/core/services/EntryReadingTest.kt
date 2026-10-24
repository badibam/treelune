package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.files.FileFormat
import app.treelune.core.files.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A Reading that starts from one entry (docs/BRICKS.md, « Reading »): its field read as it is now,
 * a file field of a single file included; its entry deleted, the reading says what it read is gone;
 * a file field holding several files gives no reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryReadingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `an entry's single file is read as it is, then gone with its entry`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Calm"))
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Sheets", "extra_fields" to listOf(
                mapOf("display_name" to "Cover", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("IMAGE"), "max" to 1)),
                mapOf("display_name" to "Photos", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("IMAGE")))
            ))
        ))
        assertTrue(tool.error, tool.isSuccess)
        Files.write(File(Files.pendingDir(context), FileFormat.PNG.fileName("cover1")), byteArrayOf(1, 2, 3))
        val entry = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to tool.data!!["tool_instance_id"], "data" to mapOf("content" to "Session 24"), "extra" to mapOf("cover" to listOf("cover1"))
        ))
        assertTrue(entry.error, entry.isSuccess)
        val entryId = entry.data!!["id"] as String
        suspend fun read(field: String) = coordinator.processUserAction("readings.read", mapOf(
            "selection" to mapOf("target" to mapOf("kind" to "ENTRY", "id" to entryId)), "field" to field, "reduction" to "LAST", "reference" to 0L
        ))

        val cover = read("extra.cover")
        assertTrue(cover.error, cover.isSuccess)
        assertEquals(listOf("cover1"), cover.data!!["value"])
        assertFalse("a field of several files gives no reading", read("extra.photos").isSuccess)

        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to entryId)).isSuccess)
        val gone = read("extra.cover")
        assertTrue(gone.error, gone.isSuccess)
        assertEquals("SOURCE_NOT_FOUND", (gone.data!!["failure"] as Map<*, *>)["reason"])
    }
}
