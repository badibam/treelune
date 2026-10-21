package app.treelune.tools.flashcards

import app.treelune.tools.flashcards.FlashcardsDue.CardKey
import app.treelune.tools.flashcards.Fsrs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What docs/TOOLS.md, « Flashcards » promises of a session: a failed card comes back after a few others
 * and at least a minute, at once when nothing else waits; Hard, Good and Easy pass a card; the
 * session is over when no card is left on a failure; undoing goes back up the session, card by card.
 */
class FlashcardsSessionTest {

    private val cards = (1..6).map { CardKey("l$it", "d") }
    private val minute = 60_000L

    /** Rates the card the session asks at [now], as the screen does. */
    private fun FlashcardsSession.rate(rating: Rating, now: Long, id: String): FlashcardsSession = rated(next(now)!!, rating, now, id)

    @Test
    fun aFailedCardComesBackAfterAFewOthersAndAMinute() {
        var session = FlashcardsSession.of(cards).rate(Rating.AGAIN, 0, "r1")
        val asked = mutableListOf<String>()
        var now = 0L
        repeat(3) {
            now += 30_000
            val card = session.next(now)!!
            asked += card.line
            session = session.rated(card, Rating.GOOD, now, "r")
        }
        // Three others asked, a minute and a half gone: l1 comes back
        assertEquals(listOf("l2", "l3", "l4"), asked)
        assertEquals("l1", session.next(now)!!.line)
    }

    @Test
    fun aFailedCardWaitsItsMinuteWhileOthersAreReady() {
        var session = FlashcardsSession.of(cards.take(5)).rate(Rating.AGAIN, 0, "r1")
        (1..3).forEach { session = session.rate(Rating.GOOD, it * 1_000L, "r$it") }
        // Three others asked in three seconds: the minute is not over, l5 first
        assertEquals("l5", session.next(4_000)!!.line)
    }

    @Test
    fun whenNothingElseWaitsAFailedCardComesBackAtOnce() {
        val session = FlashcardsSession.of(cards.take(1)).rate(Rating.AGAIN, 0, "r1")
        assertEquals("l1", session.next(1_000)!!.line)
        assertFalse(session.over)
    }

    @Test
    fun hardGoodAndEasyPassACardAndTheSessionEndsWhenNoneIsLeftOnAFailure() {
        var session = FlashcardsSession.of(cards.take(3))
        session = session.rate(Rating.HARD, 0, "r1").rate(Rating.EASY, 1, "r2").rate(Rating.AGAIN, 2, "r3")
        assertFalse(session.over)
        session = session.rate(Rating.GOOD, 3 + minute, "r4")
        assertTrue(session.over)
        assertEquals(3, session.done)
        assertNull(session.next(0))
    }

    @Test
    fun undoingGoesBackUpTheSessionCardByCard() {
        val start = FlashcardsSession.of(cards.take(3))
        val two = start.rate(Rating.GOOD, 0, "r1").rate(Rating.AGAIN, 1, "r2")
        val once = two.undone()!!
        assertEquals("r2", once.reviewId)
        assertEquals(CardKey("l2", "d"), once.card)
        assertEquals(CardKey("l2", "d"), once.session.next(2))
        val twice = once.session.undone()!!
        assertEquals("r1", twice.reviewId)
        assertEquals(start, twice.session)
        assertNull(twice.session.undone())
    }

    @Test
    fun aSessionSurvivesBeingSaved() {
        val session = FlashcardsSession.of(cards).rate(Rating.AGAIN, 5, "r1").rate(Rating.GOOD, 6, "r2")
        assertEquals(session, FlashcardsSession.fromJson(org.json.JSONObject(session.toJson().toString())))
    }
}
