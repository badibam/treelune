package app.treelune.core.look

import org.json.JSONArray
import org.json.JSONObject

/**
 * The pieces a zone's tile shows (docs/design/icon-tiles.md, « La tuile d'une zone »): an ordered
 * list of the zone's pieces — tools, Tasks, routines, variables, readouts —, each with its summary
 * or without, which every size of the tile draws. Kept in the zone's column `tile_pieces` as a
 * JSON array of `{"id", "summary"}`, null when empty.
 *
 * A piece named here and no longer in the zone (deleted, gone to another zone) is not drawn: the
 * list is not cleaned, so that undoing the deletion brings it back (docs/design/dependents.md,
 * « Nothing is cleaned »), and the zone's config says it is gone.
 */
object TilePieces {

    const val KEY = "tile_pieces"
    const val ID = "id"
    const val SUMMARY = "summary"

    /** One piece of the list: the id of a piece of the zone, and whether its summary goes with it. */
    data class Piece(val id: String, val summary: Boolean)

    /** The list stored as [json]; empty for none. */
    fun read(json: String?): List<Piece> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getJSONObject(it) }.map { Piece(it.getString(ID), it.optBoolean(SUMMARY, false)) }
    }

    /** The list as a command gives it: a JSONArray of objects, or null to empty it. */
    fun of(value: Any?): List<Piece> = when (value) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> read(value.toString())
        else -> read(value.toString())
    }

    /** [pieces] in their stored form, null when there are none. */
    fun write(pieces: List<Piece>): String? =
        if (pieces.isEmpty()) null else JSONArray(pieces.map { JSONObject().put(ID, it.id).put(SUMMARY, it.summary) }).toString()

    /** [pieces] as a result gives them, for the screen and the AI. */
    fun toList(pieces: List<Piece>): List<Map<String, Any>> = pieces.map { mapOf(ID to it.id, SUMMARY to it.summary) }
}
