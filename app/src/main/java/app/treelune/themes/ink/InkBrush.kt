package app.treelune.themes.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import app.treelune.core.icons.IconDrawing
import app.treelune.core.icons.IconStroke
import app.treelune.core.icons.IconStrokes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The ink theme's brush (docs/design/ink-theme.md, line 9), the one hand of every line it draws:
 * held at a slant, it presses where it lands and where the stroke turns, swells once slowly over
 * a stroke and lifts off in a point, its widths smoothed so they only ever flow. The same as the
 * benches' brush.js, its settings chosen at the brush bench.
 *
 * Lengths are in a drawing's own units, scaled by a unit: 1 for a Lucide icon on its grid of 24,
 * where the width 1.3 is the stroke's; for a rule in pixels, the pixels a width of 1 stands for.
 *
 * An icon, its corners sharp, is stamped in discs once per size into a mask kept in memory
 * ([iconMask]); a smooth line (a rule, a ring, a pebble, an ensō) is one outline round its
 * widths ([outline]), drawn at every frame at no cost.
 */
internal object InkBrush {

    const val WIDTH = 1.3f
    private const val TAPER = 0.5f
    private const val DIRECTION = 0.45f
    private const val CORNER = 1f
    private const val ATTACK = 0.45f
    private const val SWELL = 0.3f

    /** A wet brush: a rim at half its opacity round a core at this part of its width. */
    const val CORE = 0.72f
    const val RIM_ALPHA = 0.5f

    /** The units between two points of a stroke, at a unit of 1. */
    const val STEP = IconStrokes.STEP

    /**
     * The width along a stroke of [count] points [step] apart, as a factor of the brush's width;
     * [seed] fixes its one slow swell, so that a drawing is always drawn the same.
     */
    fun widths(points: FloatArray, count: Int, closed: Boolean, step: Float, unit: Float, seed: Int): FloatArray {
        val n = count
        if (n == 0) return FloatArray(0)
        val len = (n - 1) * step
        val attackLen = min(len * 0.3f, 1.2f * unit)
        val releaseLen = min(len * 0.5f, (0.6f + 3.4f * TAPER) * unit)
        val phase = (rand(seed) - 0.5f) * 0.8f
        // The spans the direction, the turn and the smoothing are read over, in points
        val dirSpan = max(1, (0.32f * unit / step).roundToInt())
        val turnSpan = max(1, (0.48f * unit / step).roundToInt())
        val half = max(1, (0.4f * unit / step).roundToInt())
        fun at(i: Int) = i.coerceIn(0, n - 1)
        fun dir(a: Int, b: Int) = atan2(points[b * 2 + 1] - points[a * 2 + 1], points[b * 2] - points[a * 2])
        val raw = FloatArray(n)
        for (i in 0 until n) {
            val u = i * step
            val rest = len - u
            var w = 1f
            // Held at a slant: a stroke rising gently to the right is its thinnest
            val across = abs(sin(dir(at(i - dirSpan), at(i + dirSpan)) + 0.2f))
            w *= 1f - 0.55f * DIRECTION * (1f - across)
            // It presses where the stroke turns, as the hand pauses
            val d = dir(at(i), at(i + turnSpan)) - dir(at(i - turnSpan), at(i))
            val turn = abs(atan2(sin(d), cos(d)))
            w *= 1f + 0.6f * CORNER * min(1f, turn / (PI.toFloat() / 2f))
            // One slow swell over the whole stroke, never a ripple
            w *= 1f + SWELL * sin(PI.toFloat() * (u / max(len, 0.01f)) + phase)
            if (!closed) {
                // It lands pressed, and lifts off in a long point
                if (attackLen > 0f) w *= 1f + 0.6f * ATTACK * (1f - smooth(u / attackLen))
                if (releaseLen > 0f) w *= 0.06f + 0.94f * smooth(rest / releaseLen)
            }
            raw[i] = w
        }
        // Smoothed, so that a width only ever flows from one value to the next
        return FloatArray(n) { i ->
            var sum = 0f
            var cnt = 0
            for (j in i - half..i + half) if (j in 0 until n) { sum += raw[j]; cnt++ }
            sum / cnt
        }
    }

    private val masks = LruCache<Long, ImageBitmap>(MASK_CACHE)

    /**
     * An icon traced by the brush, [sizePx] square, white on transparent for a tint to colour:
     * its rim at half its opacity, its core over it, and its filled parts whole. Made once per
     * icon and size, and kept.
     */
    fun iconMask(resourceId: Int, drawing: IconDrawing, sizePx: Int): ImageBitmap {
        val key = resourceId.toLong() shl 16 or sizePx.toLong()
        masks.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(max(1, sizePx), max(1, sizePx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(sizePx / ICON_GRID, sizePx / ICON_GRID)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val widths = drawing.strokes.mapIndexed { index, stroke ->
            widths(stroke.points, stroke.size, stroke.closed, drawing.step, 1f, resourceId + index)
        }
        fun stamp(scale: Float) = drawing.strokes.forEachIndexed { index, stroke ->
            val w = widths[index]
            for (i in 0 until stroke.size) {
                val r = max(0.04f, WIDTH * w[i] * scale / 2f)
                canvas.drawCircle(stroke.points[i * 2], stroke.points[i * 2 + 1], r, paint)
            }
        }
        // The rim drawn opaque on its own layer, then laid at once at half its opacity, so that
        // overlapping discs don't add up
        canvas.saveLayerAlpha(null, (RIM_ALPHA * 255).roundToInt())
        stamp(1f)
        canvas.restore()
        stamp(CORE)
        drawing.fills.forEach { canvas.drawPath(it.asAndroidPath(), paint) }
        return bitmap.asImageBitmap().also { masks.put(key, it) }
    }

    /**
     * The outline round a smooth stroke, its width [widthPx] times its widths and [scale]: one
     * shape to fill. A closed stroke is a ring (its two edges filled even-odd); an open one has
     * a round end where the brush lands and where it lifts.
     */
    fun outline(stroke: IconStroke, widths: FloatArray, widthPx: Float, scale: Float): Path {
        val n = stroke.size
        val p = stroke.points
        val left = FloatArray(n * 2)
        val right = FloatArray(n * 2)
        for (i in 0 until n) {
            val a = max(0, i - 1)
            val b = min(n - 1, i + 1)
            var tx = p[b * 2] - p[a * 2]
            var ty = p[b * 2 + 1] - p[a * 2 + 1]
            val l = hypot(tx, ty).takeIf { it > 0f } ?: 1f
            tx /= l; ty /= l
            val r = widthPx * widths[i] * scale / 2f
            left[i * 2] = p[i * 2] - ty * r; left[i * 2 + 1] = p[i * 2 + 1] + tx * r
            right[i * 2] = p[i * 2] + ty * r; right[i * 2 + 1] = p[i * 2 + 1] - tx * r
        }
        val path = Path()
        if (stroke.closed) {
            path.fillType = PathFillType.EvenOdd
            polygon(path, left, 0 until n)
            polygon(path, right, 0 until n)
            return path
        }
        path.moveTo(left[0], left[1])
        for (i in 1 until n) path.lineTo(left[i * 2], left[i * 2 + 1])
        for (i in n - 1 downTo 0) path.lineTo(right[i * 2], right[i * 2 + 1])
        path.close()
        // Round ends, joined to the body so that a rim laid at half its opacity stays even
        val ends = Path()
        for (i in intArrayOf(0, n - 1)) {
            val r = widthPx * widths[i] * scale / 2f
            ends.addOval(Rect(p[i * 2] - r, p[i * 2 + 1] - r, p[i * 2] + r, p[i * 2 + 1] + r))
        }
        return Path.combine(androidx.compose.ui.graphics.PathOperation.Union, path, ends)
    }

    private fun polygon(path: Path, xy: FloatArray, range: IntRange) {
        path.moveTo(xy[range.first * 2], xy[range.first * 2 + 1])
        for (i in range.drop(1)) path.lineTo(xy[i * 2], xy[i * 2 + 1])
        path.close()
    }

    /** The smooth shapes the theme traces, in a box of its own size. */
    enum class Shape { LINE, VBAR, RING, PEBBLE, ENSO }

    /**
     * [shape] traced in a box [w] × [h] pixels, the brush [widthPx] wide: its strokes and their
     * widths, ready for [outline]. [sweep] is the part of a turn an ensō covers.
     */
    fun trace(shape: Shape, w: Float, h: Float, widthPx: Float, sweep: Float = ENSO_SWEEP): List<Pair<IconStroke, FloatArray>> {
        val unit = widthPx / WIDTH
        val step = max(0.5f, STEP * unit)
        val pad = widthPx * 0.9f
        val path = Path()
        when (shape) {
            // A line across, rising a hair to the right as a hand draws it
            Shape.LINE -> { path.moveTo(pad, h / 2 + h * 0.08f); path.lineTo(max(pad + 1f, w - pad), h / 2 - h * 0.08f) }
            // A bar of a chart, drawn downwards as a vertical stroke is
            Shape.VBAR -> { path.moveTo(w / 2, min(pad, h / 3)); path.lineTo(w / 2, h) }
            // A ring, beginning a little before the top
            Shape.RING -> {
                val r = max(1f, min(w, h) / 2 - pad)
                arc(path, w / 2, h / 2, r, -100f, 360f)
                path.close()
            }
            Shape.PEBBLE -> {
                val r = max(1f, min(w, h) / 2 - pad)
                path.addRoundRect(RoundRect(pad, pad, max(pad + 1f, w - pad), max(pad + 1f, h - pad), r, r))
            }
            // One round stroke, left open
            Shape.ENSO -> arc(path, w / 2, h / 2, max(1f, min(w, h) / 2 - pad), 112f, 360f * sweep)
        }
        val seed = shape.ordinal * 7919 + w.roundToInt() * 31 + h.roundToInt()
        return IconStrokes.walk(path, step).mapIndexed { index, stroke ->
            stroke to widths(stroke.points, stroke.size, stroke.closed, step, unit, seed + index)
        }
    }

    /** An arc of a circle as points, from [start] degrees clockwise over [sweep]. */
    private fun arc(path: Path, cx: Float, cy: Float, r: Float, start: Float, sweep: Float) {
        val steps = max(8, (abs(sweep) / 4f).roundToInt())
        for (i in 0..steps) {
            val a = Math.toRadians((start + sweep * i / steps).toDouble())
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    private fun smooth(t: Float) = if (t <= 0f) 0f else if (t >= 1f) 1f else t * t * (3f - 2f * t)

    private fun rand(i: Int): Float {
        val x = sin(i * 12.9898 + 78.233) * 43758.5453
        return (x - floor(x)).toFloat()
    }

    /** The part of a turn an ensō covers, left open. */
    const val ENSO_SWEEP = 0.88f

    private const val ICON_GRID = 24f

    /** The icons of a few screens, at the sizes they are shown. */
    private const val MASK_CACHE = 384
}
