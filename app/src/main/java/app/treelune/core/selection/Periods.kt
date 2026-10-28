package app.treelune.core.selection

import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateUtils
import java.time.ZoneId
import java.util.Calendar

// The periods of the user's calendar: a period's start, its end, the ones before and after it,
// the period a chosen day is in. Where days, weeks, months and years are cut, and nowhere else
// (scripts/check_day_bounds.py); the period selector of the interface reads them from here.

/**
 * Enum for supported period types
 */
enum class PeriodType {
    HOUR, DAY, WEEK, MONTH, YEAR
}

/**
 * Data class representing an absolute period with a specific timestamp
 */
data class Period(
    val timestamp: Long,  // Absolute timestamp marking the start of the period
    val type: PeriodType
) {
    companion object {
        fun now(type: PeriodType): Period = Period(
            normalizeTimestampWithConfig(System.currentTimeMillis(), type),
            type
        )
    }
}

/**
 * Data class representing a relative period (for routine contexts)
 * Offset is relative to "now": -1 = previous, 0 = current, 1 = next
 */
data class RelativePeriod(
    val offset: Int,      // Relative offset from current period
    val type: PeriodType  // Type of period (DAY, WEEK, MONTH, YEAR)
)

/**
 * Resolve relative period to absolute Period
 * Applies offset from the period containing [reference]
 *
 * [reference] is mandatory on purpose. "Yesterday" only means something against some instant,
 * and which instant is a decision of the caller: the clock for anything the user is looking at,
 * the scheduled time for a routine run, which may be catching up on a day long past.
 * Reading the clock here by default is what made 47 catch-up runs read the same day's data.
 *
 * The day's start hour, the week's first day and the timezone are parameters defaulting to the
 * configured ones, so a caller can resolve against stated settings rather than the app's.
 */
fun resolveRelativePeriod(
    relativePeriod: RelativePeriod,
    reference: Long,
    dayStartHour: Int = AppConfigManager.getDayStartHour(),
    weekStartDay: String = AppConfigManager.getWeekStartDay(),
    zone: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()
): Period {
    val currentNormalized = normalizeTimestampWithConfig(
        reference, relativePeriod.type, dayStartHour, weekStartDay, zone
    )
    var targetPeriod = Period(currentNormalized, relativePeriod.type)

    // Apply offset by navigating periods
    repeat(kotlin.math.abs(relativePeriod.offset)) {
        targetPeriod = if (relativePeriod.offset < 0) {
            getPreviousPeriod(targetPeriod, dayStartHour, weekStartDay, zone)
        } else {
            getNextPeriod(targetPeriod, dayStartHour, weekStartDay, zone)
        }
    }

    return targetPeriod
}

/**
 * Get the end timestamp of a period (last millisecond of the period)
 */
fun getPeriodEndTimestamp(
    period: Period,
    dayStartHour: Int = AppConfigManager.getDayStartHour(),
    weekStartDay: String = AppConfigManager.getWeekStartDay(),
    zone: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()
): Long {
    // Get the start of the next period
    val nextPeriodStart = getNextPeriod(period, dayStartHour, weekStartDay, zone).timestamp
    // End of current period is 1ms before start of next period
    return nextPeriodStart - 1
}

/**
 * Extension function to get the end timestamp of a period
 * Simplifies usage: period.getEndTimestamp()
 */
fun Period.getEndTimestamp(): Long {
    return getPeriodEndTimestamp(this)
}

/**
 * Normalizes a timestamp according to period type with configuration parameters
 */
fun normalizeTimestampWithConfig(
    timestamp: Long,
    type: PeriodType,
    dayStartHour: Int = AppConfigManager.getDayStartHour(),
    weekStartDay: String = AppConfigManager.getWeekStartDay(),
    zone: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()
): Long {
    return when (type) {
        PeriodType.HOUR -> {
            // Start of hour (XX:00:00.000)
            DateUtils.calendarAt(timestamp, zone).apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        else -> {
            // The day of the app [timestamp] is in first, the day before when it comes before the
            // hour days start at; then the period that day is in, starting at that hour
            val at = java.time.Instant.ofEpochMilli(timestamp).atZone(zone)
            val day = if (at.hour < dayStartHour) at.toLocalDate().minusDays(1) else at.toLocalDate()
            val first = when (type) {
                PeriodType.WEEK -> day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.valueOf(weekStartDay.uppercase())))
                PeriodType.MONTH -> day.withDayOfMonth(1)
                PeriodType.YEAR -> day.withDayOfYear(1)
                else -> day
            }
            first.atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()
        }
    }
}

/**
 * The days from the app's day [millis] is in to the app's day of [calendar]'s reference, counted
 * on the calendar: the day after a change of hour, the day before lasted 23 or 25 hours and is
 * still one day back. Negative for a day to come.
 */
fun daysBetween(millis: Long, calendar: TimeResolver): Int =
    java.time.temporal.ChronoUnit.DAYS.between(calendar.dateOf(millis), calendar.today()).toInt()

/**
 * The weeks from the one [millis] is in to the one of [calendar]'s reference, counted between the
 * days they start on, as the user set weeks. Negative for a week to come.
 */
fun weeksBetween(millis: Long, calendar: TimeResolver): Int {
    fun start(at: Long) = calendar.dateOf(normalizeTimestampWithConfig(at, PeriodType.WEEK, calendar.dayStartHour, calendar.weekStartDay, calendar.zone))
    return java.time.temporal.ChronoUnit.WEEKS.between(start(millis), start(calendar.reference)).toInt()
}

/**
 * The period of [type] the day [date] chosen in a date picker is in: the one its start, as the
 * user set days ([calendar]), falls in. Its midnight would be the day before's.
 */
fun periodOfDay(date: java.time.LocalDate, type: PeriodType, calendar: TimeResolver): Period =
    Period(normalizeTimestampWithConfig(calendar.startOf(date), type, calendar.dayStartHour, calendar.weekStartDay, calendar.zone), type)

/** The hour [period] on the day [date], at the same time of the clock. */
fun hourOnDay(period: Period, date: java.time.LocalDate, calendar: TimeResolver): Period {
    val time = java.time.Instant.ofEpochMilli(period.timestamp).atZone(calendar.zone).toLocalTime()
    val at = java.time.ZonedDateTime.of(date, time, calendar.zone).toInstant().toEpochMilli()
    return Period(normalizeTimestampWithConfig(at, PeriodType.HOUR, calendar.dayStartHour, calendar.weekStartDay, calendar.zone), PeriodType.HOUR)
}

/**
 * Calculates previous period with normalization
 */
fun getPreviousPeriod(
    period: Period,
    dayStartHour: Int = AppConfigManager.getDayStartHour(),
    weekStartDay: String = AppConfigManager.getWeekStartDay(),
    zone: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()
): Period {
    val cal = DateUtils.calendarAt(period.timestamp, zone)

    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, -1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, -1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, -1)
    }

    // Normalize timestamp after calculation with configuration parameters
    return Period(
        normalizeTimestampWithConfig(cal.timeInMillis, period.type, dayStartHour, weekStartDay, zone),
        period.type
    )
}

/**
 * Calculates next period with normalization
 */
fun getNextPeriod(
    period: Period,
    dayStartHour: Int = AppConfigManager.getDayStartHour(),
    weekStartDay: String = AppConfigManager.getWeekStartDay(),
    zone: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()
): Period {
    val cal = DateUtils.calendarAt(period.timestamp, zone)

    when (period.type) {
        PeriodType.HOUR -> cal.add(Calendar.HOUR_OF_DAY, 1)
        PeriodType.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        PeriodType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
        PeriodType.MONTH -> cal.add(Calendar.MONTH, 1)
        PeriodType.YEAR -> cal.add(Calendar.YEAR, 1)
    }

    // Normalize timestamp after calculation with configuration parameters
    return Period(
        normalizeTimestampWithConfig(cal.timeInMillis, period.type, dayStartHour, weekStartDay, zone),
        period.type
    )
}
