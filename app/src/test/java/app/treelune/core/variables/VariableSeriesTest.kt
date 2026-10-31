package app.treelune.core.variables

import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.TimeResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The steps of a variable's series (docs/design/variable-series.md): each computed at its last
 * millisecond, as every end of a period in the app, inside its step; the step holding the instant
 * read computed at that instant; no step after it; the steps cut by the user's calendar.
 */
class VariableSeriesTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 9) = LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Days start at 4:00, weeks on Monday; read on Wednesday 16 September 2026 at 15:00. */
    private val now = at(16, 15)
    private val calendar = TimeResolver(now, zone, dayStartHour = 4, weekStartDay = "MONDAY")

    @Test
    fun `a day's step is computed at its last millisecond, the day in progress at the instant read`() {
        val instants = VariableSeries.instants(at(14, 4), null, now, PeriodType.DAY, calendar)
        assertEquals(listOf(at(15, 4) - 1, at(16, 4) - 1, now), instants)
    }

    @Test
    fun `a step's instant is inside its own day, cut at the hour days start at`() {
        // Monday's last millisecond is Tuesday 3:59:59.999, still Monday for the calendar
        val monday = VariableSeries.instants(at(14, 4), at(15, 4) - 1, now, PeriodType.DAY, calendar).single()
        assertEquals(at(14, 4), VariableSeries.stepStart(monday, PeriodType.DAY, calendar))
    }

    @Test
    fun `a period starting inside a step takes the whole step, and nothing comes after the instant read`() {
        // From Monday 10:00 to next Friday: Monday, Tuesday, and Wednesday up to now
        val instants = VariableSeries.instants(at(14, 10), at(18, 23), now, PeriodType.DAY, calendar)
        assertEquals(3, instants.size)
        assertEquals(now, instants.last())
    }

    @Test
    fun `a period after the instant read has no step`() {
        assertEquals(emptyList<Long>(), VariableSeries.instants(at(17, 4), at(18, 4), now, PeriodType.DAY, calendar))
    }

    @Test
    fun `steps of an hour, a week, a month`() {
        assertEquals(listOf(at(16, 13) - 1, at(16, 14) - 1, at(16, 15) - 1, now),
            VariableSeries.instants(at(16, 12, 30), null, now, PeriodType.HOUR, calendar))
        // The week of Monday 7, then the one in progress
        assertEquals(listOf(at(14, 4) - 1, now), VariableSeries.instants(at(9, 12), null, now, PeriodType.WEEK, calendar))
        // August, then September in progress
        assertEquals(listOf(at(1, 4) - 1, now), VariableSeries.instants(at(20, 12, month = 8), null, now, PeriodType.MONTH, calendar))
    }

    @Test
    fun `a day of a change of hour keeps its start on the calendar`() {
        // 25 October 2026: clocks go back in Paris, the day lasts 25 hours, from 4:00 to 4:00
        val october = TimeResolver(at(27, 12, month = 10), zone, dayStartHour = 4, weekStartDay = "MONDAY")
        val instants = VariableSeries.instants(at(25, 4, month = 10), at(26, 4, month = 10) - 1, at(27, 12, month = 10), PeriodType.DAY, october)
        assertEquals(listOf(at(26, 4, month = 10) - 1), instants)
    }
}
