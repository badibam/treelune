package app.treelune.core.versioning

import org.json.JSONObject

/**
 * Brings stored ai_limits settings to their v98 form: the cap of runs per chain joins them
 * (docs/CORE.md, « Chains »). A stored value is kept; a missing one gets the value decided at
 * v98, 50 runs. The single place that says so, shared by the database migration and the backup
 * import.
 *
 * Written here as decided at v98, not read from AILimitsConfig, whose default may change later
 * without changing what this migration did.
 */
object AILimitsAtV98 {

    private const val KEY_CHAIN = "chain_max_runs"

    /** @return the settings in their v98 form; [settings] is left untouched */
    fun rewrite(settings: JSONObject): JSONObject = JSONObject(settings.toString()).apply {
        if (!has(KEY_CHAIN)) put(KEY_CHAIN, 50)
    }
}
