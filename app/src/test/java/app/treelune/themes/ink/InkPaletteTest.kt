package app.treelune.themes.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ink palettes are read, one per mode, and every text they draw is legible where it is
 * drawn at every hue shift (docs/design/ink-theme.md), as its bench measures it: on the paper,
 * on the washes a text stands on (a tile's heart, a card, a track under a disabled button's word)
 * and on what is filled (a main button, a toast). At one OKLCH lightness the measured contrast
 * changes with the hue, so each is held at every step of 15 degrees.
 */
class InkPaletteTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun legible(name: String, ink: Color, ground: Color, least: Float = 4.5f) {
        val ratio = contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    private val shifts = 0 until 360 step 15

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toSet(), InkPalettes.entries.map { it.mode }.toSet())
        assertEquals(PaletteMode.entries.size, InkPalettes.entries.size)
    }

    @Test
    fun `every text is legible on what it is drawn on, at every shift`() {
        for (mode in PaletteMode.entries) for (shift in shifts) {
            val c = InkPalettes.colors(mode, shift)
            legible("$mode $shift ink on the paper", c.ink, c.ground)
            legible("$mode $shift ink on a tile's wash", c.ink, c.wash)
            legible("$mode $shift ink written on what is filled", c.onSolid, c.solid)
            legible("$mode $shift ink written on what is filled, pressed", c.onSolid, c.pressedSolid)
            legible("$mode $shift ink written on a deep wash", c.onSolid, c.deepWash)
            legible("$mode $shift secondary ink on the paper", c.dim, c.ground)
            legible("$mode $shift secondary ink on a tile's wash", c.dim, c.wash)
            legible("$mode $shift secondary ink on a card", c.dim, c.cardWash)
            legible("$mode $shift secondary ink on a track", c.dim, c.trackWash)
            legible("$mode $shift red on the paper", c.accent, c.ground)
            legible("$mode $shift what is written on the red", c.onAccent, c.accent)
            listOf(c.success, c.warning, c.error, c.info).forEach { legible("$mode state $it on the paper", it, c.ground) }
            // A tag is a small wash before its text in the ink: a mark, held to the ratio of a graphic
            TagColor.entries.forEach { legible("$mode tag $it on the paper", c.tag(it), c.ground, 3f) }
        }
    }

    @Test
    fun `a hue shift turns the red and the paper, never a tag nor a state`() {
        val base = InkPalettes.colors(PaletteMode.LIGHT, 0)
        val turned = InkPalettes.colors(PaletteMode.LIGHT, 120)
        assertTrue(base.accent != turned.accent)
        assertTrue(base.ground != turned.ground)
        TagColor.entries.forEach { assertEquals(base.tag(it), turned.tag(it)) }
        assertEquals(base.error, turned.error)
    }
}
