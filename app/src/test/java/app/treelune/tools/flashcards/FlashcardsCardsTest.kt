package app.treelune.tools.flashcards

import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the view `cards` of a deck promises (FlashcardsCards), on the demo's deck Ripasso, read as a
 * tool's entries by a reading: a row per card, new ones included; a card rated counts as reviewed,
 * no longer new; a period refused, a card having no date.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlashcardsCardsTest {

    private val deck = "demo-italian-deck"

    private fun selection(filters: List<Map<String, Any?>> = emptyList(), period: Map<String, Any?>? = null) = buildMap<String, Any?> {
        put("target", mapOf("kind" to "TOOL_INSTANCE", "id" to deck))
        put("view", "cards")
        put("filters", filters)
        put("sort", listOf(mapOf("field" to "data.due_at", "direction" to "asc")))
        period?.let { put("period", it) }
    }

    @Test
    fun `a deck's cards are read as rows, a card rated no longer new`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        app.treelune.core.demo.DemoFixture.installed(context)
        val coordinator = Coordinator(context)
        val now = System.currentTimeMillis()
        suspend fun count(filters: List<Map<String, Any?>> = emptyList()): Int {
            val read = coordinator.processUserAction("readings.read", mapOf("selection" to selection(filters), "reduction" to "COUNT", "reference" to now))
            assertTrue(read.error, read.isSuccess)
            return (read.data?.get("value") as Number).toInt()
        }
        val newOnly = listOf(mapOf("left" to mapOf("field" to "data.new"), "op" to "=", "right" to mapOf("constant" to true)))
        val all = count()
        assertTrue("the demo's deck has cards", all > 0)
        val newBefore = count(newOnly)

        // Rated once: one card less new, one review counted
        // A new card, among the view's own rows
        val rows = app.treelune.core.views.Views.of(app.treelune.core.selection.Reference(app.treelune.core.selection.ReferenceKind.TOOL_INSTANCE, deck),
            "cards", context, app.treelune.core.strings.Strings.`for`(context = context)).rows(null, null, now)
        val card = rows.map { org.json.JSONObject(it.data) }.first { it.getBoolean("new") }
        val line = card.getJSONObject("line").getString("id")
        val reviewedBefore = count(listOf(mapOf("left" to mapOf("field" to "data.reviews"), "op" to ">=", "right" to mapOf("constant" to 1))))
        assertTrue(coordinator.processUserAction("flashcards.review",
            mapOf("tool_instance_id" to deck, "source_entry" to line, "direction" to card.getString("direction"), "rating" to "GOOD")).isSuccess)
        assertEquals(all, count())
        assertEquals(newBefore - 1, count(newOnly))
        assertEquals(reviewedBefore + 1, count(listOf(mapOf("left" to mapOf("field" to "data.reviews"), "op" to ">=", "right" to mapOf("constant" to 1)))))

        // A card has no date: a period is refused, saying so
        val dated = coordinator.processUserAction("readings.read", mapOf("selection" to selection(period = mapOf("start" to now - 1000)), "reduction" to "COUNT", "reference" to now))
        assertFalse(dated.isSuccess)
        assertTrue(dated.error, dated.error!!.contains("cards"))
    }
}
