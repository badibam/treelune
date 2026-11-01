package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A Questionnaire's message to the AI leaves its config, in the database, the history's copies and a backup; the rest stays. */
class QuestionnaireAiMessageAtV112Test {

    private val config = """{"name": "Evening", "enabled": true, "ai_message": "Ask me the questions"}"""

    @Test
    fun `the message leaves a questionnaire's config, the rest stays`() {
        val next = JSONObject(QuestionnaireAiMessageAtV112.toolConfig("questionnaire", config)!!)
        assertFalse(next.has("ai_message"))
        assertEquals("Evening", next.getString("name"))
        assertEquals(true, next.getBoolean("enabled"))
    }

    @Test
    fun `another tool, or a questionnaire without the message, is left alone`() {
        assertNull(QuestionnaireAiMessageAtV112.toolConfig("notes", config))
        assertNull(QuestionnaireAiMessageAtV112.toolConfig("questionnaire", """{"name": "Evening"}"""))
    }

    @Test
    fun `a history copy and a backup lose it too`() {
        val copy = QuestionnaireAiMessageAtV112.historyCopy(JSONObject().put("tooltype", "questionnaire").put("config_json", config))
        assertFalse(JSONObject(copy.getString("config_json")).has("ai_message"))
        val backup = JSONObject().put("tool_instances", JSONArray().put(JSONObject().put("tooltype", "questionnaire").put("config_json", config)))
        QuestionnaireAiMessageAtV112.backup(backup)
        assertFalse(JSONObject(backup.getJSONArray("tool_instances").getJSONObject(0).getString("config_json")).has("ai_message"))
    }
}
