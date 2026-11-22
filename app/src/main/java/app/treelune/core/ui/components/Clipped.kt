package app.treelune.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * A long content on a page shown at [maxHeight] at most, cut there, so that the page keeps its
 * length whatever the content's: it is read whole elsewhere, [onOpen] (a place of its own, the
 * text of an identity). Taller than that, a touch on it opens it, and the button [readLabel] under
 * it says so; one that holds shows whole, without the button.
 */
@Composable
fun Clipped(onOpen: () -> Unit, readLabel: String, maxHeight: Dp = CLIPPED_HEIGHT, content: @Composable () -> Unit) {
    // Whether the content is taller than the cut, learnt when it is measured
    var overflows by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .clickable(enabled = overflows, onClick = onOpen)
                .layout { measurable, constraints ->
                    // Measured at its whole height, laid at the cut's
                    val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                    val limit = maxHeight.roundToPx()
                    overflows = placeable.height > limit
                    layout(placeable.width, minOf(placeable.height, limit)) { placeable.place(0, 0) }
                }
        ) { content() }
        if (overflows) UI.Button(type = ButtonType.DEFAULT, onClick = onOpen) { UI.Text(readLabel, TextType.BODY) }
    }
}

/** The height a long content is cut at on its page: about ten lines of text. */
val CLIPPED_HEIGHT = 240.dp
