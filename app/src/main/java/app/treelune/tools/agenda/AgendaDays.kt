package app.treelune.tools.agenda

import app.treelune.core.strings.StringsContext
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.normalizeTimestampWithConfig
import app.treelune.core.utils.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A calendar's events laid by day, as its screen and its tile show them (docs/TOOLS.md,
 * « Agenda »): an event at a time on the day it starts, the day starting at the hour the user set;
 * a whole day on each of its calendar days.
 */
object AgendaDays {

    /** One day shown: its first instant, and its events, whole days first, then by start. */
    data class Day(val date: LocalDate, val start: Long, val events: List<AgendaEvent>)

    /** The day [millis] falls in, the day starting at the hour the user set. */
    fun dayOfInstant(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(normalizeTimestampWithConfig(millis, PeriodType.DAY, zone = zone)).atZone(zone).toLocalDate()

    /** The first instant of the day an event starting at [start] is shown on. */
    fun dayStart(start: Long, allDay: Boolean, zone: ZoneId): Long =
        if (allDay) start else normalizeTimestampWithConfig(start, PeriodType.DAY, zone = zone)

    /** The days an event is shown on: its start's, or each of a whole day's. */
    private fun datesOf(event: AgendaEvent, zone: ZoneId): List<LocalDate> =
        if (event.allDay) {
            val first = Instant.ofEpochMilli(event.start).atZone(zone).toLocalDate()
            (0 until event.days).map { first.plusDays(it.toLong()) }
        } else listOf(dayOfInstant(event.start, zone))

    /**
     * The days of [events] to show: those of [range] (its first and last instants), or with none
     * what is to come — the events not ended at [now], from today on. Removed events are not shown.
     */
    fun of(events: List<AgendaEvent>, range: Pair<Long, Long>?, now: Long, zone: ZoneId): List<Day> {
        val (from, until) = dates(range, now, zone)
        val shown = events.filter { !it.isRemoved && (range != null || it.end(zone) >= now) }
        val byDate = sortedMapOf<LocalDate, MutableList<AgendaEvent>>()
        for (event in shown) {
            for (date in datesOf(event, zone)) {
                if (date < from || (until != null && date > until)) continue
                byDate.getOrPut(date) { mutableListOf() }.add(event)
            }
        }
        return byDate.map { (date, list) ->
            Day(date, startOfDate(date, zone), list.sortedWith(compareBy<AgendaEvent>({ !it.allDay }, { it.start })))
        }
    }

    /** The removed events of [range], or with none those to come, by start. */
    fun removed(events: List<AgendaEvent>, range: Pair<Long, Long>?, now: Long, zone: ZoneId): List<AgendaEvent> {
        val (from, until) = dates(range, now, zone)
        return events.filter { event ->
            event.isRemoved && (range != null || event.end(zone) >= now) &&
                datesOf(event, zone).any { it >= from && (until == null || it <= until) }
        }.sortedBy { it.start }
    }

    /** The first and last days of [range], or today and no end for what is to come. */
    private fun dates(range: Pair<Long, Long>?, now: Long, zone: ZoneId): Pair<LocalDate, LocalDate?> =
        if (range == null) dayOfInstant(now, zone) to null
        else dayOfInstant(range.first, zone) to dayOfInstant(range.second, zone)

    /** The first instant of [date] as a day the user set: at the hour it starts. */
    private fun startOfDate(date: LocalDate, zone: ZoneId): Long = AgendaToolType.calendar(zone).startOf(date)

    /** An event's time at the start of its line: « 14:30 », or « All day ». */
    fun timeLabel(event: AgendaEvent, s: StringsContext): String =
        if (event.allDay) s.tool("all_day_short") else DateUtils.formatTimeForDisplay(event.start, AgendaToolType.zone())

    /** How long an event lasts, below its name: « 14:30 – 15:15 », « 12 days », or nothing. */
    fun lengthLabel(event: AgendaEvent, s: StringsContext): String? = when {
        event.allDay && event.days > 1 -> s.tool("length_days").format(event.days)
        event.allDay -> null
        event.duration != null -> s.tool("length_range").format(
            DateUtils.formatTimeForDisplay(event.start, AgendaToolType.zone()),
            DateUtils.formatTimeForDisplay(event.start + event.duration, AgendaToolType.zone()))
        else -> null
    }

    /** The start a new event is offered: the next full hour. */
    fun nextHour(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
}
