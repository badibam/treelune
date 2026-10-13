package app.treelune.core.ui.components

import app.treelune.core.ui.UI
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints

/**
 * Choices of one size in a grid as wide as it is given: as many columns as cells hold at the
 * width [sample]'s cell takes, each row shared in equal parts, the last one left short. A cell
 * fills its part, a button in it as well (LocalButtonFill), its content centred. The
 * columns follow the theme and the interface's size, never a count written in a screen.
 *
 * @param sample The choice whose cell sets the least width of every cell; a longer content
 *   than its own wraps in its cell
 */
@Composable
fun <T> ChoiceGrid(items: List<T>, sample: T, modifier: Modifier = Modifier, cell: @Composable (T) -> Unit) {
    val gap = UI.Space.S
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val least = subcompose(ChoiceGridSlot.SAMPLE) { cell(sample) }
            .maxOfOrNull { it.measure(Constraints(maxHeight = constraints.maxHeight)).width } ?: 0
        val columns = ((width + gapPx) / (least + gapPx).coerceAtLeast(1)).coerceAtLeast(1)
        val part = (width - gapPx * (columns - 1)) / columns
        val cells = subcompose(ChoiceGridSlot.CELLS) {
            CompositionLocalProvider(LocalButtonFill provides true) { items.forEach { item -> key(item) { cell(item) } } }
        }.map { it.measure(Constraints.fixedWidth(part)) }
        val rows = cells.chunked(columns)
        val heights = rows.map { row -> row.maxOf { it.height } }
        val height = heights.sum() + gapPx * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, placeable -> placeable.place(c * (part + gapPx), y) }
                y += heights[r] + gapPx
            }
        }
    }
}

private enum class ChoiceGridSlot { SAMPLE, CELLS }
