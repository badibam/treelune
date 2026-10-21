package app.treelune.core.fields

import app.treelune.core.strings.Strings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A duration's text form (Durations.format): the units worth zero are left out, so a duration below
 * an hour says its minutes alone, and below the smallest unit a "0" rather than nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DurationFormatTest {

    private val s get() = Strings.`for`(context = RuntimeEnvironment.getApplication())

    @Test
    fun `a duration below an hour says its minutes alone`() {
        assertEquals("40m", Durations.format(40 * 60_000L, DurationUnits.HOUR_MINUTE.units, s))
        assertEquals("1h 25m", Durations.format(3_600_000L + 25 * 60_000L, DurationUnits.HOUR_MINUTE.units, s))
        assertEquals("0m", Durations.format(0L, DurationUnits.HOUR_MINUTE.units, s))
    }
}
