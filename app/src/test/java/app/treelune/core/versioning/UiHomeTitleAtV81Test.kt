package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interface settings at v81: an install or a backup from before shows the home screen's title as it did. */
class UiHomeTitleAtV81Test {

    @Test
    fun theInterfaceSettings_gainTheHomeTitle_on() {
        val v80 = JSONObject().put(AppSettings.UI_THEME, "ink").put(AppSettings.UI_ONE_COLUMN, true)
        val rewritten = UiHomeTitleAtV81.rewrite(AppSettingCategories.UI, v80)
        assertTrue(rewritten.getBoolean(AppSettings.UI_HOME_TITLE))
        assertEquals("ink", rewritten.getString(AppSettings.UI_THEME))
    }

    @Test
    fun aSettingAlreadyThere_andOtherCategories_stayAsTheyAre() {
        val set = JSONObject().put(AppSettings.UI_HOME_TITLE, false)
        assertFalse(UiHomeTitleAtV81.rewrite(AppSettingCategories.UI, set).getBoolean(AppSettings.UI_HOME_TITLE))
        assertFalse(UiHomeTitleAtV81.rewrite(AppSettingCategories.FORMAT, JSONObject().put("x", 1)).has(AppSettings.UI_HOME_TITLE))
    }
}
