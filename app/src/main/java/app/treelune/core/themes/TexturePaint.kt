package app.treelune.core.themes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection

/**
 * How a theme lays its motifs (docs/THEMES.md, « What a theme is in the app »): the size of a cell's unit on the
 * screen, whether the cell is drawn in whole pixels, and the icon an ICON motif repeats.
 *
 * @property unitPx Screen pixels a cell's unit takes: a dp in a smooth theme, a drawing pixel
 *   (a whole number) in a pixel one
 * @property pixel Drawn without smoothing, every shape on whole units (the retro)
 * @property iconUnits The side of the icon an ICON motif repeats, in units
 * @property iconGapUnits The room around each icon of an ICON motif, in units
 */
data class TextureStyle(val unitPx: Float, val pixel: Boolean, val iconUnits: Int, val iconGapUnits: Int)

/**
 * The brush a theme paints [item]'s motif with on [surface] (the colour of the tile or the header
 * it is laid on): the cell drawn once in the motif's colour (TextureTones), then repeated from the
 * surface's corner. Null for no texture.
 *
 * Each theme calls it with its own [style] and [surface]; the cells and the tones are the theme's
 * own files (TextureCell, TextureTones), read through [CurrentTheme.themeId].
 */
@Composable
fun rememberTextureBrush(item: ItemTexture?, surface: Color, style: TextureStyle): Brush? {
    if (item == null) return null
    val themeId = CurrentTheme.themeId
    val mode = CurrentTheme.paletteMode
    val intensity = CurrentTheme.textureIntensity
    val tag = item.color?.let { CurrentTheme.current.getTagColor(it, mode) }
    val color = TextureTones.of(themeId).mode(mode).motifColor(surface, tag, item.texture, intensity)
    if (item.texture == Texture.ICON) {
        // The thing's icon is the cell: the service never stores ICON on a thing without one
        val iconName = requireNotNull(item.iconName?.takeIf { it.isNotBlank() }) { "An ICON texture on a thing without an icon" }
        val context = LocalContext.current
        val resource = remember(iconName, CurrentTheme.current) { app.treelune.core.icons.Icons.drawable(context, iconName) }
            ?: error("No icon '$iconName' to repeat as a texture: a stored icon name is one of the index (ToolInstanceService, ZoneService)")
        val painter = painterResource(resource)
        val density = LocalDensity.current
        return remember(themeId, item, color, style, resource, density) { iconBrush(painter, color, style, density) }
    }
    return remember(themeId, item.texture, color, style) { cellBrush(TextureCell.of(themeId, item.texture), color, style) }
}

/** [cell] drawn in [color] at [style]'s unit, repeated. */
private fun cellBrush(cell: TextureCell, color: Color, style: TextureStyle): Brush {
    val width = kotlin.math.max(1, kotlin.math.round(cell.width * style.unitPx).toInt())
    val height = kotlin.math.max(1, kotlin.math.round(cell.height * style.unitPx).toInt())
    val bitmap = ImageBitmap(width, height)
    val canvas = Canvas(bitmap)
    // Each axis scaled to whole screen pixels, so the cells meet without a seam
    val scale = Matrix().apply { scale(width.toFloat() / cell.width, height.toFloat() / cell.height) }
    cell.shapes.forEach { shape ->
        val path = PathParser().parsePathString(shape.pathData).toPath()
        path.transform(scale)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = !style.pixel
            if (shape.stroked) {
                this.style = PaintingStyle.Stroke
                strokeWidth = shape.strokeWidth!! * style.unitPx
                strokeCap = if (shape.roundCaps) StrokeCap.Round else StrokeCap.Butt
            }
        }
        canvas.drawPath(path, paint)
        // A line leaving the cell comes back in on the other side, so the repeat closes it
        if (shape.stroked) for (dx in listOf(-width, 0, width)) for (dy in listOf(-height, 0, height)) {
            if (dx == 0 && dy == 0) continue
            canvas.save(); canvas.translate(dx.toFloat(), dy.toFloat()); canvas.drawPath(path, paint); canvas.restore()
        }
    }
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** [icon] twice in a cell, the second a half cell across and down (a quincunx), in [color], repeated. */
private fun iconBrush(icon: Painter, color: Color, style: TextureStyle, density: androidx.compose.ui.unit.Density): Brush {
    val iconPx = style.iconUnits * style.unitPx
    val gapPx = style.iconGapUnits * style.unitPx
    val side = kotlin.math.round(2 * (iconPx + gapPx)).toInt()
    val bitmap = ImageBitmap(side, side)
    val half = side / 2f
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
        listOf(gapPx / 2f to gapPx / 2f, half + gapPx / 2f to half + gapPx / 2f).forEach { (x, y) ->
            translate(left = kotlin.math.floor(x), top = kotlin.math.floor(y)) {
                with(icon) { draw(Size(iconPx, iconPx), colorFilter = ColorFilter.tint(color)) }
            }
        }
    }
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** [brush] painted over the area, inside [shape]; nothing for no brush. Drawn behind the content, over the surface's own fill. */
fun Modifier.texture(brush: Brush?, shape: Shape): Modifier =
    if (brush == null) this else drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), brush) }
