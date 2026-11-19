package app.treelune.core.look

import app.treelune.core.grid.Grid

/**
 * Where a zone's pieces go in the body of its tile with text (docs/design/icon-tiles.md, « En
 * taille à texte »): each piece as its own tile, on the body's grid of cells — with its summary a
 * line 4 wide (4×1) in a body 4 wide, a condensed tile (2×2) in one 2 wide; without, a minimal
 * tile (2×1), two side by side 4 wide. Laid in their order (Grid.pack). In a fixed body, what does
 * not fit is left out and the last place, 2×1, says how many (« +2 »), as a tool's body does.
 */
object ZoneTileBody {

    /** A piece's tile in a body [width] cells wide, with its summary or without. */
    fun size(width: Int, summary: Boolean): TileSize = when {
        !summary -> TileSize.of(TileSize.HALF, 1)
        width >= TileSize.FULL -> TileSize.of(TileSize.FULL, 1)
        else -> TileSize.of(TileSize.HALF, 2)
    }

    /**
     * The plan of a body [room] for pieces with their summary or without ([summaries], in order):
     * [placed], the pieces shown at their places (their ids the pieces' indices), and the place of
     * « +[more] » when some are left out.
     */
    data class Plan(val placed: List<Grid.Tile>, val more: Int, val morePlace: Grid.Tile?) {
        /** The rows it takes. */
        val rows: Int get() = Grid.rowCount(placed + listOfNotNull(morePlace))
    }

    fun plan(room: TileSize.Room, summaries: List<Boolean>): Plan {
        fun tiles(count: Int) = summaries.take(count).mapIndexed { i, summary ->
            val cells = size(room.width, summary).cells
            Grid.Tile(i.toString(), 0, 0, cells.width.coerceAtMost(room.width), cells.height)
        }
        // Laid on the body's own width, which may be narrower than the grid
        fun packed(tiles: List<Grid.Tile>) = pack(tiles, room.width)
        val all = packed(tiles(summaries.size))
        val rows = room.rows ?: return Plan(all, 0, null)
        if (Grid.rowCount(all) <= rows) return Plan(all, 0, null)
        // The most pieces that fit with the place of « +N » after them
        for (count in summaries.size - 1 downTo 0) {
            val withMore = packed(tiles(count) + Grid.Tile(MORE, 0, 0, TileSize.HALF.coerceAtMost(room.width), 1))
            if (Grid.rowCount(withMore) <= rows) {
                return Plan(withMore.filter { it.id != MORE }, summaries.size - count, withMore.single { it.id == MORE })
            }
        }
        return Plan(emptyList(), summaries.size, null)
    }

    /** [order] laid as Grid.pack does, on [width] columns. */
    private fun pack(order: List<Grid.Tile>, width: Int): List<Grid.Tile> {
        val placed = mutableListOf<Grid.Tile>()
        var row = 0
        var column = 0
        for (tile in order) {
            while (true) {
                if (column + tile.width > width) { row++; column = 0; continue }
                val here = tile.copy(column = column, row = row)
                if (placed.none { it.overlaps(here) }) { placed += here; break }
                column++
            }
        }
        return placed
    }

    private const val MORE = "more"
}
