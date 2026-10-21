package app.treelune.themes.paper

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.treelune.R
import app.treelune.core.themes.CurrentTheme

/**
 * The cut paper theme's sizes (docs/themes/paper.md): every length and every text of the
 * mockup, on a pitch of 8 dp, times the factor of the size step the user chose
 * (CurrentTheme.sizeStep), but for the spaces between pieces, the margin and the grid's gaps,
 * which stay in plain dp (the theme's spacing). Texts are in sp, so Android's text size enlarges
 * them on top, for the text alone. Two weights only, Bricolage's text at 500 and its labels at
 * 800; the titles in its cut drawn for large sizes.
 */
@Immutable
class PaperSize(val factor: Float) {

    /** [value] dp of the mockup, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** A finger's target, Android's 48 dp at the first step; the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(56f)

    /** An icon beside a text, alone in a button; an icon alone on its tile. */
    val icon: Dp get() = dp(24f)

    /** The page's title, on its cream strip: wrapping at its syllables, shrunk to hold in two lines. */
    val pageTitle: TextStyle = style(26f, 30f, FontWeight.ExtraBold, BricolageTitle)

    val body: TextStyle = style(16f, 22f, FontWeight.Medium)
    val strong: TextStyle = style(16f, 22f, FontWeight.ExtraBold)
    val caption: TextStyle = style(12f, 16f, FontWeight.Medium)
    /** A label above a field, a subtitle. */
    val label: TextStyle = style(13f, 18f, FontWeight.ExtraBold)
    /** A button's word, a tab, a word of the selection bar. */
    val button: TextStyle = style(15f, 18f, FontWeight.ExtraBold)
    /** A tool's name on its tile, a heading in a card. */
    val name: TextStyle = style(16f, 20f, FontWeight.ExtraBold)
    /** A group of tiles' title, on its coral band. */
    val section: TextStyle = style(17f, 22f, FontWeight.ExtraBold, BricolageTitle)
    /** A tag's word, on its coloured label. */
    val tag: TextStyle = style(12f, 16f, FontWeight.ExtraBold)
    val subtitle: TextStyle = style(20f, 24f, FontWeight.ExtraBold, BricolageTitle)
    val title: TextStyle = style(22f, 26f, FontWeight.ExtraBold, BricolageTitle)
    /** A number on show: the time's figures, a slider's value. */
    val number: TextStyle = style(40f, 44f, FontWeight.ExtraBold, BricolageTitle)
    val sliderValue: TextStyle = style(20f, 24f, FontWeight.ExtraBold)

    private fun style(size: Float, line: Float, weight: FontWeight, family: FontFamily = Bricolage, tracking: Float = 0f) = TextStyle(
        fontFamily = family,
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
fun paperSize(): PaperSize {
    val step = CurrentTheme.sizeStep
    return remember(step) { PaperSize(PaperSize.FACTORS[step.coerceIn(0, PaperSize.FACTORS.lastIndex)]) }
}

/**
 * A stroke [value] dp thick at this size, on whole screen pixels and never under one: a line
 * grown by a size step stays sharp.
 */
@Composable
fun paperStroke(value: Float): Dp {
    val density = LocalDensity.current
    val size = paperSize()
    return with(density) { maxOf(1f, kotlin.math.round(size.dp(value).toPx())).toDp() }
}

/**
 * Bricolage Grotesque's two cuts for what is read, embedded (res/font, made by
 * scripts/make_bricolage_fonts.py): the text at 500 and the labels at 800, each at the optical
 * size of what it writes. It has no italic: a read text's italic is the slant Android makes.
 */
val Bricolage = FontFamily(
    Font(R.font.bricolage_text, FontWeight.Medium),
    Font(R.font.bricolage_label, FontWeight.ExtraBold),
)

/** Bricolage Grotesque's cut for titles, at 800, drawn for large sizes. */
val BricolageTitle = FontFamily(
    Font(R.font.bricolage_title, FontWeight.ExtraBold),
)

/** The current palette's colours. */
val paperColors: PaperColors
    @Composable @ReadOnlyComposable get() = PaperPalettes.colors(CurrentTheme.paletteMode, CurrentTheme.hueShift)

/** An ink a container imposes on what it holds (the cream on the night blue, a coloured sheet's, a disabled button's). */
internal val LocalPaperInk = compositionLocalOf<Color?> { null }

/**
 * The colour of the sheet a tile is cut from, for what is drawn inside it that must stand out on
 * it (a checked box, a chart's strong ink): the ink that reads on it; null outside a tile, or on a
 * cream sheet: the night blue then.
 */
internal val LocalPaperStrong = compositionLocalOf<Color?> { null }

/** Whether what is drawn stands on an icon's tile: its icon on a round of cream cut by hand. */
internal val LocalPaperOnIconTile = compositionLocalOf { false }

/** Whether the text drawn is a section's title (CardType.SECTION_HEADER): on its coral band. */
internal val LocalPaperSection = compositionLocalOf { false }

/** Whether the tile drawn is the one lifted from its grid in edit mode. */
internal val LocalPaperLifted = compositionLocalOf { false }
