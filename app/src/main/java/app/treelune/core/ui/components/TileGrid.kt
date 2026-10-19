package app.treelune.core.ui.components

import app.treelune.core.ui.UI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Items laid in a tile's body (ToolTile.Body): row by row in [columns] columns, [perRow] items
 * stacked in a row of cells (2: an item has room for two lines of text; 4: for one; 1: for a
 * tall item). The body is as tall as the items it shows: given [rows] (EXTENDED, SQUARE), what
 * they hold at most, each slot a row of the grid and the gap under it (LocalGridRow,
 * gridRowGapPx) shared by [perRow], so that a full body is as tall as its cells; null (FULL)
 * every item, each slot a row of the grid shared by [perRow]. An empty body takes no height. In
 * one column (CurrentTheme.oneColumn) the items stand one under the other, whatever [columns]
 * asks.
 */
@Composable
fun <T> TileGrid(rows: Int?, items: List<T>, columns: Int, perRow: Int = 2, item: @Composable (T) -> Unit) {
    val laid = if (app.treelune.core.themes.CurrentTheme.oneColumn) 1 else columns
    val slotRows = tileGridSlotRows(rows, items.size, laid, perRow)
    val gap = if (rows != null) with(LocalDensity.current) { app.treelune.core.themes.CurrentTheme.current.gridRowGapPx().toDp() } else 0.dp
    val slotHeight = (LocalGridRow.current + gap) / perRow
    Column(modifier = Modifier.fillMaxWidth()) {
        for (slotRow in 0 until slotRows) {
            Row(
                modifier = Modifier.height(slotHeight).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (column in 0 until laid) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        items.getOrNull(slotRow * laid + column)?.let { item(it) }
                    }
                }
            }
        }
    }
}

/** The rows of slots [count] items take in [columns], at most the ones [rows] hold ([perRow] each); all of them for null. */
fun tileGridSlotRows(rows: Int?, count: Int, columns: Int, perRow: Int): Int {
    val needed = (count + columns - 1) / columns
    return rows?.let { minOf(it * perRow, needed) } ?: needed
}
