package app.treelune.tools.flashcards

import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.demo.DemoStartup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What docs/TOOLS.md, « Flashcards » promises of a deck's writes, on the demo's deck Ripasso and its
 * lexicon, through the dispatcher as a screen calls it: a review written by flashcards.review
 * alone, an ordinary write refused; a review never changed; only a card's last review undone; the
 * cards due counted on the tile's dot; the counts of the page and its groups, by their labels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlashcardsServiceTest {

    private val deck = "demo-italian-deck"

    @Test
    fun `a deck's reviews are written by its own operation alone, undone from the last`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        assertNull(DemoStartup.run(context))
        val coordinator = Coordinator(context)

        // The page's counts: the whole deck, and a group per theme, named by its label
        val counts = coordinator.processUserAction("flashcards.counts", mapOf("tool_instance_id" to deck))
        assertTrue(counts.error, counts.isSuccess)
        val groups = (counts.data?.get("groups") as List<*>).map { it as Map<*, *> }
        assertEquals(setOf("food", "home", "travel"), groups.map { it["group"] }.toSet())
        assertTrue(groups.none { it["label"] == it["group"] })

        // The cards to offer, with their faces: the Italian word first on the front of d_it
        val due = coordinator.processUserAction("flashcards.due", mapOf("tool_instance_id" to deck, "direction" to "d_it"))
        assertTrue(due.error, due.isSuccess)
        val card = (due.data?.get("cards") as List<*>).map { it as Map<*, *> }.first()
        val line = card["source_entry"] as String
        val front = (card["front"] as List<*>).map { it as Map<*, *> }
        assertEquals("name", front.first()["field"])
        assertTrue(line.endsWith(front.first()["value"] as String))

        // Rated twice: two reviews, the second's state computed from the first's
        suspend fun review(rating: String) = coordinator.processUserAction("flashcards.review",
            mapOf("tool_instance_id" to deck, "source_entry" to line, "direction" to "d_it", "rating" to rating))
        val first = review("AGAIN")
        assertTrue(first.error, first.isSuccess)
        val second = review("GOOD")
        assertTrue(second.error, second.isSuccess)
        val firstId = first.data?.get("id") as String
        val secondId = second.data?.get("id") as String

        // An ordinary write is refused: its fields are the app's to write
        val written = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to deck, "timestamp" to System.currentTimeMillis(),
            "data" to mapOf("source_entry" to mapOf("kind" to "ENTRY", "id" to line), "direction" to "d_it", "rating" to "EASY")
        ))
        assertFalse("an ordinary write of a review", written.isSuccess)
        assertTrue("the refusal says what to do: ${written.error}", written.error!!.contains("flashcards.review"))

        // A review never changes
        val changed = coordinator.processUserAction("tool_data.update", mapOf("id" to secondId, "state" to mapOf("stability" to 900.0)))
        assertFalse("a review changed", changed.isSuccess)

        // Only the card's last review is undone, then the one before it
        assertFalse("a review before the last undone", coordinator.processUserAction("tool_data.delete", mapOf("id" to firstId)).isSuccess)
        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to secondId)).isSuccess)
        assertTrue(coordinator.processUserAction("tool_data.delete", mapOf("id" to firstId)).isSuccess)

        // An unknown rating and a line of no source are refused
        assertFalse(review("PERFECT").isSuccess)
        assertFalse(coordinator.processUserAction("flashcards.review",
            mapOf("tool_instance_id" to deck, "source_entry" to "demo-food-oats", "direction" to "d_it", "rating" to "GOOD")).isSuccess)

        // The tile's dot counts the cards due over the whole deck
        val waiting = coordinator.processUserAction("tools.waiting", mapOf("zone_id" to "demo-italian"))
        val all = (counts.data?.get("all") as Map<*, *>)["due"] as Number
        assertEquals(all.toInt(), ((waiting.data?.get("tools") as Map<*, *>)[deck] as Number).toInt())
    }
}
