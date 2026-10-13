package app.treelune.core.ai.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers what a conversation's own reasoning sends: the provider's config with the
 * conversation's two settings in place of its own, the rest untouched.
 */
class SessionReasoningTest {

    private val providerConfig = JSONObject()
        .put("model", "a-model")
        .put("api_key", "key")
        .put(SessionReasoning.KEY_THINKING_OFF, true)
        .put(SessionReasoning.KEY_EFFORT, "low")

    /** Its effort replaces the provider's, and thinking on drops the provider's thinking off. */
    @Test
    fun applyTo_replacesBothSettings() {
        val sent = SessionReasoning(thinkingOff = false, effort = "high").applyTo(providerConfig)

        assertEquals("high", sent.getString(SessionReasoning.KEY_EFFORT))
        assertFalse(sent.has(SessionReasoning.KEY_THINKING_OFF))
        assertEquals("a-model", sent.getString("model"))
        assertEquals("key", sent.getString("api_key"))
    }

    /** No effort is the model's default, not the provider's effort. */
    @Test
    fun applyTo_withoutEffort_leavesTheModelsDefault() {
        val sent = SessionReasoning(thinkingOff = false, effort = null).applyTo(providerConfig)

        assertFalse(sent.has(SessionReasoning.KEY_EFFORT))
    }

    /** The provider's config itself is not changed. */
    @Test
    fun applyTo_keepsTheProvidersConfig() {
        SessionReasoning(thinkingOff = false, effort = "high").applyTo(providerConfig)

        assertEquals("low", providerConfig.getString(SessionReasoning.KEY_EFFORT))
    }

    /** Read back as the service writes it; none, the provider's config is followed. */
    @Test
    fun fromMap_readsWhatToMapWrites() {
        val reasoning = SessionReasoning(thinkingOff = true, effort = "medium")

        assertEquals(reasoning, SessionReasoning.fromMap(reasoning.toMap()))
        assertNull(SessionReasoning.fromMap(null))
    }
}
