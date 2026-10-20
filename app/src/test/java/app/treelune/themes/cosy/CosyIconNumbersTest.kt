package app.treelune.themes.cosy

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers of the icons' drawing (icons.json, saved by the icon bench) are read by their
 * absolute path, as the theme reads them in a release build, every one of them present and none
 * unknown: a file the bench wrote wrong would otherwise be found only on the phone, when an icon is drawn.
 */
class CosyIconNumbersTest {

    @Test
    fun `the icon bench's numbers are read whole, and draw something`() {
        val stream = CosyIconNumbersTest::class.java.getResourceAsStream("/app/treelune/themes/cosy/icons.json")
        requireNotNull(stream) { "icons.json is missing from the cosy theme's resources" }
        val numbers = Json { ignoreUnknownKeys = false }
            .decodeFromString(CosyIconNumbers.serializer(), stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        assertTrue("a stroke has a width", numbers.width > 0f)
        assertTrue("a shadow is never negative", numbers.shadow >= 0f)
        assertTrue("a fill is a share of the shadow's colour", numbers.fill in 0f..1f)
    }
}
