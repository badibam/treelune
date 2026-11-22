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
    fun `an expected file is read from its description, and one that does not read refuses all`() {
        val good = org.json.JSONObject().put("name", "vol 01.flac").put("size_bytes", 1000).put("format", "FLAC").put("duration_ms", 5000)
        val read = LinkedFiles.readExpected(org.json.JSONArray().put(good)) { it } as LinkedFiles.Expected.Read
        assertEquals(FileDescription(FileFormat.FLAC, "vol 01.flac", 1000, durationMs = 5000), read.files.single())
        listOf(
            org.json.JSONObject(good.toString()).put("name", "Music/vol 01.flac"),
            org.json.JSONObject(good.toString()).put("size_bytes", 0),
            org.json.JSONObject(good.toString()).put("format", "PNG"),
            org.json.JSONObject(good.toString()).put("duration", "soon")
        ).forEach { bad ->
            assertTrue(LinkedFiles.readExpected(org.json.JSONArray().put(good).put(bad)) { it } is LinkedFiles.Expected.Refused)
        }
    }

    @Test
    fun `a document's path under its folder, or none outside it`() {
        assertEquals("Live/a.flac", LinkedFiles.pathUnder("primary:Music", "primary:Music/Live/a.flac"))
        assertEquals("a.flac", LinkedFiles.pathUnder("primary:", "primary:a.flac"))
        assertEquals(null, LinkedFiles.pathUnder("primary:Music", "primary:Musique/a.flac"))
    }

    @Test
    fun `a backup's row is a link by its mark, or by its address before the mark`() {
        assertTrue(LinkedFiles.isLinkRow(org.json.JSONObject().put("linked", true)))
        assertTrue(LinkedFiles.isLinkRow(org.json.JSONObject().put("uri", "content://x")))
        assertFalse(LinkedFiles.isLinkRow(org.json.JSONObject().put("name", "photo.jpg")))
    }

    @Test
    fun `an audio has no limit of size, a video keeps its own`() {
        assertEquals(null, FileKind.AUDIO.maxBytes)
        assertEquals(200L * 1024 * 1024, FileKind.VIDEO.maxBytes)
    }
}
