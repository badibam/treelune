package app.treelune.themes.paper

import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cut paper palettes are read, one per mode, and every text they draw is legible where it is
 * drawn at every hue shift (docs/themes/paper.md): at one OKLCH lightness, the measured
 * contrast changes with the hue, so the kraft, the coral and the night blue are held at each step
 * of 15 degrees, as the bench measures them. What is written on a coloured sheet or on the coral
 * is the cream or the dark ink, whichever reads better; a text there holds 4.5 too. The brown
 * dashes on the kraft, a line and not a text, hold 3.
 */
class PaperPaletteTest {

    private fun legible(name: String, ink: Lch, ground: Lch, least: Float = 4.5f) {
        val ratio = PaperColors.contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    private val shifts = 0 until 360 step 15

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toSet(), PaperPalettes.entries.map { it.mode }.toSet())
        assertEquals(PaletteMode.entries.size, PaperPalettes.entries.size)
    }

    @Test
    fun `every text is legible on what it is drawn on, at every shift`() {
        for (mode in PaletteMode.entries) for (shift in shifts) {
            val c = PaperPalettes.colors(mode, shift)
            legible("$mode $shift ink on the kraft", c.ink, c.ground)
            legible("$mode $shift ink on the cream sheet", c.ink, c.sheet)
            legible("$mode $shift ink on the white sheet", c.ink, c.white)
            legible("$mode $shift secondary ink on the kraft", c.dim, c.ground)
            legible("$mode $shift secondary ink on the cream sheet", c.dim, c.sheet)
            legible("$mode $shift cream on the night blue", c.onNavy, c.navy)
            legible("$mode $shift word on the coral", c.onAccent, c.accent)
            listOf(c.success, c.warning, c.error, c.info).forEach { legible("$mode $shift state $it on the cream sheet", it, c.sheet) }
            TagColor.entries.forEach { legible("$mode $shift word on the sheet $it", c.onColour(it), c.colour(it)) }
            legible("$mode $shift dashes on the kraft", c.rule, c.ground, 3f)
        }
    }

    @Test
    fun `a hue shift turns the kraft, the coral and the night blue, paper and colours kept`() {
        val base = PaperPalettes.colors(PaletteMode.LIGHT, 0)
        val turned = PaperPalettes.colors(PaletteMode.LIGHT, 120)
        assertTrue(base.ground.srgb != turned.ground.srgb)
        assertTrue(base.accent.srgb != turned.accent.srgb)
        assertTrue(base.navy.srgb != turned.navy.srgb)
        assertEquals(base.sheet.srgb, turned.sheet.srgb)
        assertEquals(base.ink.srgb, turned.ink.srgb)
        TagColor.entries.forEach { assertEquals(base.colour(it).srgb, turned.colour(it).srgb) }
        assertEquals(base.error.srgb, turned.error.srgb)
    }

    @Test
    fun `a tile keeps its cut and its tilt, within the largest tilt`() {
        val laid = Laid("a tool's id")
        assertEquals(laid.cut, Laid("a tool's id").cut)
        assertEquals(laid.tilt, Laid("a tool's id").tilt)
        listOf("", "a", "b", "zone-1", "8b0a1d0f-5983-4c89-bc2b-93f1b5fb5562").forEach {
            assertTrue(kotlin.math.abs(Laid(it).tilt) <= MAX_TILT + 1e-6f)
        }
    }
}
