package app.treelune.themes.metro

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The metro theme's palettes (docs/design/metro-theme.md): each a handful of numbers in OKLCH
 * (palettes.json, a resource of this package, set on the bench themes/metro/bench/palettes.html,
 * which derives the colours the same way), every colour derived from them here. An off-white
 * ground, white panels and a night blue, the lines in the vivid colours of a metro map, and a red
 * for the alerts: the hue shift turns the night blue and the red, the lines and the states kept.
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class MetroNumbers(
    /** The night blue's hue: the inks', the band's, the fills', the ground's faint tint. */
    @SerialName("navy_hue") val navyHue: Float,
    /** The red's hue: the alerts (the waiting mark, danger, an interaction). */
    @SerialName("accent_hue") val accentHue: Float,
    @SerialName("ground_lightness") val groundLightness: Float,
    @SerialName("ground_chroma") val groundChroma: Float,
    /** A panel: a tile, a card, a dialog, a field. */
    @SerialName("panel_lightness") val panelLightness: Float,
    @SerialName("panel_chroma") val panelChroma: Float,
    /** The page header's band, the station's sign: white is written on it. */
    @SerialName("band_lightness") val bandLightness: Float,
    @SerialName("band_chroma") val bandChroma: Float,
    /** A fill (the main button, the floating one, a toast, the user's bubble): white is written on it. */
    @SerialName("fill_lightness") val fillLightness: Float,
    @SerialName("fill_chroma") val fillChroma: Float,
    @SerialName("ink_lightness") val inkLightness: Float,
    @SerialName("ink_chroma") val inkChroma: Float,
    /** The secondary ink: descriptions, a subtitle, what is muted. */
    @SerialName("dim_lightness") val dimLightness: Float,
    @SerialName("dim_chroma") val dimChroma: Float,
    /** The grey of a rail, a field's outline, what is disabled, a hairline. */
    @SerialName("track_lightness") val trackLightness: Float,
    /** The red as a fill, white written on it. */
    @SerialName("accent_lightness") val accentLightness: Float,
    @SerialName("accent_chroma") val accentChroma: Float,
    /** The red written on a panel: a danger's outline and word. */
    @SerialName("danger_lightness") val dangerLightness: Float,
    /** The lines, the tags' colours, at one lightness and chroma for all. */
    @SerialName("line_lightness") val lineLightness: Float,
    @SerialName("line_chroma") val lineChroma: Float,
    /** The states' lightness and chroma: a text in them holds 4.5 on a panel. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/** Every colour of one palette. */
@Immutable
class MetroColors(val numbers: MetroNumbers) {
    private val navy = numbers.navyHue

    val ground = Lch(numbers.groundLightness, numbers.groundChroma, navy)
    val panel = Lch(numbers.panelLightness, numbers.panelChroma, navy)
    val band = Lch(numbers.bandLightness, numbers.bandChroma, navy)
    val fill = Lch(numbers.fillLightness, numbers.fillChroma, navy)
    val ink = Lch(numbers.inkLightness, numbers.inkChroma, navy)
    val dim = Lch(numbers.dimLightness, numbers.dimChroma, navy)
    val track = Lch(numbers.trackLightness, 0f, navy)

    /** White: what is written on the band, a fill and the red. */
    val onFill = WHITE

    val accent = Lch(numbers.accentLightness, numbers.accentChroma, numbers.accentHue)
    val danger = Lch(numbers.dangerLightness, numbers.accentChroma, numbers.accentHue)

    val success = status(145f)
    val warning = status(55f)
    val error = status(25f)
    val info = status(250f)

    /** Muted is the secondary ink: what is off steps back. */
    val muted: Lch get() = dim

    /** What is written on a line when white reads worse: a deep night blue, the same in both modes. */
    private val deepNavy = Lch(DEEP_NAVY_LIGHTNESS, DEEP_NAVY_CHROMA, navy)

    /** A line's colour, its hue its meaning; a grey keeping a trace of the night blue. */
    fun line(color: TagColor): Lch =
        Lch(numbers.lineLightness, if (color == TagColor.GREY) GREY_CHROMA else numbers.lineChroma, TAG_HUES[color] ?: navy)

    /** What is written on a line (an icon in its roundel, a tag's text): white or the deep night blue, whichever reads better. */
    fun onLine(color: TagColor): Lch {
        val line = line(color)
        return if (contrast(WHITE, line) >= contrast(deepNavy, line)) WHITE else deepNavy
    }

    private fun status(statusHue: Float) = Lch(numbers.statusLightness, numbers.statusChroma, statusHue)

    companion object {
        val WHITE = Lch(1f, 0f, 0f)

        private const val DEEP_NAVY_LIGHTNESS = 0.22f
        private const val DEEP_NAVY_CHROMA = 0.06f

        /** A grey line keeps a trace of chroma, so it is this palette's grey. */
        private const val GREY_CHROMA = 0.015f

        /** The same hues as the other themes': a tag's hue is its meaning. */
        private val TAG_HUES = mapOf(
            TagColor.RED to 25f, TagColor.ORANGE to 55f, TagColor.YELLOW to 95f, TagColor.GREEN to 145f,
            TagColor.TEAL to 185f, TagColor.BLUE to 230f, TagColor.PURPLE to 300f, TagColor.PINK to 355f,
        )

        /** The contrast ratio of two colours, as the bench and the tests measure it. */
        fun contrast(a: Lch, b: Lch): Float {
            val (light, dark) = listOf(a.srgb.luminance(), b.srgb.luminance()).sortedDescending()
            return (light + 0.05f) / (dark + 0.05f)
        }
    }
}

/** One palette of palettes.json: its id ("metro_light", "metro_dark"), its mode, its numbers. */
@Serializable
data class MetroPaletteEntry(val id: String, val mode: PaletteMode, val numbers: MetroNumbers)

@Serializable
private data class MetroPaletteFile(val palettes: List<MetroPaletteEntry>)

/** The palettes of palettes.json, read once: one per mode. */
object MetroPalettes {

    val entries: List<MetroPaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = MetroPalettes::class.java.getResourceAsStream("/app/treelune/themes/metro/palettes.json")
            ?: error("palettes.json is missing from the metro theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(MetroPaletteFile.serializer(), text).palettes
    }

    /** The colours derived so far, by mode and hue shift: a shift is chosen by dragging a slider. */
    private val colors = java.util.concurrent.ConcurrentHashMap<Pair<PaletteMode, Int>, MetroColors>()

    /** The colours of [mode], the night blue and the red turned [hueShift] degrees (Appearance.hueShift), the rest kept. */
    fun colors(mode: PaletteMode, hueShift: Int): MetroColors = colors.getOrPut(mode to hueShift) {
        val numbers = entries.firstOrNull { it.mode == mode }?.numbers ?: error("no metro palette in ${mode.name}")
        MetroColors(numbers.copy(navyHue = (numbers.navyHue + hueShift) % 360f, accentHue = (numbers.accentHue + hueShift) % 360f))
    }
}
