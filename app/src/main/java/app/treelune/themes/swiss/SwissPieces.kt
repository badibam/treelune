package app.treelune.themes.swiss

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/*
 * The pieces every Swiss component is made of (docs/design/swiss-theme.md): rules and outlines of
 * whole pixels, never a shadow nor a rounding; motion is a clean cut. Under the finger a piece
 * inverts, ink and ground exchanged, with no transition and no ripple; a window fades in, linear,
 * and leaves at once; a lifted piece takes an outline and stands off up and to the left.
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

/** A rule [width] thick across the top of what it is put on, inside its bounds. */
internal fun Modifier.topRule(width: Dp, color: Color): Modifier = drawBehind {
    drawRect(color, Offset.Zero, Size(size.width, width.toPx()))
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

/** A window's content arriving: a linear fade, nothing growing nor sliding. It leaves at once. */
@Composable
internal fun FadeIn(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(FADE_MS, easing = LinearEasing)) }
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}

private const val FADE_MS = 120

/**
 * An item lifted from its list or from the grid: an ink outline round it, standing off up and to
 * the left; put down, it falls back to its place at once.
 */
@Composable
internal fun Lifted(lifted: Boolean, content: @Composable () -> Unit) {
    // One structure lifted or not: the content is the same nodes, so the grip it holds keeps the
    // drag that lifts it
    val size = swissSize()
    val ink = swissColors.ink.srgb
    val ground = swissColors.ground.srgb
    val stroke = swissStroke(2f)
    Box(
        modifier = Modifier
            .graphicsLayer {
                val lift = if (lifted) -size.dp(LIFT_DP).toPx() else 0f
                translationX = lift
                translationY = lift
            }
            .let { if (lifted) it.drawBehind { drawRect(ground) }.outline(stroke, ink) else it }
    ) { content() }
}

private const val LIFT_DP = 4f
