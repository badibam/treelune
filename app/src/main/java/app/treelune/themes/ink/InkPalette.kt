package app.treelune.themes.ink

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/*
 * The ink theme's palettes (docs/themes/ink.md): each a handful of numbers in OKLCH
 * (palettes.json, a resource of this package, set on the bench themes/ink/bench/palettes.html,
 * which derives the colours the same way), every colour derived from them here. Cream paper,
 * black ink and a single seal red; a wash is ink laid thin on the paper. At night nothing lights
 * up: a wash is darker than the paper, as ink soaking in, and what is filled is a grey of thinned
 * light ink, the light ink written on it. The hue shift turns the red and the paper's warmth, the
 * ink following the paper's hue.
 */

/** The numbers of one palette, as palettes.json holds them. */
@Serializable
data class InkNumbers(
    /** The red's hue. */
    val hue: Float,
    @SerialName("ground_lightness") val groundLightness: Float,
    @SerialName("ground_chroma") val groundChroma: Float,
    /** The hue of the paper's warmth, which the inks follow. */
    @SerialName("ground_hue") val groundHue: Float,
    @SerialName("ink_lightness") val inkLightness: Float,
    @SerialName("ink_chroma") val inkChroma: Float,
    /** The ink a wash is made of: the ink itself by day, a darker one than the paper at night. */
    @SerialName("wash_lightness") val washLightness: Float,
    /** The secondary ink: descriptions, labels, a field's name. */
    @SerialName("dim_lightness") val dimLightness: Float,
    /** The seal's red: the waiting mark, a checked box, a page's seal, danger, an interaction's corner. */
    @SerialName("accent_lightness") val accentLightness: Float,
    @SerialName("accent_chroma") val accentChroma: Float,
    /** What is written on the red: the paper on the light palette, the ground on the dark one. */
    @SerialName("on_accent_lightness") val onAccentLightness: Float,
    /** The part of ink of what is filled (the main button, the floating one, a day chosen): all of it by day. */
    @SerialName("solid_part") val solidPart: Float,
    /** The part of ink of what is laid over the page (a toast, the selection bar). */
    @SerialName("deep_part") val deepPart: Float,
    /** The part of ink on the paper at the heart of a tile's wash. */
    @SerialName("tile_wash") val tileWash: Float,
    /** The part of ink of a card's wash, full under its text. */
    @SerialName("card_wash") val cardWash: Float,
    /** The part of ink of a gauge's track and of what is disabled. */
    @SerialName("track_wash") val trackWash: Float,
    /** The opacity at the heart of an icon's colour, a soft wash round it: lower at night, where a colour would glow. */
    @SerialName("color_wash") val colorWash: Float,
    /** A tag's small wash, at one lightness and chroma for all: a mark, held to 3 on the paper. */
    @SerialName("tag_lightness") val tagLightness: Float,
    @SerialName("tag_chroma") val tagChroma: Float,
    /** The states' lightness and chroma: a text in them holds 4.5 on the paper. */
    @SerialName("status_lightness") val statusLightness: Float,
    @SerialName("status_chroma") val statusChroma: Float,
)

/** Every colour of one palette, the paper and the red turned [hueShift] degrees. */
@Immutable
class InkColors(val numbers: InkNumbers, hueShift: Int) {
    private val paperHue = numbers.groundHue + hueShift

    val ground = Lch(numbers.groundLightness, numbers.groundChroma, paperHue).srgb
    val ink = Lch(numbers.inkLightness, numbers.inkChroma, paperHue).srgb
    val dim = Lch(numbers.dimLightness, numbers.inkChroma, paperHue).srgb

    val accent = Lch(numbers.accentLightness, numbers.accentChroma, numbers.hue + hueShift).srgb
    val onAccent = Lch(numbers.onAccentLightness, numbers.groundChroma, paperHue).srgb

    /** The ink a wash is made of. */
    private val washInk = Lch(numbers.washLightness, numbers.inkChroma, paperHue).srgb

    /** A tile's wash at its heart, a card's, a track's. */
    val wash = blend(ground, washInk, numbers.tileWash)
    val cardWash = blend(ground, washInk, numbers.cardWash)
    val trackWash = blend(ground, washInk, numbers.trackWash)

    /** A wash one step darker: what the finger presses, what is lifted. */
    val pressedWash = blend(ground, washInk, numbers.tileWash * PRESS_FACTOR)

    /** What is filled, and the ink written on it: the ground on the ink by day, the ink on its grey at night. */
    val solid = mix(numbers.solidPart)
    val onSolid = if (contrast(ground, solid) >= contrast(ink, solid)) ground else ink

    /** What is filled, under the finger: its ink thinned a step, toward the paper. */
    val pressedSolid = mix(numbers.solidPart - SOLID_STEP)

    /** What is laid over the page (a toast, the selection bar), the ink written on it being onSolid. */
    val deepWash = mix(numbers.deepPart)

    /**
     * An icon's colour, a dab of gouache under it: the tag's colour mixed into the paper, opaque,
     * as much as its wash held at its heart -- a pastel by day, a deep muted tone at night.
     */
    fun gouache(color: TagColor): Color = blend(ground, tag(color), numbers.colorWash)

    val success = status(145f)
    val warning = status(55f)
    val error = status(25f)
    val info = status(250f)

    /** Muted is the secondary ink: what is off steps back. */
    val muted: Color get() = dim

    /** A tag's wash, its hue its meaning; a grey keeping a trace of the paper's hue. */
    fun tag(color: TagColor): Color =
        Lch(numbers.tagLightness, if (color == TagColor.GREY) GREY_CHROMA else numbers.tagChroma, TAG_HUES[color] ?: numbers.groundHue).srgb

    /** The ink laid at [part] over the paper. */
    fun mix(part: Float): Color = blend(ground, ink, part)

    /** [over] laid at [part] on [under], mixed as the screen mixes them, on whole steps of 8 bits. */
    private fun blend(under: Color, over: Color, part: Float): Color {
        fun channel(g: Float, i: Float) = ((g * (1f - part) + i * part) * 255f).roundToInt() / 255f
        return Color(channel(under.red, over.red), channel(under.green, over.green), channel(under.blue, over.blue))
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun status(statusHue: Float) = Lch(numbers.statusLightness, numbers.statusChroma, statusHue).srgb

    private companion object {
        /** A grey tag keeps a trace of chroma, so it is this palette's grey. */
        const val GREY_CHROMA = 0.015f

        /** A pressed wash's ink, over a resting one's. */
        const val PRESS_FACTOR = 1.8f

        /** The part of ink a press takes from what is filled. */
        const val SOLID_STEP = 0.1f

        /** The same hues as the other themes': a tag's hue is its meaning. */
        val TAG_HUES = mapOf(
            TagColor.RED to 25f, TagColor.ORANGE to 55f, TagColor.YELLOW to 95f, TagColor.GREEN to 145f,
            TagColor.TEAL to 185f, TagColor.BLUE to 230f, TagColor.PURPLE to 300f, TagColor.PINK to 355f,
        )
    }
}

/** One palette of palettes.json: its id ("ink_light", "ink_dark"), its mode, its numbers. */
@Serializable
data class InkPaletteEntry(val id: String, val mode: PaletteMode, val numbers: InkNumbers)

@Serializable
private data class InkPaletteFile(val palettes: List<InkPaletteEntry>)

/** The palettes of palettes.json, read once: one per mode. */
object InkPalettes {

    val entries: List<InkPaletteEntry> by lazy {
        // An absolute path: the release build renames and moves this class out of its package,
        // and a relative one would then be looked up beside the renamed class, where it is not.
        val stream = InkPalettes::class.java.getResourceAsStream("/app/treelune/themes/ink/palettes.json")
            ?: error("palettes.json is missing from the ink theme's resources")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        Json { ignoreUnknownKeys = false }.decodeFromString(InkPaletteFile.serializer(), text).palettes
    }

    /** The colours derived so far, by mode and hue shift: a shift is chosen by dragging a slider. */
    private val colors = java.util.concurrent.ConcurrentHashMap<Pair<PaletteMode, Int>, InkColors>()

    /** The colours of [mode], the red and the paper turned [hueShift] degrees (Appearance.hueShift). */
    fun colors(mode: PaletteMode, hueShift: Int): InkColors = colors.getOrPut(mode to hueShift) {
        val numbers = entries.firstOrNull { it.mode == mode }?.numbers ?: error("no ink palette in ${mode.name}")
        InkColors(numbers, hueShift % 360)
    }
}
