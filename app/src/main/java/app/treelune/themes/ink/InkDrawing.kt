package app.treelune.themes.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.treelune.core.drawing.DrawColor
import app.treelune.core.drawing.DrawShape
import app.treelune.core.drawing.Drawing
import app.treelune.core.drawing.InkLevel
import app.treelune.core.drawing.PathStep
import app.treelune.core.drawing.SymbolShape
import app.treelune.core.drawing.TextAnchor
import app.treelune.core.drawing.TextBaseline

/**
 * The ink theme's drawings (docs/design/ink-theme.md, line 10): a bar taller than wide is a
 * vertical stroke of the brush, drawn downwards and as wide as the bar; any other block a wash of
 * soft corners; surfaces are thin washes, lines have round ends and joins, labels are in
 * Literata. The colours the user gives a chart's series are kept, in the palette's tag colours:
 * they are the user's data. The inks go to the ink, the secondary ink and a track's wash; what is
 * missing is hatched faintly.
 */
internal object InkDrawing {

    @Composable
    fun Draw(drawing: Drawing, style: TextStyle, colors: InkColors, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val strong = colors.ink
        val medium = colors.dim
        val faint = colors.trackWash
        val (width, height) = with(density) { drawing.width.toDp() to drawing.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            fun resolve(color: DrawColor): Color = when (color) {
                is DrawColor.Palette -> colors.tag(color.color)
                is DrawColor.Mix -> lerp(colors.tag(color.from), colors.tag(color.to), color.t.coerceIn(0f, 1f))
                is DrawColor.Ink -> when (color.level) {
                    InkLevel.STRONG -> strong
                    InkLevel.MEDIUM -> medium
                    InkLevel.FAINT -> faint
                }
            }
            fun dash(lengths: List<Float>?) = lengths?.let { PathEffect.dashPathEffect(it.toFloatArray()) }

            drawing.shapes.forEach { shape ->
                when (shape) {
                    is DrawShape.Box -> if (shape.missing) drawMissing(shape, resolve(shape.color)) else drawBlock(shape, resolve(shape.color))
                    is DrawShape.Path -> drawPath(
                        path(shape.steps), resolve(shape.color),
                        alpha = if (shape.filled) shape.opacity * SURFACE_ALPHA else shape.opacity,
                        style = if (shape.filled) Fill else Stroke(width = shape.strokeWidth * LINE_WEIGHT, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash(shape.dash))
                    )
                    is DrawShape.Symbol -> drawSymbol(shape, resolve(shape.color))
                    is DrawShape.Arc -> drawArc(
                        resolve(shape.color),
                        // Degrees clockwise from the top; Compose counts from three o'clock
                        startAngle = shape.start - 90f, sweepAngle = shape.sweep, useCenter = true,
                        topLeft = Offset(shape.center.x - shape.outer, shape.center.y - shape.outer),
                        size = Size(shape.outer * 2, shape.outer * 2), alpha = shape.opacity * BLOCK_ALPHA
                    )
                    is DrawShape.Segment -> drawLine(
                        resolve(shape.color), Offset(shape.from.x, shape.from.y), Offset(shape.to.x, shape.to.y),
                        strokeWidth = shape.strokeWidth, cap = StrokeCap.Round, pathEffect = dash(shape.dash)
                    )
                    is DrawShape.Label -> {
                        val laid = measurer.measure(shape.text, style.copy(color = resolve(shape.color)))
                        val x = when (shape.anchor) {
                            TextAnchor.START -> shape.at.x
                            TextAnchor.MIDDLE -> shape.at.x - laid.size.width / 2f
                            TextAnchor.END -> shape.at.x - laid.size.width
                        }
                        val y = when (shape.baseline) {
                            TextBaseline.TOP -> shape.at.y
                            TextBaseline.MIDDLE -> shape.at.y - laid.size.height / 2f
                            TextBaseline.BOTTOM -> shape.at.y - laid.size.height
                        }
                        drawText(laid, topLeft = Offset(x, y))
                    }
                    is DrawShape.Arrowhead -> {
                        val s = shape.size
                        val tip = if (shape.up) -s else s
                        drawPath(Path().apply {
                            moveTo(shape.at.x, shape.at.y + tip / 2)
                            lineTo(shape.at.x - s / 2, shape.at.y - tip / 2)
                            lineTo(shape.at.x + s / 2, shape.at.y - tip / 2)
                            close()
                        }, resolve(shape.color), style = Stroke(width = s * 0.15f, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }

    private fun path(steps: List<PathStep>): Path = Path().apply {
        steps.forEach { step ->
            when (step) {
                is PathStep.MoveTo -> moveTo(step.to.x, step.to.y)
                is PathStep.LineTo -> lineTo(step.to.x, step.to.y)
                is PathStep.CubicTo -> cubicTo(step.control1.x, step.control1.y, step.control2.x, step.control2.y, step.to.x, step.to.y)
                PathStep.Close -> close()
            }
        }
    }

    /**
     * A block: a bar taller than wide is one downward stroke of the brush, as wide as the bar;
     * anything else a wash of soft corners.
     */
    private fun DrawScope.drawBlock(shape: DrawShape.Box, color: Color) {
        val r = shape.rect
        val w = r.right - r.left
        val h = r.bottom - r.top
        if (w <= 0f || h <= 0f) return
        if (h > w * TALL) {
            val traced = InkBrush.trace(InkBrush.Shape.VBAR, w, h, w)
            translate(r.left, r.top) {
                traced.forEach { (stroke, widths) ->
                    drawPath(InkBrush.outline(stroke, widths, w, 1f), color, alpha = shape.opacity * InkBrush.RIM_ALPHA)
                    drawPath(InkBrush.outline(stroke, widths, w, InkBrush.CORE), color, alpha = shape.opacity)
                }
            }
        } else {
            val corner = minOf(w, h) * 0.25f
            drawRoundRect(color, Offset(r.left, r.top), Size(w, h), CornerRadius(corner, corner), alpha = shape.opacity * BLOCK_ALPHA)
        }
    }

    /** What is missing: hatched faintly in its colour, never a value. */
    private fun DrawScope.drawMissing(shape: DrawShape.Box, color: Color) {
        val r = shape.rect
        val d = density
        clipRect(r.left, r.top, r.right, r.bottom) {
            var x = r.left - (r.bottom - r.top)
            while (x < r.right) {
                drawLine(color, Offset(x, r.bottom), Offset(x + (r.bottom - r.top), r.top), strokeWidth = d, cap = StrokeCap.Round, alpha = 0.4f)
                x += 6 * d
            }
        }
    }

    /** A point of a series in the shape the chart gives it (its shape may tell series apart), with round joins. */
    private fun DrawScope.drawSymbol(shape: DrawShape.Symbol, color: Color) {
        val c = Offset(shape.center.x, shape.center.y)
        val r = shape.size / 2
        val style = if (shape.filled) Fill else Stroke(width = 1.5f * density, join = StrokeJoin.Round)
        when (shape.shape) {
            SymbolShape.CIRCLE -> drawCircle(color, r, c, alpha = shape.opacity, style = style)
            SymbolShape.SQUARE -> drawRoundRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(r * 0.3f, r * 0.3f), alpha = shape.opacity, style = style)
            SymbolShape.TRIANGLE -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close() }, color, shape.opacity, style)
            SymbolShape.DIAMOND -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, color, shape.opacity, style)
            SymbolShape.CROSS -> {
                drawLine(color, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = 2f * density, cap = StrokeCap.Round, alpha = shape.opacity)
                drawLine(color, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = 2f * density, cap = StrokeCap.Round, alpha = shape.opacity)
            }
        }
    }

    /** A bar taller than this times its width is a stroke of the brush. */
    private const val TALL = 1.5f

    /** A surface's wash, a block's: thin enough to see through. */
    private const val SURFACE_ALPHA = 0.35f
    private const val BLOCK_ALPHA = 0.85f

    /** A line, a little heavier than asked, as a brush lays it. */
    private const val LINE_WEIGHT = 1.15f
}
