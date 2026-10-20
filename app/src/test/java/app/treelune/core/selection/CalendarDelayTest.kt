package app.treelune.core.selection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A goal's attempt lasts its duration as a delay on the calendar: a week from Monday 8:00 ends on
 * Monday 8:00, across a change of hour too, its days counted on the calendar and the rest in hours.
 */
class CalendarDelayTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val calendar = TimeResolver(0L, zone, 0, "monday")
    private fun at(month: Int, day: Int, hour: Int) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun `a week across the clocks going back ends at the same time of the clock`() {
        // The week of Monday 19 October 2026 holds a 25-hour Sunday
        assertEquals(at(10, 26, 8), calendar.after(at(10, 19, 8), 7 * day))
    }

    @Test
    fun `a week across the clocks going forward ends at the same time of the clock`() {
        assertEquals(at(3, 30, 8), calendar.after(at(3, 23, 8), 7 * day))
    }

    @Test
    fun `the hours past the days are elapsed time`() {
        // 36 h: the next day at the same time, then 12 hours
        assertEquals(at(3, 24, 20), calendar.after(at(3, 23, 8), 36 * hour))
        // Under a day, only elapsed time: 2 h from 1:00 on the night the clocks go forward is 4:00
        assertEquals(at(3, 29, 4), calendar.after(at(3, 29, 1), 2 * hour))
    }
}
