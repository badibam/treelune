package app.treelune.tools.agenda

import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.SchedulePattern
import app.treelune.core.utils.StoredSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * An event of a calendar as its entry holds it (docs/design/agenda-tool.md, « L'événement »): its
 * start is the entry's timestamp, for a whole day the start of the user's day on its date; in data, its duration, or for a whole day the number of days;
 * in state, its status, the series that gave it and when, and the reminders already sent.
 *
 * @property seriesAt The time of its series it was given for, which the next one counts from:
 *   an occurrence moved by hand keeps it, and a whole day's, which starts at the start of its day,
 *   keeps the schedule's time of day
 * @property remindedUntil The latest reminder sent, every one up to it being sent
 * @property remindedFor The start those reminders were sent for: a start moved makes them due anew
 */
data class AgendaEvent(
    val id: String,
    val name: String,
    val start: Long,
    val duration: Long?,
    val allDay: Boolean,
    val days: Int,
    val status: String,
    val series: String?,
    val seriesAt: Long?,
    val remindedUntil: Long?,
    val remindedFor: Long?,
    val extra: Map<String, Any?>
) {
    val isPlanned get() = status == AgendaToolType.Status.PLANNED
    val isRemoved get() = status == AgendaToolType.Status.REMOVED

    /**
     * Its end: the last millisecond of its last day for a whole day, the day as the user set it
     * ([calendar]: past midnight, still that day until it starts again); its start plus its
     * duration otherwise — its start itself without one.
     */
    fun end(calendar: TimeResolver): Long =
        if (allDay) calendar.startOf(date(calendar.zone).plusDays(days.toLong())) - 1
        else start + (duration ?: 0L)

    /** [end] in the app's calendar, read in [zone]. */
    fun end(zone: ZoneId): Long = end(AgendaToolType.calendar(zone))

    /** The date a whole day is on: its start's. */
    fun date(zone: ZoneId): java.time.LocalDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()

    /** Whether it is going on at [now]: begun and not ended. */
    fun isOngoing(now: Long, calendar: TimeResolver) = start <= now && now <= end(calendar)

    /** [isOngoing] in the app's calendar, read in [zone]. */
    fun isOngoing(now: Long, zone: ZoneId) = isOngoing(now, AgendaToolType.calendar(zone))

    companion object {
        /** An event read from an entry as the services give it (ToolDataEntries.toMap). */
        fun of(entry: Map<*, *>): AgendaEvent {
            val data = entry["data"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val state = entry["state"] as? Map<*, *> ?: emptyMap<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            return AgendaEvent(
                id = entry["id"] as String,
                name = entry["name"] as? String ?: "",
                start = (entry["timestamp"] as Number).toLong(),
                duration = (data[AgendaToolType.DURATION] as? Number)?.toLong(),
                allDay = data[AgendaToolType.ALL_DAY] == true,
                days = (data[AgendaToolType.DAYS] as? Number)?.toInt() ?: 1,
                status = state[AgendaToolType.STATUS] as? String ?: AgendaToolType.Status.PLANNED,
                series = state[AgendaToolType.SERIES] as? String,
                seriesAt = (state[AgendaToolType.SERIES_AT] as? Number)?.toLong(),
                remindedUntil = (state[AgendaToolType.REMINDED_UNTIL] as? Number)?.toLong(),
                remindedFor = (state[AgendaToolType.REMINDED_FOR] as? Number)?.toLong(),
                extra = (entry["extra"] as? Map<String, Any?>) ?: emptyMap()
            )
        }

        /** An event read from its stored row. */
        fun of(entity: ToolDataEntity): AgendaEvent = of(mapOf(
            "id" to entity.id,
            "name" to entity.name,
            "timestamp" to entity.timestamp,
            "data" to JsonUtils.toMap(entity.data),
            "extra" to entity.extra?.let { JsonUtils.toMap(it) },
            "state" to entity.state?.let { JsonUtils.toMap(it) }
        ))
    }
}

/**
 * A series of a calendar's config (« Les séries »): what its occurrences are given — their name,
 * their time by its schedule, their duration or whole days. Its key, given at its creation
 * (AgendaToolType.completeConfig), is what its occurrences name in their state.
 */
data class AgendaSeries(
    val key: String,
    val name: String,
    val schedule: JSONObject?,
    val duration: Long?,
    val allDay: Boolean,
    val days: Int
) {
    /** Whether [other] gives its occurrences other times: another schedule, or whole days or not. */
    fun timesDifferFrom(other: AgendaSeries): Boolean =
        allDay != other.allDay || !sameSchedule(schedule, other.schedule)

    /** The start of the occurrence given for the time [at]: for whole days, the start of the user's day on its date. */
    fun startOf(at: Long, calendar: TimeResolver): Long =
        if (allDay) calendar.startOf(Instant.ofEpochMilli(at).atZone(calendar.zone).toLocalDate()) else at

    companion object {
        /** The series of [config], in their order; one without a key yet is not read (completeConfig gives it one). */
        fun of(config: JSONObject): List<AgendaSeries> {
            val list = config.optJSONArray(AgendaToolType.SERIES_LIST) ?: return emptyList()
            return (0 until list.length()).mapNotNull { i ->
                val item = list.optJSONObject(i) ?: return@mapNotNull null
                val key = item.optString("key").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                AgendaSeries(
                    key = key,
                    name = item.optString("name"),
                    schedule = item.optJSONObject("schedule"),
                    duration = item.opt(AgendaToolType.DURATION).let { (it as? Number)?.toLong() },
                    allDay = item.optBoolean(AgendaToolType.ALL_DAY, false),
                    days = item.optInt(AgendaToolType.DAYS, 1)
                )
            }
        }

        /** Two schedules alike, both absent included. */
        private fun sameSchedule(a: JSONObject?, b: JSONObject?): Boolean =
            if (a == null || b == null) a == b else JsonUtils.toMap(a.toString()) == JsonUtils.toMap(b.toString())
    }
}

/** The rules between a calendar's series, its occurrences, its reminders and the clock. */
object AgendaRules {

    /** How far ahead a series gives its occurrences, the next one always given. */
    const val HORIZON = 90L * 24 * 60 * 60 * 1000

    /** At most this many occurrences given to one series in one pass. */
    const val MAX_PER_PASS = 400

    /**
     * The times of [series] to give an occurrence at [now]: those after the last one given
     * ([lastGiven], and never before now: a series does not fill the past) up to [HORIZON] ahead,
     * and at least the next one while none to come exists ([hasComing]).
     */
    fun timesToGive(pattern: SchedulePattern, lastGiven: Long?, hasComing: Boolean, now: Long, zone: ZoneId): List<Long> {
        var cursor = maxOf(lastGiven ?: now, now)
        val times = mutableListOf<Long>()
        while (times.size < MAX_PER_PASS) {
            val next = ScheduleCalculator.calculateNextExecution(pattern, cursor, zone) ?: break
            if (next <= cursor) break
            if (next > now + HORIZON && (times.isNotEmpty() || hasComing)) break
            times.add(next)
            cursor = next
        }
        return times
    }

    /** The pattern of a stored schedule, null when it has none or does not read (the scheduler says so). */
    fun pattern(schedule: JSONObject?): StoredSchedule = StoredSchedule.of(JSONObject().apply { schedule?.let { put("schedule", it) } })

    /** The instants of [event]'s reminders, [reminders] resolved against its start ([resolve]). */
    fun reminderTimes(event: AgendaEvent, reminders: List<TimePoint>, resolve: (reference: Long, point: TimePoint) -> Long): List<Long> =
        reminders.map { resolve(event.start, it) }.sorted()

    /**
     * The reminders of [event] due at [now] and not sent yet: their time come, the event not
     * ended, still planned. Those sent for another start count for nothing (remindedFor).
     */
    fun dueReminders(event: AgendaEvent, times: List<Long>, now: Long, calendar: TimeResolver): List<Long> {
        if (!event.isPlanned || now > event.end(calendar)) return emptyList()
        val sentUntil = event.remindedUntil.takeIf { event.remindedFor == event.start }
        return times.filter { it <= now && (sentUntil == null || it > sentUntil) }
    }

    /** The next reminder of [event] after [now], null when none is left to send. */
    fun nextReminder(event: AgendaEvent, times: List<Long>, now: Long, calendar: TimeResolver): Long? {
        if (!event.isPlanned) return null
        val end = event.end(calendar)
        return times.firstOrNull { it > now && it <= end }
    }

    /**
     * The occurrences to come of the series whose times change with [config] ([previous] as it
     * was), or which it no longer has: whatever they became, moved, cancelled, removed, they go,
     * and the series gives them again. The past is never touched.
     */
    fun removedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, now: Long): List<ToolDataEntity> {
        val before = AgendaSeries.of(previous).associateBy { it.key }
        val after = AgendaSeries.of(config).associateBy { it.key }
        return entries.filter { entry ->
            val event = AgendaEvent.of(entry)
            val key = event.series ?: return@filter false
            if (event.start <= now || key !in before) return@filter false
            val next = after[key] ?: return@filter true
            next.timesDifferFrom(before.getValue(key))
        }
    }

    /**
     * The occurrences to come of the series whose times stay but whose name, duration or days
     * change with [config]: each value still the series' former one takes the new one, a value
     * changed by hand stays.
     */
    fun updatedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, now: Long): List<ToolDataEntity> {
        val before = AgendaSeries.of(previous).associateBy { it.key }
        val after = AgendaSeries.of(config).associateBy { it.key }
        return entries.mapNotNull { entry ->
            val event = AgendaEvent.of(entry)
            val key = event.series ?: return@mapNotNull null
            val old = before[key] ?: return@mapNotNull null
            val new = after[key] ?: return@mapNotNull null
            if (event.start <= now || new.timesDifferFrom(old)) return@mapNotNull null
            val data = JSONObject(entry.data)
            var changed = false
            var name = entry.name
            if (old.name != new.name && entry.name == old.name) { name = new.name; changed = true }
            if (!new.allDay && old.duration != new.duration && event.duration == old.duration) {
                if (new.duration == null) data.remove(AgendaToolType.DURATION) else data.put(AgendaToolType.DURATION, new.duration)
                changed = true
            }
            if (new.allDay && old.days != new.days && event.days == old.days) {
                data.put(AgendaToolType.DAYS, new.days)
                changed = true
            }
            if (changed) entry.copy(name = name, data = data.toString()) else null
        }
    }

    /**
     * [entry] at the start of the user's day on its date ([calendar]) when it takes whole days and
     * starts elsewhere — written at midnight, or made whole days from a time; null when it holds.
     */
    fun settleAllDay(entry: ToolDataEntity, calendar: TimeResolver): ToolDataEntity? {
        val start = entry.timestamp ?: return null
        if (JSONObject(entry.data).opt(AgendaToolType.ALL_DAY) != true) return null
        val day = calendar.startOf(Instant.ofEpochMilli(start).atZone(calendar.zone).toLocalDate())
        return if (day == start) null else entry.copy(timestamp = day)
    }

    /** [entry] without its reminders' mark when they were sent for another start; null when it holds or there is none. */
    fun settleReminded(entry: ToolDataEntity): ToolDataEntity? {
        val state = JSONObject(entry.state ?: return null)
        if (!state.has(AgendaToolType.REMINDED_FOR)) return null
        if (state.optLong(AgendaToolType.REMINDED_FOR) == entry.timestamp) return null
        state.remove(AgendaToolType.REMINDED_FOR)
        state.remove(AgendaToolType.REMINDED_UNTIL)
        return entry.copy(state = state.toString())
    }

    /** The reminders of [config]: its list for whole days, or for events at a time. */
    fun reminders(config: JSONObject, allDay: Boolean, text: (String) -> String): List<TimePoint> {
        val list: JSONArray = config.optJSONArray(if (allDay) AgendaToolType.ALL_DAY_REMINDERS else AgendaToolType.REMINDERS) ?: return emptyList()
        return (0 until list.length()).map { TimePoint.read(list.get(it), text) }
    }
}
