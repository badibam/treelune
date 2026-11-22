package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the readouts on a zone's tile to their v120 form (docs/design/worklist-2026-11.md,
 * « Tuile de zone »): a readout reaches its zone's tile only as one of the tile's pieces
 * (`tile_pieces`), no longer by the place `zone_tile` its own settings ticked. Each readout that
 * ticked it joins the pieces of its zone's tile, with its summary, before the pieces already
 * listed, in the order the tile showed them (the zone's groups, then the place in the group); one
 * already listed keeps its place. The place leaves its settings.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object ReadoutTilePiecesAtV120 {

    const val PLACE = "zone_tile"
    private const val PLACES = "places"

    /** A readout as the move reads it: its zone, and where it stands in it. */
    data class Readout(val id: String, val zoneId: String, val group: String?, val gridX: Int, val gridY: Int)

    /** Whether a readout's [settingsJson] ticks the zone's tile. */
    fun ticked(settingsJson: String): Boolean {
        val places = JSONObject(settingsJson).optJSONArray(PLACES) ?: return false
        return (0 until places.length()).any { places.optString(it) == PLACE }
    }

    /** A readout's [settingsJson] without the place; null when it did not tick it. */
    fun settings(settingsJson: String): String? {
        if (!ticked(settingsJson)) return null
        val settings = JSONObject(settingsJson)
        val places = settings.getJSONArray(PLACES)
        settings.put(PLACES, JSONArray((0 until places.length()).map { places.getString(it) }.filter { it != PLACE }))
        return settings.toString()
    }

    /**
     * A zone's [tilePieces] (TilePieces' stored form, null for none) with [readouts], those of the
     * zone that ticked its tile, put first in the tree's order: its groups ([toolGroups], the
     * zone's column), then the row and the column in the group. Null when there are still none.
     */
    fun pieces(tilePieces: String?, toolGroups: String?, readouts: List<Readout>): String? {
        val listed = if (tilePieces.isNullOrBlank()) JSONArray() else JSONArray(tilePieces)
        val ids = (0 until listed.length()).map { listed.getJSONObject(it).getString("id") }.toSet()
        val groups = app.treelune.core.grid.ToolPositions.zoneGroups(toolGroups)
        val added = readouts.filter { it.id !in ids }.sortedWith(compareBy(
            { groups.indexOf(it.group).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.gridY }, { it.gridX }
        ))
        if (added.isEmpty()) return tilePieces
        val result = JSONArray()
        added.forEach { result.put(JSONObject().put("id", it.id).put("summary", true)) }
        for (i in 0 until listed.length()) result.put(listed.getJSONObject(i))
        return result.toString()
    }

    /** Rewrites the backup document's readouts and zones in place. */
    fun backup(data: JSONObject) {
        val readouts = data.optJSONArray("readouts") ?: return
        val moved = mutableListOf<Readout>()
        for (i in 0 until readouts.length()) {
            val row = readouts.getJSONObject(i)
            val settings = settings(row.getString("settings_json")) ?: continue
            row.put("settings_json", settings)
            moved += Readout(row.getString("id"), row.getString("zone_id"), row.optString("group").takeIf { !row.isNull("group") && it.isNotEmpty() },
                row.getInt("grid_x"), row.getInt("grid_y"))
        }
        if (moved.isEmpty()) return
        val zones = data.optJSONArray("zones") ?: return
        val byZone = moved.groupBy { it.zoneId }
        for (i in 0 until zones.length()) {
            val zone = zones.getJSONObject(i)
            val mine = byZone[zone.getString("id")] ?: continue
            val pieces = pieces(zone.optString("tile_pieces").takeIf { !zone.isNull("tile_pieces") && it.isNotEmpty() },
                zone.optString("tool_groups").takeIf { !zone.isNull("tool_groups") && it.isNotEmpty() }, mine)
            zone.put("tile_pieces", pieces ?: JSONObject.NULL)
        }
    }
}
