package app.treelune.core.backup

import android.content.Context
import android.net.Uri
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.files.FileEntity
import app.treelune.core.files.FileFormat
import app.treelune.core.files.Files
import app.treelune.core.services.BackupService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A backup restored whole (backup.import, docs/DATA.md, « FILE »): an entry's image, a message's
 * and a text joined to a message come back, each file in its owner's folder, each row naming its
 * owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFilesTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val files get() = AppDatabase.getDatabase(context).fileDao()
    private val before get() = File(context.cacheDir, "before_images.zip")

    /** The base outlives the class: the app as it was is backed up first, and restored after. */
    @Before
    fun backUp() = runBlocking {
        val service = BackupService(context)
        val token = CancellationToken()
        assertTrue(service.writeZip(before.outputStream(), service.build(token)!!, token))
    }

    @After
    fun restore() = runBlocking {
        val restored = Coordinator(context).processUserAction("backup.import", mapOf("uri" to Uri.fromFile(before).toString()))
        assertTrue(restored.error, restored.isSuccess)
    }

    @Test
    fun `an entry's image, a message's and a joined text come back, each to its owner`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Garden"))
        assertTrue(zone.error, zone.isSuccess)
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Plants", "extra_fields" to listOf(mapOf("display_name" to "Photos", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("IMAGE")))))
        ))
        assertTrue(tool.error, tool.isSuccess)

        // An entry's image, prepared as a form prepares it
        val entryBytes = byteArrayOf(1, 2, 3)
        Files.write(File(Files.pendingDir(context), FileFormat.PNG.fileName("fern")), entryBytes)
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
        Files.write(Files.sessionFile(context, "ticket", FileFormat.JPEG), messageBytes)
        files.insert(FileEntity.image("ticket", sessionId, null, null, FileFormat.JPEG, messageBytes.size.toLong(), 10, 10, 0L))

        // A text joined to the same conversation, as the composer joins it
        val joined = coordinator.processUserAction("files.attach", mapOf("session_id" to sessionId, "name" to "aliments.csv", "content" to "nom;kcal\npomme;52\n"))
        assertTrue(joined.error, joined.isSuccess)
        val textId = joined.data!!["id"] as String

        // The backup written as the export writes it, then the files gone from the app
        val file = File(context.cacheDir, "backup.zip")
        val service = BackupService(context)
        val token = CancellationToken()
        assertTrue(service.writeZip(file.outputStream(), service.build(token)!!, token))
        Files.dir(context).deleteRecursively()

        val restored = coordinator.processUserAction("backup.import", mapOf("uri" to Uri.fromFile(file).toString()))
        assertTrue(restored.error, restored.isSuccess)

        val fern = files.getById("fern")!!
        assertEquals(entryId, fern.entryId)
        assertArrayEquals(entryBytes, Files.file(context, fern).readBytes())
        assertTrue("an entry's image is in the entries' folder", Files.file(context, fern).parentFile == Files.entriesDir(context))

        val ticket = files.getById("ticket")!!
        assertEquals(sessionId, ticket.sessionId)
        assertArrayEquals(messageBytes, Files.file(context, ticket).readBytes())
        assertTrue("a message's image stays flat", Files.file(context, ticket).parentFile == Files.dir(context))

        val text = files.getById(textId)!!
        assertEquals(sessionId, text.sessionId)
        assertEquals("aliments.csv", text.name)
        assertEquals(2, text.lineCount)
        assertEquals("nom;kcal\npomme;52\n", Files.text(context, text))
    }
}
