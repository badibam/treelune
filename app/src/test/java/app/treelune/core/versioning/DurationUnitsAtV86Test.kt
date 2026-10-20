package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A duration field keeps what it shows once its precision and form become its units: the same
 * boxes to fill, the same text.
 */
class DurationUnitsAtV86Test {

    private fun units(config: String) = DurationUnitsAtV86.units(JSONObject(config))

    @Test
    fun `several units run from the hour down to the precision`() {
        assertEquals("HOUR_MINUTE", units("""{"precision": "MINUTE", "form": "COMPOSED"}"""))
        assertEquals("HOUR_MINUTE_SECOND", units("""{"precision": "SECOND"}"""))
        assertEquals("HOUR", units("""{"precision": "HOUR", "form": "COMPOSED"}"""))
        // Days were only ever alone
        assertEquals("DAY", units("""{"precision": "DAY", "form": "COMPOSED"}"""))
        // The form alone takes the former default precision, the minute
        assertEquals("HOUR_MINUTE", units("""{"form": "COMPOSED"}"""))
    }

    @Test
    fun `one unit is the precision alone`() {
        assertEquals("DAY", units("""{"precision": "DAY", "form": "SINGLE"}"""))
        assertEquals("MINUTE", units("""{"precision": "MINUTE", "form": "SINGLE"}"""))
    }

    @Test
    fun `the millisecond goes to the second`() {
        assertEquals("HOUR_MINUTE_SECOND", units("""{"precision": "MILLISECOND"}"""))
        assertEquals("SECOND", units("""{"precision": "MILLISECOND", "form": "SINGLE"}"""))
    }

    @Test
    fun `settings without either are left alone`() {
        assertNull(units("""{}"""))
    }

    @Test
    fun `a tool's users' fields and a tracking's value are rewritten, nothing else`() {
        val config = """{"type": "timer", "value": {"precision": "SECOND"}, "extra_fields": [
            {"name": "d", "display_name": "D", "type": "DURATION", "config": {"precision": "MINUTE", "form": "SINGLE"}},
            {"name": "n", "display_name": "N", "type": "NUMERIC", "config": {"decimals": 2}}]}"""
        val next = JSONObject(DurationUnitsAtV86.toolConfig("tracking", config)!!)
        assertEquals("""{"units":"HOUR_MINUTE_SECOND"}""", next.getJSONObject("value").toString())
        val fields = next.getJSONArray("extra_fields")
        assertEquals("""{"units":"MINUTE"}""", fields.getJSONObject(0).getJSONObject("config").toString())
        assertEquals("""{"decimals":2}""", fields.getJSONObject(1).getJSONObject("config").toString())
        assertNull(DurationUnitsAtV86.toolConfig("notes", """{"name": "n"}"""))
    }

    @Test
    fun `a variable's value field is found where it is held`() {
        val definition = """{"kind": "constant", "value": 60000, "field": {"type": "DURATION", "config": {"precision": "MINUTE"}}}"""
        val next = JSONObject(DurationUnitsAtV86.text(definition)!!)
        assertEquals("HOUR_MINUTE", next.getJSONObject("field").getJSONObject("config").getString("units"))
        assertFalse(next.getJSONObject("field").getJSONObject("config").has("precision"))
    }

    @Test
    fun `a model's answer that is not JSON is left as it is, a backup holding one imported all the same`() {
        assertNull(DurationUnitsAtV86.messageText("Je crée la zone.\n```json\n{\"pre_text\": \"…\"}\n```"))
        assertNull(DurationUnitsAtV86.messageText("""{"pre_text": "coupé"""))
        val backup = JSONObject().put("session_messages", org.json.JSONArray().put(JSONObject()
            .put("ai_message_json", "Voici la réponse")
            .put("ai_message_parsed_json", """{"communication_module": {"fields": [{"type": "DURATION", "config": {"precision": "MINUTE"}}]}}""")))
        DurationUnitsAtV86.backup(backup)
        val message = backup.getJSONArray("session_messages").getJSONObject(0)
        assertEquals("Voici la réponse", message.getString("ai_message_json"))
        assertEquals("HOUR_MINUTE", JSONObject(message.getString("ai_message_parsed_json")).getJSONObject("communication_module")
            .getJSONArray("fields").getJSONObject(0).getJSONObject("config").getString("units"))
    }
}
