package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the external access settings to their v83 form: the connector gains its own data
 * threshold, 200 000 characters, where it read the chat's.
 *
 * Written here as decided at v83, not read from AppSettings, whose default may change later
 * without changing what this migration did. Shared by the database migration and the backup
 * import (JsonTransformers.transformAppConfig).
 */
object ExternalAccessDataAtV83 {

    /** [settings] of [category], with the connector's data threshold. */
    fun rewrite(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.EXTERNAL_ACCESS) return settings
        val out = JSONObject(settings.toString())
        if (!out.has(AppSettings.MCP_MAX_DATA_CHARS)) out.put(AppSettings.MCP_MAX_DATA_CHARS, 200_000)
        return out
    }
}
