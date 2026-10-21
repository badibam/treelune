package app.treelune.tools.agenda

import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.selection.ShiftUnit
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.utils.SchedulePattern
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * What docs/TOOLS.md (« Agenda ») promises a calendar's series and reminders: occurrences given up
 * to 90 days ahead, never the past, never again after the last one given; reminders sent while the
 * event is not over, once for the start they were sent for; a schedule changed remaking the
 * occurrences to come, another change handed to those that still had the former value.
 */
class AgendaRulesTest {

    private val zone = ZoneId.of("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // Friday 2026-10-16, 10:00
    private val now = at(2026, 10, 16, 10)
    private val tuesdays = SchedulePattern.WeeklySimple(listOf(2), "18:00")

    private fun event(start: Long, duration: Long? = null, allDay: Boolean = false, days: Int = 1, status: String = AgendaToolType.Status.PLANNED,
                      remindedUntil: Long? = null, remindedFor: Long? = null) =
        AgendaEvent("e", "Dentist", start, duration, allDay, days, status, null, null, remindedUntil, remindedFor, emptyMap())

    private fun resolve(reference: Long, point: TimePoint) = TimeResolver(reference, zone, 0, "MONDAY").instant(point)

    // Days starting at midnight, and at 4:00
    private val midnight = TimeResolver(now, zone, 0, "MONDAY")
    private val four = TimeResolver(now, zone, 4, "MONDAY")

    @Test
    fun `a series gives its times up to ninety days ahead, after the last one given and never before now`() {
        val first = AgendaRules.timesToGive(tuesdays, null, false, now, zone)
        assertEquals(at(2026, 10, 20, 18), first.first())
        assertTrue(first.all { it > now && it <= now + AgendaRules.HORIZON })
        assertEquals(13, first.size)

        // Given up to a Tuesday, the next pass starts after it
        val later = AgendaRules.timesToGive(tuesdays, first.last(), true, now + 7L * 24 * 3600 * 1000, zone)
        assertEquals(1, later.size)
        assertTrue(later.first() > first.last())

        // A last one long past does not fill the past
        assertEquals(at(2026, 10, 20, 18), AgendaRules.timesToGive(tuesdays, at(2026, 1, 6, 18), false, now, zone).first())
    }

    @Test
    fun `a series far apart always gives its next occurrence, beyond the horizon`() {
        val yearly = SchedulePattern.SpecificDates(listOf(at(2027, 5, 12, 9)))
        assertEquals(listOf(at(2027, 5, 12, 9)), AgendaRules.timesToGive(yearly, null, false, now, zone))
        assertEquals(emptyList<Long>(), AgendaRules.timesToGive(yearly, null, true, now, zone))
    }

    @Test
    fun `a reminder is due once come, until the event is over, and once for its start`() {
        val start = at(2026, 10, 16, 14, 30)
        val dentist = event(start, duration = 45 * 60_000L)
        val times = AgendaRules.reminderTimes(dentist, listOf(TimePoint.Shift(ShiftUnit.MINUTE, -15)), ::resolve)
        assertEquals(listOf(at(2026, 10, 16, 14, 15)), times)

        assertEquals(emptyList<Long>(), AgendaRules.dueReminders(dentist, times, at(2026, 10, 16, 14, 0), midnight))
        assertEquals(times, AgendaRules.dueReminders(dentist, times, at(2026, 10, 16, 14, 20), midnight))
        // Late, while the event goes on; not once it is over
        assertEquals(times, AgendaRules.dueReminders(dentist, times, at(2026, 10, 16, 15, 0), midnight))
        assertEquals(emptyList<Long>(), AgendaRules.dueReminders(dentist, times, at(2026, 10, 16, 15, 16), midnight))
        // Sent for this start: not again; sent for another one: due anew
        assertEquals(emptyList<Long>(), AgendaRules.dueReminders(event(start, 45 * 60_000L, remindedUntil = times[0], remindedFor = start), times, at(2026, 10, 16, 14, 20), midnight))
        assertEquals(times, AgendaRules.dueReminders(event(start, 45 * 60_000L, remindedUntil = times[0], remindedFor = start - 1), times, at(2026, 10, 16, 14, 20), midnight))
        // Cancelled: none
        assertEquals(emptyList<Long>(), AgendaRules.dueReminders(event(start, status = AgendaToolType.Status.CANCELLED), times, at(2026, 10, 16, 14, 20), midnight))
    }

    @Test
    fun `a whole day is reminded the day before at its hour, and late on its own day`() {
        val birthday = event(at(2026, 10, 17), allDay = true)
        val times = AgendaRules.reminderTimes(birthday, listOf(TimePoint.Shift(ShiftUnit.DAY, -1, LocalTime.of(18, 0))), ::resolve)
        assertEquals(listOf(at(2026, 10, 16, 18)), times)
        assertEquals(times, AgendaRules.dueReminders(birthday, times, at(2026, 10, 17, 8), midnight))
        assertEquals(emptyList<Long>(), AgendaRules.dueReminders(birthday, times, at(2026, 10, 18, 0, 1), midnight))
        assertEquals(at(2026, 10, 16, 18), AgendaRules.nextReminder(birthday, times, now, midnight))
        assertNull(AgendaRules.nextReminder(birthday, times, at(2026, 10, 16, 19), midnight))
    }

    @Test
    fun `a whole day lasts until the user's next day starts, its reminders late until then`() {
        // Saturday's birthday, its start the start of Saturday as the day starts at 4:00
        val birthday = event(at(2026, 10, 17, 4), allDay = true)
        assertEquals(at(2026, 10, 18, 4) - 1, birthday.end(four))
        assertTrue(birthday.isOngoing(at(2026, 10, 18, 1), four))
        val times = listOf(at(2026, 10, 16, 18))
        assertEquals(times, AgendaRules.dueReminders(birthday, times, at(2026, 10, 18, 1), four))
        assertEquals(emptyList<Long>(), AgendaRules.dueReminders(birthday, times, at(2026, 10, 18, 4), four))
    }

    @Test
    fun `a whole day written at another time is put at the start of the user's day, its reminders' mark read against it`() {
        val data = JSONObject().put(AgendaToolType.ALL_DAY, true).put(AgendaToolType.DAYS, 1).toString()
        val written = ToolDataEntity("b", "agenda", "agenda", at(2026, 10, 17), "Birthday", data, 0L, 0L)
        assertEquals(at(2026, 10, 17, 4), AgendaRules.settleAllDay(written, four)!!.timestamp)
        assertEquals(at(2026, 10, 17, 4), AgendaRules.settleAllDay(written.copy(timestamp = at(2026, 10, 17, 14, 30)), four)!!.timestamp)
        assertNull(AgendaRules.settleAllDay(written.copy(timestamp = at(2026, 10, 17, 4)), four))
        // An event at a time stays where it is
        assertNull(AgendaRules.settleAllDay(written.copy(data = JSONObject().put(AgendaToolType.DURATION, 3_600_000L).toString()), four))
    }

    private fun config(vararg series: JSONObject) = JSONObject().put(AgendaToolType.SERIES_LIST, JSONArray(series.toList()))
    private fun series(schedule: String, name: String = "Physio", duration: Long? = 2_700_000L) = JSONObject()
        .put("key", "s_physio").put("name", name).put("schedule", JSONObject(schedule)).apply { duration?.let { put(AgendaToolType.DURATION, it) } }

    private val weekly = """{"pattern":{"type":"WeeklySimple","days_of_week":[2],"time":"18:00"}}"""
    private val otherDay = """{"pattern":{"type":"WeeklySimple","days_of_week":[3],"time":"18:00"}}"""

    private fun occurrence(id: String, start: Long, name: String = "Physio", duration: Long? = 2_700_000L) = ToolDataEntity(
        id = id, toolInstanceId = "t", tooltype = "agenda", timestamp = start, name = name,
        data = JSONObject().apply { duration?.let { put(AgendaToolType.DURATION, it) } }.toString(), createdAt = 0, updatedAt = 0,
        state = JSONObject().put(AgendaToolType.STATUS, AgendaToolType.Status.PLANNED).put(AgendaToolType.SERIES, "s_physio").put(AgendaToolType.SERIES_AT, start).toString()
    )

    @Test
    fun `a schedule changed or a series gone remakes its occurrences to come, never the past`() {
        val past = occurrence("past", at(2026, 10, 13, 18))
        val coming = occurrence("coming", at(2026, 10, 20, 18))
        val before = config(series(weekly))
        val moved = AgendaRules.removedByConfig(before, config(series(otherDay)), listOf(past, coming), now)
        assertEquals(listOf("coming"), moved.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(false), moved.map { it.gone })
        val gone = AgendaRules.removedByConfig(before, config(), listOf(past, coming), now)
        assertEquals(listOf("coming"), gone.flatMap { it.entries }.map { it.id })
        assertEquals(listOf(true), gone.map { it.gone })
        assertEquals(emptyList<String>(), AgendaRules.removedByConfig(before, config(series(weekly, name = "Kiné")), listOf(past, coming), now).flatMap { it.entries }.map { it.id })
    }

    @Test
    fun `another change goes to the occurrences to come that still had the former value`() {
        val kept = occurrence("kept", at(2026, 10, 20, 18))
        val renamed = occurrence("renamed", at(2026, 10, 27, 18), name = "Physio, Dr Martin", duration = 1_800_000L)
        val past = occurrence("past", at(2026, 10, 13, 18))
        val changed = AgendaRules.updatedByConfig(config(series(weekly)), config(series(weekly, name = "Kiné", duration = 3_600_000L)), listOf(kept, renamed, past), now)
        assertEquals(listOf("kept"), changed.map { it.id })
        assertEquals("Kiné", changed[0].name)
        assertEquals(3_600_000L, JSONObject(changed[0].data).getLong(AgendaToolType.DURATION))
    }

    @Test
    fun `a start moved clears the mark of its reminders`() {
        val sent = occurrence("e", at(2026, 10, 20, 18)).let { e ->
            e.copy(state = JSONObject(e.state!!).put(AgendaToolType.REMINDED_FOR, at(2026, 10, 20, 18)).put(AgendaToolType.REMINDED_UNTIL, at(2026, 10, 20, 17)).toString())
        }
        assertNull(AgendaRules.settleReminded(sent))
        val moved = AgendaRules.settleReminded(sent.copy(timestamp = at(2026, 10, 20, 19)))!!
        assertTrue(!JSONObject(moved.state!!).has(AgendaToolType.REMINDED_FOR))
    }
}
