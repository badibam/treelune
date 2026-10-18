package app.treelune.themes.paper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers of the icons' cut (icons.json, saved by the icon bench) are read by their absolute
 * path, as the theme reads them in a release build, every one of them present and none unknown:
 * a file the bench wrote wrong would otherwise be found only on the phone, when an icon is drawn.
 */
class PaperIconNumbersTest {

    @Test
    fun `the icon bench's numbers are read whole, and draw something`() {
        val stream = PaperIconNumbersTest::class.java.getResourceAsStream("/app/treelune/themes/paper/icons.json")
        requireNotNull(stream) { "icons.json is missing from the paper theme's resources" }
        val numbers = Json { ignoreUnknownKeys = false }
            .decodeFromString(PaperIconNumbers.serializer(), stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        assertTrue("a strip has a width", numbers.width > 0f)
        assertTrue("a facet has a length", numbers.facet > 0f)
        assertTrue("a corner is an angle", numbers.corner in 0f..180f)
        assertTrue("a shadow and a trembling are never negative", numbers.shadow >= 0f && numbers.jitter >= 0f)
    }
}
