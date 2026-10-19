package app.treelune.themes.eink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * The e-reader theme's drawings (docs/design/eink-theme.md), in the ink and the ground alone. A
 * series' colour becomes its pattern on a fill (a bar, a slice) and its dashes on a line, unless
 * the chart gives the line its own; a quantity coloured between two colours (DrawColor.Mix) a
 * screen of dots as dense as it is far toward the second; an opacity, that share of the shape's
 * ink cleared. The inks: strong and medium solid, faint dotted on a line, a screen on a fill.
 * What is missing is hatched. Every fill outlined in the ink, so two patterns side by side part.
 */
internal object EinkDrawing {

    @Composable
    fun Draw(drawing: Drawing, style: TextStyle, colors: EinkColors, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val dp = einkPatternDp()
        val ink = colors.ink
        val ground = colors.ground
        val (width, height) = with(density) { drawing.width.toDp() to drawing.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            /** What a fill of [color] at [opacity] is painted with. */
            fun fill(color: DrawColor, opacity: Float): Brush = when (color) {
                is DrawColor.Palette -> EinkPatterns.of(color.color, ink, ground, dp, fade = 1f - opacity)
                is DrawColor.Mix -> {
                    val t = color.t.coerceIn(0f, 1f)
                    val cover = EinkPatterns.cover(color.from) * (1 - t) + EinkPatterns.cover(color.to) * t
                    EinkPatterns.screen(cover * opacity, ink, ground, dp)
                }
                is DrawColor.Ink -> EinkPatterns.screen(inkCover(color.level) * opacity, ink, ground, dp)
            }
            /** What a line of [color] at [opacity] is painted with: the ink, thinned to dots below full. */
            fun line(opacity: Float): Brush = EinkPatterns.screen(opacity, ink, ground, dp)
            /** A line's dashes: the chart's own, else those its colour stands for. */
            fun dashes(color: DrawColor, given: List<Float>?): PathEffect? {
                val lengths = given ?: when (color) {
                    is DrawColor.Palette -> EinkPatterns.dashes(color.color)?.map { it * dp }
                    is DrawColor.Ink -> if (color.level == InkLevel.FAINT) listOf(1f * dp, 2f * dp) else null
                    is DrawColor.Mix -> null
                }
                return lengths?.let { PathEffect.dashPathEffect(it.toFloatArray()) }
            }
            val edge = maxOf(1f, kotlin.math.round(dp))

            drawing.shapes.forEach { shape ->
                when (shape) {
                    is DrawShape.Box -> {
                        val topLeft = Offset(shape.rect.left, shape.rect.top)
                        val size = Size(shape.rect.right - shape.rect.left, shape.rect.bottom - shape.rect.top)
                        if (shape.missing) drawMissing(shape, edge, colors)
                        else {
                            drawRect(fill(shape.color, shape.opacity), topLeft, size)
                            // A pattern's edge, so two series side by side part; a quantity's screen stands alone
                            if (shape.color is DrawColor.Palette && size.width > 2 * edge && size.height > 2 * edge)
                                drawRect(ink, topLeft + Offset(edge / 2, edge / 2), Size(size.width - edge, size.height - edge), style = Stroke(edge))
                        }
                    }
                    is DrawShape.Path -> if (shape.filled) drawPath(path(shape.steps), fill(shape.color, shape.opacity), style = Fill)
                        else drawPath(
                            path(shape.steps), line(shape.opacity),
                            style = Stroke(width = shape.strokeWidth, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = dashes(shape.color, shape.dash))
                        )
                    is DrawShape.Symbol -> drawSymbol(shape, if (shape.filled) fill(shape.color, shape.opacity) else line(shape.opacity), edge)
                    is DrawShape.Arc -> {
                        // Degrees clockwise from the top; Compose counts from three o'clock
                        val topLeft = Offset(shape.center.x - shape.outer, shape.center.y - shape.outer)
                        val size = Size(shape.outer * 2, shape.outer * 2)
                        drawArc(fill(shape.color, shape.opacity), shape.start - 90f, shape.sweep, useCenter = true, topLeft = topLeft, size = size)
                        drawArc(ink, shape.start - 90f, shape.sweep, useCenter = true, topLeft = topLeft, size = size, style = Stroke(edge, join = StrokeJoin.Miter))                    }
                    is DrawShape.Segment -> drawLine(
                        line(1f), Offset(shape.from.x, shape.from.y), Offset(shape.to.x, shape.to.y),
                        strokeWidth = shape.strokeWidth, cap = StrokeCap.Butt, pathEffect = dashes(shape.color, shape.dash)
                    )
                    is DrawShape.Label -> {
                        val laid = measurer.measure(shape.text, style.copy(color = ink))
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
                        }, ink)
                    }
                }
            }
        }
    }

    /** The share of a fill each ink covers: the strong one solid, the faint one a light screen. */
    private fun inkCover(level: InkLevel): Float = when (level) {
        InkLevel.STRONG -> 1f
        InkLevel.MEDIUM -> 0.5f
        InkLevel.FAINT -> 0.25f
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

    /** What is missing: hatched in the ink on the ground, an outline round it, never a value. */
    private fun DrawScope.drawMissing(shape: DrawShape.Box, edge: Float, colors: EinkColors) {
        val r = shape.rect
        drawRect(colors.ground, Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top))
        clipRect(r.left, r.top, r.right, r.bottom) {
            var x = r.left - (r.bottom - r.top)
            while (x < r.right) {
                drawLine(colors.ink, Offset(x, r.bottom), Offset(x + (r.bottom - r.top), r.top), strokeWidth = edge)
                x += 5 * edge
            }
        }
        drawRect(colors.ink, Offset(r.left + edge / 2, r.top + edge / 2), Size(r.right - r.left - edge, r.bottom - r.top - edge), style = Stroke(edge))
    }

    /** A point of a series in the shape the chart gives it (its shape may tell series apart), with square ends. */
    private fun DrawScope.drawSymbol(shape: DrawShape.Symbol, brush: Brush, edge: Float) {
        val c = Offset(shape.center.x, shape.center.y)
        val r = shape.size / 2
        val style = if (shape.filled) Fill else Stroke(width = 1.5f * edge, join = StrokeJoin.Miter)
        when (shape.shape) {
            SymbolShape.CIRCLE -> drawCircle(brush, r, c, style = style)
            SymbolShape.SQUARE -> drawRect(brush, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = style)
            SymbolShape.TRIANGLE -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close() }, brush, style = style)
            SymbolShape.DIAMOND -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, brush, style = style)
            SymbolShape.CROSS -> {
                drawLine(brush, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = 2f * edge, cap = StrokeCap.Butt)
                drawLine(brush, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = 2f * edge, cap = StrokeCap.Butt)
            }
        }
    }
}
