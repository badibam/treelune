package app.treelune.core.versioning

import app.treelune.core.ai.domain.AILimitsConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers the rewrite the 97 -> 98 migration and the backup import both apply to ai_limits: the
 * cap of runs per chain joins the others, and nothing stored is changed.
 */
class AILimitsAtV98Test {

    private val v97 = JSONObject()
        .put("chat_max_autonomous_roundtrips", 10).put("automation_max_autonomous_roundtrips", 20)
        .put("chat_max_data_chars", 15_000).put("automation_max_data_chars", 100_000)
        .put("always_send_max_chars", 15_000)

    @Test
    fun v97Settings_getTheCap_andReadBackWhole() {
        val rewritten = AILimitsAtV98.rewrite(v97)

        assertEquals(50, rewritten.getInt("chain_max_runs"))
        assertEquals(50, AILimitsConfig.fromSettingsJson(rewritten).chainMaxRuns)
        assertFalse(v97.has("chain_max_runs"))
    }

    @Test
    fun aStoredCap_isKept() {
        val rewritten = AILimitsAtV98.rewrite(JSONObject(v97.toString()).put("chain_max_runs", 200))

        assertEquals(200, rewritten.getInt("chain_max_runs"))
    }

    @Test
    fun theBackupImport_appliesIt_fromV97() {
        val imported = JSONObject(JsonTransformers.transformAppConfig(v97.toString(), "ai_limits", 97, 98))

        assertEquals(50, imported.getInt("chain_max_runs"))
    }
}
