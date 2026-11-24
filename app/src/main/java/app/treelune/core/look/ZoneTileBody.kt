package app.treelune.core.look

import app.treelune.core.grid.Grid

/**
 * Where a zone's pieces go on its tile with text (docs/design/icon-tiles.md, « En taille à texte »),
 * on the grid of cells under its header, laid in their order (Grid.pack). In text ([plan]), each
 * piece as its own tile — with its summary a line 4 wide (4×1) in a body 4 wide, a condensed tile
 * (2×2) in one 2 wide; without, a minimal tile (2×1), two side by side 4 wide. In icons ([icons],
 * the zone's `tile_pieces_icons`), each piece its icon, one cell, or two side by side with its
 * second cell; 4 wide, they start beside the header, which keeps the first two cells of the first
 * row ([HEADER]). In a fixed body, what does not fit is left out and the last place says how many
 * (« +2 »): 2×1 in text, one cell in icons.
 */
object ZoneTileBody {

    /** A piece's tile in a body [width] cells wide, with its summary or without. */
    fun size(width: Int, summary: Boolean): TileSize = when {
        !summary -> TileSize.of(TileSize.HALF, 1)
        width >= TileSize.FULL -> TileSize.of(TileSize.FULL, 1)
        else -> TileSize.of(TileSize.HALF, 2)
    }

    /**
     * The room the pieces of a zone's tile of [size] have, in text or in [icons]: the rows under its
     * header ([TileSize.bodyWithoutSummary]); in icons 4 wide, the whole tile, the header keeping its place
     * ([besideHeader]). Null when it has none.
     */
    fun room(size: TileSize, icons: Boolean): TileSize.Room? =
        if (besideHeader(size, icons)) TileSize.Room(size.width, size.height) else size.bodyWithoutSummary

    /** Whether the pieces start beside the header: in icons, on a tile 4 wide with text. */
    fun besideHeader(size: TileSize, icons: Boolean): Boolean = icons && !size.inIcons && size.width == TileSize.FULL

    /**
     * The plan of a body [room] for pieces with their summary or without ([summaries], in order):
     * [placed], the pieces shown at their places (their ids the pieces' indices), and the place of
     * « +[more] » when some are left out.
     */
    data class Plan(val placed: List<Grid.Tile>, val more: Int, val morePlace: Grid.Tile?, val reserved: Grid.Tile? = null) {
        /** The rows it takes, the header's kept place included. */
        val rows: Int get() = Grid.rowCount(placed + listOfNotNull(morePlace, reserved))
    }

    /** The pieces in text, each with its summary or without ([summaries]). */
    fun plan(room: TileSize.Room, summaries: List<Boolean>): Plan =
        place(room, summaries.map { size(room.width, it).cells }, Grid.Size(TileSize.HALF, 1), null)

    /**
     * The pieces in icons, each a pair with its second cell or its icon alone ([pairs]); beside
     * the header when [besideHeader], which keeps the first two cells of the first row.
     */
    fun icons(room: TileSize.Room, pairs: List<Boolean>, besideHeader: Boolean): Plan =
        place(room, pairs.map { Grid.Size(if (it) 2 else 1, 1) }, Grid.Size(1, 1),
            if (besideHeader) Grid.Tile(HEADER, 0, 0, TileSize.HALF, 1) else null)

    private fun place(room: TileSize.Room, sizes: List<Grid.Size>, moreSize: Grid.Size, reserved: Grid.Tile?): Plan {
        fun tiles(count: Int) = sizes.take(count).mapIndexed { i, cells ->
            Grid.Tile(i.toString(), 0, 0, cells.width.coerceAtMost(room.width), cells.height)
        }
        // Laid on the body's own width, which may be narrower than the grid
        fun packed(tiles: List<Grid.Tile>) = pack(tiles, room.width, reserved)
        val all = packed(tiles(sizes.size))
        val rows = room.rows ?: return Plan(all, 0, null, reserved)
        if (Grid.rowCount(all + listOfNotNull(reserved)) <= rows) return Plan(all, 0, null, reserved)
        // The most pieces that fit with the place of « +N » after them
        for (count in sizes.size - 1 downTo 0) {
            val withMore = packed(tiles(count) + Grid.Tile(MORE, 0, 0, moreSize.width.coerceAtMost(room.width), moreSize.height))
            if (Grid.rowCount(withMore + listOfNotNull(reserved)) <= rows) {
                return Plan(withMore.filter { it.id != MORE }, sizes.size - count, withMore.single { it.id == MORE }, reserved)
            }
        }
        return Plan(emptyList(), sizes.size, null, reserved)
    }

    /** [order] laid as Grid.pack does, on [width] columns, around [reserved] when given. */
    private fun pack(order: List<Grid.Tile>, width: Int, reserved: Grid.Tile?): List<Grid.Tile> {
        val placed = mutableListOf<Grid.Tile>()
        var row = 0
        var column = 0
        for (tile in order) {
            while (true) {
                if (column + tile.width > width) { row++; column = 0; continue }
                val here = tile.copy(column = column, row = row)
                if (placed.none { it.overlaps(here) } && reserved?.overlaps(here) != true) { placed += here; break }
                column++
            }
        }
        return placed
    }

    private const val MORE = "more"

    /** The id of the header's kept place, in icons 4 wide. */
    const val HEADER = "header"
}
