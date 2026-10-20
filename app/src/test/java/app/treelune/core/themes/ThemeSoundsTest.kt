package app.treelune.core.themes

import app.treelune.R
import app.treelune.core.ui.sound.UISignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds each theme's sounds to the files scripts/theme_sounds.py makes (docs/THEMES.md, The sounds):
 * a theme answers a signal with res/raw/<id>_<signal>.flac, the file of that very signal, or with
 * silence when it has none. A file made and never wired, or a signal answered by another one's
 * file, would otherwise only be heard on a phone.
 */
class ThemeSoundsTest {

    /** The name of each raw resource, by its id. */
    private val rawNames: Map<Int, String> = R.raw::class.java.fields.associate { it.getInt(null) to it.name }

    @Test
    fun everyTheme_answersASignalWithItsOwnFileOrSilence() {
        for ((id, theme) in ThemeScanner.scanForThemes()) {
            for (signal in UISignal.entries) {
                val name = "${id}_${signal.name.lowercase()}"
                // Unit tests run from the app module's folder.
                val file = File("src/main/res/raw/$name.flac")
                val sound = theme.sound(signal)
                if (sound == null) {
                    assertTrue("$name.flac exists but $id leaves $signal silent", !file.exists())
                } else {
                    assertEquals("$id answers $signal with another file", name, rawNames[sound])
                    assertTrue("$name.flac is missing", file.exists())
                }
            }
        }
    }
}
