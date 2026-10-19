package app.treelune.themes.eink

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/*
 * The pieces every e-reader component is made of (docs/design/eink-theme.md): outlines and rules
 * of whole pixels, never a rounding nor a soft shadow; nothing moves. Under the finger a piece
 * inverts at once, with no ripple; a window appears and leaves at once; a window and a lifted
 * piece stand on a full shadow of the ink, offset down and to the right.
 */

/** Something touched, told whether a finger is down on it. No ripple: the piece inverting shows it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier = modifier.combinedClickable(interactionSource = source, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick)) {
        content(pressed)
    }
}

/** A rule [width] thick across the bottom of what it is put on, inside its bounds. */
internal fun Modifier.bottomRule(width: Dp, color: Color): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
}

/** An outline [width] thick, drawn inside the bounds over the content, square at its corners. */
internal fun Modifier.outline(width: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
}

/** The same outline in dashes of three widths: what cannot be touched. */
internal fun Modifier.dashedOutline(width: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 2 * w))))
}

/**
 * A full shadow of [color] under what it is put on, [offset] down and to the right, drawn
 * outside its bounds: a window's, a lifted piece's. No blur: the theme has no grey.
 */
internal fun Modifier.hardShadow(offset: Dp, color: Color): Modifier = drawBehind {
    val o = offset.toPx()
    drawRect(color, Offset(o, o), size)
}

/**
 * An item lifted from its list or from the grid: on the full shadow, standing off up and to the
 * left by as much; put down, it falls back to its place at once.
 */
@Composable
internal fun Lifted(lifted: Boolean, content: @Composable () -> Unit) {
    // One structure lifted or not: the content is the same nodes, so the grip it holds keeps the
    // drag that lifts it
    val c = einkColors
    val lift = einkSize().dp(LIFT_DP)
    Box(
        modifier = Modifier
            .graphicsLayer {
                val shift = if (lifted) -lift.toPx() else 0f
                translationX = shift
                translationY = shift
            }
            .drawBehind {
                if (lifted) {
                    drawRect(c.ink, Offset(lift.toPx(), lift.toPx()), size)
                    drawRect(c.ground)
                }
            }
    ) { content() }
}

private const val LIFT_DP = 4f
