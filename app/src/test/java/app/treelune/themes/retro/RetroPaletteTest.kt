package app.treelune.themes.retro

import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The retro palettes derive in the theme the colours they derive on the bench.
 *
 * The bench (bench/palettes.html) draws with its own derivation and saves what it drew beside the
 * numbers; a theme deriving otherwise would show on the phone colours nobody chose, found only by
 * holding the phone next to the bench.
 */
class RetroPaletteTest {

    private fun hex(lch: Lch): String {
        val c = lch.srgb
        return "#%02x%02x%02x".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
    }

    /** Two colours a step of 8 bits apart are the same: the bench computes in doubles, the theme in floats. */
    private fun close(a: String, b: String): Boolean =
        (1..5 step 2).all { abs(a.substring(it, it + 2).toInt(16) - b.substring(it, it + 2).toInt(16)) <= 1 }

    @Test
    fun `every palette derives the bench's colours`() {
        for (entry in RetroPalettes.entries) {
            val named = RetroColors(entry.numbers).named
            assertEquals("${entry.id}: the same names as the bench", entry.derived.keys, named.keys)
            for ((name, expected) in entry.derived) {
                val actual = hex(named.getValue(name))
                assertTrue("${entry.id} $name: bench $expected, theme $actual", close(expected, actual))
            }
        }
    }

    private fun contrast(a: Lch, b: Lch): Float {
        val (light, dark) = listOf(a.srgb.luminance(), b.srgb.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun legible(name: String, ink: Lch, ground: Lch, least: Float = 4.5f) {
        val ratio = contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    private val shifts = 0 until 360 step 15

    /**
     * Every text is legible where it is drawn at every hue shift (docs/THEMES.md, « The
     * palettes »): at one OKLCH lightness, the measured contrast changes with the hue.
     */
    @Test
    fun `every text is legible on what it is drawn on, at every shift`() {
        for (mode in PaletteMode.entries) for (shift in shifts) {
            val c = RetroPalettes.colors(mode, shift)
            for ((name, s) in listOf("screen" to c.screen, "panel" to c.panel)) {
                legible("$mode $shift ink on the $name", s.ink, s.ground)
                legible("$mode $shift dim ink on the $name", s.dim, s.ground)
                legible("$mode $shift strong ink on the $name", s.strong, s.ground)
                // The states are secondary, their texts short: the large text's ratio
                listOf(s.success, s.warning, s.error, s.info).forEach { legible("$mode $shift state $it on the $name", it, s.ground, 3f) }
                // An icon is a mark: held to the ratio of a graphic
                TagColor.entries.filter { it != TagColor.GREY }.forEach { legible("$mode $shift icon $it on the $name", c.icon(it, s), s.ground, 3f) }
            }
            TagColor.entries.forEach { legible("$mode $shift a tag's name on tag $it", c.tagText, c.tag(it)) }
            TagColor.entries.forEach { legible("$mode $shift drawing $it on the panel", c.drawing(it), c.panel.ground, 3f) }
        }
    }

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toList(), RetroPalettes.entries.map { it.mode }.sorted())
    }
}
