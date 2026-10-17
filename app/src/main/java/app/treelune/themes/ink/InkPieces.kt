package app.treelune.themes.ink

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import app.treelune.core.icons.IconStrokes
import kotlin.math.hypot
import kotlin.math.min

/*
 * The pieces every ink component is made of (docs/design/ink-theme.md): washes of irregular
 * edge, the brush's strokes (InkBrush), the seal. Motion is slow and soft: under the finger a
 * wash darkens a step, with no ripple; a window fades in, slowing at its end; a lifted piece
 * darkens and stands off, with no shadow.
 */

/** Something touched, told whether a finger is down on it. No ripple: the wash darkening shows it. */
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

/**
 * A wash's irregular edge: four elliptical corners, each a part of the width across and of the
 * height down, scaled together when they would overlap, as CSS does a border radius. The
 * mockup's tile is (40 %, 55 %, 45 %, 50 %) / (30 %, 40 %, 35 %, 45 %).
 */
internal class WashShape(private val across: FloatArray, private val down: FloatArray) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(path(size))

    /** The edge in a box of [size], clockwise from the top left corner. */
    fun path(size: Size): Path {
        val w = size.width
        val h = size.height
        val rx = FloatArray(4) { across[it] * w }
        val ry = FloatArray(4) { down[it] * h }
        // Corners: top left, top right, bottom right, bottom left
        val f = minOf(1f, w / (rx[0] + rx[1]), w / (rx[3] + rx[2]), h / (ry[0] + ry[3]), h / (ry[1] + ry[2]))
        for (i in 0 until 4) { rx[i] *= f; ry[i] *= f }
        return Path().apply {
            moveTo(rx[0], 0f)
            lineTo(w - rx[1], 0f)
            arcTo(Rect(w - 2 * rx[1], 0f, w, 2 * ry[1]), -90f, 90f, false)
            lineTo(w, h - ry[2])
            arcTo(Rect(w - 2 * rx[2], h - 2 * ry[2], w, h), 0f, 90f, false)
            lineTo(rx[3], h)
            arcTo(Rect(0f, h - 2 * ry[3], 2 * rx[3], h), 90f, 90f, false)
            lineTo(0f, ry[0])
            arcTo(Rect(0f, 0f, 2 * rx[0], 2 * ry[0]), 180f, 90f, false)
            close()
        }
    }

    companion object {
        /** A tile's wash, as the mockup's. */
        val TILE = WashShape(floatArrayOf(0.40f, 0.55f, 0.45f, 0.50f), floatArrayOf(0.30f, 0.40f, 0.35f, 0.45f))
    }
}

/** A pebble of irregular corners, [r] dp about: a button, a card, a bubble, a toast. */
internal fun pebble(size: InkSize, a: Float, b: Float, c: Float, d: Float) =
    androidx.compose.foundation.shape.RoundedCornerShape(size.dp(a), size.dp(b), size.dp(c), size.dp(d))

/**
 * A tile's wash in [color], dense at its heart (a little above and left of the middle) and fading
 * to nothing before its irregular edge, as ink spreads in wet paper; a texture's [motif], when
 * there is one, laid within the same edge, over the wash.
 */
internal fun Modifier.tileWash(color: Color, motif: Brush?): Modifier = drawBehind {
    val edge = WashShape.TILE.path(size)
    clipPath(edge) {
        drawSpread(color, Offset(size.width * 0.4f, size.height * 0.45f))
        motif?.let { drawPath(edge, it) }
    }
}

/** A wash spreading from [center] over the whole box, an ellipse of its proportions. */
internal fun DrawScope.drawSpread(color: Color, center: Offset) {
    val w = size.width
    val h = size.height.coerceAtLeast(1f)
    // Drawn as a circle in a space squeezed to the box's height: the farthest corner its reach
    val reach = hypot(maxOf(center.x, w - center.x), maxOf(center.y, h - center.y) * w / h)
    scale(1f, h / w, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to color, 0.40f to color, 0.66f to color.copy(alpha = color.alpha * 0.5f), 0.82f to color.copy(alpha = 0f),
                center = center, radius = reach
            ),
            radius = reach, center = center
        )
    }
}

/**
 * A soft wash of [color] round what it is put on, reaching past it by a third: an icon's colour,
 * a tag's mark. It takes no room of its own.
 */
internal fun Modifier.colorWash(color: Color): Modifier = drawBehind {
    val r = min(size.width, size.height) * 0.8f
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.45f), 0.55f to color.copy(alpha = 0.30f), 0.9f to color.copy(alpha = 0f),
            center = center, radius = r
        ),
        radius = r, center = center
    )
}

/**
 * A smooth stroke of the brush across the box it is put on, [thickness] wide, in [color]: a rule,
 * an underline, a ring, a pebble's outline, an ensō covering [sweep] of a turn. Its rim at half its
 * opacity round its core, as the wet brush lays it.
 */
internal fun Modifier.brushed(shape: InkBrush.Shape, thickness: Dp, color: Color, sweep: Float = InkBrush.ENSO_SWEEP): Modifier =
    drawWithCache {
        val width = thickness.toPx()
        val traced = InkBrush.trace(shape, size.width, size.height, width, sweep)
        val rim = traced.map { (stroke, widths) -> InkBrush.outline(stroke, widths, width, 1f) }
        val core = traced.map { (stroke, widths) -> InkBrush.outline(stroke, widths, width, InkBrush.CORE) }
        onDrawBehind {
            rim.forEach { drawPath(it, color, alpha = InkBrush.RIM_ALPHA) }
            core.forEach { drawPath(it, color) }
        }
    }

/**
 * A Lucide icon traced by the brush, [size] square, in [tint]: its mask made once per size
 * (InkBrush.iconMask) and coloured as it is laid.
 */
@Composable
internal fun InkIcon(resourceId: Int, size: Dp, tint: Color, description: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val mask = remember(resourceId, px) { InkBrush.iconMask(resourceId, IconStrokes.of(context, resourceId), px) }
    Image(bitmap = mask, contentDescription = description, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

/** A seal: a square of red, its corners pressed unevenly, holding [text] (an initial, a "!") or nothing. */
@Composable
internal fun Seal(side: Dp, text: String?, round: Boolean = false) {
    val size = inkSize()
    val c = inkColors
    val shape = if (round) androidx.compose.foundation.shape.RoundedCornerShape(48, 52, 50, 46)
        else androidx.compose.foundation.shape.RoundedCornerShape(size.dp(3f), size.dp(6f), size.dp(2f), size.dp(7f))
    Box(modifier = Modifier.size(side).background(c.accent, shape), contentAlignment = Alignment.Center) {
        if (text != null) {
            BasicText(text, style = size.strong.copy(color = c.onAccent, fontSize = (side.value * 0.56f).sp, letterSpacing = 0.sp, lineHeight = (side.value * 0.7f).sp))
        }
    }
}

/** A window's content arriving: a fade slowing at its end, nothing growing nor sliding. */
@Composable
internal fun FadeIn(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(FADE_MS, easing = LinearOutSlowInEasing)) }
    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}

private const val FADE_MS = 220

/**
 * An item lifted from its list or from the grid: its wash a step darker, standing off up and to
 * the left, with no shadow; put down, it falls back to its place.
 */
@Composable
internal fun Lifted(lifted: Boolean, content: @Composable () -> Unit) {
    if (!lifted) {
        Box { content() }
        return
    }
    val size = inkSize()
    Box(
        modifier = Modifier.graphicsLayer {
            translationX = -size.dp(LIFT_DP).toPx()
            translationY = -size.dp(LIFT_DP).toPx()
        }
    ) {
        CompositionLocalProvider(LocalInkLifted provides true) { content() }
    }
}

private const val LIFT_DP = 4f
