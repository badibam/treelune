package app.treelune.themes.eink

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
 * The e-reader theme's sizes (docs/design/eink-theme.md): every length and every text of the
 * mockup, on a pitch of 8 dp, times the factor of the size step the user chose
 * (CurrentTheme.sizeStep), but for the spaces between pieces, the margin and the grid's gaps,
 * which stay in plain dp (the theme's spacing). Texts are in sp, so Android's text size enlarges
 * them on top, for the text alone; the running text at 16 sp, a reading theme. With a single ink,
 * the order of the texts is told by their weight and size alone. Every text writes its figures at
 * one width (tnum), so that numbers line up.
 */
@Immutable
class EinkSize(val factor: Float) {

    /** [value] dp of the mockup, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** A finger's target, Android's 48 dp at the first step; the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(56f)

    /** An icon beside a text, alone in a button; an icon alone on its tile. */
    val icon: Dp get() = dp(24f)
    val tileIcon: Dp get() = dp(32f)

    /** The page's title, in the band: shrunk to hold in two lines, never under a dialog's title. */
    val pageTitle: TextStyle = style(26f, 30f, FontWeight.ExtraBold)

    val body: TextStyle = style(16f, 22f, FontWeight.Normal)
    val strong: TextStyle = style(16f, 22f, FontWeight.Bold)
    /** The smallest text, kept readable: a description, a date under a value. */
    val caption: TextStyle = style(13f, 18f, FontWeight.Normal)
    /** A label above a field, a page's subtitle: heavier than the text, never smaller than a caption. */
    val label: TextStyle = style(14f, 18f, FontWeight.SemiBold)
    /** A button's word, a tab, a word of the selection bar. */
    val button: TextStyle = style(16f, 20f, FontWeight.SemiBold)
    /** A tool's name on its tile. */
    val name: TextStyle = style(17f, 22f, FontWeight.Bold)
    /** A group of tiles' title: capitals, heavy, spaced, over a rule of 3 dp. */
    val heading: TextStyle = style(15f, 20f, FontWeight.ExtraBold, 0.12f)
    val subtitle: TextStyle = style(18f, 24f, FontWeight.Bold)
    val title: TextStyle = style(22f, 26f, FontWeight.ExtraBold)
    /** A number on show (a counter of the time picker). */
    val number: TextStyle = style(44f, 44f, FontWeight.ExtraBold)
    val sliderValue: TextStyle = style(22f, 26f, FontWeight.ExtraBold)

    private fun style(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = Atkinson,
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

/** The size of the step the user chose. */
@Composable
fun einkSize(): EinkSize {
    val step = CurrentTheme.sizeStep
    return remember(step) { EinkSize(EinkSize.FACTORS[step.coerceIn(0, EinkSize.FACTORS.lastIndex)]) }
}

/**
 * A stroke [value] dp thick at this size, on whole screen pixels and never under one: a rule
 * grown by a size step stays sharp.
 */
@Composable
fun einkStroke(value: Float): Dp {
    val density = LocalDensity.current
    val size = einkSize()
    return with(density) { maxOf(1f, kotlin.math.round(size.dp(value).toPx())).toDp() }
}

/** The screen pixels of one dp at the size step: the unit the patterns are drawn in. */
@Composable
fun einkPatternDp(): Float = with(LocalDensity.current) { einkSize().dp(1f).toPx() }

/**
 * Atkinson Hyperlegible Next's four weights and the italic of a read text at two of them
 * (ThemeContract.emphasis), embedded (res/font, made by scripts/make_atkinson_fonts.py).
 */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Bold),
    Font(R.font.atkinson_extrabold, FontWeight.ExtraBold),
    Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** The current palette's two colours. */
val einkColors: EinkColors
    @Composable @ReadOnlyComposable get() = EinkPalettes.colors(CurrentTheme.paletteMode)

/** An ink a container imposes on what it holds (a filled button's, a pressed one's, the band's). */
internal val LocalEinkInk = compositionLocalOf<Color?> { null }

/** Whether the tile drawn is the one lifted from its grid in edit mode: it takes its shadow. */
internal val LocalEinkLifted = compositionLocalOf { false }
