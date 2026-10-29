package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the stored names of the routines to their v107 form, routine where they said automation,
 * as the screen always named them:
 * - the table automations becomes routines;
 * - a session's automation_id becomes routine_id, and its type AUTOMATION becomes ROUTINE_RUN,
 *   the run of a routine, which the routine itself (its SEED session) is not;
 * - a system message AUTOMATION_NO_COMMANDS becomes ROUTINE_NO_COMMANDS;
 * - a Task step's automation_id (the routine it runs) becomes routine_id;
 * - the AI limits' automation_max_autonomous_roundtrips and automation_max_data_chars become
 *   routine_max_autonomous_roundtrips and routine_max_data_chars;
 * - the name a Guide chapter keeps for the routine it follows, automation, becomes routine.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object RoutinesAtV107 {

    const val OLD_TABLE = "automations"
    const val NEW_TABLE = "routines"

    private const val OLD_SESSION_KEY = "automation_id"
    private const val NEW_SESSION_KEY = "routine_id"
    const val OLD_SESSION_TYPE = "AUTOMATION"
    const val NEW_SESSION_TYPE = "ROUTINE_RUN"
    private const val OLD_NO_COMMANDS = "AUTOMATION_NO_COMMANDS"
    private const val NEW_NO_COMMANDS = "ROUTINE_NO_COMMANDS"

    /** The AI limits' keys, former to current. */
    val LIMITS = mapOf(
        "automation_max_autonomous_roundtrips" to "routine_max_autonomous_roundtrips",
        "automation_max_data_chars" to "routine_max_data_chars"
    )

    /** The indices of the renamed table and column, former to current, as Room names them. */
    val INDICES = mapOf(
        "index_automations_zone_id" to "CREATE INDEX IF NOT EXISTS `index_routines_zone_id` ON `routines` (`zone_id`)",
        "index_automations_is_enabled" to "CREATE INDEX IF NOT EXISTS `index_routines_is_enabled` ON `routines` (`is_enabled`)",
        "index_automations_seed_session_id" to "CREATE INDEX IF NOT EXISTS `index_routines_seed_session_id` ON `routines` (`seed_session_id`)",
        "index_ai_sessions_automation_id" to "CREATE INDEX IF NOT EXISTS `index_ai_sessions_routine_id` ON `ai_sessions` (`routine_id`)"
    )

    /** A session's row (column -> value) at v107: its routine under routine_id, a run's type ROUTINE_RUN. */
    fun session(row: JSONObject): JSONObject {
        if (row.has(OLD_SESSION_KEY)) {
            row.put(NEW_SESSION_KEY, row.get(OLD_SESSION_KEY))
            row.remove(OLD_SESSION_KEY)
        }
        if (row.optString("type") == OLD_SESSION_TYPE) row.put("type", NEW_SESSION_TYPE)
        return row
    }

    /** A message's system message (its JSON) at v107, null when nothing in it changes. */
    fun systemMessage(json: String): String? {
        val message = JSONObject(json)
        if (message.optString("type") != OLD_NO_COMMANDS) return null
        return message.put("type", NEW_NO_COMMANDS).toString()
    }

    /** A message's row (column -> value) at v107. */
    fun message(row: JSONObject): JSONObject {
        row.optString("system_message_json").takeIf { it.isNotEmpty() && !row.isNull("system_message_json") }
            ?.let { json -> systemMessage(json)?.let { row.put("system_message_json", it) } }
        return row
    }

    /** A Task's steps (their JSON) at v107, null when no step names a routine. */
    fun steps(json: String): String? {
        val steps = JSONArray(json)
        return if (walk(steps)) steps.toString() else null
    }

    /** Each object under [value] naming a routine by automation_id, renamed in place; true when one was. */
    private fun walk(value: Any?): Boolean = when (value) {
        is JSONObject -> {
            var changed = false
            if (value.has(OLD_SESSION_KEY)) {
                value.put(NEW_SESSION_KEY, value.get(OLD_SESSION_KEY))
                value.remove(OLD_SESSION_KEY)
                changed = true
            }
            for (key in value.keys().asSequence().toList()) if (walk(value.opt(key))) changed = true
            changed
        }
        is JSONArray -> (0 until value.length()).map { walk(value.opt(it)) }.any { it }
        else -> false
    }

    /** A Task's row (column -> value) at v107. */
    fun task(row: JSONObject): JSONObject {
        row.optString("steps_json").takeIf { it.isNotEmpty() && !row.isNull("steps_json") }
            ?.let { json -> steps(json)?.let { row.put("steps_json", it) } }
        return row
    }

    /** The AI limits' settings at v107, renamed in place; true when a key was. */
    fun aiLimits(settings: JSONObject): Boolean {
        var changed = false
        for ((old, new) in LIMITS) if (settings.has(old)) {
            settings.put(new, settings.get(old))
            settings.remove(old)
            changed = true
        }
        return changed
    }

    /** The Guide's progress at v107, the name kept for a routine renamed in place; true when one was. */
    fun guide(settings: JSONObject): Boolean {
        val chapters = settings.optJSONArray("chapters") ?: return false
        var changed = false
        for (i in 0 until chapters.length()) {
            val kept = chapters.getJSONObject(i).optJSONArray("kept") ?: continue
            for (k in 0 until kept.length()) {
                val one = kept.getJSONObject(k)
                if (one.optString("name") == "automation") { one.put("name", "routine"); changed = true }
            }
        }
        return changed
    }

    /** A settings category's settings at v107, null when nothing in them changes. */
    fun settings(category: String, json: String): String? {
        val settings = JSONObject(json)
        val changed = when (category) {
            "ai_limits" -> aiLimits(settings)
            "guide" -> guide(settings)
            else -> false
        }
        return if (changed) settings.toString() else null
    }

    /** A settings category's row (column -> value) at v107. */
    fun settingsRow(row: JSONObject): JSONObject {
        val json = row.optString("settings").takeIf { it.isNotEmpty() && !row.isNull("settings") } ?: return row
        settings(row.optString("category"), json)?.let { row.put("settings", it) }
        return row
    }

    /** A copy the history keeps of a [table]'s row as it stands at v107, under the table's current name. */
    fun historyCopy(table: String, copy: JSONObject): JSONObject = when (table) {
        "ai_sessions" -> session(copy)
        "session_messages" -> message(copy)
        "tasks" -> task(copy)
        "app_settings_categories" -> settingsRow(copy)
        else -> copy
    }

    /**
     * Rewrites the backup document in place: the routines under their key, the sessions, messages
     * and Tasks. Its settings are not: the backup's settings go through the app config's own
     * chain (JsonTransformers.transformAppConfig, after the tables), which calls [settings] at v107.
     */
    fun backup(data: JSONObject) {
        data.optJSONArray(OLD_TABLE)?.let { routines ->
            data.put(NEW_TABLE, routines)
            data.remove(OLD_TABLE)
        }
        val rewrites = mapOf<String, (JSONObject) -> JSONObject>(
            "ai_sessions" to ::session, "session_messages" to ::message, "tasks" to ::task
        )
        for ((array, rewrite) in rewrites) {
            val rows = data.optJSONArray(array) ?: continue
            for (i in 0 until rows.length()) rewrite(rows.getJSONObject(i))
        }
    }
}
