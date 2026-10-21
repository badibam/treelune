package app.treelune.themes.eink

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The e-reader theme's palettes and patterns (docs/themes/eink.md): pure black and white,
 * no grey anywhere. A light palette, black ink on white, and a dark one, its reverse; nothing to
 * set, so no palettes.json, and no hue to turn. Whatever the core gives in colour becomes a
 * pattern of the ink on the ground: a tag's colour, an icon's, a chart's series; a quantity (a
 * mix, an opacity) a regular screen of dots, denser as it grows. Every pattern is drawn without
 * smoothing, in whole screen pixels, so that it stays two colours.
 */

/** The two colours of one palette. */
@Immutable
class EinkColors(val ground: Color, val ink: Color)

/** The palette of each mode. */
object EinkPalettes {
    private val light = EinkColors(ground = Color.White, ink = Color.Black)
    private val dark = EinkColors(ground = Color.Black, ink = Color.White)

    fun colors(mode: PaletteMode): EinkColors = if (mode == PaletteMode.DARK) dark else light
}

/**
 * The patterns, as brushes a theme piece paints with. Each is drawn once in a tile of whole
 * screen pixels and repeated; [dp] is the screen pixels of one dp at the size step.
 */
object EinkPatterns {

    /** How much of the ground each colour covers, for a quantity coloured between two (DrawColor.Mix). */
    private val COVER = mapOf(
        TagColor.GREY to 0.1f, TagColor.YELLOW to 0.2f, TagColor.PINK to 0.35f, TagColor.TEAL to 0.45f,
        TagColor.GREEN to 0.55f, TagColor.ORANGE to 0.6f, TagColor.BLUE to 0.7f, TagColor.PURPLE to 0.8f, TagColor.RED to 1f,
    )

    /** The share of the ground [color] covers, 0 to 1. */
    fun cover(color: TagColor): Float = COVER.getValue(color)

    /**
     * The dashes a line of a series takes, in dp, its colour told by them: none for red, a
     * solid line. Grey's dots stand furthest apart, as its pattern is the emptiest.
     */
    fun dashes(color: TagColor): List<Float>? = when (color) {
        TagColor.RED -> null
        TagColor.ORANGE -> listOf(6f, 3f)
        TagColor.YELLOW -> listOf(1f, 3f)
        TagColor.GREEN -> listOf(10f, 3f, 2f, 3f)
        TagColor.TEAL -> listOf(3f, 3f)
        TagColor.BLUE -> listOf(8f, 4f)
        TagColor.PURPLE -> listOf(8f, 2f, 2f, 2f, 2f, 2f)
        TagColor.PINK -> listOf(2f, 2f)
        TagColor.GREY -> listOf(1f, 5f)
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Brush>()

    /**
     * [color]'s pattern in [ink] on [ground]: red solid, orange hatched one way, yellow sparse
     * dots, green a grid, teal horizontal lines, blue hatched the other way, purple crosshatched,
     * pink close dots, grey the ground alone. [fade] (0 to 1) clears that share of its ink, for
     * an opacity.
     */
    fun of(color: TagColor, ink: Color, ground: Color, dp: Float, fade: Float = 0f): Brush {
        val key = "${color.name}|${ink.value}|${ground.value}|$dp|${level(fade)}"
        return cache.getOrPut(key) {
            if (color == TagColor.RED && level(fade) == 0) SolidColor(ink)
            else tile(dp, period(color, dp), ink, ground, level(fade)) { canvas, side, paint -> draw(color, canvas, side, dp, paint) }
        }
    }

    /** A screen of dots of [ink] on [ground] covering [cover] (0 to 1) of it: a quantity, an opacity. */
    fun screen(cover: Float, ink: Color, ground: Color, dp: Float): Brush {
        val n = level(1f - cover)
        if (n == 0) return SolidColor(ink)
        if (n == LEVELS) return SolidColor(ground)
        return cache.getOrPut("screen|$n|${ink.value}|${ground.value}|$dp") {
            tile(dp, 0, ink, ground, n) { canvas, side, paint -> canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint) }
        }
    }

    /**
     * The checker of [ground] laid over a subtle motif: every other cell of one dp cleared, so a
     * subtle texture shows half its ink and stays two colours.
     */
    fun halftone(ground: Color, dp: Float): Brush = cache.getOrPut("half|${ground.value}|$dp") {
        val u = unit(dp)
        val bitmap = ImageBitmap(2 * u, 2 * u)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = ground; isAntiAlias = false }
        canvas.drawRect(0f, 0f, u.toFloat(), u.toFloat(), paint)
        canvas.drawRect(u.toFloat(), u.toFloat(), 2f * u, 2f * u, paint)
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }

    /** Opacities and covers are kept to sixteen steps, those of the ordered screen. */
    private const val LEVELS = 16

    private fun level(fade: Float): Int = (fade.coerceIn(0f, 1f) * LEVELS).roundToInt()

    /** One dp in whole screen pixels, never under one. */
    private fun unit(dp: Float): Int = max(1, dp.roundToInt())

    /** The side of [color]'s tile, in screen pixels. */
    private fun period(color: TagColor, dp: Float): Int = max(2, (dp * when (color) {
        TagColor.YELLOW -> 5f
        TagColor.PINK -> 3f
        else -> 4f
    }).roundToInt())

    /** Bayer's ordered 4 × 4 screen: a cell is cleared when its rank is under the level. */
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /**
     * A repeated tile on the ground: [pattern] drawn in the ink, then [cleared] cells of sixteen
     * in each 4 × 4 block of dp given back to the ground. The tile's side is a common multiple of
     * the pattern's [period] and of the screen's, so both repeat without a seam.
     */
    private fun tile(dp: Float, period: Int, ink: Color, ground: Color, cleared: Int, pattern: (Canvas, Int, Paint) -> Unit): Brush {
        val u = unit(dp)
        val screen = 4 * u
        val side = if (period <= 0) screen else if (cleared == 0) period else lcm(period, screen)
        val bitmap = ImageBitmap(side, side)
        val canvas = Canvas(bitmap)
        // Painted as they are, so a transparent ground leaves the pixels clear
        canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), Paint().apply { color = ground; blendMode = BlendMode.Src })
        val paint = Paint().apply { color = ink; isAntiAlias = false }
        if (period > 0) {
            for (x in 0 until side step period) for (y in 0 until side step period) {
                canvas.save(); canvas.translate(x.toFloat(), y.toFloat()); pattern(canvas, period, paint); canvas.restore()
            }
        } else pattern(canvas, side, paint)
        if (cleared > 0) {
            val clear = Paint().apply { color = ground; isAntiAlias = false; blendMode = BlendMode.Src }
            for (bx in 0 until side step u) for (by in 0 until side step u) {
                val rank = BAYER[((by / u) % 4) * 4 + (bx / u) % 4]
                if (rank < cleared) canvas.drawRect(bx.toFloat(), by.toFloat(), (bx + u).toFloat(), (by + u).toFloat(), clear)
            }
        }
        return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }

    /** [color]'s pattern in one tile [side] pixels square, lines leaving it coming back on the other side. */
    private fun draw(color: TagColor, canvas: Canvas, side: Int, dp: Float, paint: Paint) {
        val s = side.toFloat()
        fun stroke(width: Float) = Paint().apply {
            this.color = paint.color; isAntiAlias = false; style = PaintingStyle.Stroke
            strokeWidth = max(1f, (width * dp).roundToInt().toFloat()); strokeCap = StrokeCap.Square
        }
        // A diagonal across the tile, drawn again a tile away on each side so the repeat joins it
        fun diagonal(rising: Boolean, width: Float) {
            val p = stroke(width)
            for (shift in listOf(-s, 0f, s)) {
                val path = Path().apply {
                    if (rising) { moveTo(shift, s); lineTo(shift + s, 0f) } else { moveTo(shift, 0f); lineTo(shift + s, s) }
                }
                canvas.drawPath(path, p)
            }
        }
        fun dot(radius: Float) {
            val r = max(1f, radius * dp)
            canvas.drawRect(s / 2 - r, s / 2 - r, s / 2 + r, s / 2 + r, paint)
        }
        when (color) {
            TagColor.RED -> canvas.drawRect(0f, 0f, s, s, paint)
            TagColor.ORANGE -> diagonal(rising = true, width = 1.5f)
            TagColor.BLUE -> diagonal(rising = false, width = 1.5f)
            TagColor.PURPLE -> { diagonal(rising = true, width = 1f); diagonal(rising = false, width = 1f) }
            TagColor.TEAL -> canvas.drawRect(0f, 0f, s, max(1f, (1.5f * dp).roundToInt().toFloat()), paint)
            TagColor.GREEN -> {
                val w = max(1f, dp.roundToInt().toFloat())
                canvas.drawRect(0f, 0f, s, w, paint)
                canvas.drawRect(0f, 0f, w, s, paint)
            }
            TagColor.YELLOW -> dot(0.6f)
            TagColor.PINK -> dot(0.5f)
            TagColor.GREY -> Unit
        }
    }

    private fun lcm(a: Int, b: Int): Int {
        tailrec fun gcd(x: Int, y: Int): Int = if (y == 0) x else gcd(y, x % y)
        return a / gcd(a, b) * b
    }
}
