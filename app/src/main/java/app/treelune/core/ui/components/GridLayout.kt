package app.treelune.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.grid.Grid
import app.treelune.core.grid.ToolPositions
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.GridTileState
import app.treelune.core.ui.UI
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * A group section in edit mode: its tiles at the [places] of the move in progress, [selectedId]
 * the tile being moved, [onSelect] what a touch on a tile does instead of its own action, by the
 * tile's id.
 */
data class GridEdit(val places: List<Grid.Tile>, val selectedId: String?, val onSelect: (String) -> Unit)

/** The height of a row of the grid a tile stands in, grown past the theme's for its header's texts (GridLayout). */
val LocalGridRow = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.unit.Dp> { error("No grid around this tile") }

/** The height of a row as the theme gives it (ThemeContract.gridRowPx), which a tile's icon is sized by: it never grows with the texts. */
val LocalGridIconRow = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.unit.Dp> { error("No grid around this tile") }

/**
 * Tiles laid on a grid: four columns of cells, each of [items] at its place in [stored] (the same
 * order), [item] drawing it. A tile is handed its item, never its index: a tile leaving (its zone
 * deleted) may draw once more on its own before the grid recomposes without it, and an index it
 * kept would then be read against the shorter list. The theme sizes a cell from the width there is (gridCellPx), a row's height from
 * the cell's width (gridRowPx), and the gaps between two columns and two rows (gridColumnGapPx,
 * gridRowGapPx), which a tile spanning several cells covers; the grid is centered in what it
 * leaves.
 *
 * A row is never lower than a tile's header needs (headerNeed): a name on two lines and its type
 * under it, in the theme's texts at the size step and Android's text size, within a tile's frame.
 * The cells stay as the theme draws them while that fits; when the texts grow past it, every row
 * grows with them and the cells stand taller than wide, rather than a header being cut. A tile
 * reads that height (LocalGridRow) for its header and its body; its icon keeps the theme's
 * (LocalGridIconRow). The sample is measured before the tiles are composed (SubcomposeLayout).
 *
 * A row is one row high, except the row of a tile that grows with its content ([grows]: a
 * tool in FULL, EXTENDED or SQUARE), which is as tall as what it shows, a row at least, rounded up to a whole pixel of
 * the theme's drawings (drawingUnit) so the rows under it stay on whole pixels. Such a tile is measured
 * once, its width fixed and its height free, and keeps the height it takes: the rest of its row
 * stays empty under it. It is as wide as the grid, alone on its rows: when it spans several
 * (EXTENDED, SQUARE), the first takes its height and the others close, with their gaps. It is never asked for its intrinsic height, which a tile holding a layout
 * measured by its constraints (BoxWithConstraints, a lazy list) cannot give. An empty cell stays
 * empty.
 *
 * In one column (CurrentTheme.oneColumn) the tiles stand where Grid.oneColumn puts them, one
 * under the other, two of a quarter side by side.
 *
 * In edit mode ([edit]), the tiles stand at its places, the theme's cells show under them, and
 * the tiles keep their content but no longer react to their own gestures: a touch selects. The
 * tile being moved is kept in view, a line of margin above and below, the screen scrolling only
 * as much as that takes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> GridLayout(items: List<T>, stored: List<Grid.Tile>, grows: List<Boolean>, edit: GridEdit?, item: @Composable (T) -> Unit) {
    require(items.size == stored.size && items.size == grows.size) { "GridLayout: ${items.size} items for ${stored.size} tiles and ${grows.size} growths" }
    if (stored.isEmpty()) return
    val placesById = edit?.places?.associateBy { it.id }
    // In one column (Appearance.oneColumn), the grid as that column shows it, its places read from the grid's
    val grid = stored.map { placesById?.get(it.id) ?: it }
    val tiles = if (CurrentTheme.oneColumn) Grid.oneColumn(grid, grid.filterIndexed { i, _ -> grows[i] }.map { it.id }.toSet()) else grid
    val rowCount = Grid.rowCount(tiles)
    val cells = if (edit != null) rowCount * Grid.COLUMNS else 0

    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val cellPx = CurrentTheme.current.gridCellPx(constraints.maxWidth)
        val rowPx = CurrentTheme.current.gridRowPx(cellPx)
        val gap = CurrentTheme.current.gridColumnGapPx()
        val rowGap = CurrentTheme.current.gridRowGapPx()
        val unit = kotlin.math.ceil(CurrentTheme.current.drawingUnit()).toInt().coerceAtLeast(1)
        val framePx = with(LocalDensity.current) {
            listOf(DisplayMode.ICON, DisplayMode.MINIMAL, DisplayMode.LINE, DisplayMode.CONDENSED)
                .maxOf { CurrentTheme.current.tileFrame(it) }.roundToPx()
        }
        val iconRow = with(LocalDensity.current) { rowPx.toDp() }

        /** The tiles on rows [rowPx] high, the height every tile reads (LocalGridRow). */
        @Composable
        fun Tiles(rowPx: Int) = Layout(
            content = {
                // In edit mode, first the cells, one per cell of the grid, then the tiles over them
                repeat(cells) { CurrentTheme.current.GridCell() }
                tiles.forEachIndexed { i, place ->
                    key(place.id) {
                        val selected = edit?.selectedId == place.id
                        val requester = remember { BringIntoViewRequester() }
                        if (selected) {
                            LaunchedEffect(place) {
                                val line = rowPx / 2f
                                requester.bringIntoView(Rect(0f, -line, place.width * (cellPx + gap).toFloat(), place.height * (rowPx + rowGap) + line))
                            }
                        }
                        val state = when {
                            edit?.selectedId == null -> GridTileState.PLAIN
                            selected -> GridTileState.CHOSEN
                            else -> GridTileState.ASIDE
                        }
                        Box(modifier = Modifier.bringIntoViewRequester(requester)) {
                            CurrentTheme.current.GridTile(state) {
                                androidx.compose.runtime.CompositionLocalProvider(
                                    LocalGridRow provides with(LocalDensity.current) { rowPx.toDp() },
                                    LocalGridIconRow provides iconRow
                                ) { item(items[i]) }
                            }
                            // In edit mode the tile's own gestures give way to a touch that selects
                            if (edit != null) {
                                Box(
                                    modifier = Modifier.matchParentSize().clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { edit.onSelect(place.id) }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { measurables, _ ->
            val cell = cellPx
            /** The pixels [count] cells span, the gaps between them included. */
            fun span(count: Int) = count * cell + (count - 1).coerceAtLeast(0) * gap
            val left = { column: Int -> column * (cell + gap) }

            // A tile that grows is measured first, at its width and a free height; its first row
            // takes that height in whole pixels of the theme, its other rows close
            val rowHeights = IntArray(rowCount) { rowPx }
            val closed = BooleanArray(rowCount)
            val grown = tiles.mapIndexedNotNull { i, tile ->
                if (!grows[i]) return@mapIndexedNotNull null
                val width = span(tile.width)
                val placeable = measurables[cells + i].measure(Constraints(minWidth = width, maxWidth = width, minHeight = rowPx))
                rowHeights[tile.row] = maxOf(rowHeights[tile.row], (placeable.height + unit - 1) / unit * unit)
                for (row in tile.row + 1 until tile.bottom) closed[row] = true
                i to placeable
            }.toMap()
            // Each row's top, a gap after every open row but the last one open; a closed row stands
            // where the next one starts
            val lastOpen = (rowCount - 1 downTo 0).firstOrNull { !closed[it] } ?: -1
            val rowTops = IntArray(rowCount + 1)
            for (row in 0 until rowCount) {
                rowTops[row + 1] = rowTops[row] + if (closed[row]) 0 else rowHeights[row] + if (row < lastOpen) rowGap else 0
            }
            /** The pixels from the top of [row] to the bottom of the row before [end]. */
            fun height(row: Int, end: Int) = rowTops[end] - rowTops[row] - if (end < rowCount) rowGap else 0

            val cellPlaceables = (0 until cells).map { index ->
                measurables[index].measure(Constraints.fixed(cell, rowHeights[index / Grid.COLUMNS]))
            }
            val tilePlaceables = tiles.mapIndexed { i, tile ->
                grown[i] ?: measurables[cells + i].measure(Constraints.fixed(span(tile.width), height(tile.row, tile.bottom)))
            }
            layout(span(Grid.COLUMNS), rowTops[rowCount]) {
                cellPlaceables.forEachIndexed { index, placeable ->
                    placeable.place(left(index % Grid.COLUMNS), rowTops[index / Grid.COLUMNS])
                }
                tilePlaceables.forEachIndexed { i, placeable ->
                    placeable.place(left(tiles[i].column), rowTops[tiles[i].row])
                }
            }
        }

        // A header measured first and never placed gives the height a row must have, which the
        // tiles are then composed with
        SubcomposeLayout { constraints ->
            val headerNeed = subcompose(HEADER_SAMPLE) { HeaderSample() }.first().measure(Constraints(maxWidth = 2 * cellPx)).height + 2 * framePx
            val row = maxOf(rowPx, (headerNeed + unit - 1) / unit * unit)
            val grid = subcompose(TILES) { Tiles(row) }.first().measure(constraints)
            layout(grid.width, grid.height) { grid.place(0, 0) }
        }
    }
}

/** GridLayout's slots: its header sample, and its tiles, the same slot whatever their rows' height so they keep their state. */
private const val HEADER_SAMPLE = "header_sample"
private const val TILES = "tiles"

/**
 * [content] as wide as a grid of tiles in the same width and centred as it is: the cards set under
 * a zone's grid (its variables, its routines) keep to the tiles' edges. A theme on whole pixels
 * (the retro) spans its grid over whole cells, inside the width; elsewhere the grid fills it.
 */
@Composable
fun GridWide(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellPx = CurrentTheme.current.gridCellPx(constraints.maxWidth)
        val gap = CurrentTheme.current.gridColumnGapPx()
        val width = (Grid.COLUMNS * cellPx + (Grid.COLUMNS - 1) * gap).coerceAtMost(constraints.maxWidth)
        Layout(content = content) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight)) }
            // Placed as the grid's Box places it (Alignment.TopCenter), to the same pixel
            val left = ((constraints.maxWidth - width) / 2f).roundToInt()
            layout(constraints.maxWidth, placeables.sumOf { it.height }) {
                var top = 0
                placeables.forEach { it.place(left, top); top += it.height }
            }
        }
    }
}

/**
 * What a tile's header holds at most, in the theme's texts (UI.TileHeader): a name on two lines
 * and its type under it. GridLayout measures it to know the height a row must give.
 */
@Composable
private fun HeaderSample() {
    androidx.compose.foundation.layout.Column {
        UI.Text("Wg\nWg", app.treelune.core.ui.TextType.HEADING, maxLines = 2)
        UI.Text("Wg", app.treelune.core.ui.TextType.CAPTION, maxLines = 1)
    }
}
