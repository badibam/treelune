package app.treelune.themes.blueprint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.treelune.core.icons.IconDrawing
import app.treelune.core.icons.IconStrokes
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Lucide redrawn the blueprint's way (docs/design/blueprint-theme.md, line 9): its strokes, read
 * from the drawable (IconStrokes), traced with a line of [STROKE] units of the icon's grid of 24
 * instead of Lucide's 2, and every line overshooting as on an architect's sketch: each open
 * stroke runs [OVERSHOOT] units past its two ends, and at each sharp corner the two lines that
 * meet there both run past it. A curve, however tight, has no corner: a corner is a turn that
 * does not grow when it is looked at over a longer stretch (see [corners]).
 */
internal object BlueprintIcons {

    /** The line's width and the overshoot, in units of the icon's grid of 24 (set at the bench, 2026-10-18). */
    const val STROKE = 1.5f
    const val OVERSHOOT = 1.2f

    private const val ICON_GRID = 24f

    /** The stretch, in points of the stroke, over which a turn is measured: about a third of a unit. */
    private const val WINDOW = 4

    /** A turn sharper than this, in degrees, is a corner, if it does not grow over twice the stretch. */
    private const val CORNER_DEGREES = 25.0
    private const val SPREAD = 1.5

    private val masks = LruCache<Long, ImageBitmap>(CACHE_SIZE)

    /**
     * The icon [resourceId] at [sizePx] square, white on transparent, to be tinted as it is laid:
     * drawn once per size and kept.
     */
    fun mask(resourceId: Int, drawing: IconDrawing, sizePx: Int): ImageBitmap {
        val key = resourceId.toLong() shl 16 or sizePx.toLong()
        masks.get(key)?.let { return it }
        val bitmap = Bitmap.createBitmap(max(1, sizePx), max(1, sizePx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(sizePx / ICON_GRID, sizePx / ICON_GRID)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in drawing.strokes) {
            val p = stroke.points
            val path = android.graphics.Path()
            path.moveTo(p[0], p[1])
            for (i in 1 until stroke.size) path.lineTo(p[i * 2], p[i * 2 + 1])
            if (stroke.closed) path.close()
            canvas.drawPath(path, line)
            for (o in overshoots(p, stroke.size, stroke.closed)) canvas.drawLine(o[0], o[1], o[2], o[3], line)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        drawing.fills.forEach { canvas.drawPath(it.asAndroidPath(), fill) }
        return bitmap.asImageBitmap().also { masks.put(key, it) }
    }

    /**
     * The short lines that make one stroke of [n] points overshoot, each as x1, y1, x2, y2: past its
     * two ends when it is open, past each of its corners on both sides.
     */
    fun overshoots(p: FloatArray, n: Int, closed: Boolean): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        if (n < 2) return out
        fun line(i: Int, dx: Float, dy: Float) {
            val h = hypot(dx, dy)
            if (h < 1e-4f) return
            out += floatArrayOf(p[i * 2], p[i * 2 + 1], p[i * 2] + dx / h * OVERSHOOT, p[i * 2 + 1] + dy / h * OVERSHOOT)
        }
        if (!closed) {
            val k = min(WINDOW, n - 1)
            // Each end runs on in the direction it arrives from
            line(0, p[0] - p[k * 2], p[1] - p[k * 2 + 1])
            line(n - 1, p[(n - 1) * 2] - p[(n - 1 - k) * 2], p[(n - 1) * 2 + 1] - p[(n - 1 - k) * 2 + 1])
        }
        for (i in corners(p, n, closed)) {
            val far = 2 * WINDOW
            val (bx, by) = index(i - far, n, closed).let { a -> p[i * 2] - p[a * 2] to p[i * 2 + 1] - p[a * 2 + 1] }
            val (ax, ay) = index(i + far, n, closed).let { b -> p[b * 2] - p[i * 2] to p[b * 2 + 1] - p[i * 2 + 1] }
            // The line arriving runs on past the corner; the line leaving runs back past it
            line(i, bx, by)
            line(i, -ax, -ay)
        }
        return out
    }

    /**
     * The points of a stroke where it turns at a corner: the turn over [WINDOW] points on each side
     * passes [CORNER_DEGREES], and over twice that stretch it does not grow by half again, as a
     * curve's turn would. Of a run of points that pass, the one turning the most.
     */
    private fun corners(p: FloatArray, n: Int, closed: Boolean): List<Int> {
        val k = WINDOW
        val range = if (closed) 0 until n else (k until n - k)
        val found = mutableListOf<Int>()
        var best = -1
        var bestTurn = 0.0
        for (i in range) {
            val turn = turn(p, n, closed, i, k)
            val wide = turn(p, n, closed, i, 2 * k)
            val corner = turn > CORNER_DEGREES && wide < turn * SPREAD
            if (corner && turn > bestTurn) { best = i; bestTurn = turn }
            if (!corner && best >= 0) { found += best; best = -1; bestTurn = 0.0 }
        }
        if (best >= 0) found += best
        return found
    }

    /** The angle, in degrees, between the stroke arriving at [i] over [k] points and leaving it over [k]. */
    private fun turn(p: FloatArray, n: Int, closed: Boolean, i: Int, k: Int): Double {
        val a = index(i - k, n, closed)
        val b = index(i + k, n, closed)
        val ix = p[i * 2]; val iy = p[i * 2 + 1]
        val ux = ix - p[a * 2]; val uy = iy - p[a * 2 + 1]
        val vx = p[b * 2] - ix; val vy = p[b * 2 + 1] - iy
        val lu = hypot(ux, uy); val lv = hypot(vx, vy)
        if (lu < 1e-4f || lv < 1e-4f) return 0.0
        val cos = ((ux * vx + uy * vy) / (lu * lv)).toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    /** A point's index [i] on a stroke of [n] points: round the loop when [closed], held to its ends otherwise. */
    private fun index(i: Int, n: Int, closed: Boolean): Int =
        if (closed) ((i % n) + n) % n else i.coerceIn(0, n - 1)

    /** The icons a screen shows at once, at their sizes, and more. */
    private const val CACHE_SIZE = 384
}

/** A Lucide icon redrawn by the theme, [size] square, in [tint]. */
@Composable
internal fun BlueprintIcon(resourceId: Int, size: Dp, tint: Color, description: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val mask = remember(resourceId, px) { BlueprintIcons.mask(resourceId, IconStrokes.of(context, resourceId), px) }
    Image(bitmap = mask, contentDescription = description, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}
