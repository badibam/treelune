package app.treelune.core.backup

import android.content.Context
import android.net.Uri
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.images.ImageEntity
import app.treelune.core.images.ImageFormat
import app.treelune.core.images.Images
import app.treelune.core.services.BackupService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A backup restored whole (backup.import, docs/DATA.md, « IMAGE »): an entry's image and a
 * message's come back, each file in its owner's folder, each row naming its owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupImagesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val images get() = AppDatabase.getDatabase(context).imageDao()

    @Test
    fun `an entry's image and a message's come back, each to its owner`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Plants", "extra_fields" to listOf(mapOf("display_name" to "Photos", "type" to "IMAGE", "always_visible" to false)))
        ))
        assertTrue(tool.error, tool.isSuccess)

        // An entry's image, prepared as a form prepares it
        val entryBytes = byteArrayOf(1, 2, 3)
        Images.write(File(Images.pendingDir(context), ImageFormat.PNG.fileName("fern")), entryBytes)
        val entry = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to tool.data!!["tool_instance_id"], "data" to mapOf("content" to "Fern"), "extra" to mapOf("photos" to listOf("fern"))
        ))
        assertTrue(entry.error, entry.isSuccess)
        val entryId = entry.data!!["id"] as String

        // A message's image, joined in a conversation
        val session = coordinator.processUserAction("ai_sessions.create_session", mapOf("name" to "Chat", "type" to "CHAT"))
        assertTrue(session.error, session.isSuccess)
        val sessionId = session.data!!["session_id"] as String
        val messageBytes = byteArrayOf(4, 5, 6, 7)
        Images.write(Images.sessionFile(context, "ticket", ImageFormat.JPEG), messageBytes)
        images.insert(ImageEntity("ticket", sessionId, null, ImageFormat.JPEG.name, messageBytes.size.toLong(), 10, 10, 0L))

        // The backup written as the export writes it, then the images gone from the app
        val file = File(context.cacheDir, "backup.zip")
        val service = BackupService(context)
        val token = CancellationToken()
        assertTrue(service.writeZip(file.outputStream(), service.build(token)!!, token))
        Images.dir(context).deleteRecursively()

        val restored = coordinator.processUserAction("backup.import", mapOf("uri" to Uri.fromFile(file).toString()))
        assertTrue(restored.error, restored.isSuccess)

        val fern = images.getById("fern")!!
        assertEquals(entryId, fern.entryId)
        assertArrayEquals(entryBytes, Images.file(context, fern).readBytes())
        assertTrue("an entry's image is in the entries' folder", Images.file(context, fern).parentFile == Images.entriesDir(context))

        val ticket = images.getById("ticket")!!
        assertEquals(sessionId, ticket.sessionId)
        assertArrayEquals(messageBytes, Images.file(context, ticket).readBytes())
        assertTrue("a message's image stays flat", Images.file(context, ticket).parentFile == Images.dir(context))
    }
}
