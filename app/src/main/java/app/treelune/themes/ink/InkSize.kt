package app.treelune.themes.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
 * The ink theme's sizes (docs/design/ink-theme.md): every length and every text of the mockup,
 * with more air than the Swiss one, times the factor of the size step the user chose
 * (CurrentTheme.sizeStep), but for the spaces between pieces, the margin and the grid's gaps, which stay in plain dp (the theme's spacing). Texts are in sp, so Android's text size enlarges them on top, for the
 * text alone. Titles are spaced, as a brush leaves room between its characters.
 */
@Immutable
class InkSize(val factor: Float) {

    /** [value] dp of the mockup, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** A finger's target, Android's 48 dp at the first step; the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(56f)

    /** An icon beside a text, alone in a button; an icon alone on its tile. */
    val icon: Dp get() = dp(24f)
    val tileIcon: Dp get() = dp(32f)

    /** The page's title, centred and spaced: shrunk to hold in two lines, never under a dialog's title. */
    val pageTitle: TextStyle = style(30f, 36f, FontWeight.Bold, 0.12f)

    val body: TextStyle = style(15f, 22f, FontWeight.Normal)
    val strong: TextStyle = style(15f, 22f, FontWeight.SemiBold)
    val caption: TextStyle = style(12f, 17f, FontWeight.Normal)
    /** A label above a field, a subtitle: small, a little spaced. */
    val label: TextStyle = style(12f, 16f, FontWeight.Normal, 0.04f)
    /** A button's word, a tab, a word of the selection bar. */
    val button: TextStyle = style(15f, 20f, FontWeight.SemiBold, 0.04f)
    /** A tool's name on its tile. */
    val name: TextStyle = style(15f, 20f, FontWeight.SemiBold, 0.06f)
    /** A group of tiles' title, a section's: bold, spaced, over a stroke of the brush. */
    val heading: TextStyle = style(16f, 22f, FontWeight.Bold, 0.1f)
    val subtitle: TextStyle = style(20f, 26f, FontWeight.Bold, 0.04f)
    val title: TextStyle = style(22f, 28f, FontWeight.Bold, 0.04f)
    /** A number on show (a counter): large, unhurried. */
    val number: TextStyle = style(40f, 44f, FontWeight.SemiBold)
    val sliderValue: TextStyle = style(20f, 26f, FontWeight.Bold)

    private fun style(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = Literata,
        fontWeight = weight,
        fontSize = (size * factor).sp,
        lineHeight = (line * factor).sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = "lnum",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    companion object {
        /** The factor of each size step, 0 to 3: the mockup, then larger by about an eighth each. */
        val FACTORS = floatArrayOf(1f, 1.12f, 1.25f, 1.4f)
    }
}

/** The size of the step the user chose. */
@Composable
fun inkSize(): InkSize {
    val step = CurrentTheme.sizeStep
    return remember(step) { InkSize(InkSize.FACTORS[step.coerceIn(0, InkSize.FACTORS.lastIndex)]) }
}

/**
 * Literata's three weights and the italic of a read text at its two (ThemeContract.emphasis),
 * embedded (res/font, made by scripts/make_literata_fonts.py).
 */
val Literata = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_semibold, FontWeight.SemiBold),
    Font(R.font.literata_bold, FontWeight.Bold),
    Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.literata_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** The current palette's colours. */
val inkColors: InkColors
    @Composable @ReadOnlyComposable get() = InkPalettes.colors(CurrentTheme.paletteMode, CurrentTheme.hueShift)

/** An ink a container imposes on what it holds (a filled button's, a deep wash's, a disabled one's). */
internal val LocalInkInk = compositionLocalOf<Color?> { null }

/** Whether the tile drawn is the one lifted from its grid in edit mode: its wash darkens. */
internal val LocalInkLifted = compositionLocalOf { false }
