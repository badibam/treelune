package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the v117 migration promises (TileSizesAtV117): every tile's display mode becomes the size
 * it had, under `tile_size`, in a zone's, a variable's, a routine's, a Task's and a readout's row
 * and in a tool's config, in a backup as in the database; a name that was never a mode fails.
 */
class TileSizesAtV117Test {

    @Test
    fun eachMode_becomesTheSizeItHad_underTheNewKey() {
        val modes = mapOf("ICON" to "icon", "MINIMAL" to "2x1", "LINE" to "4x1", "CONDENSED" to "2x2", "EXTENDED" to "4x2", "SQUARE" to "4x4", "FULL" to "4xcontent")
        modes.forEach { (mode, size) ->
            val row = TileSizesAtV117.values(JSONObject().put("id", "z").put("display_mode", mode))
            assertEquals(size, row.getString("tile_size"))
            assertFalse(row.has("display_mode"))
        }
    }

    @Test
    fun aToolsConfig_isRewritten_oneAlreadyAtV117LeftAsItIs() {
        val config = JSONObject(TileSizesAtV117.config("""{"name":"Water","display_mode":"SQUARE"}""")!!)
        assertEquals("4x4", config.getString("tile_size"))
        assertEquals("Water", config.getString("name"))
        assertNull(TileSizesAtV117.config("""{"name":"Water","tile_size":"2x3"}"""))
    }

    @Test
    fun aBackup_hasEveryTileRewritten() {
        val data = JSONObject()
        TileSizesAtV117.TABLES.forEach { table -> data.put(table, JSONArray().put(JSONObject().put("id", table).put("display_mode", "CONDENSED"))) }
        data.put("tool_instances", JSONArray().put(JSONObject().put("id", "t").put("config_json", """{"display_mode":"FULL"}""")))
        TileSizesAtV117.backup(data)
        TileSizesAtV117.TABLES.forEach { table -> assertEquals(table, "2x2", data.getJSONArray(table).getJSONObject(0).getString("tile_size")) }
        assertEquals("4xcontent", JSONObject(data.getJSONArray("tool_instances").getJSONObject(0).getString("config_json")).getString("tile_size"))
    }

    @Test
    fun aNameThatWasNeverAMode_fails() {
        assertTrue(runCatching { TileSizesAtV117.values(JSONObject().put("display_mode", "HUGE")) }.isFailure)
    }
}
