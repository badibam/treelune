package app.treelune.core.history

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What docs/DATA.md, « History of writes » promised of the history's depth: the steps past their days
 * go, then the oldest while the room is exceeded; the latest step stays whatever its size, and an
 * older step never outlives a newer one pruned for room.
 */
class HistoryDepthTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    /** A step made [daysAgo] days before now, taking [bytes]. */
    private fun step(id: String, daysAgo: Long, bytes: Long, isStep: Boolean = true) = StepSize(id, isStep, now - daysAgo * day, bytes)

    @Test
    fun `the steps past their days go`() {
        val steps = listOf(step("new", 1, 10), step("old", 40, 10))
        assertEquals(listOf("old"), HistoryDepth.gone(steps, now, 30 * day, 1_000))
    }

    @Test
    fun `past the room, the oldest go, and every one older`() {
        val steps = listOf(step("a", 1, 40), step("b", 2, 40), step("c", 3, 40), step("d", 4, 5))
        assertEquals(listOf("c", "d"), HistoryDepth.gone(steps, now, 30 * day, 100))
    }

    @Test
    fun `the latest step stays, alone above the room`() {
        val steps = listOf(step("import", 0, 500), step("before", 1, 10))
        assertEquals(listOf("before"), HistoryDepth.gone(steps, now, 30 * day, 100))
    }

    @Test
    fun `the latest kept is a step, never the app's noted writes`() {
        val steps = listOf(step("noted", 0, 500, isStep = false), step("gesture", 1, 10))
        assertEquals(listOf("noted"), HistoryDepth.gone(steps, now, 30 * day, 100))
    }
}
