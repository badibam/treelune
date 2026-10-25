package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings a goal's config to its v105 form: as a routine's, an optional schedule with « Launch
 * now » beside it, the choice of a recurrence gone.
 *
 * A recurring goal only loses the choice. A one-off goal ("once") loses it with its start and
 * deadline: one without an attempt yet takes its start (its creation when it had none) as a
 * schedule on that precise date, and the time from its start to its deadline as its duration,
 * so that it opens where it would have; one whose attempt is opened keeps it, with neither
 * schedule nor duration, launched by hand from then on.
 *
 * Shared by the database migration and the backup import.
 */
object GoalLaunchAtV105 {

    private const val RECURRENCE = "recurrence"
    private const val ONCE = "once"

    /** [config] of a [tooltype] tool made at [createdAt], which has attempts when [hasAttempts], as it stands at v105. */
    fun config(tooltype: String, config: JSONObject, createdAt: Long, hasAttempts: Boolean): JSONObject {
        if (tooltype != "goal" || !config.has(RECURRENCE)) return config
        val next = JSONObject(config.toString())
        val once = next.optString(RECURRENCE) == ONCE
        next.remove(RECURRENCE)
        if (!once) return next
        val start = (next.opt("start") as? Number)?.toLong() ?: createdAt
        val deadline = (next.opt("deadline") as? Number)?.toLong()
        next.remove("start")
        next.remove("deadline")
        next.remove("schedule")
        next.remove("duration")
        if (!hasAttempts) {
            next.put("schedule", JSONObject().put("pattern", JSONObject().put("type", "SpecificDates").put("timestamps", JSONArray().put(start))))
            deadline?.takeIf { it > start }?.let { next.put("duration", it - start) }
        }
        return next
    }

    /** A history copy of a tool's row, its config rewritten when it is a goal's; [hasAttempts] whether the goal of an id has some. */
    fun historyCopy(copy: JSONObject, hasAttempts: (String) -> Boolean): JSONObject {
        val config = copy.optString("config_json").takeIf { it.isNotEmpty() } ?: return copy
        val next = config(copy.optString("tooltype"), JSONObject(config), copy.optLong("created_at"), hasAttempts(copy.optString("id")))
        return copy.put("config_json", next.toString())
    }

    /** Rewrites the backup document's goal configs in place, their attempts read among its entries. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        val entries = data.optJSONArray("tool_data")
        val withAttempts = (0 until (entries?.length() ?: 0)).map { entries!!.getJSONObject(it).optString("tool_instance_id") }.toSet()
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            val config = JSONObject(instance.getString("config_json"))
            val id = instance.getString("id")
            instance.put("config_json", config(instance.getString("tooltype"), config, instance.getLong("created_at"), id in withAttempts).toString())
        }
    }
}
