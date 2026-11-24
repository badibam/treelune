package app.treelune.core.drawing

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.icons.Icons
import app.treelune.core.themes.CurrentTheme
import kotlin.math.roundToInt

/**
 * The icons of drawings (DrawShape.Icon) as the current theme shows them: each name's drawable,
 * loaded once, tinted and drawn on a theme's Canvas. A name that designates no icon draws nothing,
 * the drawing having been made from names the app checked.
 */
class DrawingIcons(private val context: Context) {

    private val drawables = HashMap<String, Drawable?>()

    /**
     * [icon] in [argb], [side] pixels square around its center: its own size, or the one a theme
     * rounds it to (a pixel theme draws its icons at a whole number of screen pixels per pixel).
     */
    fun draw(scope: DrawScope, icon: DrawShape.Icon, argb: Int, side: Float = icon.size) {
        val drawable = drawables.getOrPut(icon.name) {
            Icons.drawable(context, icon.name)?.let { context.getDrawable(it)?.mutate() }
        } ?: return
        val left = (icon.center.x - side / 2).roundToInt()
        val top = (icon.center.y - side / 2).roundToInt()
        val whole = side.roundToInt()
        drawable.setBounds(left, top, left + whole, top + whole)
        drawable.setTint(argb)
        scope.drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}

/** The icons of drawings for the current theme, kept while it stays the same. */
@Composable
fun rememberDrawingIcons(): DrawingIcons {
    val context = LocalContext.current
    val theme = CurrentTheme.themeId
    return remember(context, theme) { DrawingIcons(context) }
}
