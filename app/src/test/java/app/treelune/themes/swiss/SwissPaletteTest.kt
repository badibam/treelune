package app.treelune.themes.swiss

import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Swiss palettes are read, one per mode, and every text they draw is legible where it is
 * drawn at every hue shift (docs/design/swiss-theme.md): at one OKLCH lightness, the measured
 * contrast changes with the hue, so the red is held at each step of 15 degrees, as its bench
 * measures it. A palette retouched by eye can otherwise lose a contrast nobody notices until it
 * is on the phone.
 */
class SwissPaletteTest {

    private fun contrast(a: Lch, b: Lch): Float {
        val (light, dark) = listOf(a.srgb.luminance(), b.srgb.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun legible(name: String, ink: Lch, ground: Lch, least: Float = 4.5f) {
        val ratio = contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    private val shifts = 0 until 360 step 15

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toSet(), SwissPalettes.entries.map { it.mode }.toSet())
        assertEquals(PaletteMode.entries.size, SwissPalettes.entries.size)
    }

    @Test
    fun `every text is legible on what it is drawn on, at every shift`() {
        for (mode in PaletteMode.entries) for (shift in shifts) {
            val c = SwissPalettes.colors(mode, shift)
            legible("$mode $shift ink on the ground", c.ink, c.ground)
            legible("$mode $shift ground on a block of ink", c.ground, c.ink)
            legible("$mode $shift secondary ink on the ground", c.dim, c.ground)
            legible("$mode $shift red on the ground", c.accent, c.ground)
            legible("$mode $shift what is written on the red", c.onAccent, c.accent)
            listOf(c.success, c.warning, c.error, c.info).forEach { legible("$mode state $it on the ground", it, c.ground) }
            // A tag is a square before its text in the ink: a mark, held to the ratio of a graphic
            TagColor.entries.forEach { legible("$mode tag $it on the ground", c.tag(it), c.ground, 3f) }
        }
    }

    @Test
    fun `a hue shift turns the red alone`() {
        val base = SwissPalettes.colors(PaletteMode.LIGHT, 0)
        val turned = SwissPalettes.colors(PaletteMode.LIGHT, 120)
        assertTrue(base.accent.srgb != turned.accent.srgb)
        assertEquals(base.ground.srgb, turned.ground.srgb)
        assertEquals(base.ink.srgb, turned.ink.srgb)
        TagColor.entries.forEach { assertEquals(base.tag(it).srgb, turned.tag(it).srgb) }
        assertEquals(base.error.srgb, turned.error.srgb)
    }
}
