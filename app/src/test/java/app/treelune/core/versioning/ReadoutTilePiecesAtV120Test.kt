package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the v120 move: a readout that ticked its zone's tile becomes one of the tile's pieces,
 * with its summary, before the pieces already listed and in the order the tile showed them; the
 * place leaves its settings; nothing the user saw disappears, nothing shows twice.
 */
class ReadoutTilePiecesAtV120Test {

    private fun ids(pieces: String?) = JSONArray(pieces!!).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } }

    private fun places(settings: String) = JSONObject(settings).getJSONArray("places").let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test
    fun thePlaceLeavesTheSettingsAndTheOthersStay() {
        val settings = ReadoutTilePiecesAtV120.settings("""{"core": "{done}", "places": ["zone_head", "zone_tile", "home_head"]}""")!!
        assertEquals(listOf("zone_head", "home_head"), places(settings))
        assertEquals("{done}", JSONObject(settings).getString("core"))
    }

    @Test
    fun aReadoutThatDidNotTickItIsLeftAsItIs() {
        assertNull(ReadoutTilePiecesAtV120.settings("""{"places": ["zone_head"]}"""))
        assertNull(ReadoutTilePiecesAtV120.settings("""{"core": "{done}"}"""))
    }

    @Test
    fun theReadoutsComeFirstInTheTreesOrderWithTheirSummary() {
        val readouts = listOf(
            ReadoutTilePiecesAtV120.Readout("late", "z", "Evening", 0, 0),
            ReadoutTilePiecesAtV120.Readout("second", "z", "Morning", 2, 1),
            ReadoutTilePiecesAtV120.Readout("first", "z", "Morning", 0, 1)
        )
        val pieces = ReadoutTilePiecesAtV120.pieces("""[{"id": "tool", "summary": false}]""", """["Morning", "Evening"]""", readouts)
        assertEquals(listOf("first", "second", "late", "tool"), ids(pieces))
        assertEquals(true, JSONArray(pieces).getJSONObject(0).getBoolean("summary"))
        assertEquals(false, JSONArray(pieces).getJSONObject(3).getBoolean("summary"))
    }

    @Test
    fun aReadoutAlreadyListedKeepsItsPlaceOnce() {
        val pieces = ReadoutTilePiecesAtV120.pieces("""[{"id": "tool", "summary": false}, {"id": "r", "summary": false}]""", null,
            listOf(ReadoutTilePiecesAtV120.Readout("r", "z", null, 0, 0)))
        assertEquals(listOf("tool", "r"), ids(pieces))
    }

    @Test
    fun aZoneWithoutPiecesGetsItsReadouts() {
        val pieces = ReadoutTilePiecesAtV120.pieces(null, null, listOf(ReadoutTilePiecesAtV120.Readout("r", "z", null, 0, 0)))
        assertEquals(listOf("r"), ids(pieces))
    }

    @Test
    fun aBackupMovesItsReadoutsIntoTheirZonesTiles() {
        val data = JSONObject("""{
            "zones": [{"id": "z", "tile_pieces": null, "tool_groups": null}, {"id": "other", "tile_pieces": null, "tool_groups": null}],
            "readouts": [
                {"id": "r", "zone_id": "z", "group": null, "grid_x": 0, "grid_y": 0, "settings_json": "{\"places\": [\"zone_tile\"]}"},
                {"id": "kept", "zone_id": "other", "group": null, "grid_x": 0, "grid_y": 0, "settings_json": "{\"places\": [\"home_head\"]}"}
            ]
        }""")
        ReadoutTilePiecesAtV120.backup(data)
        assertEquals(listOf("r"), ids(data.getJSONArray("zones").getJSONObject(0).getString("tile_pieces")))
        assertEquals(true, data.getJSONArray("zones").getJSONObject(1).isNull("tile_pieces"))
        assertEquals(emptyList<String>(), places(data.getJSONArray("readouts").getJSONObject(0).getString("settings_json")))
        assertEquals(listOf("home_head"), places(data.getJSONArray("readouts").getJSONObject(1).getString("settings_json")))
    }
}
