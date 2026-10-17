package app.treelune.core.icons

import android.content.Context
import android.graphics.PathMeasure
import android.util.LruCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import org.xmlpull.v1.XmlPullParser

/**
 * A Lucide icon's drawing as strokes, for a theme that traces Lucide its own way rather than
 * showing its even stroke (docs/design/ink-theme.md, line 9): each contour of each path walked
 * along its length, a point every [IconDrawing.step] units of the icon's grid of 24, and the
 * paths Lucide fills, kept whole. The drawable is read as the XML it is (generate_icons.py writes
 * paths only, no group nor transform), so that what is filled is told by its fill colour.
 */
object IconStrokes {

    /** The units between two points of a stroke, on the icon's grid of 24. */
    const val STEP = 0.08f

    private val drawings = LruCache<Int, IconDrawing>(CACHE_SIZE)

    /** The strokes of the drawable [resourceId], read once and kept. */
    fun of(context: Context, resourceId: Int): IconDrawing =
        drawings.get(resourceId) ?: read(context, resourceId).also { drawings.put(resourceId, it) }

    private fun read(context: Context, resourceId: Int): IconDrawing {
        val strokes = mutableListOf<IconStroke>()
        val fills = mutableListOf<Path>()
        val transparent = "@${android.R.color.transparent}"
        val xml = context.resources.getXml(resourceId)
        try {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG || xml.name != "path") continue
                val data = requireNotNull(xml.getAttributeValue(ANDROID, "pathData")) { "A path without data in the icon $resourceId" }
                val path = PathParser().parsePathString(data).toPath()
                strokes += walk(path, STEP)
                val fill = xml.getAttributeValue(ANDROID, "fillColor")
                if (fill != null && fill != transparent) fills += path
            }
        } finally {
            xml.close()
        }
        return IconDrawing(strokes, fills, STEP)
    }

    /**
     * Each contour of [path] as a stroke, its points every [step] along it: an icon's, or any
     * drawing a theme traces the same way (a rule, a ring).
     */
    fun walk(path: Path, step: Float): List<IconStroke> {
        val out = mutableListOf<IconStroke>()
        val measure = PathMeasure(path.asAndroidPath(), false)
        val at = FloatArray(2)
        do {
            val length = measure.length
            if (length <= 0f) continue
            val count = (length / step).toInt() + 1
            val points = FloatArray(count * 2)
            for (i in 0 until count) {
                measure.getPosTan(minOf(i * step, length), at, null)
                points[i * 2] = at[0]
                points[i * 2 + 1] = at[1]
            }
            out += IconStroke(points, measure.isClosed)
        } while (measure.nextContour())
        return out
    }

    private const val ANDROID = "http://schemas.android.com/apk/res/android"

    /** The icons a screen shows at once, and more. */
    private const val CACHE_SIZE = 256
}

/**
 * An icon's drawing: its strokes, each a list of points on the grid of 24, [step] units apart,
 * and the paths it fills whole.
 */
class IconDrawing(val strokes: List<IconStroke>, val fills: List<Path>, val step: Float)

/** One contour of an icon: x and y of each point in turn, and whether it closes on itself. */
class IconStroke(val points: FloatArray, val closed: Boolean) {
    val size: Int get() = points.size / 2
}
