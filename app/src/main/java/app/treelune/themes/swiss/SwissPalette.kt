package app.treelune.themes.swiss

import androidx.compose.runtime.Immutable
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The Swiss theme's palettes (docs/design/swiss-theme.md): each a handful of numbers in OKLCH
 * (palettes.json, a resource of this package, set on the bench themes/swiss/bench/palettes.html,
 * which derives the colours the same way), every colour derived from them here. Black, an
 * off-white and a single red: the hue shift turns the red alone, the rest having no hue to turn.
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class SwissNumbers(
    /** The red's hue: the only one the hue shift turns. */
    val hue: Float,
    @SerialName("ground_lightness") val groundLightness: Float,
    @SerialName("ground_chroma") val groundChroma: Float,
    /** The hue of the ground's faint warmth, and of the inks' and greys': never turned. */
    @SerialName("ground_hue") val groundHue: Float,
    @SerialName("ink_lightness") val inkLightness: Float,
    /** The inks' chroma: none for the black ink, the off-white's trace for the light one. */
    @SerialName("ink_chroma") val inkChroma: Float,
    /** The secondary ink: descriptions, labels, a field's name. */
    @SerialName("dim_lightness") val dimLightness: Float,
    /** The grey of a gauge's track and of what is disabled. */
    @SerialName("track_lightness") val trackLightness: Float,
    /** The red: the app's signals (the floating button, the waiting mark, danger, an interaction). */
    @SerialName("accent_lightness") val accentLightness: Float,
    @SerialName("accent_chroma") val accentChroma: Float,
    /** What is written on the red: white on the light palette, the ground on the dark one. */
    @SerialName("on_accent_lightness") val onAccentLightness: Float,
    /** A tag's small square, at one lightness and chroma for all: a mark, held to 3 on the ground. */
    @SerialName("tag_lightness") val tagLightness: Float,
    @SerialName("tag_chroma") val tagChroma: Float,
    /** The states' lightness and chroma: a text in them holds 4.5 on the ground. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/** Every colour of one palette. */
@Immutable
class SwissColors(val numbers: SwissNumbers) {
    private val neutral = numbers.groundHue

    val ground = Lch(numbers.groundLightness, numbers.groundChroma, neutral)
    val ink = Lch(numbers.inkLightness, numbers.inkChroma, neutral)
    val dim = Lch(numbers.dimLightness, numbers.inkChroma, neutral)
    val track = Lch(numbers.trackLightness, 0f, neutral)

    val accent = Lch(numbers.accentLightness, numbers.accentChroma, numbers.hue)
    val onAccent = Lch(numbers.onAccentLightness, 0f, neutral)

    val success = status(145f)
    val warning = status(55f)
    val error = status(25f)
    val info = status(250f)

    /** Muted is the secondary ink: what is off steps back. */
    val muted: Lch get() = dim

    /** A tag's square, its hue its meaning; a grey keeping a trace of the ground's hue. */
    fun tag(color: TagColor): Lch =
        Lch(numbers.tagLightness, if (color == TagColor.GREY) GREY_CHROMA else numbers.tagChroma, TAG_HUES[color] ?: neutral)

    private fun status(statusHue: Float) = Lch(numbers.statusLightness, numbers.statusChroma, statusHue)

    private companion object {
        /** A grey tag keeps a trace of chroma, so it is this palette's grey. */
        const val GREY_CHROMA = 0.015f

        /** The same hues as the other themes': a tag's hue is its meaning. */
        val TAG_HUES = mapOf(
            TagColor.RED to 25f, TagColor.ORANGE to 55f, TagColor.YELLOW to 95f, TagColor.GREEN to 145f,
            TagColor.TEAL to 185f, TagColor.BLUE to 230f, TagColor.PURPLE to 300f, TagColor.PINK to 355f,
        )
    }
}

/** One palette of palettes.json: its id ("swiss_light", "swiss_dark"), its mode, its numbers. */
@Serializable
data class SwissPaletteEntry(val id: String, val mode: PaletteMode, val numbers: SwissNumbers)

@Serializable
private data class SwissPaletteFile(val palettes: List<SwissPaletteEntry>)

/** The palettes of palettes.json, read once: one per mode. */
object SwissPalettes {

    val entries: List<SwissPaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = SwissPalettes::class.java.getResourceAsStream("/app/treelune/themes/swiss/palettes.json")
            ?: error("palettes.json is missing from the swiss theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(SwissPaletteFile.serializer(), text).palettes
    }

    /** The colours derived so far, by mode and hue shift: a shift is chosen by dragging a slider. */
    private val colors = java.util.concurrent.ConcurrentHashMap<Pair<PaletteMode, Int>, SwissColors>()

    /** The colours of [mode], the red turned [hueShift] degrees (Appearance.hueShift), the rest kept. */
    fun colors(mode: PaletteMode, hueShift: Int): SwissColors = colors.getOrPut(mode to hueShift) {
        val numbers = entries.firstOrNull { it.mode == mode }?.numbers ?: error("no swiss palette in ${mode.name}")
        SwissColors(numbers.copy(hue = (numbers.hue + hueShift) % 360f))
    }
}
