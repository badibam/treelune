package app.treelune.themes.blueprint

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
 * The blueprint theme's palettes (docs/themes/blueprint.md): each a handful of numbers in
 * OKLCH (palettes.json, a resource of this package, set on the bench
 * themes/blueprint/bench/palettes.html, which derives the colours the same way), every colour
 * derived from them here. The light one is a whiteprint, blue lines on an off-white paper; the
 * dark one a blueprint, pale lines on a blue paper. One ink draws everything; an alert colour (red
 * on the whiteprint, a highlighter's yellow on the blueprint) marks what waits or destroys; the
 * tags are coloured pencils. The hue shift turns the blue and the paper, the alert, the pencils
 * and the states kept.
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class BlueprintNumbers(
    /** The blue's hue: the ink's, the secondary ink's, the grid's. */
    @SerialName("blue_hue") val blueHue: Float,
    /** The paper's hue: a warm off-white on the whiteprint, the blue itself on the blueprint. */
    @SerialName("paper_hue") val paperHue: Float,
    /** The alert's hue: the waiting mark, danger, an interaction. */
    @SerialName("alert_hue") val alertHue: Float,
    @SerialName("paper_lightness") val paperLightness: Float,
    @SerialName("paper_chroma") val paperChroma: Float,
    /** The ink: every line and text; a fill of it (the main button, a toast) has the paper written on it. */
    @SerialName("ink_lightness") val inkLightness: Float,
    @SerialName("ink_chroma") val inkChroma: Float,
    /** The secondary ink: descriptions, a subtitle, what is muted or disabled. */
    @SerialName("dim_lightness") val dimLightness: Float,
    @SerialName("dim_chroma") val dimChroma: Float,
    /** The grid's large squares and small ones: the ink at these opacities over the paper. */
    @SerialName("grid_major") val gridMajor: Float,
    @SerialName("grid_minor") val gridMinor: Float,
    @SerialName("alert_lightness") val alertLightness: Float,
    @SerialName("alert_chroma") val alertChroma: Float,
    /** The coloured pencils, the tags' colours, at one lightness and chroma for all: a text in them holds 4.5 on the paper. */
    @SerialName("crayon_lightness") val crayonLightness: Float,
    @SerialName("crayon_chroma") val crayonChroma: Float,
    /** The states' lightness and chroma: a text in them holds 4.5 on the paper. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/** Every colour of one palette. */
@Immutable
class BlueprintColors(val numbers: BlueprintNumbers) {
    private val blue = numbers.blueHue

    val paper = Lch(numbers.paperLightness, numbers.paperChroma, numbers.paperHue)
    val ink = Lch(numbers.inkLightness, numbers.inkChroma, blue)
    val dim = Lch(numbers.dimLightness, numbers.dimChroma, blue)
    val alert = Lch(numbers.alertLightness, numbers.alertChroma, numbers.alertHue)

    /** The grid's lines over the paper, its large squares and its small ones: the ink, faint. */
    val grid: Color get() = ink.srgb.copy(alpha = numbers.gridMajor)
    val subgrid: Color get() = ink.srgb.copy(alpha = numbers.gridMinor)

    val success = status(145f)
    val warning = status(55f)
    val error = status(25f)
    val info = status(250f)

    /** Muted is the secondary ink: what is off steps back. */
    val muted: Lch get() = dim

    /** A coloured pencil, its hue its meaning; a grey keeping a trace of the blue. */
    fun crayon(color: TagColor): Lch =
        Lch(numbers.crayonLightness, if (color == TagColor.GREY) GREY_CHROMA else numbers.crayonChroma, TAG_HUES[color] ?: blue)

    private fun status(statusHue: Float) = Lch(numbers.statusLightness, numbers.statusChroma, statusHue)

    companion object {
        /** A grey pencil keeps a trace of chroma, so it is this palette's grey. */
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

/** One palette of palettes.json: its id ("blueprint_light", "blueprint_dark"), its mode, its numbers. */
@Serializable
data class BlueprintPaletteEntry(val id: String, val mode: PaletteMode, val numbers: BlueprintNumbers)

@Serializable
private data class BlueprintPaletteFile(val palettes: List<BlueprintPaletteEntry>)

/** The palettes of palettes.json, read once: one per mode. */
object BlueprintPalettes {

    val entries: List<BlueprintPaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = BlueprintPalettes::class.java.getResourceAsStream("/app/treelune/themes/blueprint/palettes.json")
            ?: error("palettes.json is missing from the blueprint theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(BlueprintPaletteFile.serializer(), text).palettes
    }

    /** The colours derived so far, by mode and hue shift: a shift is chosen by dragging a slider. */
    private val colors = java.util.concurrent.ConcurrentHashMap<Pair<PaletteMode, Int>, BlueprintColors>()

    /** The colours of [mode], the blue and the paper turned [hueShift] degrees (Appearance.hueShift), the rest kept. */
    fun colors(mode: PaletteMode, hueShift: Int): BlueprintColors = colors.getOrPut(mode to hueShift) {
        val numbers = entries.firstOrNull { it.mode == mode }?.numbers ?: error("no blueprint palette in ${mode.name}")
        BlueprintColors(numbers.copy(blueHue = (numbers.blueHue + hueShift) % 360f, paperHue = (numbers.paperHue + hueShift) % 360f))
    }
}
