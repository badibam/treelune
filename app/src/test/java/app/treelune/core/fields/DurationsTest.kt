package app.treelune.core.fields

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers how a DURATION's milliseconds are cut into the units a form offers, so that what is
 * typed into the boxes comes back as the same boxes.
 */
class DurationsTest {

    @Test
    fun units_runFromTheLargestDownToTheSmallest() {
        assertEquals(listOf(DurationUnit.HOUR, DurationUnit.MINUTE), DurationUnits.HOUR_MINUTE.units)
        assertEquals(listOf(DurationUnit.DAY, DurationUnit.HOUR, DurationUnit.MINUTE), DurationUnits.DAY_HOUR_MINUTE.units)
        assertEquals(listOf(DurationUnit.MINUTE), DurationUnits.MINUTE.units)
        // Hours and minutes when the config names none
        assertEquals(DurationUnits.HOUR_MINUTE, DurationUnits.fromConfig(null))
    }

    @Test
    fun split_dropsWhatIsFinerThanTheSmallestUnit() {
        // 1 h 25 min 40 s, shown to the minute
        val millis = 3_600_000L + 25 * 60_000L + 40_000L

        assertEquals(listOf(DurationUnit.HOUR to 1L, DurationUnit.MINUTE to 25L), Durations.split(millis, DurationUnits.HOUR_MINUTE.units))
        assertEquals(listOf(DurationUnit.MINUTE to 85L), Durations.split(millis, DurationUnits.MINUTE.units))
    }

    /** Without days, hours are not capped at 24: fifty hours stay fifty hours. */
    @Test
    fun split_keepsHoursPastADayWithoutDays() {
        val fifty = 50 * 3_600_000L
        assertEquals(listOf(DurationUnit.HOUR to 50L, DurationUnit.MINUTE to 0L), Durations.split(fifty, DurationUnits.HOUR_MINUTE.units))
        assertEquals(listOf(DurationUnit.DAY to 2L, DurationUnit.HOUR to 2L), Durations.split(fifty, DurationUnits.DAY_HOUR.units))
    }

    @Test
    fun join_isTheInverseOfSplit() {
        val units = DurationUnits.HOUR_MINUTE_SECOND.units
        val millis = 2 * 3_600_000L + 3 * 60_000L + 4_000L

        assertEquals(millis, Durations.join(Durations.split(millis, units).toMap()))
    }
}
