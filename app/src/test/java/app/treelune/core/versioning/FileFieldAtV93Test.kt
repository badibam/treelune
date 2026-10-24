package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An IMAGE field becomes a FILE field taking images, wherever a field is stored; its max stays, nothing else moves. */
class FileFieldAtV93Test {

    @Test
    fun `a tool's image fields become file fields taking images, their max kept`() {
        val config = """{"name": "Plants", "extra_fields": [
            {"name": "photos", "display_name": "Photos", "type": "IMAGE", "config": {"max": 2}},
            {"name": "cover", "display_name": "Cover", "type": "IMAGE"},
            {"name": "n", "display_name": "N", "type": "NUMERIC", "config": {"decimals": 0}}]}"""
        val fields = JSONObject(FileFieldAtV93.text(config)!!).getJSONArray("extra_fields")
        assertEquals("FILE", fields.getJSONObject(0).getString("type"))
        assertEquals("""{"max":2,"kinds":["IMAGE"]}""", fields.getJSONObject(0).getJSONObject("config").toString())
        assertEquals("""{"kinds":["IMAGE"]}""", fields.getJSONObject(1).getJSONObject("config").toString())
        assertEquals("NUMERIC", fields.getJSONObject(2).getString("type"))
    }

    @Test
    fun `a config without an image field, or a message's image segment, is left alone`() {
        assertNull(FileFieldAtV93.text("""{"name": "Notes", "extra_fields": [{"name": "t", "type": "TEXT"}]}"""))
        assertNull(FileFieldAtV93.messageText("""{"segments": [{"type": "image", "image_id": "a"}]}"""))
        assertNull(FileFieldAtV93.messageText("not json"))
    }
}
