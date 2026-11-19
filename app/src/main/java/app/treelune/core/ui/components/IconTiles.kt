package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.treelune.core.grid.Grid
import app.treelune.core.look.TileSize
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * A tile drawn inside another (a zone's pieces, docs/design/icon-tiles.md): at this size, whatever
 * its own, and without its frame, the outer tile's frame alone saying « tile » (UI.LookCard).
 */
val LocalInnerTile = androidx.compose.runtime.staticCompositionLocalOf<TileSize?> { null }

/**
 * One part of a strip of icons (docs/design/icon-tiles.md): [content] drawn across its [cells], 1
 * or 2 — an icon, which opens its thing, or a pair, the icon and what its summary says without
 * words. A pair is never cut: it shows whole or not at all.
 */
class IconPiece(val key: String, val cells: Int, val content: @Composable () -> Unit)

/** A pair's two cells, [first] then [second], under each other when [vertical], side by side otherwise. */
@Composable
fun IconPair(vertical: Boolean, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (vertical) Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { first() }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { second() }
    } else Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { first() }
        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { second() }
    }
}

/** Where the parts of a strip of icons go: pure placement, read again at each drawing. */
object IconStrips {

    /** The cells a row of one column holds: two halves of the width. */
    const val ONE_COLUMN_ROW = 2

    /** How many of the parts [cells] wide fit in [length] cells, in their order: the first that does not fit ends the strip. */
    fun fitting(length: Int, cells: List<Int>): Int {
        var used = 0
        cells.forEachIndexed { i, c ->
            if (used + c > length) return i
            used += c
        }
        return cells.size
    }

    /**
     * The parts [cells] wide folded on rows of [perRow] cells, in their order, as indices per row:
     * a pair that does not fit in what is left of a row goes to the next one, the cell it leaves
     * staying empty.
     */
    fun fold(cells: List<Int>, perRow: Int): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var left = 0
        cells.forEachIndexed { i, c ->
            if (c > left) { rows += mutableListOf<Int>(); left = perRow }
            rows.last() += i
            left -= c
        }
        return rows
    }

    /**
     * The cells a strip of icons [size] holding parts [cells] wide takes in one column: a column
     * stays as it is, each cell on half the width; a row of 2 cells at most takes the whole width
     * on one row; a longer one folds by rows of two ([fold]), as many rows as that gives, one at
     * least.
     */
    fun oneColumn(size: TileSize, cells: List<Int>): Grid.Size = when {
        size.vertical || size.isIcon -> Grid.Size(ONE_COLUMN_ROW, size.length)
        size.length <= ONE_COLUMN_ROW -> Grid.Size(Grid.COLUMNS, 1)
        else -> Grid.Size(Grid.COLUMNS, fold(cells.take(fitting(size.length, cells)), ONE_COLUMN_ROW).size.coerceAtLeast(1))
    }
}

/**
 * The parts of a tile in icons [size] laid on its cells, each cell an equal share of the tile:
 * along a column or a row, as many parts as hold in its length, what is left empty. In one column
 * (CurrentTheme.oneColumn) a row longer than two cells folds by rows of two (IconStrips.fold).
 * The theme's divider parts two parts, never the two cells of a pair: across a column, upright
 * along a row; folded, between its rows alone.
 */
@Composable
fun IconStrip(size: TileSize, pieces: List<IconPiece>) {
    val shown = pieces.take(IconStrips.fitting(size.length, pieces.map { it.cells }))
    when {
        folds(size) -> Column(modifier = Modifier.fillMaxSize()) {
            IconStrips.fold(shown.map { it.cells }, IconStrips.ONE_COLUMN_ROW).forEachIndexed { index, row ->
                if (index > 0) UI.Divider()
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { i -> key(shown[i].key) { Box(modifier = Modifier.weight(shown[i].cells.toFloat()).fillMaxSize()) { shown[i].content() } } }
                    val left = IconStrips.ONE_COLUMN_ROW - row.sumOf { shown[it].cells }
                    if (left > 0) Spacer(modifier = Modifier.weight(left.toFloat()))
                }
            }
        }
        size.vertical -> Column(modifier = Modifier.fillMaxSize()) {
            shown.forEachIndexed { index, piece -> if (index > 0) UI.Divider(); key(piece.key) { Box(modifier = Modifier.weight(piece.cells.toFloat()).fillMaxWidth()) { piece.content() } } }
            val left = size.length - shown.sumOf { it.cells }
            if (left > 0) Spacer(modifier = Modifier.weight(left.toFloat()))
        }
        else -> Row(modifier = Modifier.fillMaxSize()) {
            shown.forEachIndexed { index, piece -> if (index > 0) UI.VerticalDivider(); key(piece.key) { Box(modifier = Modifier.weight(piece.cells.toFloat()).fillMaxSize()) { piece.content() } } }
            val left = size.length - shown.sumOf { it.cells }
            if (left > 0) Spacer(modifier = Modifier.weight(left.toFloat()))
        }
    }
}

/** Whether a strip of [size] folds by rows of two: a row longer than two cells, in one column. */
@Composable
fun folds(size: TileSize): Boolean = CurrentTheme.oneColumn && !size.vertical && size.length > IconStrips.ONE_COLUMN_ROW

/** A second cell's gesture: the action's button, its icon alone (ToolTile.Second). */
@Composable
fun IconGesture(action: ButtonAction, onClick: () -> Unit) =
    UI.ActionButton(action = action, display = ButtonDisplay.ICON, size = Size.S, onClick = onClick)

/** A second cell that counts what waits: the number, or a check when nothing does (ToolTile.Second). */
@Composable
fun IconCount(count: Int) {
    if (count > 0) UI.Text(count.toString(), TextType.HEADING, maxLines = 1)
    else UI.Icon("check")
}
