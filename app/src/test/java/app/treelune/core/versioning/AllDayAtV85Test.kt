package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Covers v85: an Agenda's whole-day event moved from midnight to the start of the user's day on
 * its date, the mark of its reminders following it; an event at a time, and a whole day already
 * there, left as they are; in the database as in a backup, which reads its own format settings.
 */
class AllDayAtV85Test {

    private val zone = ZoneId.of("Europe/Paris")
    private val calendar = PointerAtV44.Calendar(zone, 4, "MONDAY")

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val allDay = JSONObject().put("all_day", true).put("days", 1)

    @Test
    fun `a whole day at midnight moves to the start of its day, its reminders' mark with it`() {
        val state = JSONObject().put("status", "planned").put("reminded_until", at(16, 18)).put("reminded_for", at(17, 0)).toString()
        val moved = AllDayAtV85.entry(at(17, 0), allDay, state, calendar)!!
        assertEquals(at(17, 4), moved.timestamp)
        assertEquals(at(17, 4), JSONObject(moved.state!!).getLong("reminded_for"))
        assertEquals(at(16, 18), JSONObject(moved.state!!).getLong("reminded_until"))
    }

    @Test
    fun `an event at a time, and a whole day at the start of its day, stay as they are`() {
        assertNull(AllDayAtV85.entry(at(17, 0), JSONObject().put("duration", 3_600_000L), null, calendar))
        assertNull(AllDayAtV85.entry(at(17, 4), allDay, null, calendar))
    }

    @Test
    fun `a backup moves its calendars' whole days by its own day start`() {
        val format = JSONObject().put("day_start_hour", 6).put("timezone_override", "Europe/Paris")
        val data = JSONObject()
            .put("app_settings_categories", JSONArray().put(JSONObject().put("category", "format").put("settings", format.toString())))
            .put("tool_data", JSONArray()
                .put(JSONObject().put("id", "a").put("tooltype", "agenda").put("timestamp", at(17, 0)).put("data", allDay.toString()))
                .put(JSONObject().put("id", "b").put("tooltype", "tracking").put("timestamp", at(17, 0)).put("data", allDay.toString())))
        AllDayAtV85.backup(data)
        val entries = data.getJSONArray("tool_data")
        assertEquals(at(17, 6), entries.getJSONObject(0).getLong("timestamp"))
        assertEquals(at(17, 0), entries.getJSONObject(1).getLong("timestamp"))
    }
}
