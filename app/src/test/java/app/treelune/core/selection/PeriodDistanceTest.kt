package app.treelune.core.selection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The period selector says « Hier », « Il y a 2 semaines » from the days and weeks between a
 * period and now, counted on the calendar: the day the clocks go forward lasts 23 hours, and the
 * day before it is still one day back, not today.
 */
class PeriodDistanceTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(month: Int, day: Int, hour: Int) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun calendar(now: Long, dayStartHour: Int = 0) = TimeResolver(now, zone, dayStartHour, "monday")

    @Test
    fun `the day before the clocks go forward is yesterday`() {
        // 29 March 2026 lasts 23 hours in Paris
        val monday = calendar(at(3, 30, 10))
        assertEquals(1, daysBetween(at(3, 29, 0), monday))
        assertEquals(0, daysBetween(at(3, 30, 0), monday))
        assertEquals(-1, daysBetween(at(3, 31, 0), monday))
    }

    @Test
    fun `the day before the clocks go back is yesterday`() {
        // 25 October 2026 lasts 25 hours in Paris
        val monday = calendar(at(10, 26, 0))
        assertEquals(1, daysBetween(at(10, 25, 23), monday))
        assertEquals(2, daysBetween(at(10, 24, 0), monday))
    }

    @Test
    fun `days are the app's, starting at the hour set`() {
        // At 2:00 on Tuesday, with days starting at 4:00, today is still Monday
        val night = calendar(at(3, 31, 2), dayStartHour = 4)
        assertEquals(0, daysBetween(at(3, 30, 10), night))
        assertEquals(1, daysBetween(at(3, 29, 10), night))
    }

    @Test
    fun `the week before a short one is last week`() {
        // The week of Monday 23 March holds the 23-hour Sunday
        val monday = calendar(at(3, 30, 10))
        assertEquals(1, weeksBetween(at(3, 23, 10), monday))
        assertEquals(0, weeksBetween(at(4, 5, 10), monday))
        assertEquals(-1, weeksBetween(at(4, 6, 10), monday))
    }
}
