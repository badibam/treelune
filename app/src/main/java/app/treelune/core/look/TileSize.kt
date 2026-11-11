package app.treelune.core.look

import app.treelune.core.grid.Grid

/**
 * The size of a tile (`tile_size`, docs/design/tile-sizes.md): the icon, one cell; or a width of
 * 2 or 4 cells of the grid's four columns and a height of 1 to [MAX_HEIGHT] rows of cells; or, 4
 * wide only, a height that follows the content ([height] null), the tile alone on its row as tall
 * as what it shows. A fixed height is a fixed box: the tile never shrinks when it has little to
 * show. Every kind of tile takes every size.
 *
 * What the tile shows follows from its size alone: its header on its first row; its summary
 * beside the header when 4 wide, under it when 2 wide and at least 2 high; its body in the rows
 * left ([body]).
 *
 * Stored as text, one of a closed list ([ALL]): `icon`, `2x1` … `2x10`, `4x1` … `4x10`, `4xcontent`.
 */
data class TileSize private constructor(val width: Int, val height: Int?) {

    /** Whether it is the icon, one cell. */
    val isIcon: Boolean get() = width == 1

    /** Whether its height follows its content. */
    val followsContent: Boolean get() = height == null

    /** Its stored form, one of [ALL]'s. */
    val stored: String get() = when {
        isIcon -> ICON
        height == null -> "${width}x$CONTENT"
        else -> "${width}x$height"
    }

    /** The cells it takes on the grid; a tile that follows its content takes one row, its height being its content's. */
    val cells: Grid.Size get() = Grid.Size(width, height ?: 1)

    /** Whether its summary stands beside its header (4 wide) rather than under it. */
    val summaryBeside: Boolean get() = width == FULL

    /**
     * The room its body has: as wide as the tile, in rows of cells under the header and the
     * summary ([Room.rows] null when it follows its content); null when it has none (the icon,
     * 2×1, 4×1, 2×2), where the summary stands alone with the header.
     */
    val body: Room? get() {
        if (isIcon) return null
        val above = if (summaryBeside) 1 else 2
        val h = height ?: return Room(width, null)
        return if (h > above) Room(width, h - above) else null
    }

    /**
     * What it is laid as in one column: every tile as a tile 2 wide drawn across the whole width,
     * a tile 4 wide one row taller, its header and its summary one under the other; the icon
     * keeps its cell, two side by side; one that follows its content keeps doing so. A tile 2 wide
     * that follows its content exists only here: it is never stored.
     */
    fun inOneColumn(): TileSize = when {
        isIcon || width == HALF -> this
        else -> TileSize(HALF, height?.let { it + 1 })
    }

    override fun toString(): String = stored

    /** The room a tile's body is given: [width] cells across, [rows] rows of cells high, null for as tall as its content. */
    data class Room(val width: Int, val rows: Int?)

    companion object {
        const val HALF = 2
        const val FULL = 4
        const val MAX_HEIGHT = 10
        private const val ICON = "icon"
        private const val CONTENT = "content"

        val Icon = TileSize(1, 1)

        /** A tile [width] wide, 2 or 4, [height] rows of cells high, 1 to [MAX_HEIGHT]. */
        fun of(width: Int, height: Int): TileSize {
            require(width == HALF || width == FULL) { "A tile is 2 or 4 cells wide, not $width" }
            require(height in 1..MAX_HEIGHT) { "A tile is 1 to $MAX_HEIGHT rows high, not $height" }
            return TileSize(width, height)
        }

        /** A tile across the whole width, as tall as its content. */
        val Content = TileSize(FULL, null)

        /** Every size, in the order they are offered: the icon, the half widths up, the full widths up, then the content's. */
        val ALL: List<TileSize> = listOf(Icon) +
            (1..MAX_HEIGHT).map { of(HALF, it) } +
            (1..MAX_HEIGHT).map { of(FULL, it) } +
            Content

        private val byStored = ALL.associateBy { it.stored }

        /** The size stored as [text]; null when it names none, which the services refuse. */
        fun parse(text: String?): TileSize? = text?.let { byStored[it.trim()] }

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
