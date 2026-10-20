package app.treelune.core.selection

import app.treelune.core.fields.FieldType
import app.treelune.core.selection.PeriodType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A date stored to compare with: a fixed one as its field stores it, a relative one as an object
 * that says its edge, resolved against the reference of its context -- with days starting at
 * 4:00, a day runs to 3:59 the day after.
 */
class TimePointTest {

    private val zone = ZoneId.of("Europe/Paris")

    // Wednesday 2026-09-16, 15:00 in Paris; days start at 4:00, weeks on Monday
    private val reference = at(2026, 9, 16, 15, 0)
    private val resolver = TimeResolver(reference, zone, dayStartHour = 4, weekStartDay = "MONDAY")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun read(json: String): TimePoint = TimePoint.read(JSONObject(json)) { it }

    private val yesterdayStart = TimePoint.Relative(PeriodType.DAY, -1, Edge.START)
    private val yesterdayEnd = TimePoint.Relative(PeriodType.DAY, -1, Edge.END)

    @Test
    fun `a relative date reads from its object, and writes back the same`() {
        val json = """{"relative":{"unit":"DAY","offset":-1,"edge":"START"}}"""
        assertEquals(yesterdayStart, read(json))
        assertEquals(JSONObject(json).toString(), yesterdayStart.toJson().toString())
        assertEquals(TimePoint.Now, read("""{"relative":"NOW"}"""))
        assertEquals("""{"relative":"NOW"}""", TimePoint.Now.toJson().toString())
    }

    @Test
    fun `the command's map reads as the stored object does`() {
        val map = mapOf("relative" to mapOf("unit" to "WEEK", "offset" to 0.0, "edge" to "END"))
        assertEquals(TimePoint.Relative(PeriodType.WEEK, 0, Edge.END), TimePoint.read(map) { it })
    }

    @Test
    fun `only the relative key makes a value relative, whatever it looks like`() {
        assertEquals(TimePoint.Fixed("NOW"), TimePoint.read("NOW") { it })
        assertEquals(TimePoint.Fixed("-1_DAY"), TimePoint.read("-1_DAY") { it })
        assertEquals(TimePoint.Fixed(1000L), TimePoint.read(1000L) { it })
        assertFalse(TimePoint.isRelative(JSONObject("""{"unit":"DAY"}""")))
        assertTrue(TimePoint.isRelative(JSONObject("""{"relative":"NOW"}""")))
    }

    @Test
    fun `a relative date without its edge, or with an unknown unit, is refused`() {
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"DAY","offset":-1}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"FORTNIGHT","offset":0,"edge":"START"}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"DAY","offset":0.5,"edge":"START"}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":"TODAY"}""") }
    }

    @Test
    fun `an instant takes the edge it says, the end being the last millisecond`() {
        assertEquals(at(2026, 9, 15, 4, 0), resolver.instant(yesterdayStart))
        assertEquals(at(2026, 9, 16, 4, 0) - 1, resolver.instant(yesterdayEnd))
        assertEquals(reference, resolver.instant(TimePoint.Now))
        assertEquals(1000L, resolver.instant(TimePoint.Fixed(1000L)))
    }

    @Test
    fun `a day is the one a period starts in, or the eve of the next one's start`() {
        assertEquals("2026-09-15", resolver.day(yesterdayStart))
        assertEquals("2026-09-15", resolver.day(yesterdayEnd))
        val thisWeekStart = TimePoint.Relative(PeriodType.WEEK, 0, Edge.START)
        val thisWeekEnd = TimePoint.Relative(PeriodType.WEEK, 0, Edge.END)
        assertEquals("2026-09-14", resolver.day(thisWeekStart))
        assertEquals("2026-09-20", resolver.day(thisWeekEnd))
        assertEquals("2026-09-16", resolver.day(TimePoint.Now))
        assertEquals("2026-01-02", resolver.day(TimePoint.Fixed("2026-01-02")))
    }

    @Test
    fun `the field's type decides between a day and an instant`() {
        assertEquals("2026-09-15", resolver.resolve(yesterdayStart, FieldType.DATE))
        assertEquals(at(2026, 9, 15, 4, 0), resolver.resolve(yesterdayStart, FieldType.DATETIME))
    }

    @Test
    fun `a shift reads from its object, and writes back the same`() {
        val same = """{"relative":{"unit":"MINUTE","offset":-15,"edge":"SAME"}}"""
        assertEquals(TimePoint.Shift(ShiftUnit.MINUTE, -15), read(same))
        assertEquals(JSONObject(same).toString(), TimePoint.Shift(ShiftUnit.MINUTE, -15).toJson().toString())
        val at = """{"relative":{"unit":"DAY","offset":-1,"edge":{"at":"18:00"}}}"""
        assertEquals(TimePoint.Shift(ShiftUnit.DAY, -1, LocalTime.of(18, 0)), read(at))
        assertEquals(JSONObject(at).toString(), TimePoint.Shift(ShiftUnit.DAY, -1, LocalTime.of(18, 0)).toJson().toString())
    }

    @Test
    fun `a minute stands on no edge, and an hour of the day goes with the day alone`() {
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"MINUTE","offset":-15,"edge":"START"}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"WEEK","offset":-1,"edge":{"at":"18:00"}}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"DAY","offset":-1,"edge":{"at":"6pm"}}}""") }
        assertThrows(IllegalArgumentException::class.java) { read("""{"relative":{"unit":"DAY","offset":-1,"edge":{}}}""") }
    }

    @Test
    fun `a shift keeps its place in the unit, the start of the day playing no part`() {
        assertEquals(at(2026, 9, 16, 14, 45), resolver.instant(TimePoint.Shift(ShiftUnit.MINUTE, -15)))
        assertEquals(at(2026, 9, 16, 14, 0), resolver.instant(TimePoint.Shift(ShiftUnit.HOUR, -1)))
        assertEquals(at(2026, 9, 15, 15, 0), resolver.instant(TimePoint.Shift(ShiftUnit.DAY, -1)))
        assertEquals(at(2026, 9, 9, 15, 0), resolver.instant(TimePoint.Shift(ShiftUnit.WEEK, -1)))
        assertEquals(at(2026, 8, 16, 15, 0), resolver.instant(TimePoint.Shift(ShiftUnit.MONTH, -1)))
        assertEquals(at(2025, 9, 16, 15, 0), resolver.instant(TimePoint.Shift(ShiftUnit.YEAR, -1)))
        assertEquals("2026-09-15", resolver.day(TimePoint.Shift(ShiftUnit.DAY, -1)))
    }

    @Test
    fun `an hour of the day is that hour of the day moved, before the day starts included`() {
        assertEquals(at(2026, 9, 15, 18, 0), resolver.instant(TimePoint.Shift(ShiftUnit.DAY, -1, LocalTime.of(18, 0))))
        assertEquals(at(2026, 9, 16, 2, 0), resolver.instant(TimePoint.Shift(ShiftUnit.DAY, 0, LocalTime.of(2, 0))))
    }

    @Test
    fun `the day before keeps its time across a change of hour, and a month too short its last day`() {
        // Sunday 2026-10-25 the clocks go back: the day before at 14:30 is 25 hours earlier
        val afterChange = TimeResolver(at(2026, 10, 25, 14, 30), zone, 4, "MONDAY")
        assertEquals(at(2026, 10, 24, 14, 30), afterChange.instant(TimePoint.Shift(ShiftUnit.DAY, -1)))
        val endOfMarch = TimeResolver(at(2026, 3, 31, 9, 0), zone, 4, "MONDAY")
        assertEquals(at(2026, 2, 28, 9, 0), endOfMarch.instant(TimePoint.Shift(ShiftUnit.MONTH, -1)))
    }

    @Test
    fun `an instant belongs to the day the user set, which starts at its hour`() {
        assertEquals(java.time.LocalDate.of(2026, 9, 15), resolver.dateOf(at(2026, 9, 16, 3, 59)))
        assertEquals(java.time.LocalDate.of(2026, 9, 16), resolver.dateOf(at(2026, 9, 16, 4, 0)))
        assertEquals(at(2026, 9, 16, 4, 0), resolver.startOf(java.time.LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `before the hour days start at, the current week, month and year are those of the day before`() {
        // Wednesday 2026-10-14 at 2:00 is still Tuesday the 13th; at 15:00, Wednesday
        for (reference in listOf(at(2026, 10, 14, 2, 0), at(2026, 10, 14, 15, 0))) {
            fun start(unit: PeriodType, weekStart: String = "monday") =
                TimeResolver(reference, zone, 4, weekStart).instant(TimePoint.Relative(unit, 0, Edge.START))
            assertEquals(at(2026, 10, 12, 4, 0), start(PeriodType.WEEK))
            assertEquals(at(2026, 10, 11, 4, 0), start(PeriodType.WEEK, "sunday"))
            assertEquals(at(2026, 10, 11, 4, 0), start(PeriodType.WEEK, "SUNDAY"))
            assertEquals(at(2026, 10, 1, 4, 0), start(PeriodType.MONTH))
            assertEquals(at(2026, 1, 1, 4, 0), start(PeriodType.YEAR))
        }
        // The first of a month before 4:00 is still the month before
        val firstAtNight = TimeResolver(at(2026, 10, 1, 2, 0), zone, 4, "monday")
        assertEquals(at(2026, 9, 1, 4, 0), firstAtNight.instant(TimePoint.Relative(PeriodType.MONTH, 0, Edge.START)))
        assertEquals(at(2026, 9, 30, 4, 0), firstAtNight.instant(TimePoint.Relative(PeriodType.DAY, 0, Edge.START)))
    }

    @Test
    fun `today and a shift in days are the user's days, a time of the clock the calendar's`() {
        // Wednesday 2026-10-14 at 0:30 is still Tuesday; at 15:00, Wednesday
        val night = TimeResolver(at(2026, 10, 14, 0, 30), zone, 4, "monday")
        val day = TimeResolver(at(2026, 10, 14, 15, 0), zone, 4, "monday")
        assertEquals("2026-10-13", night.day(TimePoint.Now))
        assertEquals("2026-10-14", day.day(TimePoint.Now))
        assertEquals("2026-10-16", night.day(TimePoint.Shift(ShiftUnit.DAY, 3)))
        assertEquals("2026-10-17", day.day(TimePoint.Shift(ShiftUnit.DAY, 3)))
        assertEquals("2026-10-12", night.day(TimePoint.Shift(ShiftUnit.DAY, -1)))
        // The day before at 18:00 of a flight at 1:30 on Thursday: Wednesday at 18:00
        val flight = TimeResolver(at(2026, 10, 15, 1, 30), zone, 4, "monday")
        assertEquals(at(2026, 10, 14, 18, 0), flight.instant(TimePoint.Shift(ShiftUnit.DAY, -1, LocalTime.of(18, 0))))
    }
}
