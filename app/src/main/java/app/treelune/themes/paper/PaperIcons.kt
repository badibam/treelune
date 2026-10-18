package app.treelune.themes.paper

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.treelune.core.icons.IconDrawing
import app.treelune.core.icons.IconStroke
import app.treelune.core.icons.IconStrokes
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lucide cut with scissors (docs/design/paper-theme.md, line 9), as the icon bench cuts it
 * (themes/paper/bench/scissors.js), with the same numbers: each stroke the core reads
 * (IconStrokes) cut again in straight facets, a point kept wherever the stroke truly turns and
 * between two of them one every [FACET] units of the grid of 24; laid as a strip [WIDTH] wide,
 * its ends cut square and its joins sharp, on a hard shadow shifted [SHADOW] units down and
 * right. A stroke shorter than its width (Lucide's dots, held up by round caps) is a small
 * square of paper instead. An icon is cut once, on its grid, and kept: drawn at any size by scaling.
 */
internal object PaperIcons {

    /** The strip's width, on the grid of 24: Lucide's 2, thickened. */
    const val WIDTH = 2.6f

    /** A facet's length between two corners, on the grid of 24. */
    const val FACET = 1.6f

    /** How far a stroke turns, in degrees over a few points, to be a corner kept whole. */
    const val CORNER = 35f

    /** The hard shadow's shift, down and right, on the grid of 24. */
    const val SHADOW = 0.9f

    /** The points either side over which a stroke's turn is measured: a curve's slow turn never counts. */
    private const val WINDOW = 4

    /** The tilt of a dot's square, at most, in radians. */
    private const val DOT_TILT = 0.25f

    /** An icon cut: its strips, the squares of its dots and the paths it fills, on the grid of 24. */
    class Cut(val strips: Path, val solids: Path)

    private val cuts = LruCache<Int, Cut>(CACHE_SIZE)

    /** The drawable [resourceId] cut, once and kept. */
    fun of(context: Context, resourceId: Int): Cut =
        cuts.get(resourceId) ?: cut(IconStrokes.of(context, resourceId)).also { cuts.put(resourceId, it) }

    private fun cut(drawing: IconDrawing): Cut {
        val strips = Path()
        val solids = Path()
        drawing.strokes.forEachIndexed { index, stroke ->
            if (length(stroke) < WIDTH) dot(solids, stroke, index) else strip(strips, stroke)
        }
        drawing.fills.forEach { solids.addPath(it) }
        return Cut(strips, solids)
    }

    private fun length(s: IconStroke): Float {
        var sum = 0f
        for (i in 1 until s.size) sum += hypot(s.points[i * 2] - s.points[i * 2 - 2], s.points[i * 2 + 1] - s.points[i * 2 - 1])
        return sum
    }

    /** A dot as a square of paper [WIDTH] across, a little askew, the same at every cut. */
    private fun dot(into: Path, s: IconStroke, index: Int) {
        val mid = s.size / 2
        val cx = s.points[mid * 2]
        val cy = s.points[mid * 2 + 1]
        val h = WIDTH / 2
        val a = ((index * 37 % 11) / 10f - 0.5f) * 2 * DOT_TILT
        fun corner(dx: Float, dy: Float) = Offset(cx + dx * cos(a) - dy * sin(a), cy + dx * sin(a) + dy * cos(a))
        val corners = listOf(corner(-h, -h), corner(h, -h), corner(h, h), corner(-h, h))
        into.moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { into.lineTo(it.x, it.y) }
        into.close()
    }

    /** A stroke cut in facets, added to [into] as a polyline. */
    private fun strip(into: Path, s: IconStroke) {
        val n = s.size
        fun dir(i: Int, j: Int) = atan2(s.points[j * 2 + 1] - s.points[i * 2 + 1], s.points[j * 2] - s.points[i * 2])
        fun turn(i: Int): Float {
            var t = abs(dir(i, i + WINDOW) - dir(i - WINDOW, i)) * 180f / Math.PI.toFloat()
            if (t > 180f) t = 360f - t
            return t
        }
        // The corners: where it turns past CORNER, the sharpest point of its neighbourhood, kept once
        val keep = mutableListOf(0)
        var i = WINDOW
        while (i < n - WINDOW) {
            if (turn(i) > CORNER && i - keep.last() > WINDOW) {
                var best = i
                for (j in i + 1 until min(n - WINDOW, i + WINDOW)) if (turn(j) > turn(best)) best = j
                keep += best
                i = best + WINDOW
            }
            i++
        }
        keep += n - 1
        // Between two corners, facets of one length, as near FACET as the stretch allows
        into.moveTo(s.points[0], s.points[1])
        for (k in 0 until keep.size - 1) {
            val a = keep[k]
            val b = keep[k + 1]
            var stretch = 0f
            for (p in a + 1..b) stretch += hypot(s.points[p * 2] - s.points[p * 2 - 2], s.points[p * 2 + 1] - s.points[p * 2 - 1])
            val parts = max(1, (stretch / FACET).roundToInt())
            for (q in 1..parts) {
                val p = (a + (b - a) * q.toFloat() / parts).roundToInt()
                into.lineTo(s.points[p * 2], s.points[p * 2 + 1])
            }
        }
        if (s.closed) into.close()
    }

    /** Draw [cut] filling this scope, in [tint], on its shadow of [shadow] when there is one. */
    fun DrawScope.draw(cut: Cut, tint: Color, shadow: Color?) {
        scale(size.width / GRID, size.height / GRID, pivot = Offset.Zero) {
            val stroke = Stroke(WIDTH, miter = MITER, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
            if (shadow != null) translate(SHADOW, SHADOW) {
                drawPath(cut.strips, shadow, style = stroke)
                drawPath(cut.solids, shadow)
            }
            drawPath(cut.strips, tint, style = stroke)
            drawPath(cut.solids, tint)
        }
    }

    private const val GRID = 24f
    private const val MITER = 4f

    /** The icons a screen shows at once, and more. */
    private const val CACHE_SIZE = 256
}

/** The icon [resourceId] cut from paper, [size] across, in [tint], on a shadow of [shadow] (none when null). */
@Composable
internal fun CutIcon(resourceId: Int, size: Dp, tint: Color, shadow: Color?, contentDescription: String? = null) {
    val context = LocalContext.current
    val cut = remember(resourceId) { PaperIcons.of(context, resourceId) }
    val described = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Canvas(modifier = described.size(size)) { with(PaperIcons) { draw(cut, tint, shadow) } }
}
