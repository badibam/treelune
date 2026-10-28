package app.treelune.core.ai.data

import app.treelune.core.utils.ScheduleConfig

/**
 * Routine configuration defining when and how AI should act automatically
 *
 * A routine is a template that creates AUTOMATION sessions when triggered.
 * It references a SEED session containing the initial user message/prompt.
 */
data class Routine(
    val id: String,
    val name: String,
    val zoneId: String,                     // Routine attached to a zone
    val seedSessionId: String,              // Points to SEED session with initial message
    val schedule: ScheduleConfig?,          // null = no time-based triggering
    /** How many triggers start it beside its schedule (Triggers); 0 when none does */
    val triggers: Int,
    /** The event a run that ends well emits (EventPoints), its id; null for none */
    val emitEvent: String? = null,
    /** A scheduled routine's choice about missed runs (RoutineSettings.LIMITED or UNLIMITED); null without schedule */
    val catchUp: String? = null,

    /**
     * How late a scheduled occurrence may be and still run, in milliseconds, for a limited
     * catch-up; null otherwise.
     *
     * Beyond it the occurrence is skipped: it leaves a log line and no session, since the
     * history is made of sessions and an empty "skipped" one would be a shape to handle
     * everywhere. Same notion as the Messages tooltype's validity_window.
     */
    val catchUpWindow: Long? = null,

    /** Among the occurrences that are due, run only the most recent one instead of each */
    val dismissOlderInstances: Boolean = false,
    val providerId: String?,                // AI provider its runs call; null for none, not run then
    /** The identity its runs speak as; null for the app's own AI */
    val identityId: String? = null,
    val isEnabled: Boolean,
    /** Which of its runs wait for the user (RoutineSettings.NOTIFY_*) */
    val notifyEnd: String = RoutineSettings.NOTIFY_ALWAYS,
    val group: String? = null,              // Group within zone (null = ungrouped)
    val createdAt: Long,
    val updatedAt: Long,                    // Last modification timestamp (used to skip missed executions after disable)
    val lastExecutionId: String?,           // ID of most recent execution session
    val executionHistory: List<String>,     // IDs of execution sessions (newest first)
    /** What its AI may reach (docs/AI.md, « Levels »): empty, everything */
    val access: app.treelune.core.access.AccessMask = app.treelune.core.access.AccessMask(),
    /** How its tile shows (RoutineSettings.LOOK), its name among it */
    val look: app.treelune.core.look.Look,
    /** Its place in the grid of its group's section (SectionGrid) */
    val gridX: Int,
    val gridY: Int
) {
    val tile: app.treelune.core.grid.Grid.Tile
        get() = app.treelune.core.grid.Grid.size(look.displayMode).let { app.treelune.core.grid.Grid.Tile(id, gridX, gridY, it.width, it.height) }

    companion object {
        /**
         * A routine as the routines service gives it in a result: the single reader of
         * that form, which fails loudly on a result that does not have it.
         */
        fun fromResult(map: Map<*, *>): Routine {
            @Suppress("UNCHECKED_CAST")
            val schedule = (map["schedule"] as Map<String, Any?>?)?.let {
                kotlinx.serialization.json.Json.decodeFromString(ScheduleConfig.serializer(),
                    app.treelune.core.utils.JsonUtils.toJSONObject(it).toString())
            }
            val catchUp = map["catch_up"] as Map<*, *>?
            return Routine(
                id = map["id"] as String,
                name = map["name"] as String,
                zoneId = map["zone_id"] as String,
                seedSessionId = map["seed_session_id"] as String,
                schedule = schedule,
                triggers = (map[app.treelune.core.triggers.Triggers.KEY] as List<*>?)?.size ?: 0,
                emitEvent = (map[app.treelune.core.events.EventPoints.KEY] as Map<*, *>?)?.get("id") as String?,
                catchUp = catchUp?.get("limit") as String?,
                catchUpWindow = (catchUp?.get("window") as Number?)?.toLong(),
                // Without a schedule nothing is missed, and the setting is absent
                dismissOlderInstances = catchUp?.get("dismiss_older_instances") as Boolean? ?: false,
                providerId = map["provider_id"] as String?,
                identityId = map[RoutineSettings.IDENTITY_ID] as String?,
                isEnabled = map["is_enabled"] as Boolean,
                notifyEnd = map["notify_end"] as String,
                group = map["group"] as String?,
                createdAt = (map["created_at"] as Number).toLong(),
                updatedAt = (map["updated_at"] as Number).toLong(),
                lastExecutionId = map["last_execution_id"] as String?,
                executionHistory = (map["execution_history"] as List<*>).map { it as String },
                access = app.treelune.core.access.AccessMask.fromJson(org.json.JSONArray(map["access"] as List<*>).toString()),
                look = app.treelune.core.look.Looks.read(RoutineSettings.LOOK,
                    app.treelune.core.utils.JsonUtils.toJSONObject(map.entries.associate { it.key.toString() to it.value })),
                gridX = (map["grid_x"] as Number).toInt(),
                gridY = (map["grid_y"] as Number).toInt()
            )
        }
    }
}

/**
 * What a scheduled routine saved before catch-up settings existed is read as.
 *
 * No window and the most recent occurrence only: back from a long absence it starts once,
 * and the user adjusts from there. This is a reading rule for old data, applied by the
 * database migration and by the import of a backup that predates the field. It is not a
 * "needs configuring" state, which the scheduler and the screen would then have to carry.
 */
object LegacyCatchUp {
    val WINDOW_MINUTES: Long? = null
    const val DISMISS_OLDER = true
}
