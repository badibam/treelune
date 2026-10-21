package app.treelune.themes.swiss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.treelune.R
import app.treelune.core.themes.CurrentTheme

/**
 * The Swiss theme's sizes (docs/themes/swiss.md): every length and every text of the
 * mockup, on a pitch of 8 dp, times the factor of the size step the user chose
 * (CurrentTheme.sizeStep), but for the spaces between pieces, the margin and the grid's gaps, which stay in plain dp (the theme's spacing). Texts are in sp, so Android's text size enlarges them on top, for the
 * text alone. Every text writes its figures at one width (tnum), so that numbers line up.
 */
@Immutable
class SwissSize(val factor: Float) {

    /** [value] dp of the mockup, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** A finger's target, Android's 48 dp at the first step; the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(56f)

    /** An icon beside a text, alone in a button; an icon alone on its tile. */
    val icon: Dp get() = dp(24f)
    val tileIcon: Dp get() = dp(32f)

    /** The page's title, its largest: shrunk to hold in two lines, never under a group's title. */
    val pageTitle: TextStyle = style(46f, 46f, FontWeight.Black, (-0.04f))

    val body: TextStyle = style(16f, 22f, FontWeight.Normal)
    val strong: TextStyle = style(16f, 22f, FontWeight.SemiBold)
    val caption: TextStyle = style(12f, 16f, FontWeight.Normal)
    /** A label above a field, a subtitle: small capitals, spaced. */
    val label: TextStyle = style(11f, 16f, FontWeight.SemiBold, 0.08f)
    /** A button's word, a tab, a word of the selection bar: capitals, spaced. */
    val button: TextStyle = style(13f, 16f, FontWeight.SemiBold, 0.08f)
    /** A tool's name on its tile: small capitals. */
    val name: TextStyle = style(13f, 18f, FontWeight.SemiBold, 0.06f)
    /**
     * A group of tiles' title, a tile's name: capitals, heavy, spaced; at 0.10 em, a word of
     * twelve capitals holds on a two cells' tile beside its icon up to the second size step.
     */
    val heading: TextStyle = style(12f, 16f, FontWeight.ExtraBold, 0.10f)
    val subtitle: TextStyle = style(22f, 26f, FontWeight.Black, (-0.02f))
    val title: TextStyle = style(22f, 26f, FontWeight.Black, (-0.02f))
    /** A number on show (a counter, a slider's value): the numbers are the theme's stars. */
    val number: TextStyle = style(46f, 46f, FontWeight.Black, (-0.04f))
    val sliderValue: TextStyle = style(22f, 26f, FontWeight.Black, (-0.03f))

    private fun style(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = Archivo,
        fontWeight = weight,
        fontSize = (size * factor).sp,
        lineHeight = (line * factor).sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    companion object {
        /** The factor of each size step, 0 to 3: the mockup, then larger by about an eighth each. */
        val FACTORS = floatArrayOf(1f, 1.12f, 1.25f, 1.4f)
    }
}

/** The size of the step the user chose, or of step 0 on a tile's icon (CurrentTheme.drawnStep). */
@Composable
fun swissSize(): SwissSize {
    val step = CurrentTheme.drawnStep()
    return remember(step) { SwissSize(SwissSize.FACTORS[step.coerceIn(0, SwissSize.FACTORS.lastIndex)]) }
}

/**
 * A stroke [value] dp thick at this size, on whole screen pixels and never under one: a rule
 * grown by a size step stays sharp.
 */
@Composable
fun swissStroke(value: Float): Dp {
    val density = LocalDensity.current
    val size = swissSize()
    return with(density) { maxOf(1f, kotlin.math.round(size.dp(value).toPx())).toDp() }
}

/**
 * Archivo's four weights and the italic of a read text at its two (ThemeContract.emphasis),
 * embedded (res/font, made by scripts/make_archivo_fonts.py).
 */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
    Font(R.font.archivo_black, FontWeight.Black),
    Font(R.font.archivo_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.archivo_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** The current palette's colours. */
val swissColors: SwissColors
    @Composable @ReadOnlyComposable get() = SwissPalettes.colors(CurrentTheme.paletteMode, CurrentTheme.hueShift)

/** An ink a container imposes on what it holds (a filled button's, a pressed one's, a disabled one's). */
internal val LocalSwissInk = compositionLocalOf<Color?> { null }

/** Whether the tile drawn is the one lifted from its grid in edit mode: it takes its outline. */
internal val LocalSwissLifted = compositionLocalOf { false }
