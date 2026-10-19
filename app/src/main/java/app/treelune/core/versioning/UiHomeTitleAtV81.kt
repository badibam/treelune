package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the interface settings to their v81 form: « Home screen title » joins them, on, the home
 * screen showing the app's name and mark as it did.
 *
 * Shared by the database migration and the backup import (JsonTransformers.transformAppConfig).
 */
object UiHomeTitleAtV81 {

    /** [settings] of [category], with the home screen title's setting. */
    fun rewrite(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI) return settings
        val out = JSONObject(settings.toString())
        if (!out.has(AppSettings.UI_HOME_TITLE)) out.put(AppSettings.UI_HOME_TITLE, true)
        return out
    }
}
