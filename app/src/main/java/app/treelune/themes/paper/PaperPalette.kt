package app.treelune.themes.paper

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The cut paper theme's palettes (docs/design/paper-theme.md): each a handful of numbers in OKLCH
 * (palettes.json, a resource of this package, set on the bench themes/paper/bench/palettes.html,
 * which derives the colours the same way), every colour derived from them here. A kraft ground, a
 * cream sheet, a dark brown ink, a coral and a night blue, and the coloured sheets of the tags: the
 * hue shift turns the kraft, the coral and the night blue; paper stays paper, the coloured sheets
 * and the states are kept.
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class PaperNumbers(
    /** The kraft's hue: the ground's, its fibres' and the dashes', the shadow's. */
    @SerialName("kraft_hue") val kraftHue: Float,
    /** The coral's hue: the accent and the alerts (a group's title, the waiting mark, danger). */
    @SerialName("accent_hue") val accentHue: Float,
    /** The night blue's hue: a main button, a toast, the user's bubble. */
    @SerialName("navy_hue") val navyHue: Float,
    @SerialName("kraft_lightness") val kraftLightness: Float,
    @SerialName("kraft_chroma") val kraftChroma: Float,
    /** The cream sheet: a tile without a colour, a card, a dialog. */
    @SerialName("sheet_lightness") val sheetLightness: Float,
    @SerialName("sheet_chroma") val sheetChroma: Float,
    /** The white sheet: a field, a checkbox; a third of the cream's chroma. */
    @SerialName("white_lightness") val whiteLightness: Float,
    @SerialName("ink_lightness") val inkLightness: Float,
    @SerialName("ink_chroma") val inkChroma: Float,
    /** The secondary ink: descriptions, a subtitle, what is muted. */
    @SerialName("dim_lightness") val dimLightness: Float,
    @SerialName("dim_chroma") val dimChroma: Float,
    /** The brown of the dashes (a chart's target, the grid's cells) and of the kraft's fibres. */
    @SerialName("rule_lightness") val ruleLightness: Float,
    @SerialName("rule_chroma") val ruleChroma: Float,
    /** The hard shadow under every sheet: its lightness, at half the kraft's chroma, and its opacity. */
    @SerialName("shadow_lightness") val shadowLightness: Float,
    @SerialName("shadow_alpha") val shadowAlpha: Float,
    @SerialName("accent_lightness") val accentLightness: Float,
    @SerialName("accent_chroma") val accentChroma: Float,
    @SerialName("navy_lightness") val navyLightness: Float,
    @SerialName("navy_chroma") val navyChroma: Float,
    /** The coloured sheets, the tags' colours, at one lightness and chroma for all. */
    @SerialName("colour_lightness") val colourLightness: Float,
    @SerialName("colour_chroma") val colourChroma: Float,
    /** The states' lightness and chroma: a text in them holds 4.5 on the cream sheet. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/** Every colour of one palette. */
@Immutable
class PaperColors(val numbers: PaperNumbers) {
    private val kraft = numbers.kraftHue

    val ground = Lch(numbers.kraftLightness, numbers.kraftChroma, kraft)
    val sheet = Lch(numbers.sheetLightness, numbers.sheetChroma, SHEET_HUE)
    val white = Lch(numbers.whiteLightness, numbers.sheetChroma / 3f, SHEET_HUE)
    val ink = Lch(numbers.inkLightness, numbers.inkChroma, INK_HUE)
    val dim = Lch(numbers.dimLightness, numbers.dimChroma, INK_HUE)
    val rule = Lch(numbers.ruleLightness, numbers.ruleChroma, kraft)

    /** The hard shadow, with its opacity. */
    val shadow: Color = Lch(numbers.shadowLightness, numbers.kraftChroma / 2f, kraft).srgb.copy(alpha = numbers.shadowAlpha)

    val accent = Lch(numbers.accentLightness, numbers.accentChroma, numbers.accentHue)
    val navy = Lch(numbers.navyLightness, numbers.navyChroma, numbers.navyHue)

    /** What is written on the night blue: the cream. */
    val onNavy = CREAM

    /** What is written on the coral: the cream or the dark ink, whichever reads better. */
    val onAccent = onColour(accent)

    val success = status(145f)
    val warning = status(55f)
    val error = status(25f)
    val info = status(250f)

    /** Muted is the secondary ink: what is off steps back. */
    val muted: Lch get() = dim

    /** A coloured sheet, its hue its meaning; a grey keeping a trace of the cream. */
    fun colour(color: TagColor): Lch =
        if (color == TagColor.GREY) Lch(numbers.colourLightness, GREY_CHROMA, SHEET_HUE)
        else Lch(numbers.colourLightness, numbers.colourChroma, TAG_HUES.getValue(color))

    /** What is written on a coloured sheet (a tile's text, an icon, a tag's word): the cream or the dark ink, whichever reads better. */
    fun onColour(color: TagColor): Lch = onColour(colour(color))

    private fun status(statusHue: Float) = Lch(numbers.statusLightness, numbers.statusChroma, statusHue)

    companion object {
        /** The cream sheet's hue and the ink's, kept by the hue shift: paper stays paper. */
        const val SHEET_HUE = 83f
        const val INK_HUE = 67f

        /** The cream and the dark ink of the light palette, written on a colour in both modes. */
        val CREAM = Lch(0.982f, 0.015f, 77f)
        val DARK_INK = Lch(0.255f, 0.021f, INK_HUE)

        /** A grey sheet keeps a trace of chroma, so it is this palette's grey. */
        private const val GREY_CHROMA = 0.012f

        /** The same hues as the other themes': a tag's hue is its meaning. */
        private val TAG_HUES = mapOf(
            TagColor.RED to 25f, TagColor.ORANGE to 55f, TagColor.YELLOW to 95f, TagColor.GREEN to 145f,
            TagColor.TEAL to 185f, TagColor.BLUE to 230f, TagColor.PURPLE to 300f, TagColor.PINK to 355f,
        )

        /** The cream or the dark ink on [background], whichever reads better. */
        fun onColour(background: Lch): Lch = if (contrast(CREAM, background) >= contrast(DARK_INK, background)) CREAM else DARK_INK

        /** The contrast ratio of two colours, as the bench and the tests measure it. */
        fun contrast(a: Lch, b: Lch): Float {
            val (light, dark) = listOf(a.srgb.luminance(), b.srgb.luminance()).sortedDescending()
            return (light + 0.05f) / (dark + 0.05f)
        }
    }
}

/** One palette of palettes.json: its id ("paper_light", "paper_dark"), its mode, its numbers. */
@Serializable
data class PaperPaletteEntry(val id: String, val mode: PaletteMode, val numbers: PaperNumbers)

@Serializable
private data class PaperPaletteFile(val palettes: List<PaperPaletteEntry>)

/** The palettes of palettes.json, read once: one per mode. */
object PaperPalettes {

    val entries: List<PaperPaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = PaperPalettes::class.java.getResourceAsStream("/app/treelune/themes/paper/palettes.json")
            ?: error("palettes.json is missing from the paper theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(PaperPaletteFile.serializer(), text).palettes
    }

    /** The colours derived so far, by mode and hue shift: a shift is chosen by dragging a slider. */
    private val colors = java.util.concurrent.ConcurrentHashMap<Pair<PaletteMode, Int>, PaperColors>()

    /** The colours of [mode], the kraft, the coral and the night blue turned [hueShift] degrees (Appearance.hueShift), the rest kept. */
    fun colors(mode: PaletteMode, hueShift: Int): PaperColors = colors.getOrPut(mode to hueShift) {
        val numbers = entries.firstOrNull { it.mode == mode }?.numbers ?: error("no paper palette in ${mode.name}")
        PaperColors(numbers.copy(
            kraftHue = (numbers.kraftHue + hueShift) % 360f,
            accentHue = (numbers.accentHue + hueShift) % 360f,
            navyHue = (numbers.navyHue + hueShift) % 360f,
        ))
    }
}
