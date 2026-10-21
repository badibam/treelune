package app.treelune.themes.blueprint

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
 * The blueprint theme's sizes (docs/themes/blueprint.md): every length and every text of the
 * bench, on a pitch of 8 dp, times the factor of the size step the user chose
 * (CurrentTheme.sizeStep), but for the spaces between pieces, the margin and the grid's gaps,
 * which stay in plain dp (the theme's spacing). Texts are in sp, so Android's text size enlarges
 * them on top, for the text alone. Two fonts: the hand lettering of a plan (Architects Daughter)
 * for titles, names and labels, in spaced capitals; IBM Plex Mono for every other text, the
 * figures of the dimensions.
 */
@Immutable
class BlueprintSize(val factor: Float) {

    /** [value] dp of the bench, at this size. */
    fun dp(value: Float): Dp = (value * factor).dp

    /** A finger's target, Android's 48 dp at the first step; the floating button's side. */
    val touch: Dp get() = dp(48f)
    val floating: Dp get() = dp(56f)

    /** An icon beside a text, alone in a button; an icon alone on its tile. */
    val icon: Dp get() = dp(24f)
    val tileIcon: Dp get() = dp(34f)

    /** The page's title in its frame: wrapping at its syllables, shrunk to hold in two lines. */
    val pageTitle: TextStyle = lettering(24f, 28f)
    /** The scale's cell beside the page's title. */
    val scale: TextStyle = mono(11f, 14f, FontWeight.Normal)

    val body: TextStyle = mono(15f, 21f, FontWeight.Normal)
    val strong: TextStyle = mono(15f, 21f, FontWeight.Medium)
    val caption: TextStyle = mono(12f, 16f, FontWeight.Normal)
    /** A label above a field. */
    val label: TextStyle = lettering(13f, 18f)
    /** A button's word, a tab, a word of the selection bar. */
    val button: TextStyle = lettering(15f, 18f)
    /** A tool's name on its tile, a heading in a card. */
    val name: TextStyle = lettering(15f, 20f)
    /** A group of tiles' title, more spaced. */
    val section: TextStyle = lettering(15f, 20f, 0.12f)
    /** A tag's word, in its pencil. */
    val tag: TextStyle = mono(12f, 16f, FontWeight.Medium)
    val subtitle: TextStyle = lettering(18f, 22f)
    val title: TextStyle = lettering(18f, 22f)
    /** A number on show: the time's counters, a slider's value. */
    val number: TextStyle = mono(38f, 42f, FontWeight.Medium)
    val sliderValue: TextStyle = mono(18f, 22f, FontWeight.Medium)

    /** The lettering: one weight, spaced; a caller writes it in capitals (BlueprintTheme.Line). */
    private fun lettering(size: Float, line: Float, tracking: Float = 0.08f) = style(Lettering, FontWeight.Normal, size, line, tracking)

    private fun mono(size: Float, line: Float, weight: FontWeight) = style(PlexMono, weight, size, line, 0f)

    private fun style(family: FontFamily, weight: FontWeight, size: Float, line: Float, tracking: Float) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * factor).sp,
        lineHeight = (line * factor).sp,
        letterSpacing = tracking.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    companion object {
        /** The factor of each size step, 0 to 3: the bench, then larger by about an eighth each. */
        val FACTORS = floatArrayOf(1f, 1.12f, 1.25f, 1.4f)
    }
}

/** The size of the step the user chose, or of step 0 on a tile's icon (CurrentTheme.drawnStep). */
@Composable
fun blueprintSize(): BlueprintSize {
    val step = CurrentTheme.drawnStep()
    return remember(step) { BlueprintSize(BlueprintSize.FACTORS[step.coerceIn(0, BlueprintSize.FACTORS.lastIndex)]) }
}

/**
 * A stroke [value] dp thick at this size, never under one screen pixel: the theme's lines are
 * fine, 1.5 dp, and drawn smooth rather than rounded to whole pixels, which would make some of
 * them twice as thick as others.
 */
@Composable
fun blueprintStroke(value: Float): Dp {
    val density = LocalDensity.current
    val size = blueprintSize()
    return with(density) { maxOf(1f, size.dp(value).toPx()).toDp() }
}

/** Whether a text style is the lettering, written in capitals. */
val TextStyle.isLettering: Boolean get() = fontFamily == Lettering

/** The hand lettering of a plan (res/font, made by scripts/make_blueprint_fonts.py): one weight. */
val Lettering = FontFamily(Font(R.font.architects_daughter, FontWeight.Normal))

/**
 * IBM Plex Mono's text, its medium (the values, a read text's bold) and its italic (ThemeContract.emphasis),
 * embedded (res/font, copied by scripts/make_blueprint_fonts.py).
 */
val PlexMono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
    Font(R.font.plexmono_italic, FontWeight.Normal, FontStyle.Italic),
)

/** The current palette's colours. */
val blueprintColors: BlueprintColors
    @Composable @ReadOnlyComposable get() = BlueprintPalettes.colors(CurrentTheme.paletteMode, CurrentTheme.hueShift)

/** An ink a container imposes on what it holds (a fill's paper, a disabled button's secondary ink). */
internal val LocalBlueprintInk = compositionLocalOf<Color?> { null }

/**
 * The pencil of the tile drawn, for what is drawn inside it (a gauge, a checked box, a chart's
 * strong ink); null outside a tile, or in a tile without a colour: the ink then.
 */
internal val LocalBlueprintCrayon = compositionLocalOf<Color?> { null }

/** Whether the text drawn is a section's title (CardType.SECTION_HEADER): more spaced. */
internal val LocalBlueprintSection = compositionLocalOf { false }

/** Whether the tile drawn is the one lifted from its grid in edit mode: its frame thicker. */
internal val LocalBlueprintLifted = compositionLocalOf { false }
