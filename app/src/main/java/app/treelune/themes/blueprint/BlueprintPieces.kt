package app.treelune.themes.blueprint

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/*
 * The pieces every blueprint component is made of (docs/themes/blueprint.md): fine lines
 * drawn with a ruler, each side of a frame running past its corners as on an architect's sketch,
 * hatching, the title block's double frame. Under the finger a piece fills with light hatching,
 * with no ripple; a window fades in; a lifted piece thickens its lines and leaves its place drawn
 * in dashes.
 */

/** Something touched, told whether a finger is down on it. No ripple: the piece's hatching shows it. */
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

/** A flat fill of [color] behind the content. */
internal fun Modifier.fill(color: Color): Modifier = drawBehind { drawRect(color) }

/**
 * A frame of four lines [width] thick over the content, inside the bounds, each running [overshoot]
 * past the two corners it meets: the sides of an architect's sketch cross instead of joining.
 * Dashed when [dashed] (a cell of the grid in edit mode, a disabled button), without overshoot then.
 */
internal fun Modifier.sketchFrame(width: Dp, color: Color, overshoot: Dp, dashed: Boolean = false): Modifier = drawWithContent {
    drawContent()
    drawSketchFrame(Offset.Zero, size, width.toPx(), color, if (dashed) 0f else overshoot.toPx(), dashed)
}

/** The same frame drawn on [size] from [topLeft], for a piece that draws it beside other strokes. */
internal fun DrawScope.drawSketchFrame(topLeft: Offset, size: Size, w: Float, color: Color, over: Float, dashed: Boolean = false) {
    val left = topLeft.x + w / 2
    val top = topLeft.y + w / 2
    val right = topLeft.x + size.width - w / 2
    val bottom = topLeft.y + size.height - w / 2
    val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4 * w, 3 * w)) else null
    // Butt ends: a line stops where it is meant to, its overshoot measured from the corner
    drawLine(color, Offset(left - over, top), Offset(right + over, top), w, StrokeCap.Butt, dash)
    drawLine(color, Offset(left - over, bottom), Offset(right + over, bottom), w, StrokeCap.Butt, dash)
    drawLine(color, Offset(left, top - over), Offset(left, bottom + over), w, StrokeCap.Butt, dash)
    drawLine(color, Offset(right, top - over), Offset(right, bottom + over), w, StrokeCap.Butt, dash)
}

/**
 * The title block's frame: a line [width] thick round the bounds, and a hairline [gap] inside it,
 * as the border of a plan's sheet.
 */
internal fun Modifier.doubleFrame(width: Dp, gap: Dp, color: Color): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
    val inner = gap.toPx() + w
    val hair = w * HAIRLINE
    drawRect(color, Offset(inner, inner), Size(size.width - 2 * inner, size.height - 2 * inner), style = Stroke(hair))
}

/**
 * Hatching over the bounds: lines at 45 degrees, [width] thick, [pitch] apart, in [color], cut to
 * the bounds. What a section through a wall shows on a plan; here, what is done of a gauge, a
 * bar of a chart, a piece pressed.
 */
internal fun DrawScope.drawHatching(topLeft: Offset, size: Size, color: Color, width: Float, pitch: Float) {
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(color, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), width)
            x += pitch
        }
    }
}

/** Hatching behind the content, over the whole bounds. */
internal fun Modifier.hatched(color: Color, width: Dp, pitch: Dp): Modifier = drawBehind {
    drawHatching(Offset.Zero, size, color, width.toPx(), pitch.toPx())
}

/** Under the finger, light hatching of [color] laid over the piece. */
internal fun Modifier.pressedHatch(pressed: Boolean, color: Color, width: Dp, pitch: Dp): Modifier =
    if (!pressed) this else drawWithContent {
        drawContent()
        drawHatching(Offset.Zero, size, color.copy(alpha = PRESSED_ALPHA), width.toPx(), pitch.toPx())
    }

/** How strong the hatching of a piece under the finger is. */
internal const val PRESSED_ALPHA = 0.45f

/** A hairline's width, as a part of the theme's line: the inner line of a double frame. */
internal const val HAIRLINE = 0.6f

/** A window's content arriving: fading in over [FADE_MS] ms. It leaves at once, its caller removing it. */
@Composable
internal fun FadeIn(content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(FADE_MS, easing = LinearEasing)) }
    Box(modifier = Modifier.graphicsLayer { alpha = shown.value }) { content() }
}

private const val FADE_MS = 120

/**
 * An item lifted from its list or from the grid: it moves [LIFT_DP] dp up and to the left,
 * leaving its place drawn in dashed lines; put down, it falls back to its place at once. A tile
 * lifted also thickens its frame (LocalBlueprintLifted, read by the tile).
 */
@Composable
internal fun Lifted(lifted: Boolean, content: @Composable () -> Unit) {
    // One structure lifted or not: the content is the same nodes, so the grip it holds keeps the
    // drag that lifts it
    val size = blueprintSize()
    val line = blueprintColors.dim.srgb
    val stroke = blueprintStroke(LINE)
    val lift = size.dp(LIFT_DP)
    Box(
        modifier = Modifier
            .drawBehind { if (lifted) drawSketchFrame(Offset.Zero, this.size, stroke.toPx(), line, 0f, dashed = true) }
            .graphicsLayer {
                translationX = if (lifted) -lift.toPx() else 0f
                translationY = if (lifted) -lift.toPx() else 0f
            }
    ) { content() }
}

private const val LIFT_DP = 4f

/** The theme's line, in dp; a lifted tile's, thicker; how far a frame's sides run past its corners. */
internal const val LINE = 1.5f
internal const val LIFTED_LINE = 2.5f
internal const val OVERSHOOT = 3f
