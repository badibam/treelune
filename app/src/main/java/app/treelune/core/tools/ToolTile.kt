package app.treelune.core.tools

import androidx.compose.runtime.Composable
import app.treelune.core.look.TileSize

/**
 * What a tool type shows on a tool's tile, beside the header the core draws (icon and name,
 * 2×1 cells), as its size lays it out (TileSize, docs/design/tile-sizes.md). Made once per tile
 * by ToolTypeContract.rememberTile, so that its parts share what the tile has loaded.
 *
 * Nothing in a tile scrolls: what does not fit is left out, and said (TileGrid's « +12 »).
 */
interface ToolTile {

    /**
     * One component of 2×1 cells, two lines high, the same in every size that has it, its
     * actions included: beside the header 4 wide, under it 2 wide.
     */
    @Composable
    fun Summary()

    /**
     * The body under the header and the summary, in [room]: 2 or 4 cells wide, on its rows of
     * cells, or as tall as what it shows when [TileSize.Room.rows] is null. A fixed room is a
     * fixed box. Its elements are laid by TileGrid, which takes their width and lines from the
     * tool's config (ItemSpec); a body that is one drawing (a chart) fills the room.
     */
    @Composable
    fun Body(room: TileSize.Room)

    /**
     * What stands at the summary's place, 2×1, in the sizes without a body (4×1, 2×2): the
     * summary, unless the tool type shows there what its body shows better in so little room.
     */
    @Composable
    fun Glance() = Summary()

    /**
     * What the second cell of the tile in icons holds (docs/design/icon-tiles.md, « La tuile d'un
     * outil »): what the summary says without words. [NONE] by default, the icon alone.
     */
    val second: IconSecond get() = IconSecond.NONE

    /**
     * The second cell's content, one cell, without a word: the gesture's button ([IconSecond.GESTURE],
     * IconGesture), or the state ([IconSecond.STATE], [IconSecond.WAITING], IconCount), which a
     * touch passes to the tile, opening the tool. Composed only when [second] is not [IconSecond.NONE].
     */
    @Composable
    fun Second() {}
}

/**
 * What a tile's second cell in icons is (docs/design/icon-tiles.md): nothing, the icon alone
 * centred; a gesture, the canonical one of the summary, which does something else than opening;
 * a state, which opens the tool as the icon does; or what waits, counted, which takes the place of
 * the icon's waiting mark — the same thing is not read twice.
 */
enum class IconSecond { NONE, GESTURE, STATE, WAITING }
