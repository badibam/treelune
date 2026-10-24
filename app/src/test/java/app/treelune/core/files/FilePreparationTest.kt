package app.treelune.core.files

import android.content.Context
import android.net.Uri
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * What docs/DATA.md (« FILE ») promises of a file of the phone added to a form: its kind told from
 * its type or its name, held to the kinds its place takes; a text read as UTF-8 and set aside with
 * its name, which the entry's row then keeps; an audio or a video known by its type or its name;
 * an image kept as the original byte for byte, reduced only on its way to a model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilePreparationTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun phoneFile(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.cacheDir, name).apply { writeBytes(bytes) })

    @Test
    fun `an audio or a video is known by its type, or by its name when the type says nothing`() {
        assertEquals(FileFormat.MP3, FileFormat.ofMedia("audio/mpeg", null))
        assertEquals(FileFormat.M4A, FileFormat.ofMedia("audio/x-m4a", "x"))
        assertEquals(FileFormat.OGG, FileFormat.ofMedia(null, "session.opus"))
        assertEquals(FileFormat.MP4, FileFormat.ofMedia("application/octet-stream", "clip.MP4"))
        assertNull(FileFormat.ofMedia("audio/flac", "song.flac"))
        assertNull(FileFormat.ofMedia(null, "notes.txt"))
    }

    @Test
    fun `a file of a kind its place does not take is refused, naming what it takes`() {
        val prepared = FilePreparation.prepare(context, phoneFile("notes.csv", "a;b\n".toByteArray()), listOf(FileKind.AUDIO)) { key ->
            if (key == "file_error_not_accepted") "%1\$s / %2\$s" else key
        }
        assertEquals("notes.csv / file_kind_audio", (prepared as FilePreparation.Result.Refused).message)
    }

    @Test
    fun `a text that is not UTF-8, or holds a NUL, is refused`() {
        val latin = FilePreparation.prepare(context, phoneFile("old.txt", byteArrayOf(0x63, 0xE9.toByte(), 0x0A)), listOf(FileKind.TEXT)) { it }
        assertTrue(latin is FilePreparation.Result.Refused)
        val binary = FilePreparation.prepare(context, phoneFile("bin.txt", byteArrayOf(0x61, 0x00, 0x62)), listOf(FileKind.TEXT)) { it }
        assertTrue(binary is FilePreparation.Result.Refused)
    }

    @Test
    fun `an image kept as the original is kept byte for byte, in its own format`() {
        val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4)
        val prepared = FilePreparation.prepare(context, phoneFile("garden.webp", bytes), listOf(FileKind.IMAGE), original = true) { it }
        val done = prepared as FilePreparation.Result.Done
        assertEquals(FileFormat.WEBP, done.description.format)
        val kept = Files.pendingFile(context, done.id)!!
        assertTrue(bytes.contentEquals(kept.readBytes()))
    }

    @Test
    fun `an image goes to a model as it is only as a JPEG or a PNG within the long side`() {
        fun image(format: FileFormat, side: Int) = FileEntity.image("i", null, "e", null, format, 1, side, side / 2, 0)
        assertTrue(Images.readyForModel(image(FileFormat.JPEG, Images.LONG_SIDE)))
        assertFalse(Images.readyForModel(image(FileFormat.JPEG, Images.LONG_SIDE + 1)))
        assertFalse(Images.readyForModel(image(FileFormat.HEIC, 800)))
    }

    @Test
    fun `a text set aside comes into the entry with its name and its lines`() = runBlocking {
        val coordinator = Coordinator(context)
        val zone = coordinator.processUserAction("zones.create", mapOf("name" to "Kitchen"))
        val tool = coordinator.processUserAction("tools.create", mapOf(
            "zone_id" to zone.data!!["zone_id"], "tooltype" to "notes",
            "config" to mapOf("name" to "Recipes", "extra_fields" to listOf(mapOf("display_name" to "Lists", "type" to "FILE", "always_visible" to false, "config" to mapOf("kinds" to listOf("TEXT")))))
        ))
        assertTrue(tool.error, tool.isSuccess)

        val prepared = coordinator.processUserAction("files.prepare", mapOf("uri" to phoneFile("courses.md", "﻿oeufs\nfarine\n".toByteArray()).toString(), "kinds" to listOf("TEXT")))
        assertTrue(prepared.error, prepared.isSuccess)
        assertEquals("courses.md", prepared.data!!["name"])
        assertEquals(2, prepared.data!!["line_count"])
        val id = prepared.data!!["id"] as String

        val entry = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to tool.data!!["tool_instance_id"], "data" to mapOf("content" to "Crêpes"), "extra" to mapOf("lists" to listOf(id))
        ))
        assertTrue(entry.error, entry.isSuccess)
        val row = AppDatabase.getDatabase(context).fileDao().getById(id)!!
        assertEquals("TEXT", row.kind)
        assertEquals("courses.md", row.name)
        assertEquals(2, row.lineCount)
        assertEquals("oeufs\nfarine\n", Files.text(context, row))
        assertFalse("its name no longer waits beside it", Files.pendingName(context, id).exists())

        val read = coordinator.processUserAction("files.read", mapOf("id" to id, "start_line" to 2))
        assertTrue(read.error, read.isSuccess)
        assertEquals("farine", read.data!!["text"])
    }
}
