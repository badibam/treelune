package app.treelune.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers the formatting and parsing every screen goes through to show or read a date.
 *
 * Two things are pinned here. The first is the ordinary behaviour: what a timestamp looks
 * like once displayed, what a displayed date means once read back, and where a day starts
 * and ends -- all of which depend on the timezone, which is why it is a parameter.
 *
 * The second is what happens when the parsing fails. Every parser answers null, and these
 * cases hold it there: docs/reference.md says a failure is explicit or it is not, and a
 * date that silently became the current moment was not.
 */
class DateUtilsTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = paris): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zone)
            .toInstant().toEpochMilli()

    // ==================== Showing a timestamp ====================

    /** The three display formats, and the fact that all of them read the timezone. */
    @Test
    fun aTimestampIsShownInTheGivenTimezone() {
        val instant = at(2025, 3, 15, 14, 30)

        assertEquals("15/03/2025", DateUtils.formatDateForDisplay(instant, paris))
        assertEquals("14:30", DateUtils.formatTimeForDisplay(instant, paris))
        assertEquals("15/03/25 14:30", DateUtils.formatFullDateTime(instant, paris))

        // The same instant, eight hours further east, is already the small hours of the 15th.
        assertEquals("15/03/2025", DateUtils.formatDateForDisplay(instant, tokyo))
        assertEquals("22:30", DateUtils.formatTimeForDisplay(instant, tokyo))
    }

    /** Far enough east, the same instant is a different day. */
    @Test
    fun theTimezoneCanChangeWhichDayItIs() {
        val lateEvening = at(2025, 3, 15, 23, 30)

        assertEquals("15/03/2025", DateUtils.formatDateForDisplay(lateEvening, paris))
        assertEquals("16/03/2025", DateUtils.formatDateForDisplay(lateEvening, tokyo))
    }

    // ==================== Reading a displayed date ====================

    /** Out and back returns the day it started on. */
    @Test
    fun showingThenReadingADateKeepsTheDay() {
        val instant = at(2025, 3, 15, 14, 30)
        assertEquals(LocalDate.of(2025, 3, 15), DateUtils.parseDisplayDate(DateUtils.formatDateForDisplay(instant, paris)))
    }

    // ==================== The ISO helpers for custom fields ====================

    /** A date field's value, a day, shown as the display writes days; an instant's day in the zone given. */
    @Test
    fun anIsoDateIsShownAsItsDay() {
        assertEquals("15/03/2025", DateUtils.formatIsoDate("2025-03-15"))
        assertEquals("2025-03-15", DateUtils.timestampToIso8601Date(at(2025, 3, 15, 14, 30), paris))
    }

    /** A time field has no date of its own: it is read as that time today. */
    @Test
    fun anIsoTimeIsReadAsThatTimeToday() {
        val timestamp = DateUtils.parseIso8601Time("14:30", paris)!!

        assertEquals("14:30", DateUtils.timestampToIso8601Time(timestamp, paris))
        assertEquals(DateUtils.calendarToday(paris), java.time.Instant.ofEpochMilli(timestamp).atZone(paris).toLocalDate())
    }

    /** A time in hours and minutes is read as written. */
    @Test
    fun aTimeIsSplitIntoHoursAndMinutes() {
        assertEquals(Pair(9, 30), DateUtils.parseTime("09:30", paris))
        assertEquals(Pair(0, 0), DateUtils.parseTime("00:00", paris))
        assertEquals(Pair(23, 59), DateUtils.parseTime("23:59", paris))
    }

    /** A date and a time given separately combine into the moment they name. */
    @Test
    fun aDateAndATimeCombineIntoTheMomentTheyName() {
        assertEquals(
            at(2025, 3, 15, 14, 30),
            DateUtils.combineDateTime("15/03/2025", "14:30", paris)
        )
    }


    // ==================== The two that read the clock ====================

    /**
     * These are the only two functions left that look at the clock, and they mean it: they
     * are what a picker opens on when nothing has been chosen. Each is its formatter
     * applied to now, which is all there is to check without freezing time.
     */
    @Test
    fun todayAndNowAreTheirFormattersAppliedToTheClock() {
        val now = System.currentTimeMillis()

        assertEquals(DateUtils.formatDateForDisplay(now, paris), DateUtils.getTodayFormatted(paris))
        assertEquals(DateUtils.formatTimeForDisplay(now, paris), DateUtils.getCurrentTimeFormatted(paris))
    }

    /** And they answer to the timezone given, like everything else here. */
    @Test
    fun todayDependsOnTheTimezone() {
        val inParis = DateUtils.getTodayFormatted(paris)
        val inTokyo = DateUtils.getTodayFormatted(tokyo)

        // Same day or the next one, never anything else: Tokyo is eight hours ahead.
        val dayInParis = DateUtils.parseDisplayDate(inParis)!!
        val dayInTokyo = DateUtils.parseDisplayDate(inTokyo)!!
        assertTrue(dayInTokyo == dayInParis || dayInTokyo == dayInParis.plusDays(1))
    }

    // ==================== What happens when it cannot be read ====================

    /**
     * Every parser answers an unreadable value with null rather than with a date.
     *
     * The alternative, which these functions used to do, was to return the current time:
     * nothing afterwards could then tell "the string was nonsense" from "the date really is
     * today". combineDateTime is the one that mattered, being what sets the timestamp of an
     * entry in JournalEntryScreen and in TrackingEntryDialog.
     */
    @Test
    fun anUnreadableValue_isRefused() {
        val unreadable = listOf("", "not a date", "2025-03-15", "32/13/2025", "15/03/2025 14:30")

        for (bad in unreadable) {
            assertNull("parseDisplayDate(\"$bad\")", DateUtils.parseDisplayDate(bad))
            assertNull("combineDateTime(\"$bad\")", DateUtils.combineDateTime(bad, "14:30", paris))
        }

        assertNull(DateUtils.formatIsoDate("15/03/2025"))
        assertNull(DateUtils.parseIso8601Time("not a time", paris))
    }

    /** An unreadable time is refused too, rather than becoming the current hour. */
    @Test
    fun anUnreadableTime_isRefused() {
        val unreadable = listOf("", "half past two", "14", "14:30:00", "abc:def")

        for (bad in unreadable) {
            assertNull("parseTime(\"$bad\")", DateUtils.parseTime(bad, paris))
        }
    }

    /**
     * A time outside the clock is refused where it is read, not left for whatever comes
     * next. It used to come back as written -- 99:99 as Pair(99, 99) -- and only failed one
     * call later inside combineDateTime, where the time string was no longer in sight.
     */
    @Test
    fun anOutOfRangeTime_isRefusedWhereItIsRead() {
        assertNull(DateUtils.parseTime("99:99", paris))
        assertNull(DateUtils.parseTime("24:00", paris))
        assertNull(DateUtils.parseTime("12:60", paris))
        assertNull(DateUtils.parseTime("-1:30", paris))

        assertNull(DateUtils.combineDateTime("15/03/2025", "99:99", paris))
    }

    /** The bounds of the clock are themselves valid. */
    @Test
    fun theEdgesOfTheClockAreAccepted() {
        assertEquals(Pair(0, 0), DateUtils.parseTime("00:00", paris))
        assertEquals(Pair(23, 59), DateUtils.parseTime("23:59", paris))
    }

    @Test
    fun `a day written for the display reads back as the same day, without a zone`() {
        assertEquals(LocalDate.of(2026, 10, 14), DateUtils.parseDisplayDate("14/10/2026"))
        assertEquals("14/10/2026", DateUtils.formatDisplayDate(LocalDate.of(2026, 10, 14)))
        assertNull(DateUtils.parseDisplayDate(""))
        assertNull(DateUtils.parseDisplayDate("2026-10-14"))
    }

    @Test
    fun `the day a Material picker hands back is its UTC day, west of UTC too`() {
        val day = LocalDate.of(2026, 10, 14)
        val millis = DateUtils.pickerMillisOf(day)
        assertEquals(day, DateUtils.dateOfPickerMillis(millis))
        // Read in New York, the same midnight UTC is the evening before: the day is not read there
        assertEquals(LocalDate.of(2026, 10, 13), java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.of("America/New_York")).toLocalDate())
    }
}
