package app.treelune.themes.metro

import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The metro palettes are read, one per mode, and every text they draw is legible where it is
 * drawn at every hue shift (docs/themes/metro.md): at one OKLCH lightness, the measured
 * contrast changes with the hue, so the night blue and the red are held at each step of 15
 * degrees, as the bench measures them. What is written on a line (an icon in its roundel, a tag's
 * word) is white or the deep night blue, whichever reads better; a text there holds 4.5 too.
 */
class MetroPaletteTest {

    private fun legible(name: String, ink: Lch, ground: Lch, least: Float = 4.5f) {
        val ratio = MetroColors.contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    private val shifts = 0 until 360 step 15

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toSet(), MetroPalettes.entries.map { it.mode }.toSet())
        assertEquals(PaletteMode.entries.size, MetroPalettes.entries.size)
    }

    @Test
    fun `every text is legible on what it is drawn on, at every shift`() {
        for (mode in PaletteMode.entries) for (shift in shifts) {
            val c = MetroPalettes.colors(mode, shift)
            legible("$mode $shift ink on the ground", c.ink, c.ground)
            legible("$mode $shift ink on a panel", c.ink, c.panel)
            legible("$mode $shift secondary ink on the ground", c.dim, c.ground)
            legible("$mode $shift secondary ink on a panel", c.dim, c.panel)
            legible("$mode $shift white on the band", c.onFill, c.band)
            legible("$mode $shift white on a fill", c.onFill, c.fill)
            legible("$mode $shift white on the red", c.onFill, c.accent)
            legible("$mode $shift danger on a panel", c.danger, c.panel)
            listOf(c.success, c.warning, c.error, c.info).forEach { legible("$mode $shift state $it on a panel", it, c.panel) }
            TagColor.entries.forEach { legible("$mode $shift word on the line $it", c.onLine(it), c.line(it)) }
        }
    }

    @Test
    fun `a hue shift turns the night blue and the red, the lines and the states kept`() {
        val base = MetroPalettes.colors(PaletteMode.LIGHT, 0)
        val turned = MetroPalettes.colors(PaletteMode.LIGHT, 120)
        assertTrue(base.accent.srgb != turned.accent.srgb)
        assertTrue(base.band.srgb != turned.band.srgb)
        // The grey line keeps a trace of the night blue, and turns with it
        TagColor.entries.filter { it != TagColor.GREY }.forEach { assertEquals(base.line(it).srgb, turned.line(it).srgb) }
        assertEquals(base.error.srgb, turned.error.srgb)
    }
}
