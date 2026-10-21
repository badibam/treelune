package app.treelune.themes.metro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/*
 * The pieces every metro component is made of (docs/themes/metro.md): panels with corners of
 * 4 dp, lines of a colour, round stops. Under the finger a piece darkens with the night blue, with
 * no ripple; a window slides in as a train enters the station; a lifted piece rises on a flat shadow.
 */

/** Something touched, told whether a finger is down on it. No ripple: the piece darkening shows it. */
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

/** A rounded panel of [color], corners of [radius], behind the content. */
internal fun Modifier.panel(color: Color, radius: Dp): Modifier = drawBehind {
    val r = radius.toPx()
    drawRoundRect(color, cornerRadius = CornerRadius(r, r))
}

/** An outline [width] thick, inside the bounds over the content, its corners of [radius]. */
internal fun Modifier.outline(width: Dp, color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val r = (radius.toPx() - w / 2).coerceAtLeast(0f)
    drawRoundRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(r, r), style = Stroke(w))
}

/**
 * A panel's head: a line [width] thick across its top, of [color], the panel's corners rounding its
 * ends; then the panel itself, under it.
 */
internal fun Modifier.headLine(width: Dp, color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val r = radius.toPx()
    // Cut from a rounded rectangle twice its height, so its top corners follow the panel's
    clipRect(0f, 0f, size.width, w) { drawRoundRect(color, Offset.Zero, Size(size.width, w * 2 + r), CornerRadius(r, r)) }
}

/** A flat shadow [depth] thick under the panel, in [color]: a piece standing on the page. */
internal fun Modifier.plate(depth: Dp, color: Color, radius: Dp): Modifier = drawBehind {
    val d = depth.toPx()
    val r = radius.toPx()
    drawRoundRect(color, Offset(0f, d), size, CornerRadius(r, r))
}

/** Under the finger, the night blue laid over the piece at [alpha], inside its corners. */
internal fun Modifier.pressedTint(pressed: Boolean, color: Color, radius: Dp, alpha: Float = PRESSED_ALPHA): Modifier =
    if (!pressed) this else drawWithContent {
        drawContent()
        val r = radius.toPx()
        drawRoundRect(color, cornerRadius = CornerRadius(r, r), alpha = alpha)
    }

/** How much of the night blue darkens a piece under the finger. */
internal const val PRESSED_ALPHA = 0.12f

/**
 * A window's content arriving as a train enters the station: rising [SLIDE_DP] dp and fading in,
 * easing out. It leaves at once, its caller removing it.
 */
@Composable
internal fun SlideIn(content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(SLIDE_MS, easing = FastOutSlowInEasing)) }
    val slide = metroSize().dp(SLIDE_DP)
    Box(modifier = Modifier.graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * slide.toPx()
    }) { content() }
}

private const val SLIDE_MS = 150
private const val SLIDE_DP = 8f

/**
 * An item lifted from its list or from the grid: it rises [LIFT_DP] dp over a flat shadow of
 * [PLATE_DP] dp; put down, it falls back to its place at once.
 */
@Composable
internal fun Lifted(lifted: Boolean, radius: Dp, content: @Composable () -> Unit) {
    // One structure lifted or not: the content is the same nodes, so the grip it holds keeps the
    // drag that lifts it
    val size = metroSize()
    val shadow = metroColors.ink.srgb.copy(alpha = SHADOW_ALPHA)
    val plate = size.dp(PLATE_DP)
    Box(
        modifier = Modifier
            .graphicsLayer { translationY = if (lifted) -size.dp(LIFT_DP).toPx() else 0f }
            .let { if (lifted) it.plate(plate, shadow, radius) else it }
    ) { content() }
}

private const val LIFT_DP = 2f
private const val PLATE_DP = 4f
private const val SHADOW_ALPHA = 0.3f
