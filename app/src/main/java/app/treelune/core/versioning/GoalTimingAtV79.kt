package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings a goal's config to its v79 form: its timing chosen first, once or recurring
 * ("recurrence"), each bringing only the settings it reads.
 *
 * A goal with a schedule is recurring, and loses its start and deadline, which its scheduler never
 * read. A goal without one is a one-off, and loses its duration: the end it gave, its start (its
 * creation when it had none) plus the duration, becomes its deadline when it had none, so its
 * attempt ends where it did.
 *
 * Shared by the database migration and the backup import.
 */
object GoalTimingAtV79 {

    const val RECURRENCE = "recurrence"
    const val ONCE = "once"
    const val RECURRING = "recurring"

    /** [config] of a [tooltype] tool made at [createdAt], as it stands at v79. */
    fun config(tooltype: String, config: JSONObject, createdAt: Long): JSONObject {
        if (tooltype != "goal" || config.has(RECURRENCE)) return config
        val next = JSONObject(config.toString())
        if (next.optJSONObject("schedule") != null) {
            next.put(RECURRENCE, RECURRING)
            next.remove("start")
            next.remove("deadline")
        } else {
            next.put(RECURRENCE, ONCE)
            next.remove("schedule")
            val duration = (next.opt("duration") as? Number)?.toLong()
            if (duration != null && next.opt("deadline") !is Number) {
                val start = (next.opt("start") as? Number)?.toLong() ?: createdAt
                next.put("deadline", start + duration)
            }
            next.remove("duration")
        }
        return next
    }

    /** Rewrites the backup document's goal configs in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            val config = JSONObject(instance.getString("config_json"))
            instance.put("config_json", config(instance.getString("tooltype"), config, instance.getLong("created_at")).toString())
        }
    }
}
