package app.treelune.themes.cosy

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
import app.treelune.core.icons.IconStroke
import app.treelune.core.icons.IconStrokes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.hypot

/**
 * Lucide in the cosy's register, round and plump, as its icon bench draws it
 * (themes/cosy/bench/icons.html), with the same numbers: each stroke the core reads (IconStrokes)
 * traced again thicker, its ends and joins round, on a solid shadow shifted straight down, never
 * blurred, like the theme's tiles and buttons; inside the strokes that come back on themselves, a
 * pale tone of the shadow's colour when the numbers ask for one. An icon is read once, on its grid,
 * and kept: drawn at any size by scaling. The numbers are the theme's icons.json, which the icon
 * bench reads and saves ([CosyIconNumbers]).
 */
internal object CosyIcons {

    /** The numbers of the drawing, read once. */
    val numbers: CosyIconNumbers by lazy {
        // An absolute path: the release build renames and moves this class out of its package
        val stream = CosyIcons::class.java.getResourceAsStream("/app/treelune/themes/cosy/icons.json")
            ?: error("icons.json is missing from the cosy theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(CosyIconNumbers.serializer(), text)
    }

    /** How near, on the grid of 24, a stroke's end must come to its start to close a shape, as the bench measures it. */
    private const val RETURNS = 0.5f

    /** An icon read: its strokes, the shapes they close, and the paths Lucide fills, on the grid of 24. */
    class Drawn(val strokes: Path, val closed: Path, val solids: Path)

    private val drawn = LruCache<Int, Drawn>(CACHE_SIZE)

    /** The drawable [resourceId] read, once and kept. */
    fun of(context: Context, resourceId: Int): Drawn =
        drawn.get(resourceId) ?: read(context, resourceId).also { drawn.put(resourceId, it) }

    private fun read(context: Context, resourceId: Int): Drawn {
        val drawing = IconStrokes.of(context, resourceId)
        val strokes = Path()
        val closed = Path()
        drawing.strokes.forEach { stroke ->
            strokes.addPath(polyline(stroke))
            if (returns(stroke)) closed.addPath(polyline(stroke).apply { close() })
        }
        val solids = Path()
        drawing.fills.forEach { solids.addPath(it) }
        return Drawn(strokes, closed, solids)
    }

    private fun polyline(s: IconStroke): Path = Path().apply {
        moveTo(s.points[0], s.points[1])
        for (i in 1 until s.size) lineTo(s.points[i * 2], s.points[i * 2 + 1])
        if (s.closed) close()
    }

    /** Whether [s] closes a shape: closed in Lucide, or its last point on its first, as Lucide draws the heart. */
    private fun returns(s: IconStroke): Boolean =
        s.closed || (s.size > 2 && hypot(s.points[0] - s.points[s.size * 2 - 2], s.points[1] - s.points[s.size * 2 - 1]) < RETURNS)

    /** Draw [icon] filling this scope, in [tint], on its shadow of [shadow] when there is one. */
    fun DrawScope.draw(icon: Drawn, tint: Color, shadow: Color?) {
        scale(size.width / GRID, size.height / GRID, pivot = Offset.Zero) {
            val stroke = Stroke(numbers.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (shadow != null) {
                if (numbers.shadow > 0f) translate(0f, numbers.shadow) {
                    drawPath(icon.strokes, shadow, style = stroke)
                    drawPath(icon.solids, shadow)
                }
                if (numbers.fill > 0f) drawPath(icon.closed, shadow.copy(alpha = numbers.fill))
            }
            drawPath(icon.strokes, tint, style = stroke)
            drawPath(icon.solids, tint)
        }
    }

    private const val GRID = 24f

    /** The icons a screen shows at once, and more. */
    private const val CACHE_SIZE = 256
}

/** The numbers of the drawing, as icons.json holds them, on the icon's grid of 24 but the fill. */
@Serializable
data class CosyIconNumbers(
    /** The stroke's width: Lucide's 2, thickened. */
    val width: Float,
    /** The solid shadow's shift, straight down. */
    val shadow: Float,
    /** The pale tone inside the closed shapes, from none (0) to the shadow's colour (1). */
    val fill: Float,
)

/** The icon [resourceId] in the cosy's register, [size] across, in [tint], on a shadow of [shadow] (none when null). */
@Composable
internal fun CosyIcon(resourceId: Int, size: Dp, tint: Color, shadow: Color?, contentDescription: String? = null) {
    val context = LocalContext.current
    val icon = remember(resourceId) { CosyIcons.of(context, resourceId) }
    val described = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Canvas(modifier = described.size(size)) { with(CosyIcons) { draw(icon, tint, shadow) } }
}
