package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * Brings the interface settings to their v75 form: the textures' intensity joins them, subtle
 * (docs/THEMES.md, « What a theme is in the app »). No thing has a texture yet, so nothing shows differently.
 *
 * Shared by the database migration and the backup import (JsonTransformers.transformAppConfig).
 */
object UiTextureIntensityAtV75 {

    /** [settings] of [category], with the textures' intensity. */
    fun rewrite(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.UI) return settings
        val out = JSONObject(settings.toString())
        if (!out.has(AppSettings.UI_TEXTURE_INTENSITY)) out.put(AppSettings.UI_TEXTURE_INTENSITY, app.treelune.core.themes.TextureIntensity.SUBTLE.name)
        return out
    }
}
