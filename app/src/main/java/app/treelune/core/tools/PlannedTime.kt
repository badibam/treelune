package app.treelune.core.tools

/**
 * The time an item a tool planned was planned at (a questionnaire to fill, a session of a
 * sequence), once it is done. A planned item is dated at its planned time; done (filled,
 * started, marked done), it takes the moment it was done, and its planned time stays in its state
 * under [KEY]. Its tool's scheduler finds its slots taken by this time, the date having moved.
 */
object PlannedTime {

    const val KEY = "planned_at"

    /**
     * The slot of the schedule [entry] took, an entry as `tool_data.get` gives it: its planned
     * time once done, its date otherwise (planned, ignored, expired, or never planned).
     */
    fun slotOf(entry: Map<*, *>): Long? =
        ((entry["state"] as? Map<*, *>)?.get(KEY) as? Number)?.toLong() ?: (entry["timestamp"] as? Number)?.toLong()
}
