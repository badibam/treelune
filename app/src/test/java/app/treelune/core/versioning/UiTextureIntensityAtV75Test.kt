package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The interface settings at v75: an install or a backup from before gets the textures' intensity, subtle. */
class UiTextureIntensityAtV75Test {

    @Test
    fun theInterfaceSettings_gainTheIntensity_subtle() {
        val v74 = JSONObject().put(AppSettings.UI_THEME, "cosy").put(AppSettings.UI_ONE_COLUMN, false)
        val rewritten = UiTextureIntensityAtV75.rewrite(AppSettingCategories.UI, v74)
        assertEquals("SUBTLE", rewritten.getString(AppSettings.UI_TEXTURE_INTENSITY))
        assertEquals("cosy", rewritten.getString(AppSettings.UI_THEME))
    }

    @Test
    fun aSettingAlreadyThere_andOtherCategories_stayAsTheyAre() {
        val set = JSONObject().put(AppSettings.UI_TEXTURE_INTENSITY, "MARKED")
        assertEquals("MARKED", UiTextureIntensityAtV75.rewrite(AppSettingCategories.UI, set).getString(AppSettings.UI_TEXTURE_INTENSITY))
        assertFalse(UiTextureIntensityAtV75.rewrite(AppSettingCategories.FORMAT, JSONObject()).has(AppSettings.UI_TEXTURE_INTENSITY))
    }
}
