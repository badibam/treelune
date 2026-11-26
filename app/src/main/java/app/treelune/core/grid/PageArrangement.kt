package app.treelune.core.grid

import app.treelune.core.look.ItemLayout
import app.treelune.core.look.Looks
import app.treelune.core.look.TilePieces
import app.treelune.core.look.TileSize
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.Texture
import org.json.JSONArray
import org.json.JSONObject

/**
 * What ARRANGE_PAGE asks of a [Page], read and checked without the database
 * (docs/design/ai-page-layout.md): each listed section given whole, its tiles in order, laid by
 * [Grid.pack]; what a tile's or a zone tile's owner accepts is checked where it is written (PageLayout).
 */
object PageArrangement {

    private const val TILE = "tile"
    private const val HIDDEN = "hidden"
    private val LOOK_KEYS = listOf(Looks.TILE_SIZE, Looks.ICON, IconColor.KEY, Texture.KEY)
    private val TILE_KEYS = setOf(TILE) + LOOK_KEYS + listOf(ItemLayout.WIDTH, ItemLayout.LINES)
    private val ZONE_TILE_KEYS = LOOK_KEYS.toSet() + setOf(TilePieces.KEY, TilePieces.ICONS)

    sealed interface Outcome

    /** Refused, nothing written: the text [key] of the shared strings, with [args]. */
    data class Refused(val key: String, val args: List<Any> = emptyList()) : Outcome

    /** A tile of a listed section: its section, whether it is hidden there, its place in that part, and the look settings given for it (null takes one out). */
    data class Placed(val id: String, val section: String?, val column: Int, val row: Int, val changes: Map<String, Any?>, val hidden: Boolean = false)

    /** What changes on the zone's own tile; [pieces] and [piecesInIcons] null when not given. */
    data class ZoneTileChange(val changes: Map<String, Any?>, val pieces: List<TilePieces.Piece>?, val piecesInIcons: Boolean?)

    /** The page's named sections in their new order, the tiles placed, and the zone's tile. */
    data class Arranged(val groups: List<String>, val placed: List<Placed>, val zoneTile: ZoneTileChange?) : Outcome

    /** [params] read against [page]. */
    fun resolve(page: Page, params: JSONObject): Outcome {
        if (params.optString("plan") != PagePlan.fingerprint(page)) return Refused("page_error_plan_stale")
        val sections = params.optJSONArray("sections") ?: JSONArray()
        if (sections.length() == 0 && !params.has("section_order") && !params.has("zone_tile")) return Refused("page_error_nothing")

        val byMark = page.tiles.associateBy { page.refs.getValue(it.id) }
        val sectionOf = page.sections.flatMap { section -> section.tiles.map { it.id to section.name } }.toMap()
        val existing = page.sections.mapNotNull { it.name }
        val listed = mutableListOf<String?>()
        val placed = mutableListOf<Placed>()
        val seen = mutableSetOf<String>()
        val emptied = mutableListOf<String>()

        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: return Refused("page_error_section_name")
            if (!section.has("name")) return Refused("page_error_section_name")
            val name = if (section.isNull("name")) null else section.optString("name").trim().ifEmpty { return Refused("page_error_section_name") }
            if (name in listed) return Refused("page_error_section_twice", listOf(name ?: "-"))
            listed += name
            val entries = section.optJSONArray("tiles") ?: return Refused("page_error_section_name")
            if (entries.length() == 0 && name != null) {
                if (name !in existing) return Refused("page_error_section_new_empty", listOf(name))
                emptied += name
            }
            val ordered = mutableListOf<Pair<PageTile, Map<String, Any?>>>()
            val hidden = mutableMapOf<String, Boolean>()
            for (j in 0 until entries.length()) {
                val entry = entries.get(j)
                val mark = (if (entry is JSONObject) entry.optString(TILE) else entry as? String).orEmpty().trim().uppercase()
                val tile = byMark[mark] ?: return Refused("page_error_tile_unknown", listOf(mark))
                if (!seen.add(tile.id)) return Refused("page_error_tile_twice", listOf(mark))
                val changes = if (entry is JSONObject) when (val read = changes(entry, TILE_KEYS - TILE)) {
                    is Read.No -> return read.refused
                    is Read.Yes -> read.values
                } else emptyMap()
                // Hidden or shown as given, as it was otherwise
                hidden[tile.id] = if (entry is JSONObject && entry.has(HIDDEN)) entry.get(HIDDEN) as? Boolean
                    ?: return Refused("page_error_value", listOf(HIDDEN, entry.get(HIDDEN))) else tile.hidden
                ordered += tile to changes
            }
            // The shown tiles laid in the section's grid, the hidden ones in its hidden part's
            val laid = ordered.partition { (tile, _) -> hidden.getValue(tile.id) }.toList().flatMap { part ->
                Grid.pack(part.map { (tile, changes) ->
                    val size = (changes[Looks.TILE_SIZE] as? String)?.let { TileSize.parse(it) } ?: tile.size
                    size.cells.let { Grid.Tile(tile.id, 0, 0, it.width, it.height) }
                })
            }.associateBy { it.id }
            ordered.forEach { (tile, changes) ->
                val at = laid.getValue(tile.id)
                placed += Placed(tile.id, name, at.column, at.row, changes, hidden.getValue(tile.id))
            }
        }

        // A listed section is given whole, and a tile leaves only a section that is listed
        page.tiles.firstOrNull { sectionOf[it.id] in listed && it.id !in seen }?.let {
            return Refused("page_error_tile_forgotten", listOf(page.refs.getValue(it.id), it.name))
        }
        page.tiles.firstOrNull { it.id in seen && sectionOf[it.id] !in listed }?.let {
            return Refused("page_error_tile_section_unlisted", listOf(page.refs.getValue(it.id), sectionOf[it.id] ?: "-"))
        }

        val groups = existing.filter { it !in emptied } + listed.filterNotNull().filter { it !in existing }
        val order = params.optJSONArray("section_order")?.let { array -> (0 until array.length()).map { array.optString(it) } }
        if (order != null && (order.size != groups.size || order.toSet() != groups.toSet())) {
            return Refused("page_error_section_order", listOf(groups.joinToString(", ")))
        }

        val zoneTile = if (!params.has("zone_tile")) null else {
            if (page.zoneId == null) return Refused("page_error_zone_tile_home")
            val given = params.optJSONObject("zone_tile") ?: return Refused("page_error_key", listOf("zone_tile", ZONE_TILE_KEYS.joinToString(", ")))
            when (val read = zoneTile(given, page, byMark)) {
                is ZoneRead.No -> return read.refused
                is ZoneRead.Yes -> read.change
            }
        }
        return Arranged(order ?: groups, placed, zoneTile)
    }

    private sealed interface Read {
        data class Yes(val values: Map<String, Any?>) : Read
        data class No(val refused: Refused) : Read
    }

    /** The look settings [entry] gives among [allowed], each of the form its key takes. */
    private fun changes(entry: JSONObject, allowed: Set<String>): Read {
        val values = mutableMapOf<String, Any?>()
        for (key in entry.keys()) {
            if (key == TILE || key == HIDDEN || key == TilePieces.KEY || key == TilePieces.ICONS) continue
            if (key !in allowed) return Read.No(Refused("page_error_key", listOf(key, allowed.joinToString(", "))))
            val value = entry.get(key)
            values[key] = when {
                key == ItemLayout.LINES -> (value as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt()
                    ?: return Read.No(Refused("page_error_value", listOf(key, value)))
                value == JSONObject.NULL -> {
                    if (key == Looks.TILE_SIZE || key == ItemLayout.WIDTH) return Read.No(Refused("page_error_value", listOf(key, "null")))
                    null
                }
                value !is String -> return Read.No(Refused("page_error_value", listOf(key, value)))
                key == Looks.TILE_SIZE && TileSize.parse(value) == null -> return Read.No(Refused("page_error_value", listOf(key, value)))
                else -> value
            }
        }
        return Read.Yes(values)
    }

    private sealed interface ZoneRead {
        data class Yes(val change: ZoneTileChange) : ZoneRead
        data class No(val refused: Refused) : ZoneRead
    }

    /** The zone's own tile: its look, and its pieces, each a mark or `{"tile", "summary"}`; a bare mark keeps the summary it had. */
    private fun zoneTile(given: JSONObject, page: Page, byMark: Map<String, PageTile>): ZoneRead {
        val look = when (val read = changes(given, ZONE_TILE_KEYS - TilePieces.KEY - TilePieces.ICONS)) {
            is Read.No -> return ZoneRead.No(read.refused)
            is Read.Yes -> read.values
        }
        val before = page.zoneTile?.pieces.orEmpty().associateBy { it.id }
        val pieces = if (!given.has(TilePieces.KEY)) null else {
            val array = if (given.isNull(TilePieces.KEY)) JSONArray() else given.optJSONArray(TilePieces.KEY)
                ?: return ZoneRead.No(Refused("page_error_value", listOf(TilePieces.KEY, given.get(TilePieces.KEY))))
            val read = mutableListOf<TilePieces.Piece>()
            for (i in 0 until array.length()) {
                val entry = array.get(i)
                val mark = (if (entry is JSONObject) entry.optString(TILE) else entry as? String).orEmpty().trim().uppercase()
                val tile = byMark[mark] ?: return ZoneRead.No(Refused("page_error_tile_unknown", listOf(mark)))
                if (read.any { it.id == tile.id }) return ZoneRead.No(Refused("page_error_tile_twice", listOf(mark)))
                val summary = if (entry is JSONObject && entry.has(TilePieces.SUMMARY)) entry.optBoolean(TilePieces.SUMMARY) else before[tile.id]?.summary ?: false
                read += TilePieces.Piece(tile.id, summary)
            }
            read
        }
        val inIcons = if (!given.has(TilePieces.ICONS)) null else given.get(TilePieces.ICONS) as? Boolean
            ?: return ZoneRead.No(Refused("page_error_value", listOf(TilePieces.ICONS, given.get(TilePieces.ICONS))))
        return ZoneRead.Yes(ZoneTileChange(look, pieces, inIcons))
    }
}
