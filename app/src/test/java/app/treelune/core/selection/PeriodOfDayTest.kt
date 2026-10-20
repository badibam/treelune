package app.treelune.core.selection

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A day chosen in the period selector's date picker shows its own period, the user's days
 * starting at 4:00: its midnight belongs to the day before, and is not where it starts.
 */
class PeriodOfDayTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val calendar = TimeResolver(0L, zone, 4, "monday")
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a day chosen is its own day, week and month`() {
        assertEquals(at(10, 14, 4), periodOfDay(LocalDate.of(2026, 10, 14), PeriodType.DAY, calendar).timestamp)
        assertEquals(at(10, 12, 4), periodOfDay(LocalDate.of(2026, 10, 12), PeriodType.WEEK, calendar).timestamp)
        assertEquals(at(10, 1, 4), periodOfDay(LocalDate.of(2026, 10, 1), PeriodType.MONTH, calendar).timestamp)
        assertEquals(at(1, 1, 4), periodOfDay(LocalDate.of(2026, 1, 1), PeriodType.YEAR, calendar).timestamp)
    }

    @Test
    fun `an hour moved to another day keeps its time of the clock`() {
        val hour = Period(at(10, 14, 2), PeriodType.HOUR)
        assertEquals(at(10, 20, 2), hourOnDay(hour, LocalDate.of(2026, 10, 20), calendar).timestamp)
    }
}
