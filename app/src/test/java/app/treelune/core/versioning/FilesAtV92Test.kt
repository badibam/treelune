package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A backup before v92 brings its images and its joined texts into one list of files, a text carrying its content until its file is written. */
class FilesAtV92Test {

    @Test
    fun `images and joined texts become files, the missing images missing files`() {
        val data = JSONObject()
            .put("images", JSONArray().put(JSONObject().put("id", "img").put("entry_id", "e").put("format", "PNG")
                .put("size_bytes", 3).put("width", 10).put("height", 20).put("created_at", 1)))
            .put("attached_files", JSONArray().put(JSONObject().put("id", "txt").put("session_id", "s").put("name", "aliments.csv")
                .put("mime_type", "text/csv").put("size_bytes", 9).put("line_count", 2).put("content", "a;b\n1;2\n").put("created_at", 2)))
            .put("missing_images", JSONArray(listOf("img")))
        FilesAtV92.backup(data)

        assertFalse(data.has("images"))
        assertFalse(data.has("attached_files"))
        assertEquals(listOf("img"), data.getJSONArray("missing_files").let { a -> (0 until a.length()).map { a.getString(it) } })
        val files = data.getJSONArray("files")
        val image = files.getJSONObject(0)
        assertEquals("IMAGE", image.getString("kind"))
        assertEquals("PNG", image.getString("format"))
        assertEquals(10, image.getInt("width"))
        val text = files.getJSONObject(1)
        assertEquals("TEXT", text.getString("kind"))
        assertEquals("TEXT", text.getString("format"))
        assertEquals("aliments.csv", text.getString("name"))
        assertEquals(2, text.getInt("line_count"))
        assertEquals("a;b\n1;2\n", text.getString(FilesAtV92.CONTENT))
        assertFalse("the type is the name's to say", text.has("mime_type"))
    }

    @Test
    fun `a backup without images or texts is left alone`() {
        val data = JSONObject().put("zones", JSONArray())
        FilesAtV92.backup(data)
        assertFalse(data.has("files"))
    }

    @Test
    fun `the history's copy of an image's row gains its kind`() {
        val copy = FilesAtV92.copy(JSONObject().put("id", "img").put("format", "JPEG"))
        assertEquals("IMAGE", copy.getString("kind"))
        assertTrue(copy.isNull("line_count"))
    }
}
