package app.treelune.themes.blueprint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * The blueprint theme's drawings (docs/themes/blueprint.md, line 10): a box (a bar) is an
 * outline hatched at 45 degrees, as a section through a wall; a curve a fine line; a round point
 * a small cross; a dashed line (a goal) the dash and dot of an axis; labels in Plex Mono. The
 * colours the user gives a chart's series are kept, in the palette's pencils. The strong ink is
 * the tile's pencil inside a tile ([crayon]), the ink elsewhere; the medium ink the secondary
 * ink, the faint one the ink at the grid's large squares; what is missing is hatched sparsely,
 * with no outline.
 */
internal object BlueprintDrawing {

    @Composable
    fun Draw(drawing: Drawing, style: TextStyle, colors: BlueprintColors, crayon: Color?, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val screen = LocalDensity.current
        val strong = crayon ?: colors.ink.srgb
        val medium = colors.dim.srgb
        val faint = colors.ink.srgb.copy(alpha = FAINT_ALPHA)
        val paper = colors.paper.srgb
        val (width, height) = with(screen) { drawing.width.toDp() to drawing.height.toDp() }

        Canvas(modifier = modifier.size(width, height)) {
            fun resolve(color: DrawColor): Color = when (color) {
                is DrawColor.Palette -> colors.crayon(color.color).srgb
                is DrawColor.Mix -> lerp(colors.crayon(color.from).srgb, colors.crayon(color.to).srgb, color.t.coerceIn(0f, 1f))
                is DrawColor.Ink -> when (color.level) {
                    InkLevel.STRONG -> strong
                    InkLevel.MEDIUM -> medium
                    InkLevel.FAINT -> faint
                }
            }
            val line = LINE * density
            // Any dashed line is drawn as an axis: a long dash, a dot, at the line's own width
            fun axis(width: Float) = PathEffect.dashPathEffect(floatArrayOf(6 * width + 4 * density, 2 * density + width, width, 2 * density + width))

            drawing.shapes.forEach { shape ->
                when (shape) {
                    is DrawShape.Box -> {
                        val topLeft = Offset(shape.rect.left, shape.rect.top)
                        val size = Size(shape.rect.right - shape.rect.left, shape.rect.bottom - shape.rect.top)
                        val color = resolve(shape.color)
                        if (shape.missing) drawHatching(topLeft, size, color.copy(alpha = MISSING_ALPHA), density, HATCH_PITCH * 2 * density)
                        else {
                            drawHatching(topLeft, size, color.copy(alpha = shape.opacity), density, HATCH_PITCH * density)
                            // A bar too thin for its outline is its hatching's colour, full
                            if (size.width < 3 * line || size.height < 3 * line) drawRect(color, topLeft, size, alpha = shape.opacity)
                            else drawRect(color, Offset(topLeft.x + line / 2, topLeft.y + line / 2), Size(size.width - line, size.height - line), alpha = shape.opacity, style = Stroke(line))
                        }
                    }
                    is DrawShape.Path -> drawPath(
                        path(shape.steps), resolve(shape.color), alpha = shape.opacity,
                        style = if (shape.filled) Fill else Stroke(width = shape.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = shape.dash?.let { axis(shape.strokeWidth) })
                    )
                    is DrawShape.Symbol -> drawSymbol(shape, resolve(shape.color), paper)
                    is DrawShape.Arc -> {
                        val topLeft = Offset(shape.center.x - shape.outer, shape.center.y - shape.outer)
                        val size = Size(shape.outer * 2, shape.outer * 2)
                        // Degrees clockwise from the top; Compose counts from three o'clock
                        drawArc(resolve(shape.color), shape.start - 90f, shape.sweep, true, topLeft, size, alpha = shape.opacity)
                        drawArc(paper, shape.start - 90f, shape.sweep, true, topLeft, size, style = Stroke(line))
                    }
                    is DrawShape.Segment -> drawLine(
                        resolve(shape.color), Offset(shape.from.x, shape.from.y), Offset(shape.to.x, shape.to.y),
                        strokeWidth = shape.strokeWidth, cap = StrokeCap.Butt, pathEffect = shape.dash?.let { axis(shape.strokeWidth) }
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

    /**
     * A point of a series: a round one is a small cross, as a surveyed point on a plan; the other
     * shapes keep their form, which may tell series apart, outlined on the paper unless filled.
     */
    private fun DrawScope.drawSymbol(shape: DrawShape.Symbol, color: Color, paper: Color) {
        val c = Offset(shape.center.x, shape.center.y)
        val r = shape.size / 2
        val line = LINE * density
        when (shape.shape) {
            SymbolShape.CIRCLE, SymbolShape.CROSS -> {
                drawLine(color, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), strokeWidth = line, alpha = shape.opacity)
                drawLine(color, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), strokeWidth = line, alpha = shape.opacity)
            }
            else -> {
                val outline = when (shape.shape) {
                    SymbolShape.SQUARE -> Path().apply { addRect(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
                    SymbolShape.TRIANGLE -> Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close() }
                    else -> Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
                }
                drawPath(outline, if (shape.filled) color else paper, shape.opacity)
                drawPath(outline, color, shape.opacity, Stroke(line, join = StrokeJoin.Miter))
            }
        }
    }

    /** The faint ink, the ink at this opacity: an axis's ticks, a gridline. */
    private const val FAINT_ALPHA = 0.35f

    /** The hatching of a bar: a line every this many dp; what is missing, twice as sparse and fainter. */
    private const val HATCH_PITCH = 5f
    private const val MISSING_ALPHA = 0.5f
}
