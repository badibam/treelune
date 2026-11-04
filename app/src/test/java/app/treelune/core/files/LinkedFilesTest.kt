package app.treelune.core.files

import app.treelune.core.fields.FileField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What docs/design/linked-files.md promises outside the phone: a path under an opened folder never
 * climbs out of it, a field links only sounds and videos, and an audio is no longer held to a size.
 */
class LinkedFilesTest {

    @Test
    fun `a path is its names, never one that climbs out of its folder`() {
        assertEquals(listOf("Album", "01.flac"), LinkedFiles.parts("Album/01.flac"))
        assertEquals(listOf("a", "b"), LinkedFiles.parts("/a//b/"))
        assertEquals(emptyList<String>(), LinkedFiles.parts(""))
        listOf("../Download/x.mp3", "a/../../b", "./a").forEach { path ->
            assertTrue(path, runCatching { LinkedFiles.parts(path) }.exceptionOrNull() is LinkedFiles.NotFound)
        }
    }

    @Test
    fun `a field links only sounds and videos, and copies by default`() {
        assertEquals(listOf(FileKind.AUDIO, FileKind.VIDEO), LinkedFiles.KINDS)
        assertFalse(FileField.linked(FileField.config(listOf(FileKind.AUDIO))))
        assertTrue(FileField.linked(FileField.config(listOf(FileKind.AUDIO)) + (FileField.STORAGE to FileField.LINKED)))
    }

    @Test
    fun `an audio has no limit of size, a video keeps its own`() {
        assertEquals(null, FileKind.AUDIO.maxBytes)
        assertEquals(200L * 1024 * 1024, FileKind.VIDEO.maxBytes)
    }
}
