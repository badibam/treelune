package app.treelune.core.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A texture's names (docs/THEMES.md, « What a theme is in the app »): fifteen motifs, or none; ICON only with an icon. */
class TextureTest {

    private val text: (String) -> String = { key -> "$key %s %s" }

    @Test
    fun theNames_inTheirOrder() {
        assertEquals(listOf("LINES", "DIAGONALS", "GRID", "ZIGZAG", "WAVES", "DOTS", "CROSSES", "STARS", "RINGS", "DIAMONDS",
            "CONFETTI", "CHECKER", "BRICKS", "SCALES", "ICON"), Texture.NAMES)
    }

    @Test
    fun aName_orNone_isAccepted() {
        Texture.NAMES.forEach { assertTrue(it, Texture.accepts(it)) }
        assertTrue(Texture.accepts(null))
        assertTrue(Texture.accepts(""))
    }

    @Test
    fun anUnknownName_orOneInLowerCase_isRefused() {
        assertFalse(Texture.accepts("PAPER"))
        assertFalse(Texture.accepts("dots"))
        assertNotNull(Texture.refusal("PAPER", "heart", text))
    }

    @Test
    fun icon_needsAnIcon() {
        assertNull(Texture.refusal("ICON", "heart", text))
        assertEquals("service_error_texture_icon_without_icon %s %s", Texture.refusal("ICON", null, text))
        assertNotNull(Texture.refusal("ICON", "", text))
        // Another motif, or none, needs no icon
        assertNull(Texture.refusal("DOTS", null, text))
        assertNull(Texture.refusal(null, null, text))
    }

    @Test
    fun aStoredName_readsAsItsTexture() {
        assertEquals(Texture.WAVES, Texture.of("WAVES"))
        assertNull(Texture.of(null))
        assertNull(Texture.of(""))
    }
}
