package app.treelune.tools.flashcards

import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.tools.flashcards.FlashcardsDue.Card
import app.treelune.tools.flashcards.FlashcardsDue.CardKey
import app.treelune.tools.flashcards.FlashcardsDue.Kind
import app.treelune.tools.flashcards.FlashcardsDue.Line
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What docs/TOOLS.md, « Flashcards » promises of the cards to offer: due from the start of the day
 * their due date falls in, a failed card due until passed again, the new ones in the source's
 * order, the day's limits counted in cards for the whole deck, two directions of a line never the
 * same day, one direction after another, the groups as views of the deck.
 */
class FlashcardsDueTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    // Today runs from dayStart to dayEnd
    private val dayStart = 1_000L * day
    private val dayEnd = dayStart + day

    private val itFr = FlashcardsDeck.Direction("it_fr", "IT → FR", listOf("data.italien"), listOf("data.francais"))
    private val frIt = FlashcardsDeck.Direction("fr_it", "FR → IT", listOf("data.francais"), listOf("data.italien"))

    private fun deck(newPerDay: Int = 20, reviewsPerDay: Int = 200, afterAnother: Boolean = false, directions: List<FlashcardsDeck.Direction> = listOf(itFr, frIt)) =
        FlashcardsDeck(EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, "lexicon")), "data.theme", directions, afterAnother, newPerDay, reviewsPerDay, 0.9)

    private fun line(id: String, vararg groups: String, asks: Set<String> = setOf("it_fr", "fr_it")) = Line(id, groups.toSet(), asks)

    private fun card(first: Long, reviewedAt: Long, due: Long, rating: Fsrs.Rating = Fsrs.Rating.GOOD, stability: Double = 3.0) =
        Card(first, Fsrs.State(stability, 5.0, reviewedAt, due), rating)

    private fun offer(deck: FlashcardsDeck, lines: List<Line>, cards: Map<CardKey, Card> = emptyMap(), group: String? = null, direction: String? = null) =
        FlashcardsDue.offer(deck, lines, cards, dayStart, dayEnd, group, direction)

    @Test
    fun aCardIsDueFromTheStartOfTheDayItsDueDateFallsIn() {
        val lines = listOf(line("a"), line("b"), line("c"))
        val cards = mapOf(
            // Due tonight: due already this morning
            CardKey("a", "it_fr") to card(dayStart - 5 * day, dayStart - 3 * day, dayStart + 20 * hour),
            // Due at the very start of tomorrow: not today
            CardKey("b", "it_fr") to card(dayStart - 5 * day, dayStart - 3 * day, dayEnd),
            // Late by days
            CardKey("c", "it_fr") to card(dayStart - 9 * day, dayStart - 8 * day, dayStart - 4 * day)
        )
        val offered = offer(deck(newPerDay = 0), lines, cards, direction = "it_fr")
        assertEquals(listOf("c", "a"), offered.cards.map { it.key.line })
        assertEquals(2, offered.due)
    }

    @Test
    fun aFailedCardStaysDueUntilPassedAgain() {
        val cards = mapOf(CardKey("a", "it_fr") to card(dayStart - 5 * day, dayStart + hour, dayEnd + hour, Fsrs.Rating.AGAIN))
        assertEquals(listOf(Kind.DUE), offer(deck(newPerDay = 0), listOf(line("a")), cards).cards.map { it.kind })
    }

    @Test
    fun theNewComeInTheSourcesOrderUpToWhatIsLeftOfTheDay() {
        val lines = (1..5).map { line("l$it", asks = setOf("it_fr")) }
        // Two started today already, out of three a day
        val cards = mapOf(
            CardKey("l1", "it_fr") to card(dayStart + hour, dayStart + hour, dayEnd + day),
            CardKey("l2", "it_fr") to card(dayStart + 2 * hour, dayStart + 2 * hour, dayEnd + day)
        )
        val offered = offer(deck(newPerDay = 3), lines, cards)
        assertEquals(listOf("l3"), offered.cards.map { it.key.line })
        assertEquals(1, offered.new)
    }

    @Test
    fun theReviewsOfTheDayAreCountedInCardsAndAFailedOneTakesNothingMore() {
        val lines = (1..4).map { line("l$it", asks = setOf("it_fr")) }
        val cards = mapOf(
            // Seen before, failed today: counts once, and is offered again
            CardKey("l1", "it_fr") to card(dayStart - 9 * day, dayStart + hour, dayEnd, Fsrs.Rating.AGAIN),
            CardKey("l2", "it_fr") to card(dayStart - 9 * day, dayStart - 2 * day, dayStart - day),
            CardKey("l3", "it_fr") to card(dayStart - 9 * day, dayStart - 2 * day, dayStart - day + hour),
            CardKey("l4", "it_fr") to card(dayStart - 9 * day, dayStart - 2 * day, dayStart - day + 2 * hour)
        )
        val offered = offer(deck(newPerDay = 0, reviewsPerDay = 2), lines, cards)
        assertEquals(setOf("l1", "l2"), offered.cards.map { it.key.line }.toSet())
    }

    @Test
    fun twoDirectionsOfALineAreNeverOfferedTheSameDay() {
        val lines = listOf(line("a"), line("b"))
        val cards = mapOf(
            // a reviewed today in it_fr: its fr_it, due, waits for another day
            CardKey("a", "it_fr") to card(dayStart - 9 * day, dayStart + hour, dayEnd + 3 * day),
            CardKey("a", "fr_it") to card(dayStart - 9 * day, dayStart - 5 * day, dayStart - day),
            // b due in both: one only, the soonest due
            CardKey("b", "it_fr") to card(dayStart - 9 * day, dayStart - 5 * day, dayStart - day),
            CardKey("b", "fr_it") to card(dayStart - 9 * day, dayStart - 5 * day, dayStart - 2 * day)
        )
        val offered = offer(deck(), lines, cards)
        assertEquals(listOf(CardKey("b", "fr_it")), offered.cards.map { it.key })
        // Asked in it_fr only, b's it_fr comes
        assertEquals(listOf(CardKey("b", "it_fr")), offer(deck(), lines, cards, direction = "it_fr").cards.map { it.key })
    }

    @Test
    fun aNewLineGivesOneDirectionADay() {
        val offered = offer(deck(), listOf(line("a")))
        assertEquals(listOf(CardKey("a", "it_fr")), offered.cards.map { it.key })
    }

    @Test
    fun oneDirectionAfterAnotherWaitsForTheOneBeforeToBeAcquired() {
        val lines = listOf(line("a"), line("b"), line("c", asks = setOf("fr_it")))
        val cards = mapOf(
            CardKey("a", "it_fr") to card(dayStart - 30 * day, dayStart - 10 * day, dayEnd + 20 * day, stability = 12.0),
            CardKey("b", "it_fr") to card(dayStart - 30 * day, dayStart - 2 * day, dayEnd + 2 * day, stability = 4.0)
        )
        val offered = offer(deck(afterAnother = true), lines, cards, direction = "fr_it")
        // a's first direction acquired; b's not yet; c asks nothing before fr_it
        assertEquals(listOf("a", "c"), offered.cards.map { it.key.line })
    }

    @Test
    fun aGroupIsAViewOfTheDeckUnderItsLimits() {
        val lines = listOf(line("a", "animals"), line("b", "animals", "home"), line("c", "home"), line("d"))
        val home = offer(deck(newPerDay = 2), lines, group = "home")
        assertEquals(listOf("b", "c"), home.cards.map { it.key.line })
        val animals = offer(deck(newPerDay = 2), lines, group = "animals")
        assertEquals(listOf("a", "b"), animals.cards.map { it.key.line })
        // Everything: not the sum of the groups
        assertEquals(listOf("a", "b"), offer(deck(newPerDay = 2), lines).cards.map { it.key.line })
    }

    @Test
    fun aLineWithoutItsQuestionMakesNoCardAndALineGoneIsLeftOut() {
        val lines = listOf(line("a", asks = setOf("fr_it")))
        val cards = mapOf(CardKey("gone", "it_fr") to card(dayStart - 9 * day, dayStart + hour, dayStart - day))
        val offered = offer(deck(newPerDay = 1), lines, cards, direction = "it_fr")
        assertEquals(emptyList<Any>(), offered.cards)
        // The gone line's review today takes nothing from the day's new cards
        assertEquals(listOf(CardKey("a", "fr_it")), offer(deck(newPerDay = 1), lines, cards).cards.map { it.key })
    }
}
