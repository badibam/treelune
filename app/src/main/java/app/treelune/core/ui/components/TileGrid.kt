package app.treelune.core.ui.components

import app.treelune.core.ui.UI

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import app.treelune.core.look.ItemLayout
import app.treelune.core.look.TileSize
import app.treelune.core.ui.TextType

/** How the tile being drawn lays its elements: its tool's (UI.ToolCard, ItemSpec); null outside a tile that lists some. */
val LocalItemLayout = staticCompositionLocalOf<ItemLayout?> { null }

/** The lines of text an element of the tile being drawn has (ItemLayout.lines), which TileLine keeps its text to. */
val LocalItemLines = staticCompositionLocalOf { 2 }

/**
 * Elements laid in a tile's body (ToolTile.Body, docs/design/tile-sizes.md), in [room]: on the
 * columns their width gives in that room (ItemLayout.columns: two of halves 4 wide, one 2 wide or
 * in one column), each as tall as its lines of text, measured at display with the theme's text
 * and the size setting. In a fixed room, as many as it holds: their places come from the room,
 * never from how many there are, so one arriving or leaving moves none of the others, and the
 * height left is shared between the places; what does not fit is not shown, the last place saying
 * how many are left (« +12 »). In a room that follows its content, every element, the body as tall
 * as they are; empty, it takes no height.
 */
@Composable
fun <T> TileGrid(room: TileSize.Room, items: List<T>, item: @Composable (T) -> Unit) {
    val layout = requireNotNull(LocalItemLayout.current) { "TileGrid outside a tile that lays elements" }
    val columns = layout.columns(room.width)
    val padding = UI.Space.XS
    val context = androidx.compose.ui.platform.LocalContext.current
    val s = androidx.compose.runtime.remember { app.treelune.core.strings.Strings.`for`(context = context) }
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        // One element's height: its lines of the theme's text, measured, and air above and under
        val sample = subcompose("sample") {
            UI.Text(List(layout.lines) { "Ag" }.joinToString("\n"), TextType.BODY, maxLines = layout.lines)
        }.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val itemPx = sample.height + 2 * padding.roundToPx()
        val bodyPx = if (room.rows != null && constraints.hasBoundedHeight) constraints.maxHeight else null
        val plan = tileGridPlan(itemPx, bodyPx, items.size, columns)
        val shown = items.take(plan.shown)
        val content = subcompose("items") {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (row in 0 until plan.rowsUsed) {
                    Row(
                        modifier = Modifier.height(plan.slotPx.toDp()).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (column in 0 until columns) {
                            val index = row * columns + column
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                CompositionLocalProvider(LocalItemLines provides layout.lines) {
                                    when {
                                        index < shown.size -> item(shown[index])
                                        index == shown.size && plan.more > 0 -> UI.Text(s.shared("tile_more").format(plan.more), TextType.CAPTION, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val height = bodyPx ?: content.height
        layout(constraints.maxWidth, height) { content.place(0, 0) }
    }
}

/**
 * Where a body's elements go: [shown] of them on [rowsUsed] rows of places [slotPx] high, and
 * [more] left out (the last place says how many).
 */
data class TileGridPlan(val shown: Int, val more: Int, val rowsUsed: Int, val slotPx: Int)

/**
 * The plan for [count] elements [itemPx] high on [columns]: in a fixed body [bodyPx] high, as many
 * rows of places as it holds (one at least), each as tall as the body shared between them,
 * whatever [count]; when they do not all fit, the last place says how many are left. Without
 * [bodyPx], every element, each row [itemPx] high.
 */
fun tileGridPlan(itemPx: Int, bodyPx: Int?, count: Int, columns: Int): TileGridPlan {
    fun rows(n: Int) = (n + columns - 1) / columns
    if (bodyPx == null) return TileGridPlan(count, 0, rows(count), itemPx)
    val places = maxOf(1, bodyPx / itemPx)
    val capacity = places * columns
    val shown = if (count <= capacity) count else capacity - 1
    val more = count - shown
    return TileGridPlan(shown, more, rows(shown + if (more > 0) 1 else 0), bodyPx / places)
}
