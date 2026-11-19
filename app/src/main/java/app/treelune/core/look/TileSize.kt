package app.treelune.core.look

import app.treelune.core.grid.Grid

/**
 * The size of a tile (`tile_size`, docs/design/tile-sizes.md, docs/design/icon-tiles.md): a width
 * of 2 or 4 cells of the grid's four columns and a height of 1 to [MAX_HEIGHT] rows of cells; or,
 * 4 wide only, a height that follows the content ([height] null), the tile alone on its row as
 * tall as what it shows; or a tile in icons ([inIcons]), cells without a word: the icon, one
 * cell, or a strip of cells in a column (1 × 2 … 1 × [MAX_HEIGHT]) or in a row (2 × 1 … 4 × 1).
 * A fixed height is a fixed box: the tile never shrinks when it has little to show.
 *
 * What a tile with text shows follows from its size alone: its header on its first row; its
 * summary beside the header when 4 wide, under it when 2 wide and at least 2 high; its body in the
 * rows left ([body]). A tile in icons shows its icon and, in its second cell, what its summary
 * says without words (IconTile).
 *
 * Stored as text, one of a closed list ([ALL]): `icon`, `icons_1x2` … `icons_1x10`, `icons_2x1` …
 * `icons_4x1`, `2x1` … `2x10`, `4x1` … `4x10`, `4xcontent`. The prefix tells the pair in a row
 * (`icons_2x1`) from the tile 2 wide with its header (`2x1`).
 */
data class TileSize private constructor(val width: Int, val height: Int?, val inIcons: Boolean) {

    /** Whether it is the icon alone, one cell. */
    val isIcon: Boolean get() = inIcons && width == 1 && height == 1

    /** Whether its cells stand in a column (a strip 1 wide); false for a row, and for the icon alone. */
    val vertical: Boolean get() = inIcons && width == 1 && (height ?: 1) > 1

    /** In icons, the cells it has: its height in a column, its width in a row. */
    val length: Int get() = if (vertical) height!! else width

    /** Whether its height follows its content. */
    val followsContent: Boolean get() = height == null

    /** Its stored form, one of [ALL]'s. */
    val stored: String get() = when {
        isIcon -> ICON
        inIcons -> "$ICONS${width}x$height"
        height == null -> "${width}x$CONTENT"
        else -> "${width}x$height"
    }

    /** The cells it takes on the grid; a tile that follows its content takes one row, its height being its content's. */
    val cells: Grid.Size get() = Grid.Size(width, height ?: 1)

    /** Whether its summary stands beside its header (4 wide) rather than under it. */
    val summaryBeside: Boolean get() = width == FULL && !inIcons

    /**
     * The room its body has: as wide as the tile, in rows of cells under the header and the
     * summary ([Room.rows] null when it follows its content); null when it has none (in icons,
     * 2×1, 4×1, 2×2), where the summary stands alone with the header.
     */
    val body: Room? get() {
        if (inIcons) return null
        val above = if (summaryBeside) 1 else 2
        val h = height ?: return Room(width, null)
        return if (h > above) Room(width, h - above) else null
    }

    /**
     * What it is laid as in one column: every tile with text as a tile 2 wide drawn across the
     * whole width, a tile 4 wide one row taller, its header and its summary one under the other;
     * a tile in icons keeps its cells, each a half of the width (IconTile folds a long row). One
     * that follows its content keeps doing so. A tile 2 wide that follows its content exists only
     * here: it is never stored.
     */
    fun inOneColumn(): TileSize = when {
        inIcons || width == HALF -> this
        else -> TileSize(HALF, height?.let { it + 1 }, false)
    }

    override fun toString(): String = stored

    /** The room a tile's body is given: [width] cells across, [rows] rows of cells high, null for as tall as its content. */
    data class Room(val width: Int, val rows: Int?)

    companion object {
        const val HALF = 2
        const val FULL = 4
        const val MAX_HEIGHT = 10
        /** The longest strip of icons in a row: the grid's width. */
        const val MAX_ROW = Grid.COLUMNS
        private const val ICON = "icon"
        private const val ICONS = "icons_"
        private const val CONTENT = "content"

        val Icon = TileSize(1, 1, true)

        /** A tile [width] wide, 2 or 4, [height] rows of cells high, 1 to [MAX_HEIGHT]. */
        fun of(width: Int, height: Int): TileSize {
            require(width == HALF || width == FULL) { "A tile is 2 or 4 cells wide, not $width" }
            require(height in 1..MAX_HEIGHT) { "A tile is 1 to $MAX_HEIGHT rows high, not $height" }
            return TileSize(width, height, false)
        }

        /** A tile in icons of [length] cells, in a column when [vertical], in a row otherwise; the icon alone at length 1. */
        fun icons(length: Int, vertical: Boolean): TileSize {
            require(length in 1..(if (vertical) MAX_HEIGHT else MAX_ROW)) { "No strip of icons $length long ${if (vertical) "in a column" else "in a row"}" }
            return when {
                length == 1 -> Icon
                vertical -> TileSize(1, length, true)
                else -> TileSize(length, 1, true)
            }
        }

        /** A tile across the whole width, as tall as its content. */
        val Content = TileSize(FULL, null, false)

        /** The sizes in icons of a zone's tile: the icon, the columns 2 to 10 long, the rows 2 to 4 long. */
        val ZONE_ICONS: List<TileSize> = listOf(Icon) +
            (2..MAX_HEIGHT).map { icons(it, vertical = true) } +
            (2..MAX_ROW).map { icons(it, vertical = false) }

        /** The sizes in icons of a piece's tile (a tool, a Task, a routine, a variable, a readout): the icon and the pair, in a column or in a row. */
        val PIECE_ICONS: List<TileSize> = listOf(Icon, icons(2, vertical = true), icons(2, vertical = false))

        /** The sizes with text, every owner's: the half widths up, the full widths up, then the content's. */
        val TEXT: List<TileSize> = (1..MAX_HEIGHT).map { of(HALF, it) } + (1..MAX_HEIGHT).map { of(FULL, it) } + Content

        /** The sizes a piece's tile takes, in the order they are offered. */
        val PIECE: List<TileSize> = PIECE_ICONS + TEXT

        /** The sizes a zone's tile takes, in the order they are offered. */
        val ZONE: List<TileSize> = ZONE_ICONS + TEXT

        /** Every size: a zone's, which hold a piece's. */
        val ALL: List<TileSize> = ZONE

        private val byStored = ALL.associateBy { it.stored }

        /** The size stored as [text]; null when it names none, which the services refuse. */
        fun parse(text: String?): TileSize? = text?.let { byStored[it.trim()] }

        /** The size of a stored row, which the services checked: one that names none is a corrupt row, and fails. */
        fun ofStored(text: String): TileSize = requireNotNull(parse(text)) { "No tile size '$text'" }

        /**
         * The stored size a former display mode's name became (migration 116 → 117, an older
         * backup's or part's tiles); null for a name that was never one.
         */
        val FORMER_MODES: Map<String, String> = mapOf(
            "ICON" to Icon.stored,
            "MINIMAL" to of(HALF, 1).stored,
            "LINE" to of(FULL, 1).stored,
            "CONDENSED" to of(HALF, 2).stored,
            "EXTENDED" to of(FULL, 2).stored,
            "SQUARE" to of(FULL, 4).stored,
            "FULL" to Content.stored
        )
    }
}
