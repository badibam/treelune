package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the AI providers to their v80 form, created by the user (docs/AI.md, « Provider abstraction »):
 * eight fixed ids, each kind in a standard and an economic variant, become the providers the user
 * had configured, each keeping its id, its name and its config, of the kind its id named; the
 * active one becomes the default. A variant never configured is gone, and a routine that named
 * one is left without a provider. A session keeps the id it named, a provider gone or not; a
 * seed's empty id becomes none.
 *
 * Shared by the database migration ([STATEMENTS], run in order) and the backup import ([backup]).
 */
object ProvidersAtV80 {

    /** The kinds, by the prefix of the fixed ids before v80: "claude_standard", "compatible_economic"… */
    val KINDS = listOf("claude", "openai", "deepseek", "compatible")

    /** The kind a fixed id before v80 named, null for an id of none. */
    fun kindOf(id: String): String? = KINDS.firstOrNull { id == it || id.startsWith(it + "_") }

    // The kind of the row's provider_id, in SQL, as kindOf reads it
    private val KIND_SQL = "CASE " + KINDS.joinToString(" ") { "WHEN provider_id = '$it' OR provider_id LIKE '${it}\\_%' ESCAPE '\\' THEN '$it'" } + " END"

    /**
     * The migration's statements: the providers' table in its new form, then the routines' and
     * the sessions', their provider null when it is none. A table is rebuilt to change a column's
     * nullability, which SQLite cannot alter; the foreign keys pointing at the sessions are off
     * while it runs (Room turns them on when the base opens, after the migrations).
     */
    val STATEMENTS = listOf(
        "CREATE TABLE `ai_provider_configs_new` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `config_json` TEXT NOT NULL, `is_default` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "INSERT INTO `ai_provider_configs_new` (id, kind, name, config_json, is_default, created_at, updated_at) " +
            "SELECT provider_id, kind, display_name, config_json, is_active, created_at, updated_at " +
            "FROM (SELECT *, $KIND_SQL AS kind FROM ai_provider_configs) WHERE is_configured = 1 AND kind IS NOT NULL",
        "DROP TABLE `ai_provider_configs`",
        "ALTER TABLE `ai_provider_configs_new` RENAME TO `ai_provider_configs`",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_provider_configs_name` ON `ai_provider_configs` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_ai_provider_configs_is_default` ON `ai_provider_configs` (`is_default`)",

        "CREATE TABLE `automations_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `zone_id` TEXT NOT NULL, `seed_session_id` TEXT NOT NULL, `schedule_json` TEXT, `trigger_ids_json` TEXT NOT NULL, `catch_up` TEXT, `catch_up_window` INTEGER, `dismiss_older_instances` INTEGER NOT NULL, `provider_id` TEXT, `is_enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `last_execution_id` TEXT, `execution_history_json` TEXT NOT NULL, `group` TEXT, `access_json` TEXT NOT NULL, `notify_end` TEXT NOT NULL, `identity_id` TEXT, PRIMARY KEY(`id`))",
        "INSERT INTO `automations_new` SELECT `id`, `name`, `zone_id`, `seed_session_id`, `schedule_json`, `trigger_ids_json`, `catch_up`, `catch_up_window`, `dismiss_older_instances`, " +
            "CASE WHEN `provider_id` IN (SELECT `id` FROM `ai_provider_configs`) THEN `provider_id` END, " +
            "`is_enabled`, `created_at`, `updated_at`, `last_execution_id`, `execution_history_json`, `group`, `access_json`, `notify_end`, `identity_id` FROM `automations`",
        "DROP TABLE `automations`",
        "ALTER TABLE `automations_new` RENAME TO `automations`",
        "CREATE INDEX IF NOT EXISTS `index_automations_zone_id` ON `automations` (`zone_id`)",
        "CREATE INDEX IF NOT EXISTS `index_automations_is_enabled` ON `automations` (`is_enabled`)",
        "CREATE INDEX IF NOT EXISTS `index_automations_seed_session_id` ON `automations` (`seed_session_id`)",
        // A routine without a provider is not on: it could not run
        "UPDATE `automations` SET `is_enabled` = 0 WHERE `provider_id` IS NULL",

        "CREATE TABLE `ai_sessions_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `validate_app` INTEGER NOT NULL, `validate_zones` INTEGER NOT NULL, `validate_data` INTEGER NOT NULL, `reasoning_thinking_off` INTEGER, `reasoning_effort` TEXT, `phase` TEXT NOT NULL, `total_roundtrips` INTEGER NOT NULL, `last_event_time` INTEGER NOT NULL, `last_user_interaction_time` INTEGER NOT NULL, `automation_id` TEXT, `scheduled_execution_time` INTEGER, `provider_id` TEXT, `provider_session_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `last_activity` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `end_reason` TEXT, `seen_at` INTEGER, `summary` TEXT, `app_state_snapshot` TEXT, `identity_id` TEXT, PRIMARY KEY(`id`))",
        "INSERT INTO `ai_sessions_new` SELECT `id`, `name`, `type`, `validate_app`, `validate_zones`, `validate_data`, `reasoning_thinking_off`, `reasoning_effort`, `phase`, `total_roundtrips`, `last_event_time`, `last_user_interaction_time`, `automation_id`, `scheduled_execution_time`, " +
            "NULLIF(`provider_id`, ''), `provider_session_id`, `created_at`, `last_activity`, `is_active`, `end_reason`, `seen_at`, `summary`, `app_state_snapshot`, `identity_id` FROM `ai_sessions`",
        "DROP TABLE `ai_sessions`",
        "ALTER TABLE `ai_sessions_new` RENAME TO `ai_sessions`",
        "CREATE INDEX IF NOT EXISTS `index_ai_sessions_is_active` ON `ai_sessions` (`is_active`)",
        "CREATE INDEX IF NOT EXISTS `index_ai_sessions_type` ON `ai_sessions` (`type`)",
        "CREATE INDEX IF NOT EXISTS `index_ai_sessions_last_activity` ON `ai_sessions` (`last_activity`)",
        "CREATE INDEX IF NOT EXISTS `index_ai_sessions_automation_id` ON `ai_sessions` (`automation_id`)",
        "CREATE INDEX IF NOT EXISTS `index_ai_sessions_phase` ON `ai_sessions` (`phase`)",
        "CREATE INDEX IF NOT EXISTS `index_ai_sessions_end_reason` ON `ai_sessions` (`end_reason`)"
    )

    /** The providers, routines and sessions of a backup's [data], each in its v80 form. */
    fun backup(data: JSONObject) {
        val kept = mutableSetOf<String>()
        data.optJSONArray("ai_provider_configs")?.let { configs ->
            val providers = JSONArray()
            for (i in 0 until configs.length()) {
                val old = configs.getJSONObject(i)
                val id = old.getString("provider_id")
                val kind = kindOf(id) ?: continue
                if (!old.getBoolean("is_configured")) continue
                kept += id
                providers.put(JSONObject()
                    .put("id", id)
                    .put("kind", kind)
                    .put("name", old.getString("display_name"))
                    .put("config_json", old.getString("config_json"))
                    .put("is_default", old.getBoolean("is_active"))
                    .put("created_at", old.getLong("created_at"))
                    .put("updated_at", old.getLong("updated_at")))
            }
            data.put("ai_provider_configs", providers)
        }
        data.optJSONArray("automations")?.let { routines ->
            for (i in 0 until routines.length()) {
                val routine = routines.getJSONObject(i)
                if (routine.optString("provider_id") !in kept) {
                    routine.put("provider_id", JSONObject.NULL)
                    routine.put("is_enabled", false)
                }
            }
        }
        data.optJSONArray("ai_sessions")?.let { sessions ->
            for (i in 0 until sessions.length()) {
                val session = sessions.getJSONObject(i)
                if (session.optString("provider_id").isEmpty()) session.put("provider_id", JSONObject.NULL)
            }
        }
    }
}
