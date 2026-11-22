package app.treelune.tools.sequence.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** « What to play? » tests first the link nearest to what the list shows (docs/design/linked-files.md). */
class MediaPresenceTest {

    @Test
    fun `the nearest link comes first, the lower of two as near`() {
        assertEquals(10, nearestTo(listOf(2, 10, 40), 12))
        assertEquals(8, nearestTo(listOf(8, 12), 10))
        assertNull(nearestTo(emptyList(), 3))
    }
}
