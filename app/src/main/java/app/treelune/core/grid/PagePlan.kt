package app.treelune.core.grid

import app.treelune.core.look.TileSize
import app.treelune.core.look.TilePieces
import java.security.MessageDigest

/** One tile of a page as the AI's plan shows it: a zone's piece, or a zone on the home screen. */
data class PageTile(
    val id: String,
    val kind: String,
    val name: String,
    val size: TileSize,
    val iconName: String?,
    val iconColor: String?,
    val texture: String?,
    val column: Int,
    val row: Int,
    val itemWidth: String? = null,
    val itemLines: Int? = null,
    val inOneColumn: Grid.Size? = null
) {
    val gridTile: Grid.Tile get() = size.cells.let { Grid.Tile(id, column, row, it.width, it.height, inOneColumn) }
}

/** A section of a page: its name, null for the ungrouped one, and its tiles in reading order. */
data class PageSection(val name: String?, val tiles: List<PageTile>)

/** The tile of the zone a page shows, as it stands on the home screen. */
data class PageZoneTile(
    val size: TileSize,
    val iconName: String?,
    val iconColor: String?,
    val texture: String?,
    val pieces: List<TilePieces.Piece>,
    val piecesInIcons: Boolean
)

/**
 * A page the AI arranges (docs/design/ai-page-layout.md): a zone ([zoneId], with its own tile
 * [zoneTile]) or the home screen ([zoneId] null), its sections in their order, the ungrouped last.
 */
data class Page(val zoneId: String?, val sections: List<PageSection>, val zoneTile: PageZoneTile?, val oneColumn: Boolean) {

    val tiles: List<PageTile> = sections.flatMap { it.tiles }

    /** Each tile's mark, T1, T2… across the whole page in reading order: computed again at each read, never stored. */
    val refs: Map<String, String> = tiles.mapIndexed { index, tile -> tile.id to "T${index + 1}" }.toMap()
}

/** The plan of a [Page], the text the AI reads, and the print that proves it still describes the page. */
object PagePlan {

    private const val UNGROUPED = "(ungrouped)"
    private const val PRINT_LENGTH = 6

    /** The plan: its print, then what the page holds. */
    fun text(page: Page): String = "plan ${fingerprint(page)}\n${body(page)}"

    /** A short print of all the plan says and of the ids its marks stand for. */
    fun fingerprint(page: Page): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((body(page) + page.tiles.joinToString(",") { it.id }).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(PRINT_LENGTH)
    }

    private fun body(page: Page): String = buildList {
        if (page.oneColumn) add("one_column: the phone shows each section as its “one column” drawing; the 4-column grid is what is stored")
        page.zoneTile?.let { add(zoneTileLine(it, page)) }
        page.sections.forEach { section ->
            add("## ${section.name ?: UNGROUPED}")
            addAll(drawing(section.tiles.map { it.gridTile }, page))
            if (page.oneColumn && section.tiles.isNotEmpty()) {
                add("one column:")
                val grows = section.tiles.filter { it.size.followsContent }.map { it.id }.toSet()
                addAll(drawing(Grid.oneColumn(section.tiles.map { it.gridTile }, grows), page))
            }
            section.tiles.forEach { add(line(it, page)) }
        }
    }.joinToString("\n")

    private fun zoneTileLine(tile: PageZoneTile, page: Page): String = buildString {
        append("zone_tile ${tile.size.stored} ${tile.iconName ?: "-"} ${tile.iconColor ?: "-"} ${tile.texture ?: "-"}")
        // A piece no longer in the zone is not drawn (TilePieces)
        val pieces = tile.pieces.mapNotNull { piece -> page.refs[piece.id]?.let { if (piece.summary) "$it+summary" else it } }
        if (pieces.isNotEmpty()) append(" tile_pieces=${pieces.joinToString(",")}")
        if (tile.piecesInIcons) append(" tile_pieces_icons")
    }

    private fun line(tile: PageTile, page: Page): String = buildString {
        append("${mark(tile.id, tile.size.inIcons, page)} ${tile.kind} «${tile.name}» ${tile.size.stored} ${tile.iconName ?: "-"} ${tile.iconColor ?: "-"} ${tile.texture ?: "-"}")
        if (tile.size.body != null && tile.itemWidth != null) append(" item_width=${tile.itemWidth} item_lines=${tile.itemLines}")
    }

    /** A tile's mark, in lower case for a tile in icons, so that the drawing tells it from a tile with text. */
    private fun mark(id: String, inIcons: Boolean, page: Page): String =
        page.refs.getValue(id).let { if (inIcons) it.lowercase() else it }

    /** [tiles] drawn row by row on the four columns, an empty cell as a dot. */
    private fun drawing(tiles: List<Grid.Tile>, page: Page): List<String> {
        val inIcons = page.tiles.associate { it.id to it.size.inIcons }
        val cells = tiles.flatMap { tile ->
            (tile.row until tile.bottom).flatMap { row ->
                (tile.column until tile.column + tile.width).map { column -> (row to column) to mark(tile.id, inIcons.getValue(tile.id), page) }
            }
        }.toMap()
        return (0 until Grid.rowCount(tiles)).map { row ->
            (0 until Grid.COLUMNS).joinToString(" ") { column -> (cells[row to column] ?: ".").padStart(3) }
        }
    }
}
