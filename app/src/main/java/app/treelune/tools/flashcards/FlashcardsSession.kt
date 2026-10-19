package app.treelune.tools.flashcards

import org.json.JSONArray
import org.json.JSONObject

/**
 * A session (docs/design/flashcards.md, « La séance »): a list fixed at its launch, each card asked,
 * turned and rated. A failed card comes back later in the session, after [GAP] other cards and at
 * least [WAIT] ms; when nothing else waits, it comes back at once: a session never makes one wait.
 * The session is over when no card is left on a failure — Hard, Good and Easy pass a card.
 *
 * Undoing goes back to the card before, its back hidden, the session as it stood before its
 * rating; several times over, up the session. The review written is deleted by the screen.
 *
 * Immutable and pure: the screen keeps the current one, saved across a rotation (toJson).
 *
 * @property waiting The cards still to ask, in their order, each with the instant before which it
 *   does not come back
 * @property done How many cards were passed
 * @property history What undoing goes back to: the session before each rating, with the review written
 */
data class FlashcardsSession(
    val waiting: List<Waiting>,
    val done: Int,
    val history: List<Step>
) {
    data class Waiting(val card: FlashcardsDue.CardKey, val notBefore: Long)

    /** A rating, which undoing goes back on: the session before it, the card rated and the review it wrote. */
    data class Step(val before: FlashcardsSession, val card: FlashcardsDue.CardKey, val reviewId: String)

    val over: Boolean get() = waiting.isEmpty()

    /**
     * The card to ask at [now]: the first ready, a failed card waiting its turn; if none is ready,
     * the one that will be the soonest. The screen asks it once a card is rated, and keeps it
     * shown until it is rated in turn.
     */
    fun next(now: Long): FlashcardsDue.CardKey? =
        (waiting.firstOrNull { it.notBefore <= now } ?: waiting.minByOrNull { it.notBefore })?.card

    /** The session once [card], the one shown, got [rating] at [now], written as the review [reviewId]. */
    fun rated(card: FlashcardsDue.CardKey, rating: Fsrs.Rating, now: Long, reviewId: String): FlashcardsSession {
        require(waiting.any { it.card == card }) { "the card rated is not in the session" }
        val rest = waiting.filterNot { it.card == card }
        val step = Step(copy(history = emptyList()), card, reviewId)
        return if (rating == Fsrs.Rating.AGAIN) {
            val back = Waiting(card, now + WAIT)
            copy(waiting = rest.take(GAP) + back + rest.drop(GAP), history = history + step)
        } else {
            copy(waiting = rest, done = done + 1, history = history + step)
        }
    }

    /** What undoing the last rating gives: the session before it, the card to show again, the review to delete. */
    data class Undone(val session: FlashcardsSession, val card: FlashcardsDue.CardKey, val reviewId: String)

    /** The last rating undone; null at the session's start. */
    fun undone(): Undone? {
        val last = history.lastOrNull() ?: return null
        return Undone(last.before.copy(history = history.dropLast(1)), last.card, last.reviewId)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("waiting", JSONArray(waiting.map { JSONObject().put("line", it.card.line).put("direction", it.card.direction).put("not_before", it.notBefore) }))
        .put("done", done)
        .put("history", JSONArray(history.map { JSONObject().put("before", it.before.toJson()).put("line", it.card.line).put("direction", it.card.direction).put("review_id", it.reviewId) }))

    companion object {
        /** How many other cards are asked before a failed one comes back. */
        const val GAP = 3
        /** The least time before a failed card comes back. */
        const val WAIT = 60_000L

        /** A session over [cards], in the order offered. */
        fun of(cards: List<FlashcardsDue.CardKey>): FlashcardsSession = FlashcardsSession(cards.map { Waiting(it, 0L) }, 0, emptyList())

        fun fromJson(json: JSONObject): FlashcardsSession = FlashcardsSession(
            waiting = FlashcardsDeck.objects(json.getJSONArray("waiting")).map {
                Waiting(FlashcardsDue.CardKey(it.getString("line"), it.getString("direction")), it.getLong("not_before"))
            },
            done = json.getInt("done"),
            history = FlashcardsDeck.objects(json.getJSONArray("history")).map {
                Step(fromJson(it.getJSONObject("before")), FlashcardsDue.CardKey(it.getString("line"), it.getString("direction")), it.getString("review_id"))
            }
        )
    }
}
