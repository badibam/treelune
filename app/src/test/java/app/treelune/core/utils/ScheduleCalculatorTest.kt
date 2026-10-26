package app.treelune.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Covers the next-execution calculation for all six schedule patterns.
 *
 * Every case fixes both the clock and the timezone. That is the whole point of testing this
 * here: TODO.md still carries "check a real catch-up on the device, automation scheduled,
 * app closed for several days", and a scheduling question that costs days to observe by
 * hand costs milliseconds with fromTimestamp set by hand.
 *
 * Europe/Paris throughout, because the interesting cases are the ones a timezone with
 * daylight saving produces, and because it is the zone this app is actually used in.
 */
class ScheduleCalculatorTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    /** A wall-clock moment in Paris, as the epoch milliseconds the calculator deals in. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), paris)
            .toInstant().toEpochMilli()

    private fun next(
        pattern: SchedulePattern,
        from: Long
    ): Long? = ScheduleCalculator.calculateNextExecution(pattern, from, paris)

    // ==================== Type 1: several times a day ====================

    /** The next time today, when there is one still to come. */
    @Test
    fun daily_takesTheNextTimeStillToComeToday() {
        val pattern = SchedulePattern.DailyMultiple(listOf("09:00", "14:00", "18:00"))

        assertEquals(at(2025, 1, 15, 14, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2025, 1, 15, 18, 0), next(pattern, from = at(2025, 1, 15, 14, 30)))
    }

    /** Once the day's times have all gone by, the first one tomorrow. */
    @Test
    fun daily_rollsOverToTomorrow() {
        val pattern = SchedulePattern.DailyMultiple(listOf("09:00", "14:00", "18:00"))

        assertEquals(at(2025, 1, 16, 9, 0), next(pattern, from = at(2025, 1, 15, 20, 0)))
    }

    /**
     * Tomorrow's first time is the earliest hour, however the hour is written. An hour without
     * its leading zero sorts after every hour that has one, so reading the list as text used to
     * land the rollover on the afternoon. Nothing in the app writes unpadded times -- the editor
     * produces HH:mm -- but a schedule from the model or from a restored backup can.
     */
    @Test
    fun daily_picksTomorrowsEarliestTimeHoweverItIsWritten() {
        val padded = SchedulePattern.DailyMultiple(listOf("09:00", "14:00"))
        val unpadded = SchedulePattern.DailyMultiple(listOf("9:00", "14:00"))
        val afterBothHaveGone = at(2025, 1, 15, 20, 0)

        assertEquals(at(2025, 1, 16, 9, 0), next(padded, from = afterBothHaveGone))
        assertEquals(at(2025, 1, 16, 9, 0), next(unpadded, from = afterBothHaveGone))
    }

    /** No times, nothing to schedule. */
    @Test
    fun daily_withNoTimes_hasNoNextExecution() {
        assertNull(next(SchedulePattern.DailyMultiple(emptyList()), from = at(2025, 1, 15, 10, 0)))
    }

    /** A time that is not a time schedules nothing, rather than being rounded into one. */
    @Test
    fun daily_withAnImpossibleTime_hasNoNextExecution() {
        assertNull(next(SchedulePattern.DailyMultiple(listOf("25:00")), from = at(2025, 1, 15, 10, 0)))
    }

    // ==================== Type 2: certain days, one time ====================

    /** 2025-01-15 is a Wednesday, so day 3 of the week. */
    private val mondayWednesdayFriday = SchedulePattern.WeeklySimple(listOf(1, 3, 5), "09:00")

    /** Today counts when its time has not gone by. */
    @Test
    fun weekly_takesTodayWhenTheTimeIsStillAhead() {
        assertEquals(
            at(2025, 1, 15, 9, 0),
            next(mondayWednesdayFriday, from = at(2025, 1, 15, 8, 0))
        )
    }

    /** Once it has gone by, the next day in the list. */
    @Test
    fun weekly_movesToTheNextDayInTheList() {
        assertEquals(
            at(2025, 1, 17, 9, 0),
            next(mondayWednesdayFriday, from = at(2025, 1, 15, 10, 0))
        )
    }

    /** With no day left this week, it wraps to the first one of the next. */
    @Test
    fun weekly_wrapsToNextWeek() {
        val mondaysOnly = SchedulePattern.WeeklySimple(listOf(1), "09:00")

        assertEquals(at(2025, 1, 20, 9, 0), next(mondaysOnly, from = at(2025, 1, 15, 10, 0)))
    }

    // ==================== Type 3: certain months, a fixed day ====================

    /** The current month counts when the day and time are still ahead. */
    @Test
    fun monthly_takesTheCurrentMonthWhenItIsStillAhead() {
        val pattern = SchedulePattern.MonthlyRecurrent(listOf(1, 3, 6), dayOfMonth = 15, time = "10:00")

        assertEquals(at(2025, 1, 15, 10, 0), next(pattern, from = at(2025, 1, 15, 9, 0)))
        assertEquals(at(2025, 3, 15, 10, 0), next(pattern, from = at(2025, 1, 15, 11, 0)))
    }

    /** A month too short for the day is passed over, not clamped to its last day. */
    @Test
    fun monthly_skipsAMonthTooShortForTheDay() {
        val pattern = SchedulePattern.MonthlyRecurrent(listOf(2, 3), dayOfMonth = 31, time = "10:00")

        assertEquals(at(2025, 3, 31, 10, 0), next(pattern, from = at(2025, 1, 1, 0, 0)))
    }

    /** A day no listed month ever has schedules nothing at all. */
    @Test
    fun monthly_withADayNoListedMonthHas_hasNoNextExecution() {
        val pattern = SchedulePattern.MonthlyRecurrent(listOf(2), dayOfMonth = 31, time = "10:00")

        assertNull(next(pattern, from = at(2025, 1, 1, 0, 0)))
    }

    // ==================== Type 4: a time per day ====================

    /** Several moments on the same day are taken in order. */
    @Test
    fun weeklyCustom_takesTheDaysMomentsInOrder() {
        val pattern = SchedulePattern.WeeklyCustom(
            listOf(WeekMoment(3, "09:00"), WeekMoment(3, "14:00"), WeekMoment(5, "17:00"))
        )

        assertEquals(at(2025, 1, 15, 14, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2025, 1, 17, 17, 0), next(pattern, from = at(2025, 1, 15, 15, 0)))
    }

    /** And wrap to the first moment of the following week. */
    @Test
    fun weeklyCustom_wrapsToNextWeek() {
        val pattern = SchedulePattern.WeeklyCustom(listOf(WeekMoment(1, "09:00"), WeekMoment(3, "09:00")))

        assertEquals(at(2025, 1, 20, 9, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
    }

    // ==================== Type 5: the same dates every year ====================

    /** This year when the date is still ahead, next year once it has gone. */
    @Test
    fun yearly_takesThisYearThenTheNext() {
        val pattern = SchedulePattern.YearlyRecurrent(listOf(YearlyDate(12, 25, "08:00")))

        assertEquals(at(2025, 12, 25, 8, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2026, 12, 25, 8, 0), next(pattern, from = at(2025, 12, 26, 10, 0)))
    }

    /** A 29 February schedule waits for the next leap year rather than stopping. */
    @Test
    fun yearly_waitsForTheNextLeapYear() {
        val pattern = SchedulePattern.YearlyRecurrent(listOf(YearlyDate(2, 29, "08:00")))

        assertEquals(at(2024, 2, 29, 8, 0), next(pattern, from = at(2024, 1, 15, 10, 0)))
        assertEquals(at(2028, 2, 29, 8, 0), next(pattern, from = at(2025, 1, 15, 10, 0)))
        assertEquals(at(2028, 2, 29, 8, 0), next(pattern, from = at(2024, 3, 1, 10, 0)))
    }

    /**
     * A year that skips a leap day is still found: 2100 is not a leap year, so the eight
     * year gap from 2096 to 2104 is the longest a date can go without coming round, and the
     * search has to outlast it.
     */
    @Test
    fun yearly_crossesACenturyThatIsNotALeapYear() {
        val pattern = SchedulePattern.YearlyRecurrent(listOf(YearlyDate(2, 29, "08:00")))

        assertEquals(at(2104, 2, 29, 8, 0), next(pattern, from = at(2096, 3, 1, 10, 0)))
    }

    /**
     * With several dates, one of them being impossible this year does not stop the others.
     * The old fallback only ever looked at the first date of the list.
     */
    @Test
    fun yearly_doesNotLetOneImpossibleDateHideTheRest() {
        val pattern = SchedulePattern.YearlyRecurrent(
            listOf(YearlyDate(2, 29, "08:00"), YearlyDate(12, 25, "08:00"))
        )

        // Past Christmas in a non-leap year: the next one is Christmas again, not nothing.
        assertEquals(at(2026, 12, 25, 8, 0), next(pattern, from = at(2025, 12, 26, 10, 0)))
    }

    // ==================== Type 6: fixed dates, once each ====================

    /** The next one still ahead, then nothing. */
    @Test
    fun specificDates_runOnceEachAndThenStop() {
        val first = at(2025, 3, 15, 14, 30)
        val second = at(2025, 4, 20, 10, 0)
        val pattern = SchedulePattern.SpecificDates(listOf(second, first))

        assertEquals(first, next(pattern, from = at(2025, 1, 1, 0, 0)))
        assertEquals(second, next(pattern, from = first))
        assertNull(next(pattern, from = second))
    }

    // ==================== Clocks that move ====================

    /**
     * On the night the clocks go forward, 02:30 does not exist in Paris. The schedule is
     * not skipped: it lands at 03:30, the same instant the missing half hour would have
     * been. Java's own rule for a gap, pinned here because a schedule silently moving by an
     * hour once a year is the sort of thing nobody notices.
     */
    @Test
    fun springForward_movesATimeThatDoesNotExistToTheEndOfTheGap() {
        val pattern = SchedulePattern.DailyMultiple(listOf("02:30"))

        assertEquals(
            at(2024, 3, 31, 3, 30),
            next(pattern, from = at(2024, 3, 30, 12, 0))
        )
    }

    /**
     * On the night they go back, 02:30 happens twice. The earlier one is taken, so the
     * schedule runs once rather than twice.
     */
    @Test
    fun fallBack_takesTheFirstOfTwoIdenticalTimes() {
        val pattern = SchedulePattern.DailyMultiple(listOf("02:30"))
        val beforeTheChange = ZonedDateTime.of(
            LocalDate.of(2024, 10, 27), LocalTime.of(1, 0), paris
        ).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()

        val result = next(pattern, from = beforeTheChange)!!

        // The earlier 02:30 is still on summer time, an hour ahead of the later one.
        val earlier = ZonedDateTime.of(LocalDate.of(2024, 10, 27), LocalTime.of(2, 30), paris)
            .withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals(earlier, result)
    }

    /** The zone is what the wall-clock time is read in, so it changes the instant produced. */
    @Test
    fun theZone_decidesWhichInstantAWallClockTimeMeans() {
        val pattern = SchedulePattern.DailyMultiple(listOf("09:00"))
        val from = at(2025, 1, 15, 10, 0)

        val inParis = ScheduleCalculator.calculateNextExecution(pattern, from, paris)
        val inUtc = ScheduleCalculator.calculateNextExecution(pattern, from, ZoneId.of("UTC"))

        // Paris is an hour ahead of UTC in January, so its 09:00 comes an hour earlier.
        assertEquals(3_600_000L, inUtc!! - inParis!!)
    }

    // ==================== A cycle of days or weeks (Cycle) ====================

    /** With weeks starting on [weekStart], Monday unless given. */
    private fun nextIn(pattern: SchedulePattern, from: Long, weekStart: DayOfWeek = DayOfWeek.MONDAY): Long? =
        ScheduleCalculator.calculateNextExecution(pattern, from, paris, weekStart)

    /** The [count] next times from [from], each from the one before. */
    private fun series(pattern: SchedulePattern, from: Long, count: Int, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<Long> {
        val times = mutableListOf<Long>()
        var cursor = from
        repeat(count) { cursor = nextIn(pattern, cursor, weekStart)!!; times += cursor }
        return times
    }

    /** "Every 3 weeks, on Sunday at 8:00", next time Sunday 1 November: then 22 November, 13 December. */
    @Test
    fun weekly_everyThreeWeeks_keepsOneSundayInThree() {
        val pattern = SchedulePattern.WeeklySimple(listOf(7), "08:00", every = 3, startDate = "2026-11-01")

        assertEquals(
            listOf(at(2026, 11, 1, 8, 0), at(2026, 11, 22, 8, 0), at(2026, 12, 13, 8, 0)),
            series(pattern, from = at(2026, 10, 26, 10, 0), count = 3)
        )
    }

    /** The start date only places the cycle: a Sunday of the series long gone gives the same times. */
    @Test
    fun weekly_aStartDateInThePast_placesTheSameCycle() {
        val placedLater = SchedulePattern.WeeklySimple(listOf(7), "08:00", every = 3, startDate = "2026-11-01")
        val placedEarlier = SchedulePattern.WeeklySimple(listOf(7), "08:00", every = 3, startDate = "2026-01-11")

        assertEquals(series(placedLater, at(2026, 10, 26, 10, 0), 4), series(placedEarlier, at(2026, 10, 26, 10, 0), 4))
    }

    /** "Monday and Thursday, one week in two": both days of a week kept, then both of the week after next. */
    @Test
    fun weekly_severalDays_oneWeekInTwo() {
        val pattern = SchedulePattern.WeeklySimple(listOf(1, 4), "09:00", every = 2, startDate = "2026-10-26")

        assertEquals(
            listOf(at(2026, 10, 29, 9, 0), at(2026, 11, 9, 9, 0), at(2026, 11, 12, 9, 0), at(2026, 11, 23, 9, 0)),
            series(pattern, from = at(2026, 10, 26, 10, 0), count = 4)
        )
    }

    /**
     * The weeks are the user's: Sunday and Wednesday one week in two go together when weeks start on
     * Sunday, and a Wednesday with the Sunday after when they start on Monday.
     */
    @Test
    fun weekly_theWeeksStartOnTheUsersDay() {
        val pattern = SchedulePattern.WeeklySimple(listOf(3, 7), "09:00", every = 2, startDate = "2026-11-01")
        val from = at(2026, 10, 31, 10, 0)

        assertEquals(
            listOf(at(2026, 11, 1, 9, 0), at(2026, 11, 4, 9, 0), at(2026, 11, 15, 9, 0)),
            series(pattern, from, 3, weekStart = DayOfWeek.SUNDAY)
        )
        assertEquals(
            listOf(at(2026, 11, 1, 9, 0), at(2026, 11, 11, 9, 0), at(2026, 11, 15, 9, 0)),
            series(pattern, from, 3, weekStart = DayOfWeek.MONDAY)
        )
    }

    /** Different times on different days, one week in two. */
    @Test
    fun weeklyCustom_oneWeekInTwo() {
        val pattern = SchedulePattern.WeeklyCustom(listOf(WeekMoment(1, "09:00"), WeekMoment(5, "17:00")), every = 2, startDate = "2026-11-02")

        assertEquals(
            listOf(at(2026, 11, 2, 9, 0), at(2026, 11, 6, 17, 0), at(2026, 11, 16, 9, 0)),
            series(pattern, from = at(2026, 10, 26, 10, 0), count = 3)
        )
    }

    /** "Every 4 days at 8:00", next time Tuesday 27 October: 31 October, 4 November follow. */
    @Test
    fun daily_everyFourDays() {
        val pattern = SchedulePattern.DailyMultiple(listOf("08:00"), every = 4, startDate = "2026-10-27")

        assertEquals(
            listOf(at(2026, 10, 27, 8, 0), at(2026, 10, 31, 8, 0), at(2026, 11, 4, 8, 0)),
            series(pattern, from = at(2026, 10, 26, 10, 0), count = 3)
        )
    }

    /** Every time of a day of the cycle is kept, none of the days between. */
    @Test
    fun daily_everyTwoDays_keepsEachTimeOfTheDay() {
        val pattern = SchedulePattern.DailyMultiple(listOf("08:00", "20:00"), every = 2, startDate = "2026-10-26")

        assertEquals(
            listOf(at(2026, 10, 26, 20, 0), at(2026, 10, 28, 8, 0), at(2026, 10, 28, 20, 0)),
            series(pattern, from = at(2026, 10, 26, 10, 0), count = 3)
        )
    }

    /**
     * The choices of the next time: one per place in the cycle, each the first time of its day. At
     * 10:00 with a time at 8:00, today is not one: it would come back in 4 days, after the others.
     */
    @Test
    fun cycleStarts_daily_beginTomorrowOnceTodaysTimeIsGone() {
        val pattern = SchedulePattern.DailyMultiple(listOf("08:00"))

        assertEquals(
            listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)),
            ScheduleCalculator.cycleStarts(pattern, 4, at(2026, 10, 26, 10, 0), paris)
        )
    }

    /** For weeks, the first time of each week: a Thursday this week, its Monday being gone. */
    @Test
    fun cycleStarts_weekly_oneByWeek() {
        val pattern = SchedulePattern.WeeklySimple(listOf(1, 4), "09:00")

        assertEquals(
            listOf(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 2)),
            ScheduleCalculator.cycleStarts(pattern, 2, at(2026, 10, 26, 10, 0), paris, DayOfWeek.MONDAY)
        )
    }

    /** A start date stored long ago finds its place among the choices made today. */
    @Test
    fun sameCycle_findsTheChoiceOfAStoredStartDate() {
        val pattern = SchedulePattern.WeeklySimple(listOf(7), "08:00")
        val stored = LocalDate.of(2026, 1, 18)
        val choices = ScheduleCalculator.cycleStarts(pattern, 3, at(2026, 10, 26, 10, 0), paris, DayOfWeek.MONDAY)

        assertEquals(listOf(LocalDate.of(2026, 11, 8)), choices.filter { ScheduleCalculator.sameCycle(pattern, 3, stored, it, DayOfWeek.MONDAY) })
    }

    /** What a cycle refuses, said so that whoever wrote it knows what to give. */
    @Test
    fun cycle_refusesWhatDoesNotRead() {
        assertThrows(IllegalArgumentException::class.java) { SchedulePattern.WeeklySimple(listOf(7), "08:00", every = 3) }
        assertThrows(IllegalArgumentException::class.java) { SchedulePattern.WeeklySimple(listOf(7), "08:00", every = 1, startDate = "2026-11-01") }
        assertThrows(IllegalArgumentException::class.java) { SchedulePattern.DailyMultiple(listOf("08:00"), every = 0) }
        assertThrows(IllegalArgumentException::class.java) { SchedulePattern.DailyMultiple(listOf("08:00"), every = 2, startDate = "1 Nov") }
    }

    /** A schedule stored before the cycle, without every, runs every week as it did. */
    @Test
    fun cycle_absentFromAStoredSchedule_isEveryWeek() {
        val stored = kotlinx.serialization.json.Json.decodeFromString(ScheduleConfig.serializer(),
            """{"pattern":{"type":"WeeklySimple","days_of_week":[7],"time":"08:00"}}""")
        val pattern = stored.pattern as SchedulePattern.WeeklySimple

        assertEquals(1, pattern.every)
        assertTrue(pattern.startDate == null)
        assertFalse(kotlinx.serialization.json.Json.encodeToString(ScheduleConfig.serializer(), stored).contains("every"))
    }
}
