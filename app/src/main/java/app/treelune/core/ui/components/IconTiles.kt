package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
 * One part of a strip of icons (docs/design/icon-tiles.md): [first], one cell — an icon, which
 * opens its thing —, and [second], the cell beside it in a row or under it in a column, when the
 * part is drawn with its summary. A pair is never cut: it shows whole or not at all.
 */
class IconPiece(val key: String, val first: @Composable () -> Unit, val second: (@Composable () -> Unit)? = null) {
    /** The cells it takes: 1, or 2 with its second. */
    val cells: Int get() = if (second != null) 2 else 1
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
 */
@Composable
fun IconStrip(size: TileSize, pieces: List<IconPiece>) {
    val shown = pieces.take(IconStrips.fitting(size.length, pieces.map { it.cells }))
    val folds = CurrentTheme.oneColumn && !size.vertical && size.length > IconStrips.ONE_COLUMN_ROW
    when {
        folds -> Column(modifier = Modifier.fillMaxSize()) {
            IconStrips.fold(shown.map { it.cells }, IconStrips.ONE_COLUMN_ROW).forEach { row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { i -> key(shown[i].key) { Cells(shown[i]) } }
                    val left = IconStrips.ONE_COLUMN_ROW - row.sumOf { shown[it].cells }
                    if (left > 0) Spacer(modifier = Modifier.weight(left.toFloat()))
                }
            }
        }
        size.vertical -> Column(modifier = Modifier.fillMaxSize()) {
            shown.forEach { piece -> key(piece.key) { Cells(piece) } }
            val left = size.length - shown.sumOf { it.cells }
            if (left > 0) Spacer(modifier = Modifier.weight(left.toFloat()))
        }
        else -> Row(modifier = Modifier.fillMaxSize()) {
            shown.forEach { piece -> key(piece.key) { Cells(piece) } }
            val left = size.length - shown.sumOf { it.cells }
            if (left > 0) Spacer(modifier = Modifier.weight(left.toFloat()))
        }
    }
}

/** A part's cells in a column. */
@Composable
private fun ColumnScope.Cells(piece: IconPiece) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { piece.first() }
    piece.second?.let { second -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { second() } }
}

/** A part's cells in a row. */
@Composable
private fun RowScope.Cells(piece: IconPiece) {
    Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { piece.first() }
    piece.second?.let { second -> Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) { second() } }
}

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
