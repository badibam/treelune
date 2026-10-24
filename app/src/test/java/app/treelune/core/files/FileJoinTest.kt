package app.treelune.core.files

import android.content.Context
import android.net.Uri
import app.treelune.core.ai.data.MessageSegment
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * What « Join » keeps with a conversation (docs/AI.md, « Composer »): a file of the phone made the
 * session's own, a tool's files copied for it, the originals left where they were; a sound or a
 * video joined is a segment of its own in the message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileJoinTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a file of the phone and a tool's files join the conversation as its own`() = runBlocking {
        val coordinator = Coordinator(context)
        val session = coordinator.processUserAction("ai_sessions.create_session", mapOf("name" to "Chat", "type" to "CHAT"))
        val sessionId = session.data!!["session_id"] as String

        val phone = Uri.fromFile(File(context.cacheDir, "list.csv").apply { writeText("a;b\n1;2\n") })
        val joined = coordinator.processUserAction("files.join", mapOf("session_id" to sessionId, "uri" to phone.toString()))
        assertTrue(joined.error, joined.isSuccess)
        val text = (joined.data!!["files"] as List<*>).single() as Map<*, *>
        assertEquals("TEXT", text["kind"])
        assertEquals("list.csv", text["name"])
        val row = AppDatabase.getDatabase(context).fileDao().getById(text["id"] as String)!!
        assertEquals(sessionId, row.sessionId)
        assertEquals("a;b\n1;2\n", Files.text(context, row))

        // A tool's image, copied for the conversation, the entry keeping its own
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        val tool = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Plants", "extra_fields" to listOf(mapOf("display_name" to "Photos", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("IMAGE")))))))
        Files.write(File(Files.pendingDir(context), FileFormat.PNG.fileName("fern")), byteArrayOf(1, 2, 3))
        val entry = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to tool.data!!["tool_instance_id"],
            "data" to mapOf("content" to "Fern"), "extra" to mapOf("photos" to listOf("fern"))))
        assertTrue(entry.error, entry.isSuccess)
        val copied = coordinator.processUserAction("files.join", mapOf("session_id" to sessionId, "ids" to listOf("fern")))
        assertTrue(copied.error, copied.isSuccess)
        val copy = ((copied.data!!["files"] as List<*>).single() as Map<*, *>)["id"] as String
        assertNotEquals("fern", copy)
        assertEquals(sessionId, AppDatabase.getDatabase(context).fileDao().getById(copy)!!.sessionId)
        assertEquals(entry.data!!["id"], AppDatabase.getDatabase(context).fileDao().getById("fern")!!.entryId)
    }

    @Test
    fun `a sound or a video joined is a segment of its own, kept through the message's stored form`() {
        val message = RichMessage(listOf(MessageSegment.Text("file this"), MessageSegment.Media("sound1")))
        assertEquals(message, RichMessage.fromJson(message.toJson()))
        assertEquals(listOf("sound1"), message.mediaIds)
    }
}
