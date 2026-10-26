package app.treelune.core.utils

import java.time.*
import java.time.temporal.TemporalAdjusters

/**
 * Calculator for next execution time based on schedule patterns
 * Handles all 6 schedule pattern types
 *
 * Note: Timezone is resolved from AppConfig (user-configured global timezone)
 */
object ScheduleCalculator {

    /**
     * How far ahead a yearly schedule is searched before giving up.
     *
     * Eight is the longest a date can go without coming round: 29 February skips the
     * centuries that are not leap years, so 1896 is followed by 1904 and 2096 by 2104.
     * Anything shorter would make such a schedule stop rather than wait.
     */
    private const val YEARS_SEARCHED_AHEAD = 8

    /**
     * Calculate next execution time for a schedule
     *
     * @param pattern The schedule pattern to evaluate
     * @param fromTimestamp Calculate from this timestamp (default = now)
     * @param zoneId Timezone the pattern's wall-clock times are read in (default = the
     *   user-configured global timezone). A caller passes it to compute in a stated zone
     *   rather than the app's; the default is what production uses.
     * @param weekStart The first day of a week, for a weekly pattern's cycle (default = the
     *   one the user set, read only when a cycle of weeks needs it)
     * @return Next execution timestamp in UTC milliseconds, or null if no more executions
     */
    fun calculateNextExecution(
        pattern: SchedulePattern,
        fromTimestamp: Long = System.currentTimeMillis(),
        zoneId: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId(),
        weekStart: DayOfWeek? = null
    ): Long? {
        val fromZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromTimestamp), zoneId)
        var next = nextOfPattern(fromZoned, pattern, zoneId) ?: return null

        // A cycle of more than one day or week: the pattern's times, those on the cycle kept
        val cycle = cycleOf(pattern, weekStart) ?: return next.toInstant().toEpochMilli()
        // A whole cycle of the pattern's times holds one on it; beyond, none ever will
        var left = cycle.every * timesPerUnit(pattern)
        while (!cycle.holds(next.toLocalDate())) {
            if (--left < 0) return null
            next = nextOfPattern(next, pattern, zoneId) ?: return null
        }
        return next.toInstant().toEpochMilli()
    }

    /**
     * The days that can be the next time of [pattern] with a cycle of [every] (Cycle), from
     * [fromTimestamp]: one per place in the cycle, earliest first, each the first time of the pattern
     * on its day (a cycle of days) or in its week (of weeks). The one chosen is the start date.
     * Empty when the pattern has no time to come.
     */
    fun cycleStarts(
        pattern: SchedulePattern,
        every: Int,
        fromTimestamp: Long = System.currentTimeMillis(),
        zoneId: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId(),
        weekStart: DayOfWeek? = null
    ): List<LocalDate> {
        val base = withCycle(pattern, 1, null)
        val unit = Cycle.unitOf(base) ?: return emptyList()
        val first = if (unit == Cycle.Unit.WEEK) weekStart ?: DateUtils.weekStart() else null
        val starts = mutableListOf<LocalDate>()
        var from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromTimestamp), zoneId)
        // A unit holds at most timesPerUnit times: past them, the next time is in another unit
        var left = every * timesPerUnit(base) + timesPerUnit(base)
        while (starts.size < every && left-- > 0) {
            val next = nextOfPattern(from, base, zoneId) ?: break
            val day = next.toLocalDate()
            if (starts.none { Cycle.sameUnit(it, day, unit, first) }) starts += day
            from = next
        }
        return starts
    }

    /**
     * Whether [a] and [b] place a cycle of [every] alike for [pattern]: their days, or their
     * weeks, a whole number of cycles apart.
     */
    fun sameCycle(pattern: SchedulePattern, every: Int, a: LocalDate, b: LocalDate, weekStart: DayOfWeek? = null): Boolean {
        val unit = Cycle.unitOf(pattern) ?: return true
        return Cycle.Placed(unit, every, a, if (unit == Cycle.Unit.WEEK) weekStart ?: DateUtils.weekStart() else null).holds(b)
    }

    /** [pattern] with the cycle [every], [startDate]; a pattern without cycle as it is. */
    fun withCycle(pattern: SchedulePattern, every: Int, startDate: String?): SchedulePattern = when (pattern) {
        is SchedulePattern.DailyMultiple -> pattern.copy(every = every, startDate = startDate)
        is SchedulePattern.WeeklySimple -> pattern.copy(every = every, startDate = startDate)
        is SchedulePattern.WeeklyCustom -> pattern.copy(every = every, startDate = startDate)
        else -> pattern
    }

    /** The cycle [pattern] runs on, null when it runs every day or week (or has no cycle). */
    private fun cycleOf(pattern: SchedulePattern, weekStart: DayOfWeek?): Cycle.Placed? {
        val (every, startDate) = when (pattern) {
            is SchedulePattern.DailyMultiple -> pattern.every to pattern.startDate
            is SchedulePattern.WeeklySimple -> pattern.every to pattern.startDate
            is SchedulePattern.WeeklyCustom -> pattern.every to pattern.startDate
            else -> return null
        }
        if (every == 1) return null
        val unit = Cycle.unitOf(pattern)!!
        // Cycle.check made sure a start date is there and reads
        val start = LocalDate.parse(startDate!!)
        return Cycle.Placed(unit, every, start, if (unit == Cycle.Unit.WEEK) weekStart ?: DateUtils.weekStart() else null)
    }

    /** The most times [pattern] gives in one day (daily) or one week (weekly), at least 1. */
    private fun timesPerUnit(pattern: SchedulePattern): Int = maxOf(1, when (pattern) {
        is SchedulePattern.DailyMultiple -> pattern.times.size
        is SchedulePattern.WeeklySimple -> pattern.daysOfWeek.size
        is SchedulePattern.WeeklyCustom -> pattern.moments.size
        else -> 1
    })

    /** The next time of [pattern] strictly after [from], its cycle left aside. */
    private fun nextOfPattern(from: ZonedDateTime, pattern: SchedulePattern, zoneId: ZoneId): ZonedDateTime? = when (pattern) {
        is SchedulePattern.DailyMultiple -> calculateDailyMultiple(from, pattern, zoneId)
        is SchedulePattern.WeeklySimple -> calculateWeeklySimple(from, pattern, zoneId)
        is SchedulePattern.MonthlyRecurrent -> calculateMonthlyRecurrent(from, pattern, zoneId)
        is SchedulePattern.WeeklyCustom -> calculateWeeklyCustom(from, pattern, zoneId)
        is SchedulePattern.YearlyRecurrent -> calculateYearlyRecurrent(from, pattern, zoneId)
        is SchedulePattern.SpecificDates -> calculateSpecificDates(from.toInstant().toEpochMilli(), pattern, zoneId)
    }

    /**
     * Type 1: Daily - Multiple times per day
     * Example: ["09:00", "14:00", "18:00"] → next occurrence of any of these times
     */
    private fun calculateDailyMultiple(
        from: ZonedDateTime,
        pattern: SchedulePattern.DailyMultiple,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.times.isEmpty()) return null

        // Try all times today
        val today = from.toLocalDate()
        val candidatesToday = pattern.times.mapNotNull { timeStr ->
            parseTime(timeStr)?.let { (hour, minute) ->
                ZonedDateTime.of(today, LocalTime.of(hour, minute), zoneId)
            }
        }.filter { it.isAfter(from) }

        if (candidatesToday.isNotEmpty()) {
            return candidatesToday.minOrNull()
        }

        // No match today, so the earliest time tomorrow. Read as times, the way the candidates
        // above are: sorting the text would put "14:00" before "9:00" and land the rollover in
        // the afternoon, since an hour written without its leading zero sorts after every hour
        // that has one.
        val tomorrow = today.plusDays(1)
        return pattern.times
            .mapNotNull { timeStr ->
                parseTime(timeStr)?.let { (hour, minute) ->
                    ZonedDateTime.of(tomorrow, LocalTime.of(hour, minute), zoneId)
                }
            }
            .minOrNull()
    }

    /**
     * Type 2: Weekly Simple - Specific days with single time
     * Example: daysOfWeek=[1,3,5], time="09:00" → Monday/Wednesday/Friday at 9am
     */
    private fun calculateWeeklySimple(
        from: ZonedDateTime,
        pattern: SchedulePattern.WeeklySimple,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.daysOfWeek.isEmpty()) return null
        val (hour, minute) = parseTime(pattern.time) ?: return null

        // Try today if it matches
        val todayDayOfWeek = from.dayOfWeek.value // 1=Monday, 7=Sunday
        if (todayDayOfWeek in pattern.daysOfWeek) {
            val todayCandidate = ZonedDateTime.of(from.toLocalDate(), LocalTime.of(hour, minute), zoneId)
            if (todayCandidate.isAfter(from)) {
                return todayCandidate
            }
        }

        // Find next matching day in current week or next week
        val sortedDays = pattern.daysOfWeek.sorted()
        val nextDay = sortedDays.firstOrNull { it > todayDayOfWeek }
            ?: (sortedDays.first() + 7) // Wrap to next week

        val daysToAdd = if (nextDay > todayDayOfWeek) {
            nextDay - todayDayOfWeek
        } else {
            7 - todayDayOfWeek + nextDay
        }

        val targetDate = from.toLocalDate().plusDays(daysToAdd.toLong())
        return ZonedDateTime.of(targetDate, LocalTime.of(hour, minute), zoneId)
    }

    /**
     * Type 3: Monthly Recurrent - Specific months with fixed day
     * Example: months=[1,3,6], dayOfMonth=15, time="10:00" → 15th of Jan/Mar/Jun at 10am
     *
     * Note: If day doesn't exist in month (e.g., Feb 31), skip that month
     */
    private fun calculateMonthlyRecurrent(
        from: ZonedDateTime,
        pattern: SchedulePattern.MonthlyRecurrent,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.months.isEmpty()) return null
        val (hour, minute) = parseTime(pattern.time) ?: return null

        // Try current month if it matches
        val currentMonth = from.monthValue
        val currentYear = from.year
        if (currentMonth in pattern.months) {
            val daysInMonth = from.toLocalDate().lengthOfMonth()
            if (pattern.dayOfMonth <= daysInMonth) {
                val candidate = ZonedDateTime.of(
                    LocalDate.of(currentYear, currentMonth, pattern.dayOfMonth),
                    LocalTime.of(hour, minute),
                    zoneId
                )
                if (candidate.isAfter(from)) {
                    return candidate
                }
            }
        }

        // Find next valid month/year combination
        val sortedMonths = pattern.months.sorted()
        var testYear = currentYear
        var testMonth = sortedMonths.firstOrNull { it > currentMonth } ?: run {
            testYear = currentYear + 1
            sortedMonths.first()
        }

        // Search up to 2 years ahead
        for (attempt in 0..24) {
            val testDate = LocalDate.of(testYear, testMonth, 1)
            val daysInMonth = testDate.lengthOfMonth()

            if (pattern.dayOfMonth <= daysInMonth) {
                return ZonedDateTime.of(
                    LocalDate.of(testYear, testMonth, pattern.dayOfMonth),
                    LocalTime.of(hour, minute),
                    zoneId
                )
            }

            // Move to next matching month
            val nextMonthIdx = sortedMonths.indexOf(testMonth) + 1
            if (nextMonthIdx >= sortedMonths.size) {
                testYear++
                testMonth = sortedMonths.first()
            } else {
                testMonth = sortedMonths[nextMonthIdx]
            }
        }

        return null // Could not find valid date in 2 years
    }

    /**
     * Type 4: Weekly Custom - Different times for different days
     * Example: [(1,"09:00"), (3,"14:00"), (5,"17:00")] → Mon 9am, Wed 2pm, Fri 5pm
     */
    private fun calculateWeeklyCustom(
        from: ZonedDateTime,
        pattern: SchedulePattern.WeeklyCustom,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.moments.isEmpty()) return null

        // Try today if it has a matching moment
        val todayDayOfWeek = from.dayOfWeek.value
        val todayMoments = pattern.moments.filter { it.dayOfWeek == todayDayOfWeek }
        for (moment in todayMoments) {
            val (hour, minute) = parseTime(moment.time) ?: continue
            val candidate = ZonedDateTime.of(from.toLocalDate(), LocalTime.of(hour, minute), zoneId)
            if (candidate.isAfter(from)) {
                return candidate
            }
        }

        // Find next moment in current or next week
        val sortedMoments = pattern.moments.sortedWith(compareBy({ it.dayOfWeek }, { it.time }))
        val nextMoment = sortedMoments.firstOrNull { it.dayOfWeek > todayDayOfWeek }
            ?: sortedMoments.first() // Wrap to next week

        val daysToAdd = if (nextMoment.dayOfWeek > todayDayOfWeek) {
            nextMoment.dayOfWeek - todayDayOfWeek
        } else {
            7 - todayDayOfWeek + nextMoment.dayOfWeek
        }

        val (hour, minute) = parseTime(nextMoment.time) ?: return null
        val targetDate = from.toLocalDate().plusDays(daysToAdd.toLong())
        return ZonedDateTime.of(targetDate, LocalTime.of(hour, minute), zoneId)
    }

    /**
     * Type 5: Yearly Recurrent - Same dates every year
     * Example: [(1,1,"08:00"), (12,25,"08:00")] → Jan 1 and Dec 25 at 8am every year
     */
    private fun calculateYearlyRecurrent(
        from: ZonedDateTime,
        pattern: SchedulePattern.YearlyRecurrent,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.dates.isEmpty()) return null

        val sortedDates = pattern.dates.sortedWith(compareBy({ it.month }, { it.day }, { it.time }))

        // Walk forward year by year, trying every date each year. The current year is not a
        // special case: a candidate in it only counts if it is still ahead, and one in a
        // later year always is.
        for (year in from.year..(from.year + YEARS_SEARCHED_AHEAD)) {
            for (date in sortedDates) {
                val (hour, minute) = parseTime(date.time) ?: continue
                // Skip a day the month does not have that year: 29 February outside a leap
                // year, or 31 in a thirty-day month.
                if (date.day > YearMonth.of(year, date.month).lengthOfMonth()) continue

                val candidate = ZonedDateTime.of(
                    LocalDate.of(year, date.month, date.day),
                    LocalTime.of(hour, minute),
                    zoneId
                )
                if (candidate.isAfter(from)) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Type 6: Specific Dates - One-shot executions
     * Example: [1735030800000, 1738051200000] → Specific timestamps, no repetition
     */
    private fun calculateSpecificDates(
        fromTimestamp: Long,
        pattern: SchedulePattern.SpecificDates,
        zoneId: ZoneId
    ): ZonedDateTime? {
        if (pattern.timestamps.isEmpty()) return null

        // Find next timestamp after fromTimestamp
        val nextTimestamp = pattern.timestamps.sorted().firstOrNull { it > fromTimestamp }
            ?: return null

        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextTimestamp), zoneId)
    }

    /**
     * Parse time string "HH:mm" to (hour, minute) pair
     */
    private fun parseTime(timeStr: String): Pair<Int, Int>? {
        return try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            if (hour !in 0..23 || minute !in 0..59) return null
            Pair(hour, minute)
        } catch (e: Exception) {
            null
        }
    }
}
