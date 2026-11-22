package app.treelune.tools.flashcards

import app.treelune.core.fields.FieldType
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A field a deck cannot use is named by the place of the deck that shows it, the one to correct in
 * its config: the grouping, or a direction's front or back. A field gone from the source is said
 * gone, its label having gone with it; a reference field is refused, no card reading the name of
 * what it designates.
 */
class FlashcardsRefusedFieldTest {

    private val deck = FlashcardsDeck(
        EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, "lexicon")),
        "extra.theme",
        listOf(FlashcardsDeck.Direction("it", "IT → FR", listOf("name", "extra.nature"), listOf("extra.translation"))),
        afterAnother = false, newPerDay = 6, reviewsPerDay = 200, desiredRetention = 0.9
    )

    /** Each key's text with its arguments, to read which one was given. */
    private val text: (String) -> String = { if (it.startsWith("error_group_by_")) "$it %s" else "$it %s %s" }

    private fun gone(missing: String) = deck.refusedField({ if (it == missing) null else FieldType.TEXT }, "Lessico", text)

    private fun reference(path: String) = deck.refusedField({ if (it == path) FieldType.REFERENCE else FieldType.TEXT }, "Lessico", text)

    @Test
    fun aFieldGoneFromTheFrontNamesTheFront() = assertEquals("error_front_gone IT → FR Lessico", gone("extra.nature"))

    @Test
    fun aFieldGoneFromTheBackNamesTheBack() = assertEquals("error_back_gone IT → FR Lessico", gone("extra.translation"))

    @Test
    fun theGroupingFieldGoneNamesTheGrouping() = assertEquals("error_group_by_gone Lessico", gone("extra.theme"))

    @Test
    fun aReferenceGroupingIsRefused() = assertEquals("error_group_by_reference Lessico", reference("extra.theme"))

    @Test
    fun aReferenceOnTheFrontIsRefused() = assertEquals("error_front_reference IT → FR Lessico", reference("extra.nature"))

    @Test
    fun aReferenceOnTheBackIsRefused() = assertEquals("error_back_reference IT → FR Lessico", reference("extra.translation"))

    @Test
    fun everyFieldUsableIsNoError() = assertNull(deck.refusedField({ FieldType.TEXT }, "Lessico", text))
}
