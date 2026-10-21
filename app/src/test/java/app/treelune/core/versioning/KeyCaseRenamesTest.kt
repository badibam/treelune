package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KeyCaseRenames.rename() is what migration 26 to 27 runs over every JSON column of the
 * database, and what the backup import runs over a whole backup file, then over the same columns
 * of its rows (backup), each a string the first pass does not reach. A mistake in it does not
 * fail loudly: it writes a document that still parses, with a key nothing reads any more.
 */
class KeyCaseRenamesTest {

    @Test
    fun `renames a known key at the top level`() {
        val result = JSONObject(KeyCaseRenames.rename("""{"toolInstanceId": "abc"}"""))
        assertEquals("abc", result.getString("tool_instance_id"))
        assertTrue(result.isNull("toolInstanceId"))
    }

    @Test
    fun `renames inside nested objects and arrays`() {
        val source = """
            {"dataCommands": [{"params": {"toolInstanceId": "abc", "startTime": 1}}]}
        """.trimIndent()

        val result = JSONObject(KeyCaseRenames.rename(source))
        val params = result.getJSONArray("data_commands").getJSONObject(0).getJSONObject("params")

        assertEquals("abc", params.getString("tool_instance_id"))
        assertEquals(1, params.getInt("start_time"))
    }

    @Test
    fun `leaves values alone even when a value reads like a key`() {
        val result = JSONObject(KeyCaseRenames.rename("""{"name": "toolInstanceId"}"""))
        assertEquals("toolInstanceId", result.getString("name"))
    }

    @Test
    fun `leaves a key the map does not know`() {
        val result = JSONObject(KeyCaseRenames.rename("""{"somethingElse": 1, "already_snake": 2}"""))
        assertEquals(1, result.getInt("somethingElse"))
        assertEquals(2, result.getInt("already_snake"))
    }

    @Test
    fun `renames the tool type key to the single word the project uses`() {
        val result = JSONObject(KeyCaseRenames.rename("""{"tool_type": "tracking"}"""))
        assertEquals("tracking", result.getString("tooltype"))
    }

    @Test
    fun `handles a document that is an array at its root`() {
        val result = JSONArray(KeyCaseRenames.rename("""[{"sessionId": "s1"}]"""))
        assertEquals("s1", result.getJSONObject(0).getString("session_id"))
    }

    @Test
    fun `returns a blank document untouched`() {
        assertEquals("", KeyCaseRenames.rename(""))
    }

    @Test
    fun `returns a value that is not a JSON document untouched`() {
        assertEquals("plain text", KeyCaseRenames.rename("plain text"))
    }

    @Test
    fun `converts a document wrapped in a markdown fence and keeps the wrapper`() {
        // How a model's reply is stored: exactly as it came, fence included.
        val stored = "```json\n{\"preText\": \"hello\"}\n```"

        val result = KeyCaseRenames.rename(stored)

        assertTrue("the fence is kept", result.startsWith("```json"))
        assertTrue("the fence is kept", result.trimEnd().endsWith("```"))
        assertTrue("the key is converted", result.contains("pre_text"))
        assertTrue("nothing of the old key is left", !result.contains("preText"))
    }

    @Test
    fun `leaves text alone when what it wraps is not JSON`() {
        val stored = "see {this} and nothing else"
        assertEquals(stored, KeyCaseRenames.rename(stored))
    }

    @Test
    fun `a backup's columns holding JSON are renamed, as the database migration renames them`() {
        val schedule = """{"pattern":{"type":"DailyMultiple","times":["04:10"]},"startDate":1}"""
        val limits = """{"chatMaxActionRetries":3}"""
        val data = JSONObject()
            .put("automations", JSONArray().put(JSONObject().put("id", "a").put("schedule_json", schedule)))
            .put("app_settings_categories", JSONArray().put(JSONObject().put("category", "ai_limits").put("settings", limits)))
            .put("tool_instances", JSONArray().put(JSONObject().put("id", "t").put("config_json", JSONObject.NULL)))
        KeyCaseRenames.backup(data) { table, column, reason -> throw AssertionError("$table.$column: $reason") }
        val renamed = JSONObject(data.getJSONArray("automations").getJSONObject(0).getString("schedule_json"))
        assertEquals(1, renamed.getInt("start_date"))
        assertTrue(renamed.isNull("startDate"))
        assertEquals(3, JSONObject(data.getJSONArray("app_settings_categories").getJSONObject(0).getString("settings")).getInt("chat_max_action_retries"))
        assertTrue("an empty column stays empty", data.getJSONArray("tool_instances").getJSONObject(0).isNull("config_json"))
    }

    @Test
    fun `maps every entry to a name that is already snake_case`() {
        val wrong = KeyCaseRenames.MAP.values.filter { it.any(Char::isUpperCase) }
        assertEquals(emptyList<String>(), wrong)
    }
}
