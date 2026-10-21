package app.treelune.core.themes

import app.treelune.core.config.AppSettings
import org.json.JSONObject

/** Whether a theme's colours are drawn on a light ground with dark ink, or the other way round. */
enum class PaletteMode { LIGHT, DARK }

/**
 * The mode the user asks for: one of the two, or the phone's, which follows Android's dark theme
 * setting as it changes.
 */
enum class AppearanceMode { LIGHT, DARK, SYSTEM }

/**
 * The look the interface settings choose (AppSettings, category UI): a theme, the mode, how far
 * round the hue circle its colours are turned, the size step, one column or the grid, the
 * textures' intensity, the home screen's title.
 *
 * @property hueShift Degrees, 0 to 359, added to the hue of every colour of the theme but those
 *   that say something whatever the hue (states, tags): 0 shows the theme's own colours
 * @property oneColumn The grids shown in one column, a half of the screen taking its whole width
 *   (Grid.oneColumn), for a narrow screen; the places stored stay those of the grid
 * @property textureIntensity How strongly the motifs of tiles and page headers show
 *   (docs/THEMES.md, « What a theme is in the app »), for the whole app
 * @property homeTitle The app's name and mark shown on the home screen under its buttons, which
 *   stay without it
 */
data class Appearance(
    val theme: String,
    val mode: AppearanceMode,
    val hueShift: Int,
    val sizeStep: Int,
    val oneColumn: Boolean,
    val textureIntensity: TextureIntensity,
    val homeTitle: Boolean
) {
    companion object {
        /** The look [settings] choose, the interface settings as stored or as being edited; null while a choice is missing. */
        fun from(settings: JSONObject): Appearance? {
            val theme = settings.optString(AppSettings.UI_THEME).ifEmpty { return null }
            val mode = settings.optString(AppSettings.UI_MODE).ifEmpty { return null }
            if (!settings.has(AppSettings.UI_HUE_SHIFT) || !settings.has(AppSettings.UI_SIZE_STEP) || !settings.has(AppSettings.UI_ONE_COLUMN) || !settings.has(AppSettings.UI_HOME_TITLE)) return null
            val intensity = settings.optString(AppSettings.UI_TEXTURE_INTENSITY).ifEmpty { return null }
            return Appearance(theme, AppearanceMode.valueOf(mode), settings.getInt(AppSettings.UI_HUE_SHIFT), settings.getInt(AppSettings.UI_SIZE_STEP),
                settings.getBoolean(AppSettings.UI_ONE_COLUMN), TextureIntensity.valueOf(intensity), settings.getBoolean(AppSettings.UI_HOME_TITLE))
        }
    }
}
