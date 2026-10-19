package app.treelune.tools.flashcards

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * FSRS, the Free Spaced Repetition Scheduler (MIT licence), as Anki runs it: from a card's state and
 * a rating, its next state — its stability, how many days its memory holds with a 90 % chance of
 * recall; its difficulty, from 1 to 10; and when it is due, the day the chance of recall falls to
 * the desired retention.
 *
 * Ported from py-fsrs 6.3.2 (FSRS-6), its default parameters, run as the flashcards tool needs it
 * (docs/design/flashcards.md): no learning nor relearning steps, a session bringing a failed card
 * back itself, so every review gives a due date in whole days; no fuzz, so that one suite of
 * ratings always gives the same dates. FsrsTest compares it with py-fsrs on the same suites
 * (scripts/make_fsrs_reference.py): a wrong port gives wrong dates and breaks nothing else.
 */
object Fsrs {

    /** The version written in every review, which a later port tells its states apart by. */
    const val VERSION = "fsrs-6"

    enum class Rating(val grade: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

    /**
     * A card's state after a review.
     *
     * @property reviewedAt The instant of that review, which the next one counts its days from
     * @property due The instant the card is to be reviewed again
     */
    data class State(val stability: Double, val difficulty: Double, val reviewedAt: Long, val due: Long)

    /** FSRS-6's default parameters, w0 to w20. */
    private val W = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
    )
    private const val STABILITY_MIN = 0.001
    private const val DIFFICULTY_MIN = 1.0
    private const val DIFFICULTY_MAX = 10.0
    private const val MAXIMUM_INTERVAL = 36500
    private const val DAY = 86_400_000L

    private val DECAY = -W[20]
    private val FACTOR = 0.9.pow(1 / DECAY) - 1

    /**
     * The state of a card rated [rating] at [at], [previous] its state before (null for a new card).
     *
     * @param desiredRetention The chance of recall the due date is set at, between 0 and 1
     */
    fun review(previous: State?, rating: Rating, at: Long, desiredRetention: Double): State {
        val stability: Double
        val difficulty: Double
        if (previous == null) {
            stability = clampStability(W[rating.grade - 1])
            difficulty = clampDifficulty(initialDifficulty(rating))
        } else {
            // Whole days elapsed, as py-fsrs counts them (a timedelta's days)
            val days = floor((at - previous.reviewedAt).toDouble() / DAY).toLong()
            stability = if (days < 1) shortTermStability(previous.stability, rating)
                else nextStability(previous.difficulty, previous.stability, retrievability(previous.stability, max(0, days)), rating)
            difficulty = nextDifficulty(previous.difficulty, rating)
        }
        return State(stability, difficulty, at, at + interval(stability, desiredRetention) * DAY)
    }

    /** The chance of recalling a card of [stability] after [days] whole days. */
    fun retrievability(stability: Double, days: Long): Double = (1 + FACTOR * days / stability).pow(DECAY)

    /** The days until the chance of recall falls to [desiredRetention]: at least one, at most a hundred years. */
    private fun interval(stability: Double, desiredRetention: Double): Long {
        val days = (stability / FACTOR) * (desiredRetention.pow(1 / DECAY) - 1)
        // Python's round, to the even number at a half
        return Math.rint(days).toLong().coerceIn(1, MAXIMUM_INTERVAL.toLong())
    }

    private fun initialDifficulty(rating: Rating): Double = W[4] - exp(W[5] * (rating.grade - 1)) + 1

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = exp(W[17] * (rating.grade - 3 + W[18])) * stability.pow(-W[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -(W[6] * (rating.grade - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        // Mean reversion towards the initial difficulty of an Easy
        return clampDifficulty(W[7] * initialDifficulty(Rating.EASY) + (1 - W[7]) * damped)
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double =
        clampStability(if (rating == Rating.AGAIN) forgetStability(difficulty, stability, retrievability)
            else recallStability(difficulty, stability, retrievability, rating))

    private fun forgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val longTerm = W[11] * difficulty.pow(-W[12]) * ((stability + 1).pow(W[13]) - 1) * exp((1 - retrievability) * W[14])
        val shortTerm = stability / exp(W[17] * W[18])
        return min(longTerm, shortTerm)
    }

    private fun recallStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) W[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) W[16] else 1.0
        return stability * (1 + exp(W[8]) * (11 - difficulty) * stability.pow(-W[9]) *
            (exp((1 - retrievability) * W[10]) - 1) * hardPenalty * easyBonus)
    }

    private fun clampStability(stability: Double) = max(stability, STABILITY_MIN)
    private fun clampDifficulty(difficulty: Double) = min(max(difficulty, DIFFICULTY_MIN), DIFFICULTY_MAX)
}
