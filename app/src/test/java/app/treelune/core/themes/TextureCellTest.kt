package app.treelune.core.themes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The themes' cells of the motifs (TextureCell): every theme has one for each motif but ICON and
 * nothing else in its folder, each a cell the app reads; a pixel theme's of whole pixels only.
 */
class TextureCellTest {

    private val themes = listOf("default", "cosy", "retro", "swiss")

    /** The themes drawn in whole pixels, whose cells are filled rectangles on whole units. */
    private val pixelThemes = setOf("retro")

    @Test
    fun everyTheme_hasACell_ofEachMotif_andNothingElse() {
        val expected = Texture.entries.filter { it.hasCell }.map { "${it.name.lowercase()}.svg" }.toSet()
        themes.forEach { theme ->
            val files = File("src/main/resources/app/treelune/themes/$theme/textures").list()?.toSet() ?: emptySet()
            assertEquals(theme, expected, files)
        }
    }

    @Test
    fun everyCell_reads_throughThePathTheAppUses() {
        themes.forEach { theme ->
            Texture.entries.filter { it.hasCell }.forEach { texture ->
                val cell = TextureCell.of(theme, texture)
                assertTrue("$theme $texture", cell.shapes.isNotEmpty())
            }
        }
    }

    @Test
    fun aPixelTheme_drawsOnlyWholePixels() {
        pixelThemes.forEach { theme ->
            Texture.entries.filter { it.hasCell }.forEach { texture ->
                assertTrue("$theme $texture", TextureCell.of(theme, texture).wholePixels)
            }
        }
    }

    @Test
    fun aCell_isRefused_withWhatItHoldsWrong() {
        val square = """<svg viewBox="0 0 4 4"><rect x="0" y="0" width="1" height="1"/></svg>"""
        assertTrue(TextureCell.parse(square).wholePixels)
        assertFalse(TextureCell.parse("""<svg viewBox="0 0 4 4"><rect x="0.5" y="0" width="1" height="1"/></svg>""").wholePixels)
        assertFalse(TextureCell.parse("""<svg viewBox="0 0 4 4"><circle cx="2" cy="2" r="1"/></svg>""").wholePixels)
        listOf(
            """<svg><rect width="1" height="1"/></svg>""",
            """<svg viewBox="0 0 4 4"><ellipse cx="2" cy="2" rx="1" ry="1"/></svg>""",
            """<svg viewBox="0 0 4 4"></svg>""",
        ).forEach { svg ->
            val refused = runCatching { TextureCell.parse(svg) }.isFailure
            assertTrue(svg, refused)
        }
    }

    @Test
    fun aShapeWithAStrokeWidth_isALine() {
        val cell = TextureCell.parse("""<svg viewBox="0 0 8 8"><path d="M0 0L8 8" stroke-width="1.5" stroke-linecap="round"/><circle cx="4" cy="4" r="1"/></svg>""")
        assertEquals(1.5f, cell.shapes[0].strokeWidth)
        assertTrue(cell.shapes[0].roundCaps)
        assertFalse(cell.shapes[1].stroked)
    }
}
