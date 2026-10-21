package app.treelune.themes.paper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
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
import androidx.compose.ui.graphics.drawscope.rotate
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
 * The cut paper theme's drawings (docs/themes/paper.md): a box is a strip of paper cut with
 * uneven corners, the strips leaning in turn by [BOX_TILT] degrees about their foot; a curve's
 * strokes have square ends and sharp joins, its round points are small rounds of its colour on a
 * hard shadow; labels in Bricolage's figures of one width. The colours the user gives a chart's
 * series are kept, in the palette's coloured sheets. The strong ink is the one that reads on the
 * tile's sheet ([strong]), the night blue elsewhere; the medium ink the secondary ink, the faint
 * one the brown of the dashes; what is missing is hatched.
 */
internal object PaperDrawing {

    @Composable
    fun Draw(drawing: Drawing, style: TextStyle, colors: PaperColors, strong: Color?, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val screen = LocalDensity.current
        val strongInk = strong ?: colors.navy.srgb
        val medium = colors.dim.srgb
        val faint = colors.rule.srgb
        val shadow = colors.shadow
        val (width, height) = with(screen) { drawing.width.toDp() to drawing.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            fun resolve(color: DrawColor): Color = when (color) {
                is DrawColor.Palette -> colors.colour(color.color).srgb
                is DrawColor.Mix -> lerp(colors.colour(color.from).srgb, colors.colour(color.to).srgb, color.t.coerceIn(0f, 1f))
                is DrawColor.Ink -> when (color.level) {
                    InkLevel.STRONG -> strongInk
                    InkLevel.MEDIUM -> medium
                    InkLevel.FAINT -> faint
                }
            }
            fun dash(lengths: List<Float>?) = lengths?.let { PathEffect.dashPathEffect(it.toFloatArray()) }

            var boxes = 0
            drawing.shapes.forEach { shape ->
                when (shape) {
                    is DrawShape.Box -> if (shape.missing) drawMissing(shape, resolve(shape.color)) else {
                        val w = shape.rect.right - shape.rect.left
                        val h = shape.rect.bottom - shape.rect.top
                        // Each strip leans the other way from the one before
                        val tilt = if (boxes++ % 2 == 0) -BOX_TILT else BOX_TILT
                        rotate(tilt, pivot = Offset(shape.rect.left + w / 2, shape.rect.bottom)) {
                            drawPath(cutBox(shape.rect.left, shape.rect.top, w, h, density), resolve(shape.color), alpha = shape.opacity)
                        }
                    }
                    is DrawShape.Path -> drawPath(
                        path(shape.steps), resolve(shape.color), alpha = shape.opacity,
                        style = if (shape.filled) Fill else Stroke(width = shape.strokeWidth, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = dash(shape.dash))
                    )
                    is DrawShape.Symbol -> drawSymbol(shape, resolve(shape.color), shadow)
                    is DrawShape.Arc -> drawArc(
                        resolve(shape.color),
                        // Degrees clockwise from the top; Compose counts from three o'clock
                        startAngle = shape.start - 90f, sweepAngle = shape.sweep, useCenter = true,
                        topLeft = Offset(shape.center.x - shape.outer, shape.center.y - shape.outer),
                        size = Size(shape.outer * 2, shape.outer * 2), alpha = shape.opacity
                    )
                    is DrawShape.Segment -> drawLine(
                        resolve(shape.color), Offset(shape.from.x, shape.from.y), Offset(shape.to.x, shape.to.y),
                        // A dashed segment keeps square ends, so its dashes stay apart
                        strokeWidth = shape.strokeWidth, cap = if (shape.dash == null) StrokeCap.Round else StrokeCap.Butt, pathEffect = dash(shape.dash)
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
                        }, resolve(shape.color))
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

    /** What is missing: hatched in its colour, a rounded outline round it, never a value. */
    private fun DrawScope.drawMissing(shape: DrawShape.Box, color: Color) {
        val r = shape.rect
        val d = density
        clipRect(r.left, r.top, r.right, r.bottom) {
            var x = r.left - (r.bottom - r.top)
            while (x < r.right) {
                drawLine(color, Offset(x, r.bottom), Offset(x + (r.bottom - r.top), r.top), strokeWidth = d, alpha = 0.5f)
                x += 6 * d
            }
        }
        val corner = minOf(MISSING_RADIUS_DP * d, (r.right - r.left) / 2, (r.bottom - r.top) / 2)
        drawRoundRect(color, Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top), CornerRadius(corner, corner), alpha = 0.6f, style = Stroke(width = d))
    }

    /**
     * A point of a series: a round one is a small round of its colour cut from paper, on a hard
     * shadow (its ring alone when not filled); the other shapes keep their form, which may tell
     * series apart, with sharp joins.
     */
    private fun DrawScope.drawSymbol(shape: DrawShape.Symbol, color: Color, shadow: Color) {
        val c = Offset(shape.center.x, shape.center.y)
        val r = shape.size / 2
        val style = if (shape.filled) Fill else Stroke(width = 1.5f * density, join = StrokeJoin.Miter)
        when (shape.shape) {
            SymbolShape.CIRCLE -> {
                val ring = maxOf(1.5f * density, r / 3)
                if (shape.filled) {
                    drawCircle(shadow, r, c + Offset(density, density * 1.5f), alpha = shape.opacity)
                    drawCircle(color, r, c, alpha = shape.opacity)
                } else drawCircle(color, r - ring / 2, c, alpha = shape.opacity, style = Stroke(ring))
            }
            SymbolShape.SQUARE -> drawRoundRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(r / 3, r / 3), style = style, alpha = shape.opacity)
            SymbolShape.TRIANGLE -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close() }, color, shape.opacity, style)
            SymbolShape.DIAMOND -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, color, shape.opacity, style)
            SymbolShape.CROSS -> {
                drawLine(color, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = 2f * density, cap = StrokeCap.Round, alpha = shape.opacity)
                drawLine(color, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = 2f * density, cap = StrokeCap.Round, alpha = shape.opacity)
            }
        }
    }

    /**
     * A box cut with uneven corners (the mockup's 2, 4, 1 and 3 dp), never past half its narrower
     * side: a thin bar stays a bar.
     */
    private fun cutBox(x: Float, y: Float, w: Float, h: Float, density: Float): Path {
        fun r(dp: Float) = CornerRadius(minOf(dp * density, w / 2, h / 2).coerceAtLeast(0f))
        return Path().apply {
            addRoundRect(RoundRect(x, y, x + w, y + h, topLeftCornerRadius = r(2f), topRightCornerRadius = r(4f), bottomRightCornerRadius = r(1f), bottomLeftCornerRadius = r(3f)))
        }
    }

    /** How far a box leans about its foot, in degrees, one way then the other: its height stays its value. */
    private const val BOX_TILT = 1.5f

    /** The rounding of a missing box's outline, in dp. */
    private const val MISSING_RADIUS_DP = 2f
}
