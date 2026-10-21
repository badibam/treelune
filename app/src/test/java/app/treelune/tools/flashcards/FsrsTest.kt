package app.treelune.tools.flashcards

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * What docs/TOOLS.md, « Flashcards » promises of FSRS: the port gives, review after review, the state
 * the reference implementation gives on the same ratings at the same instants — stability,
 * difficulty and due date. The suites are py-fsrs's own answers (scripts/make_fsrs_reference.py),
 * run without learning steps nor fuzz as the tool runs it.
 */
class FsrsTest {

    private val reference: JSONObject = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("fsrs/reference.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    )

    @Test
    fun everySuiteGivesTheReferenceStates() {
        val suites = reference.getJSONArray("suites")
        assertTrue("the reference holds suites", suites.length() > 50)
        for (i in 0 until suites.length()) {
            val suite = suites.getJSONObject(i)
            val retention = suite.getDouble("desired_retention")
            val reviews = suite.getJSONArray("reviews")
            var state: Fsrs.State? = null
            for (j in 0 until reviews.length()) {
                val review = reviews.getJSONObject(j)
                val at = review.getLong("at")
                state = Fsrs.review(state, Fsrs.Rating.valueOf(review.getString("rating")), at, retention)
                val where = "suite $i, review $j"
                assertClose("$where: stability", review.getDouble("stability"), state.stability)
                assertClose("$where: difficulty", review.getDouble("difficulty"), state.difficulty)
                assertEquals("$where: due", review.getLong("due"), state.due)
                assertEquals("$where: reviewed at", at, state.reviewedAt)
            }
        }
    }

    @Test
    fun theReferenceIsThePortedVersion() {
        assertEquals("6.3.2", reference.getString("py_fsrs"))
        assertEquals("fsrs-6", Fsrs.VERSION)
    }

    /** Equal but for the last bits of a double: both sides run the same formulas, in another order of operations at most. */
    private fun assertClose(what: String, expected: Double, actual: Double) {
        assertTrue("$what: expected $expected, got $actual", abs(expected - actual) <= 1e-9 * maxOf(1.0, abs(expected)))
    }
}
