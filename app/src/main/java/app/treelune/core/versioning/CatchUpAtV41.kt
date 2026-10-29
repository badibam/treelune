package app.treelune.core.versioning

import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.ai.data.LegacyCatchUp
import org.json.JSONObject

/**
 * Brings an automation's catch-up settings to their v41 form (docs/DATA.md): the explicit
 * choice "limited" or "unlimited" is stored, and a limited window is a DURATION in milliseconds
 * instead of a number of minutes. A scheduled automation with no window had chosen "unlimited",
 * since the screen refused to save it otherwise; an automation without schedule has no catch-up
 * settings at all.
 *
 * Shared by the database migration and the backup import.
 */
object CatchUpAtV41 {

    private const val MILLIS_PER_MINUTE = 60_000L

    /** The catch-up choice and window of an automation, scheduled or not, whose window was [windowMinutes]. */
    fun of(scheduled: Boolean, windowMinutes: Long?): Pair<String?, Long?> = when {
        !scheduled -> null to null
        windowMinutes == null -> RoutineSettings.UNLIMITED to null
        else -> RoutineSettings.LIMITED to windowMinutes * MILLIS_PER_MINUTE
    }

    /**
     * Rewrites the backup document's automations in place. A backup written before the catch-up
     * settings existed carries no window, and is read by LegacyCatchUp, as the v23 migration did.
     */
    fun backup(data: JSONObject) {
        val routines = data.optJSONArray("automations") ?: return
        for (i in 0 until routines.length()) {
            val routine = routines.getJSONObject(i)
            val hasWindow = routine.has("catch_up_window_minutes") && !routine.isNull("catch_up_window_minutes")
            val scheduled = !routine.isNull("schedule_json") && routine.optString("schedule_json").isNotEmpty()
            val (choice, window) = of(scheduled, if (hasWindow) routine.getLong("catch_up_window_minutes") else LegacyCatchUp.WINDOW_MINUTES)
            if (!hasWindow) routine.put("dismiss_older_instances", LegacyCatchUp.DISMISS_OLDER)
            routine.remove("catch_up_window_minutes")
            choice?.let { routine.put("catch_up", it) }
            window?.let { routine.put("catch_up_window", it) }
        }
    }
}
