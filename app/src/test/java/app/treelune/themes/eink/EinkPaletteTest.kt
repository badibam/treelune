package app.treelune.themes.eink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The e-reader palettes are black and white alone, every text legible on them, and the
 * colours the core gives stay told apart once turned into patterns (docs/design/eink-theme.md):
 * two series of a chart differ by their dashes on a line and by how much of the ground they
 * cover on a quantity, so a chart of several colours still reads.
 */
class EinkPaletteTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun `each palette is black and white, its ink legible on its ground`() {
        for (mode in PaletteMode.entries) {
            val c = EinkPalettes.colors(mode)
            assertEquals(setOf(Color.Black, Color.White), setOf(c.ink, c.ground))
            assertTrue("$mode: ${contrast(c.ink, c.ground)}", contrast(c.ink, c.ground) >= 4.5f)
        }
    }

    @Test
    fun `every colour draws its line in dashes of its own`() {
        val dashes = TagColor.entries.map { EinkPatterns.dashes(it) }
        assertEquals(dashes.size, dashes.toSet().size)
    }

    @Test
    fun `every colour covers a share of its own, red the whole`() {
        val covers = TagColor.entries.map { EinkPatterns.cover(it) }
        assertEquals(covers.size, covers.toSet().size)
        assertTrue(covers.all { it in 0f..1f })
        assertEquals(1f, EinkPatterns.cover(TagColor.RED))
    }
}
