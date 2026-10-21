package app.treelune.themes.metro

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
 * The metro theme's sizes (docs/themes/metro.md): every length and every text of the
 * mockup, on a pitch of 8 dp, times the factor of the size step the user chose
 * (CurrentTheme.sizeStep), but for the spaces between pieces, the margin and the grid's gaps,
 * which stay in plain dp (the theme's spacing). Texts are in sp, so Android's text size enlarges
 * them on top, for the text alone. Every text writes its figures at one width (tnum), so that
 * numbers line up, as on a departures board.
 */
@Immutable
class MetroSize(val factor: Float) {

    /** [value] dp of the mockup, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** A finger's target, Android's 48 dp at the first step; the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(56f)

    /** An icon beside a text, alone in a button; an icon alone on its tile. */
    val icon: Dp get() = dp(24f)
    val tileIcon: Dp get() = dp(34f)

    /** The page's title, white on the band: wrapping at its syllables, shrunk to hold in two lines. */
    val pageTitle: TextStyle = style(26f, 30f, FontWeight.ExtraBold)

    val body: TextStyle = style(16f, 22f, FontWeight.Medium)
    val strong: TextStyle = style(16f, 22f, FontWeight.Bold)
    val caption: TextStyle = style(12f, 16f, FontWeight.Medium)
    /** A label above a field, a subtitle. */
    val label: TextStyle = style(13f, 18f, FontWeight.Bold)
    /** A button's word, a tab, a word of the selection bar. */
    val button: TextStyle = style(15f, 18f, FontWeight.Bold)
    /** A tool's name on its tile, a heading in a card. */
    val name: TextStyle = style(15f, 20f, FontWeight.ExtraBold)
    /** A group of tiles' title, a stop on the line: capitals, heavy, spaced. */
    val section: TextStyle = style(13f, 18f, FontWeight.ExtraBold, 0.08f)
    /** A tag's word, on its line's colour. */
    val tag: TextStyle = style(12f, 16f, FontWeight.Bold)
    val subtitle: TextStyle = style(20f, 24f, FontWeight.ExtraBold)
    val title: TextStyle = style(20f, 24f, FontWeight.ExtraBold)
    /** A number on show: a departures board's figures (the time), a slider's value. */
    val number: TextStyle = style(40f, 44f, FontWeight.ExtraBold)
    val sliderValue: TextStyle = style(20f, 24f, FontWeight.ExtraBold)

    private fun style(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = Overpass,
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
fun metroSize(): MetroSize {
    val step = CurrentTheme.drawnStep()
    return remember(step) { MetroSize(MetroSize.FACTORS[step.coerceIn(0, MetroSize.FACTORS.lastIndex)]) }
}

/**
 * A stroke [value] dp thick at this size, on whole screen pixels and never under one: a line
 * grown by a size step stays sharp.
 */
@Composable
fun metroStroke(value: Float): Dp {
    val density = LocalDensity.current
    val size = metroSize()
    return with(density) { maxOf(1f, kotlin.math.round(size.dp(value).toPx())).toDp() }
}

/**
 * Overpass's three weights and the italic of a read text at two of them (ThemeContract.emphasis),
 * embedded (res/font, made by scripts/make_overpass_fonts.py).
 */
val Overpass = FontFamily(
    Font(R.font.overpass_medium, FontWeight.Medium),
    Font(R.font.overpass_bold, FontWeight.Bold),
    Font(R.font.overpass_extrabold, FontWeight.ExtraBold),
    Font(R.font.overpass_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.overpass_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** The current palette's colours. */
val metroColors: MetroColors
    @Composable @ReadOnlyComposable get() = MetroPalettes.colors(CurrentTheme.paletteMode, CurrentTheme.hueShift)

/** An ink a container imposes on what it holds (a fill's white, a line's, a disabled button's grey). */
internal val LocalMetroInk = compositionLocalOf<Color?> { null }

/**
 * The colour of the line a tile is drawn in, for what is drawn inside it (a gauge, a checked box, a
 * chart's strong ink); null outside a tile, or in a tile without a colour: the night blue then.
 */
internal val LocalMetroLine = compositionLocalOf<Color?> { null }

/** Whether what is drawn stands on a block of a line's colour (an icon's tile): an icon there is bare. */
internal val LocalMetroOnLine = compositionLocalOf { false }

/** Whether the text drawn is a section's title (CardType.SECTION_HEADER): a stop, in spaced capitals. */
internal val LocalMetroSection = compositionLocalOf { false }

/** Whether the tile drawn is the one lifted from its grid in edit mode. */
internal val LocalMetroLifted = compositionLocalOf { false }
