package app.treelune.core.ui.selectors

import app.treelune.core.selection.Edge
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimePoint
import app.treelune.core.strings.Strings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A pointer's period in words (PointerDescription): a relative bound is said by its side, the
 * start or the end, whatever the day the app starts on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PointerDescriptionTest {

    @Test
    fun `a period says each bound by its side`() {
        val s = Strings.`for`(context = RuntimeEnvironment.getApplication())
        val period = EntryPeriod(TimePoint.Relative(PeriodType.DAY, -7, Edge.START), TimePoint.Relative(PeriodType.DAY, 0, Edge.END))
        val description = PointerDescription.period(period, s)!!
        assertTrue(description, description.contains("start"))
        assertTrue(description, description.contains("end"))
        assertTrue(description, description.contains("7 days"))
        assertTrue(description, description.contains("the same day"))
    }
}
