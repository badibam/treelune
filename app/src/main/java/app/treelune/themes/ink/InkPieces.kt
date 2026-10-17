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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
 * to nothing at its irregular edge, as ink spreads in wet paper; a texture's [motif], when there is one,
 * laid within the same edge, over the wash, and fading as the wash does, the same spread its mask. It bleeds past the tile by [bleedX] across and [bleedY]
 * down on each side, half the grid's gaps, so that its edge falls between two tiles, the tile
 * itself, its touch and its content, keeping its cells.
 */
internal fun Modifier.tileWash(color: Color, motif: Brush?, bleedX: Float, bleedY: Float): Modifier = drawBehind {
    val box = Size(size.width + 2 * bleedX, size.height + 2 * bleedY)
    translate(-bleedX, -bleedY) {
        val edge = WashShape.TILE.path(box)
        clipPath(edge) {
            val heart = Offset(box.width * 0.4f, box.height * 0.45f)
            drawSpreadToEdges(color, heart, box)
            motif?.let { brush ->
                // The motif in a layer of its own, then kept as much as the spread holds
                drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, box), Paint()) }
                drawPath(edge, brush)
                drawSpreadToEdges(Color.Black, heart, box, BlendMode.DstIn)
                drawIntoCanvas { it.restore() }
            }
        }
    }
}

/**
 * A wash spreading from [center] and gone at the edge of [box] in every direction: each quarter
 * round the center an ellipse reaching that quarter's two sides, so that a heart off the middle
 * fades out as much on its near side as on its far one. Dense over half the way, half as dense at
 * four fifths, nothing at the edge.
 */
internal fun DrawScope.drawSpreadToEdges(color: Color, center: Offset, box: Size, blendMode: BlendMode = BlendMode.SrcOver) {
    val stops = arrayOf(0f to color, 0.5f to color, 0.8f to color.copy(alpha = color.alpha * 0.5f), 1f to color.copy(alpha = 0f))
    for (right in listOf(false, true)) for (below in listOf(false, true)) {
        val rx = (if (right) box.width - center.x else center.x).coerceAtLeast(1f)
        val ry = (if (below) box.height - center.y else center.y).coerceAtLeast(1f)
        clipRect(
            if (right) center.x else 0f, if (below) center.y else 0f,
            if (right) box.width else center.x, if (below) box.height else center.y
        ) {
            // A circle of the quarter's width, squeezed to its height
            scale(1f, ry / rx, pivot = center) {
                drawCircle(Brush.radialGradient(*stops, center = center, radius = rx), radius = rx, center = center, blendMode = blendMode)
            }
        }
    }
}

/**
 * A card's wash in [color]: full under the whole card, so that no motif runs under its text, its
 * edge [shape]'s wavering a little, and fading past it over [feather], ink soaking into the paper
 * round it. The fade is rings of the same edge, each wider and as faint, so that the closer to the
 * card, the more of them lie over the paper.
 */
internal fun Modifier.cardWash(color: Color, shape: Shape, feather: Float, seed: Int): Modifier = drawWithCache {
    // The edge walked once per size, not at every frame
    val outline = shape.createOutline(size, layoutDirection, this)
    val edge = wavering(Path().apply { addOutline(outline) }, amplitude = feather * 0.25f, step = feather * 0.75f, seed = seed)
    val rings = (FEATHER_RINGS downTo 1).map { Stroke(width = 2 * feather * it / FEATHER_RINGS, join = StrokeJoin.Round) }
    onDrawBehind {
        rings.forEach { drawPath(edge, color.copy(alpha = color.alpha * FEATHER_ALPHA), style = it) }
        drawPath(edge, color)
    }
}

private const val FEATHER_RINGS = 6
private const val FEATHER_ALPHA = 0.22f

/**
 * [path]'s outline wavering along its normal by up to [amplitude], walked a point every [step]:
 * two waves of their own lengths and phases, drawn from [seed], so that a straight side is never
 * ruled.
 */
internal fun wavering(path: Path, amplitude: Float, step: Float, seed: Int): Path {
    val measure = android.graphics.PathMeasure(path.asAndroidPath(), true)
    val length = measure.length
    if (length <= 0f || step <= 0f) return path
    val random = java.util.Random(seed.toLong())
    val waves = List(2) { Triple(length / (3f + random.nextInt(4)), random.nextFloat() * 2 * Math.PI.toFloat(), if (it == 0) 0.65f else 0.35f) }
    val count = (length / step).toInt().coerceAtLeast(8)
    val at = FloatArray(2)
    val tangent = FloatArray(2)
    return Path().apply {
        for (i in 0 until count) {
            val d = length * i / count
            measure.getPosTan(d, at, tangent)
            val shift = amplitude * waves.sumOf { (wave, phase, part) -> (part * kotlin.math.sin(2 * Math.PI * d / wave + phase)) }.toFloat()
            // Along the normal, either way as the waves swing
            val x = at[0] - tangent[1] * shift
            val y = at[1] + tangent[0] * shift
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** A wash spreading from [center] over the whole [box], an ellipse of its proportions. */
internal fun DrawScope.drawSpread(color: Color, center: Offset, box: Size = size) {
    val w = box.width
    val h = box.height.coerceAtLeast(1f)
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
 * A dab of gouache in [color] under what it is put on, reaching past it by [reach] of its smaller
 * side: opaque, flat and matte, a disc painted by hand ([HAND_ROUND]) drawn from [seed] so that
 * each thing keeps its own dab. Paint, not light: a transparent colour lightening the dark paper, or
 * fading from a bright heart, read as a glow. It takes no room of its own.
 */
internal fun Modifier.gouache(color: Color, seed: Int, reach: Float = 0.66f): Modifier = drawBehind {
    drawPath(blob(center, min(size.width, size.height) * reach, seed, HAND_ROUND, HAND_SQUASHING), color)
}

/**
 * A round thing's shape as a disc painted by hand, never a compass circle: round, its edge a little
 * uneven and a little oval, as [HAND_ROUND] waves it (a button, a check, a knob, a day of the
 * calendar), the draw of [seed].
 */
internal class BlobShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(blob(Offset(size.width / 2, size.height / 2), min(size.width, size.height) * 0.49f, seed, HAND_ROUND, HAND_SQUASHING))
}

/** The draw of a seal's shape. */
private const val SEAL_SEED = 23

/**
 * [shape]'s outline traced by hand: its sides trembling along their normal by a hundred and twentieth of its
 * smaller side, the draw of [seed].
 */
internal class HandShape(private val shape: Shape, private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = min(size.width, size.height)
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, density)) }
        return Outline.Generic(wavering(path, amplitude = side / 120f, step = side / 16f, seed = seed))
    }
}

/** A disc painted by hand, its waves round its edge (waves round, part of the radius): a faint oval, a slight lopsidedness, and a fine tremor of the hand. */
internal val HAND_ROUND = listOf(2 to 0.018f, 3 to 0.012f, 7 to 0.006f, 11 to 0.004f)

/** How far a disc painted by hand may be oval. */
internal const val HAND_SQUASHING = 0.06f

/**
 * A closed blob round [center], [radius] about: its edge [waves] (each a number of waves round it and
 * its part of the radius), squashed by up to [squashing] along an axis turned at random, all drawn
 * from [seed]; walked clockwise from [start] radians (0 on the right), where a brush begins it.
 */
internal fun blob(center: Offset, radius: Float, seed: Int, waves: List<Pair<Int, Float>>, squashing: Float, start: Float = 0f): Path {
    val random = java.util.Random(seed.toLong())
    // The waves round the edge, each at its own phase
    val phased = waves.map { (k, a) -> Triple(k, a, random.nextFloat() * 2 * Math.PI.toFloat()) }
    val squash = 1f + (random.nextFloat() - 0.5f) * squashing
    val axis = random.nextFloat() * Math.PI.toFloat()
    val points = 96
    return Path().apply {
        for (i in 0..points) {
            val t = start + (i % points) * 2 * Math.PI.toFloat() / points
            val r = radius * (1f + phased.sumOf { (k, a, p) -> (a * kotlin.math.sin(k * t + p)).toDouble() }.toFloat())
            // Squashed along the turned axis: stretched along it, narrowed across it
            val u = r * squash * kotlin.math.cos(t - axis)
            val v = r / squash * kotlin.math.sin(t - axis)
            val x = center.x + u * kotlin.math.cos(axis) - v * kotlin.math.sin(axis)
            val y = center.y + u * kotlin.math.sin(axis) + v * kotlin.math.cos(axis)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * A soft wash of [color] round what it is put on, reaching past it by a third, [strength] its
 * opacity at its heart: a cell of the grid in edit mode. It takes no room of its own.
 */
internal fun Modifier.colorWash(color: Color, strength: Float): Modifier = drawBehind {
    val r = min(size.width, size.height) * 0.8f
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = strength), 0.55f to color.copy(alpha = strength * 0.66f), 0.9f to color.copy(alpha = 0f),
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

/**
 * A seal pressed by hand: a square of red, its corners pressed unevenly and its sides trembling a
 * little, or [round], a disc painted by hand; holding [text] (an initial, a "!") or nothing.
 */
@Composable
internal fun Seal(side: Dp, text: String?, round: Boolean = false) {
    val size = inkSize()
    val c = inkColors
    val shape = if (round) BlobShape(SEAL_SEED)
        else HandShape(androidx.compose.foundation.shape.RoundedCornerShape(size.dp(3f), size.dp(6f), size.dp(2f), size.dp(7f)), SEAL_SEED)
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
