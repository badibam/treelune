package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The external access settings at v84: an install or a backup from before gains the connector's own data threshold. */
class ExternalAccessDataAtV84Test {

    @Test
    fun theExternalAccessSettings_gainTheConnectorsThreshold() {
        val v83 = JSONObject().put(AppSettings.ACCESS_MODE, AppSettings.ACCESS_MODE_RELAY)
        val rewritten = ExternalAccessDataAtV84.rewrite(AppSettingCategories.EXTERNAL_ACCESS, v83)
        assertEquals(200_000, rewritten.getInt(AppSettings.MCP_MAX_DATA_CHARS))
        assertEquals(AppSettings.ACCESS_MODE_RELAY, rewritten.getString(AppSettings.ACCESS_MODE))
    }

    @Test
    fun aThresholdAlreadyThere_andOtherCategories_stayAsTheyAre() {
        val set = JSONObject().put(AppSettings.MCP_MAX_DATA_CHARS, 50_000)
        assertEquals(50_000, ExternalAccessDataAtV84.rewrite(AppSettingCategories.EXTERNAL_ACCESS, set).getInt(AppSettings.MCP_MAX_DATA_CHARS))
        assertFalse(ExternalAccessDataAtV84.rewrite(AppSettingCategories.UI, JSONObject()).has(AppSettings.MCP_MAX_DATA_CHARS))
    }
}
