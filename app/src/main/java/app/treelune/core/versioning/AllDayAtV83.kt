package app.treelune.core.versioning

import org.json.JSONObject
import java.time.Instant

/**
 * Brings an Agenda's whole-day events to their v83 form: an event that takes whole days starts at
 * the start of the user's day on its date (day_start_hour), no longer at midnight, so that what
 * reads entries by period finds it on its day. The mark of its reminders (reminded_for), set for
 * the start they were sent for, follows it.
 *
 * Shared by the database migration and the backup import.
 */
object AllDayAtV83 {

    /** An entry's start and state, as they stand at v83. */
    data class Moved(val timestamp: Long, val state: String?)

    /**
     * The entry stored at [timestamp] with [data] and [state], moved; null when it takes no whole
     * days or starts at the start of its day already.
     */
    fun entry(timestamp: Long, data: JSONObject, state: String?, calendar: PointerAtV44.Calendar): Moved? {
        if (data.opt("all_day") != true) return null
        val date = Instant.ofEpochMilli(timestamp).atZone(calendar.zone).toLocalDate()
        val start = date.atTime(calendar.dayStartHour, 0).atZone(calendar.zone).toInstant().toEpochMilli()
        if (start == timestamp) return null
        val moved = state?.let { JSONObject(it) }?.also {
            if (it.has("reminded_for") && it.optLong("reminded_for") == timestamp) it.put("reminded_for", start)
        }
        return Moved(start, moved?.toString())
    }

    /** Rewrites the backup document's whole-day events in place. */
    fun backup(data: JSONObject) {
        val entries = data.optJSONArray("tool_data") ?: return
        val calendar = PointerAtV44.Calendar.of(PointerAtV44.backupFormat(data))
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            if (entry.optString("tooltype") != "agenda" || entry.isNull("timestamp")) continue
            val state = if (entry.isNull("state")) null else entry.getString("state")
            val moved = entry(entry.getLong("timestamp"), JSONObject(entry.getString("data")), state, calendar) ?: continue
            entry.put("timestamp", moved.timestamp)
            if (moved.state != null) entry.put("state", moved.state)
        }
    }
}
