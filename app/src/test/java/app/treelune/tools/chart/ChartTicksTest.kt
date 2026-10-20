package app.treelune.tools.chart

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.TimeResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Graduations fall on round numbers, round durations and the calendar's own instants. */
class ChartTicksTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val midnight = TimeResolver(0L, zone, 0, "monday")
    private val four = TimeResolver(0L, zone, 4, "monday")
    private fun at(month: Int, day: Int, hour: Int = 0) = LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `numbers step by 1, 2 or 5 times a power of ten`() {
        assertEquals(500.0, ChartTicks.numberStep(0.0, 2400.0, 5), 1e-9)
        assertEquals(0.2, ChartTicks.numberStep(71.3, 72.1, 5), 1e-9)
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0), ChartTicks.multiples(0.0, 2100.0, 500.0))
        assertEquals(0.0 to 2500.0, ChartTicks.nice(0.0, 2100.0, 500.0))
        assertEquals(1, ChartTicks.decimals(0.2))
        assertEquals(0, ChartTicks.decimals(500.0))
    }

    @Test
    fun `durations step by round minutes and hours`() {
        assertEquals(3_600_000L, ChartTicks.roundStep(8 * 3_600_000.0, 8, ChartTicks.DURATION_STEPS))
        assertEquals(15 * 60_000L, ChartTicks.roundStep(90 * 60_000.0, 6, ChartTicks.DURATION_STEPS))
    }

    @Test
    fun `a month graduates by week, from the week's first day, across a change of time`() {
        val step = ChartTicks.calendarStep(at(10, 1), at(11, 1), 5)
        assertEquals(ChartTicks.CalendarStep(ChronoUnit.WEEKS, 1), step)
        // October 2026: Monday the 5th, 12th, 19th, 26th; the clocks go back on the 25th
        assertEquals(listOf(at(10, 5), at(10, 12), at(10, 19), at(10, 26)), ChartTicks.calendar(at(10, 1), at(11, 1), step, midnight))
    }

    @Test
    fun `a year graduates by quarter from January`() {
        val step = ChartTicks.calendarStep(at(2, 10), at(12, 31), 4)
        assertEquals(ChartTicks.CalendarStep(ChronoUnit.MONTHS, 3), step)
        assertEquals(listOf(at(4, 1), at(7, 1), at(10, 1)), ChartTicks.calendar(at(2, 10), at(12, 31), step, midnight))
    }

    @Test
    fun `with days starting at 4, the calendar graduates from the start of the user's days`() {
        // Days, each at 4:00, the night before 4:00 still the day before
        assertEquals(listOf(at(10, 13, 4), at(10, 14, 4)), ChartTicks.calendar(at(10, 13, 2), at(10, 14, 12), ChartTicks.CalendarStep(ChronoUnit.DAYS, 1), four))
        // Six hours, counted from 4:00
        assertEquals(listOf(at(10, 14, 4), at(10, 14, 10), at(10, 14, 16), at(10, 14, 22)),
            ChartTicks.calendar(at(10, 14, 3), at(10, 14, 23), ChartTicks.CalendarStep(ChronoUnit.HOURS, 6), four))
        // A week from its Monday at 4:00, across the change of time of the 25th
        assertEquals(listOf(at(10, 19, 4), at(10, 26, 4)), ChartTicks.calendar(at(10, 14, 12), at(10, 31, 0), ChartTicks.CalendarStep(ChronoUnit.WEEKS, 1), four))
        // A day stored by a DATE field sits at the start of the user's day on it
        val date = FieldDefinition("d", "d", null, FieldType.DATE, false, null)
        assertEquals(at(10, 14, 4).toDouble(), ChartValues.number(date, "2026-10-14", four)!!, 0.0)
    }
}
