package app.treelune.core.selection

import app.treelune.core.fields.FieldType
import app.treelune.core.utils.AppConfigManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What a relative date resolves against: the reference its context gives (an automation's
 * scheduled time, the end of a goal's attempt, the instant a variable is read at, the clock where
 * there is none), and the user's calendar (timezone, the hour a day starts, the first day of a
 * week).
 */
data class TimeResolver(
    val reference: Long,
    val zone: ZoneId,
    val dayStartHour: Int,
    val weekStartDay: String
) {

    /** The instant [point] designates, in milliseconds; an end is the last millisecond of its period. */
    fun instant(point: TimePoint): Long = when (point) {
        is TimePoint.Fixed -> (point.value as? Number)?.toLong()
            ?: throw IllegalArgumentException("a fixed instant is milliseconds, not ${point.value}")
        TimePoint.Now -> reference
        is TimePoint.Relative -> {
            val start = resolveRelativePeriod(RelativePeriod(point.offset, point.unit), reference, dayStartHour, weekStartDay, zone)
            if (point.edge == Edge.START) start.timestamp else getPeriodEndTimestamp(start, dayStartHour, weekStartDay, zone)
        }
        is TimePoint.Shift -> shifted(point)
    }

    /**
     * [point] on the calendar of the zone: the reference moved by its units, its time of day kept
     * across a change of hour (the day before at 14:30 is 14:30, not 24 hours before), or the day
     * moved and set at its hour. An instant at a time of the clock is the calendar's, as every
     * instant the user writes: the day before at 18:00 of a flight at 01:30 on Thursday is
     * Wednesday at 18:00, though the day of the app the flight is in is Wednesday. Only a day
     * read from it ([day]) is the app's.
     */
    private fun shifted(point: TimePoint.Shift): Long {
        val from = Instant.ofEpochMilli(reference).atZone(zone)
        val at = point.at
        if (at != null) return from.toLocalDate().plusDays(point.offset.toLong()).atTime(at).atZone(zone).toInstant().toEpochMilli()
        val unit = when (point.unit) {
            ShiftUnit.MINUTE -> ChronoUnit.MINUTES
            ShiftUnit.HOUR -> ChronoUnit.HOURS
            ShiftUnit.DAY -> ChronoUnit.DAYS
            ShiftUnit.WEEK -> ChronoUnit.WEEKS
            ShiftUnit.MONTH -> ChronoUnit.MONTHS
            ShiftUnit.YEAR -> ChronoUnit.YEARS
        }
        return from.plus(point.offset.toLong(), unit).toInstant().toEpochMilli()
    }

    /**
     * The day [point] designates, "2026-09-15", as the user set days: now, or a shift, is the day
     * of the app the instant is in (at 0:30 on Wednesday, with days starting at 4:00, today is
     * Tuesday). A period's start is the day it starts in; its end, the eve of the day the next
     * period starts in: with days starting at 4:00, a day runs to 3:59 the day after, and is still
     * one day.
     */
    fun day(point: TimePoint): String = when (point) {
        is TimePoint.Fixed -> point.value as? String
            ?: throw IllegalArgumentException("a fixed day is \"yyyy-MM-dd\", not ${point.value}")
        TimePoint.Now -> dateOf(reference).toString()
        is TimePoint.Relative -> {
            val start = resolveRelativePeriod(RelativePeriod(point.offset, point.unit), reference, dayStartHour, weekStartDay, zone)
            val first = dayOf(start.timestamp)
            if (point.edge == Edge.START) first.toString()
            else {
                val next = resolveRelativePeriod(RelativePeriod(point.offset + 1, point.unit), reference, dayStartHour, weekStartDay, zone)
                maxOf(first, dayOf(next.timestamp).minusDays(1)).toString()
            }
        }
        is TimePoint.Shift -> dateOf(shifted(point)).toString()
    }

    /** The day [millis] belongs to, as the user set days: with days starting at 4:00, 2:00 belongs to the day before. */
    fun dateOf(millis: Long): LocalDate = dayOf(normalizeTimestampWithConfig(millis, PeriodType.DAY, dayStartHour, weekStartDay, zone))

    /** The day of the app the reference is in: at 0:30, with days starting at 4:00, the day before. */
    fun today(): LocalDate = dateOf(reference)

    /** The instant the day [date] starts at, as the user set days: [dayStartHour] on that date. */
    fun startOf(date: LocalDate): Long = date.atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * [start] moved by [duration] as a delay on the calendar reads: its whole days at the same
     * time of the clock, the rest in elapsed time. A week from Monday 8:00 is Monday 8:00 across a
     * change of hour, not 7:00 or 9:00; 36 h is the next day at the same time, then 12 hours.
     */
    fun after(start: Long, duration: Long): Long {
        val days = duration / app.treelune.core.fields.DurationUnit.DAY.millis
        val rest = duration % app.treelune.core.fields.DurationUnit.DAY.millis
        return Instant.ofEpochMilli(start).atZone(zone).plusDays(days).toInstant().toEpochMilli() + rest
    }

    /** [point] in the stored form of a field of [type]: a day for a DATE, milliseconds otherwise. */
    fun resolve(point: TimePoint, type: FieldType): Any = if (type == FieldType.DATE) day(point) else instant(point)

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    companion object {
        /** Against [reference], with the calendar the user set, read in [zone]: the app's unless given. */
        fun at(reference: Long, zone: ZoneId = AppConfigManager.getDateTimeConfig().getZoneId()) = TimeResolver(
            reference = reference,
            zone = zone,
            dayStartHour = AppConfigManager.getDayStartHour(),
            weekStartDay = AppConfigManager.getWeekStartDay()
        )
    }
}
