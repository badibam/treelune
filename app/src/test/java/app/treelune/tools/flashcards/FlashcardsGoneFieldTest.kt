package app.treelune.tools.flashcards

import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A field gone from a deck's source is named by the place of the deck that shows it, the one to
 * correct in its config: the grouping, or a direction's front or back. Its label went with it, and
 * the deck keeps only its key, so the error once showed the key ("the field nature").
 */
class FlashcardsGoneFieldTest {

    private val deck = FlashcardsDeck(
        EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, "lexicon")),
        "extra.theme",
        listOf(FlashcardsDeck.Direction("it", "IT → FR", listOf("name", "extra.nature"), listOf("extra.translation"))),
        afterAnother = false, newPerDay = 6, reviewsPerDay = 200, desiredRetention = 0.9
    )

    /** Each key's text with its arguments, to read which one was given. */
    private val text: (String) -> String = { if (it == "error_group_by_gone") "$it %s" else "$it %s %s" }

    private fun gone(missing: String) = deck.goneField({ it != missing }, "Lessico", text)

    @Test
    fun aFieldOnTheFrontNamesTheFront() = assertEquals("error_front_gone IT → FR Lessico", gone("extra.nature"))

    @Test
    fun aFieldOnTheBackNamesTheBack() = assertEquals("error_back_gone IT → FR Lessico", gone("extra.translation"))

    @Test
    fun theGroupingFieldNamesTheGrouping() = assertEquals("error_group_by_gone Lessico", gone("extra.theme"))

    @Test
    fun nothingGoneIsNoError() = assertNull(deck.goneField({ true }, "Lessico", text))
}
