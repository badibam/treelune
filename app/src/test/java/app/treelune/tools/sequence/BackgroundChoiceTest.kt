package app.treelune.tools.sequence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import kotlin.random.Random

/** What docs/design/sequence-background.md promises of a background's order and of what is kept. */
class BackgroundChoiceTest {

    @Test
    fun `in order, a tour is the files as chosen`() {
        assertEquals(listOf("a", "b", "c"), BackgroundChoice.tour(listOf("a", "b", "c"), shuffle = false, last = "c"))
    }

    @Test
    fun `shuffled, a tour plays each file once and never begins with the one just finished`() {
        val files = listOf("a", "b", "c", "d")
        repeat(200) { seed ->
            val tour = BackgroundChoice.tour(files, shuffle = true, last = "b", random = Random(seed))
            assertEquals(files.toSet(), tour.toSet())
            assertEquals(files.size, tour.size)
            assertNotEquals("b", tour.first())
        }
    }

    @Test
    fun `a single file shuffled is that file`() {
        assertEquals(listOf("a"), BackgroundChoice.tour(listOf("a"), shuffle = true, last = "a"))
    }

    @Test
    fun `no file keeps nothing, and a choice reads back as it was kept`() {
        assertTrue(BackgroundChoice(emptyList(), false, true, 50).toData().isEmpty())
        assertNull(BackgroundChoice.of(JSONObject()))
        val kept = JSONObject()
            .put(SequenceToolType.BACKGROUND, JSONArray(listOf("a", "b")))
            .put(SequenceToolType.BACKGROUND_SHUFFLE, true)
            .put(SequenceToolType.BACKGROUND_LOOP, false)
            .put(SequenceToolType.BACKGROUND_VOLUME, 35)
        assertEquals(BackgroundChoice(listOf("a", "b"), true, false, 35), BackgroundChoice.of(kept))
    }
}
