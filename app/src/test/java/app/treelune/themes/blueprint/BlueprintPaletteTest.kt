package app.treelune.themes.blueprint

import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blueprint palettes are read, one per mode, and every text they draw is legible where it is
 * drawn at every hue shift (docs/design/blueprint-theme.md): at one OKLCH lightness, the measured
 * contrast changes with the hue, so the blue and the paper are held at each step of 15 degrees,
 * as the bench measures them. Everything stands on the paper: the ink, the secondary ink, the
 * alert, the states and the pencils; and the paper is written on a fill of the ink.
 */
class BlueprintPaletteTest {

    private fun legible(name: String, ink: Lch, ground: Lch, least: Float = 4.5f) {
        val ratio = BlueprintColors.contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    private val shifts = 0 until 360 step 15

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toSet(), BlueprintPalettes.entries.map { it.mode }.toSet())
        assertEquals(PaletteMode.entries.size, BlueprintPalettes.entries.size)
    }

    @Test
    fun `every text is legible on what it is drawn on, at every shift`() {
        for (mode in PaletteMode.entries) for (shift in shifts) {
            val c = BlueprintPalettes.colors(mode, shift)
            legible("$mode $shift ink on the paper", c.ink, c.paper)
            legible("$mode $shift secondary ink on the paper", c.dim, c.paper)
            legible("$mode $shift paper on a fill of the ink", c.paper, c.ink)
            legible("$mode $shift alert on the paper", c.alert, c.paper)
            listOf(c.success, c.warning, c.error, c.info).forEach { legible("$mode $shift state $it on the paper", it, c.paper) }
            TagColor.entries.forEach { legible("$mode $shift pencil $it on the paper", c.crayon(it), c.paper) }
        }
    }

    @Test
    fun `a hue shift turns the blue and the paper, the alert, the pencils and the states kept`() {
        val base = BlueprintPalettes.colors(PaletteMode.DARK, 0)
        val turned = BlueprintPalettes.colors(PaletteMode.DARK, 120)
        assertTrue(base.paper.srgb != turned.paper.srgb)
        assertTrue(base.ink.srgb != turned.ink.srgb)
        assertEquals(base.alert.srgb, turned.alert.srgb)
        // The grey pencil keeps a trace of the blue, and turns with it
        TagColor.entries.filter { it != TagColor.GREY }.forEach { assertEquals(base.crayon(it).srgb, turned.crayon(it).srgb) }
        assertEquals(base.error.srgb, turned.error.srgb)
    }
}
