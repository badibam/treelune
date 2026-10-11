package app.treelune.core.ai.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The scheduler's two queries split the end reasons between them by the resumable ones alone. */
class SessionEndReasonTest {

    @Test
    fun theQueriesListExactlyTheResumableReasons() {
        val listed = Regex("'(\\w+)'").findAll(SessionEndReason.RESUMABLE_SQL).map { SessionEndReason.valueOf(it.groupValues[1]) }.toSet()
        assertEquals(SessionEndReason.resumable, listed)
    }

    /** A session cut while it acted, or stopped at its limit, ends its occurrence: it is not run again. */
    @Test
    fun anInterruptedOrLimitedSessionIsNotResumed() {
        assert(SessionEndReason.INTERRUPTED !in SessionEndReason.resumable)
        assert(SessionEndReason.LIMIT_REACHED !in SessionEndReason.resumable)
    }
}
