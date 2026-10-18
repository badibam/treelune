package app.treelune.themes.paper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/*
 * The pieces every cut paper component is made of (docs/design/paper-theme.md): sheets with
 * uneven corners, each on a hard shadow shifted down and right, never blurred; a tile laid a
 * little askew, the same way at every drawing. Under the finger a sheet is pressed flat onto its
 * shadow, with no ripple; a window falls in as a sheet laid on the table; a lifted piece rises on
 * a longer shadow, more askew.
 */

/** Something touched, told whether a finger is down on it. No ripple: the sheet pressed flat shows it. */
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
 * The four ways a sheet's corners are cut, each a different radius at each corner, in dp of the
 * mockup: no two neighbours cut alike. A piece takes one by its kind, a tile by its seed.
 */
internal enum class Cut(private val topStart: Float, private val topEnd: Float, private val bottomEnd: Float, private val bottomStart: Float) {
    A(3f, 7f, 4f, 6f),
    B(6f, 3f, 7f, 4f),
    C(4f, 6f, 3f, 7f),
    D(7f, 4f, 6f, 3f);

    /** The shape at [size]. */
    fun shape(size: PaperSize): Shape = RoundedCornerShape(size.dp(topStart), size.dp(topEnd), size.dp(bottomEnd), size.dp(bottomStart))
}

/**
 * How a tile of [seed] is laid: its corners' cut and its tilt, from −[MAX_TILT] to +[MAX_TILT]
 * degrees in tenths, both read from the seed's hash, so a tile keeps them wherever it moves.
 */
internal class Laid(seed: String) {
    private val hash = seed.hashCode() and Int.MAX_VALUE
    val cut: Cut = Cut.entries[hash % Cut.entries.size]
    val tilt: Float = ((hash / Cut.entries.size) % (TILT_STEPS * 2 + 1) - TILT_STEPS) * MAX_TILT / TILT_STEPS
}

/** The largest tilt of a tile, in degrees: past it, a text on it blurs. */
internal const val MAX_TILT = 1.5f
private const val TILT_STEPS = 15

/** The hard shadow's shift, down and right, in dp of the mockup. */
internal const val SHADOW_X = 2f
internal const val SHADOW_Y = 3f

/**
 * A sheet of [color] cut in [shape], on its hard shadow shifted ([dx], [dy]); [pressed], it lies
 * flat on its shadow, which it then hides. The shift is always a layer, pressed or not, so the
 * content under it is never recreated.
 */
internal fun Modifier.sheet(color: Color, shape: Shape, shadow: Color, dx: Dp, dy: Dp, pressed: Boolean = false): Modifier = this
    .graphicsLayer {
        translationX = if (pressed) dx.toPx() else 0f
        translationY = if (pressed) dy.toPx() else 0f
    }
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        if (!pressed && shadow.alpha > 0f) translate(dx.toPx(), dy.toPx()) { drawOutline(outline, shadow) }
        drawOutline(outline, color)
    }

/** A sheet on the theme's own shadow, at [size]. */
@Composable
internal fun Modifier.paperSheet(color: Color, shape: Shape, pressed: Boolean = false): Modifier {
    val size = paperSize()
    return sheet(color, shape, paperColors.shadow, size.dp(SHADOW_X), size.dp(SHADOW_Y), pressed)
}

/** An outline [width] thick of [color] inside the bounds, over the content, following [shape]. */
internal fun Modifier.outline(width: Dp, color: Color, shape: Shape): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val inner = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
    translate(w / 2, w / 2) { drawOutline(shape.createOutline(inner, layoutDirection, this), color, style = Stroke(w)) }
}

/** Under the finger, [color] laid over a piece without a shadow at [alpha], inside its shape. */
internal fun Modifier.pressedTint(pressed: Boolean, color: Color, shape: Shape, alpha: Float = PRESSED_ALPHA): Modifier =
    if (!pressed) this else drawWithContent {
        drawContent()
        drawOutline(shape.createOutline(size, layoutDirection, this), color, alpha = alpha)
    }

/** How much of the ink darkens a piece without a shadow under the finger. */
internal const val PRESSED_ALPHA = 0.12f

/** A piece laid askew by [degrees], about its middle; drawn tilted, it takes the room it would straight. */
internal fun Modifier.tilted(degrees: Float): Modifier = if (degrees == 0f) this else graphicsLayer { rotationZ = degrees }

/**
 * A window's content laid down as a sheet on the table: falling in [FALL_MS] ms from [FALL_TILT]
 * degrees to its own place, and fading in, easing out. It leaves at once, its caller removing it.
 */
@Composable
internal fun FallIn(content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(FALL_MS, easing = FastOutSlowInEasing)) }
    Box(modifier = Modifier.graphicsLayer {
        alpha = shown.value
        rotationZ = (1f - shown.value) * FALL_TILT
    }) { content() }
}

private const val FALL_MS = 160
private const val FALL_TILT = 3f

/**
 * An item lifted from its list or from the grid: it rises up and left over a shadow of
 * [LIFT_SHADOW] dp, [LIFT_TILT] degrees more askew; put down, it falls back to its place at once.
 * Its shadow is drawn behind its frame, which keeps its own.
 */
@Composable
internal fun Lifted(lifted: Boolean, shape: Shape, content: @Composable () -> Unit) {
    // One structure lifted or not: the content is the same nodes, so the grip it holds keeps the
    // drag that lifts it
    val s = paperSize()
    val shadow = paperColors.shadow
    val reach = s.dp(LIFT_SHADOW)
    val rise = s.dp(LIFT_RISE)
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = if (lifted) -rise.toPx() else 0f
                translationY = if (lifted) -rise.toPx() else 0f
                rotationZ = if (lifted) LIFT_TILT else 0f
            }
            .drawBehind {
                if (lifted) translate(reach.toPx(), reach.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), shadow) }
            }
    ) { content() }
}

private const val LIFT_SHADOW = 6f
private const val LIFT_RISE = 2f
private const val LIFT_TILT = 2f
