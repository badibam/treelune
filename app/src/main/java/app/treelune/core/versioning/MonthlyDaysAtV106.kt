package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the monthly schedules to their v106 form: a MonthlyRecurrent pattern holds the days of
 * the month it runs on, "days_of_month", where it held one, "day_of_month". Wherever a schedule
 * is stored: an automation's and a Task's schedule_json, and anywhere in a tool's config (the
 * "schedule" of a Messages, Goal, Questionnaire or Sequence config, those of an Agenda's series).
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object MonthlyDaysAtV106 {

    /** [json] (a schedule, or a config holding some) at v106, null when nothing in it changes. */
    fun rewrite(json: String): String? {
        val value = JSONObject(json)
        return if (walk(value)) value.toString() else null
    }

    /** Each monthly pattern under [value], its day made a list in place; true when one changed. */
    private fun walk(value: Any?): Boolean = when (value) {
        is JSONObject -> {
            var changed = false
            if (value.optString("type") == "MonthlyRecurrent" && value.has("day_of_month")) {
                value.put("days_of_month", JSONArray().put(value.get("day_of_month")))
                value.remove("day_of_month")
                changed = true
            }
            for (key in value.keys().asSequence().toList()) if (walk(value.opt(key))) changed = true
            changed
        }
        is JSONArray -> (0 until value.length()).map { walk(value.opt(it)) }.any { it }
        else -> false
    }

    /** The column of each table that holds schedules. */
    val COLUMNS = mapOf("tool_instances" to "config_json", "automations" to "schedule_json", "tasks" to "schedule_json")

    /** A copy the history keeps of a [table]'s row (column -> value), as it stands at v106. */
    fun historyCopy(table: String, copy: JSONObject): JSONObject {
        val column = COLUMNS.getValue(table)
        copy.optString(column).takeIf { it.isNotEmpty() && !copy.isNull(column) }?.let { json ->
            rewrite(json)?.let { copy.put(column, it) }
        }
        return copy
    }

    /** Rewrites the backup document's tools, automations and Tasks in place. */
    fun backup(data: JSONObject) {
        for ((array, column) in listOf("tool_instances" to "config_json", "automations" to "schedule_json", "tasks" to "schedule_json")) {
            val rows = data.optJSONArray(array) ?: continue
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (row.isNull(column)) continue
                val json = row.optString(column).takeIf { it.isNotEmpty() } ?: continue
                rewrite(json)?.let { row.put(column, it) }
            }
        }
    }
}
