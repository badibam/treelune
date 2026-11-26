package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** What v122 promises of a Schema's boxes: « Yes » folded at first, « No » never folded, nothing set folded only to fit. */
class SchemaFoldAtV122Test {

    @Test
    fun `a box's folded becomes its fold, a box without it left without`() {
        val config = """{"nodes": [{"id": "a", "folded": true}, {"id": "b", "folded": false}, {"id": "c"}]}"""
        val nodes = JSONObject(SchemaFoldAtV122.toolConfig("schema", config)!!).getJSONArray("nodes")
        assertEquals("folded", nodes.getJSONObject(0).getString("fold"))
        assertEquals("open", nodes.getJSONObject(1).getString("fold"))
        assertFalse(nodes.getJSONObject(2).has("fold"))
        assertFalse((0 until 3).any { nodes.getJSONObject(it).has("folded") })
    }

    @Test
    fun `nothing changes in a schema without it, nor in another tool`() {
        assertNull(SchemaFoldAtV122.toolConfig("schema", """{"nodes": [{"id": "c"}]}"""))
        assertNull(SchemaFoldAtV122.toolConfig("notes", """{"nodes": [{"id": "a", "folded": true}]}"""))
    }
}
