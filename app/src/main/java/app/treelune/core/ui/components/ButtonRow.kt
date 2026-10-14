package app.treelune.core.ui.components

import app.treelune.core.ui.UI
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints

/**
 * Whether a button takes the whole width it is given, its content centred: a theme's button
 * reads it (ButtonRow's stacked buttons). False, a button keeps the width of its content.
 */
val LocalButtonFill = staticCompositionLocalOf { false }

/**
 * Buttons side by side when they all hold on one line at their own width, else each on its own
 * line across the whole width, in the order given (the main one first). Never a part of them
 * moved to a second line: two groups would show where there is one, and the cut would change
 * with the language. [align] places the line: at the start, in the middle (a form's actions)
 * or at the end.
 *
 * The content is composed once to measure the buttons at their own width, and once more,
 * filling, when they do not hold on one line.
 */
@Composable
fun ButtonRow(align: Alignment.Horizontal = Alignment.Start, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val gap = UI.Space.M
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val free = Constraints(maxHeight = constraints.maxHeight)
        val own = subcompose(ButtonRowSlot.OWN, content).map { it.measure(free) }
        val line = own.sumOf { it.width } + gapPx * (own.size - 1).coerceAtLeast(0)
        if (line <= width) {
            val height = own.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = align.align(line, width, layoutDirection)
                own.forEach { button ->
                    button.place(x, (height - button.height) / 2)
                    x += button.width + gapPx
                }
            }
        } else {
            val filling = subcompose(ButtonRowSlot.FILLING) {
                CompositionLocalProvider(LocalButtonFill provides true, content = content)
            }.map { it.measure(Constraints(maxWidth = width, maxHeight = constraints.maxHeight)) }
            val height = filling.sumOf { it.height } + gapPx * (filling.size - 1).coerceAtLeast(0)
            layout(width, height) {
                var y = 0
                filling.forEach { button ->
                    button.place((width - button.width) / 2, y)
                    y += button.height + gapPx
                }
            }
        }
    }
}

private enum class ButtonRowSlot { OWN, FILLING }
