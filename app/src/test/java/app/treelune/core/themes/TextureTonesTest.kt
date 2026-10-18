package app.treelune.core.themes

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The themes' tones of the motifs (TextureTones): every theme has both intensities of every
 * motif in both modes, a marked one standing further from its surface than a subtle one.
 */
class TextureTonesTest {

    private val themes = listOf("default", "cosy", "retro", "swiss", "ink", "metro")

    @Test
    fun everyTheme_hasEveryTone() {
        themes.forEach { theme ->
            val tones = TextureTones.of(theme)
            PaletteMode.entries.forEach { mode ->
                Texture.entries.forEach { texture ->
                    val subtle = tones.mode(mode).tone(texture, TextureIntensity.SUBTLE)
                    val marked = tones.mode(mode).tone(texture, TextureIntensity.MARKED)
                    assertTrue("$theme $mode $texture", subtle > 0f && marked > subtle)
                }
            }
        }
    }

    @Test
    fun aMissingTone_isRefused_withItsName() {
        val json = """{"modes": {"LIGHT": {"tag_chroma": 0.05, "tones": {}}, "DARK": {"tag_chroma": 0.05, "tones": {}}}}"""
        val error = runCatching { TextureTones.parse(json) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("LINES"))
    }

    @Test
    fun aMotif_standsTowardTheInk_ofItsSurface() {
        val mode = TextureTones.of("default").mode(PaletteMode.LIGHT)
        val light = Oklch.toLch(Color.White).l
        val onLight = Oklch.toLch(mode.motifColor(Color.White, null, Texture.DOTS, TextureIntensity.MARKED)).l
        assertTrue(onLight < light)
        val dark = Oklch.toLch(Color(0xFF202020)).l
        val onDark = Oklch.toLch(mode.motifColor(Color(0xFF202020), null, Texture.DOTS, TextureIntensity.MARKED)).l
        assertTrue(onDark > dark)
    }
}
