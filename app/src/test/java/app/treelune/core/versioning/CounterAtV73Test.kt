package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** Covers the v73 rewrite: a counter tracking tool becomes a numeric one that counts the same, both ways. */
class CounterAtV73Test {

    private fun JSONArray.shortcuts() = (0 until length()).map { getJSONObject(it).let { item -> item.getString("name") to item.get("value").toString().toDouble() } }

    @Test
    fun aCounterBecomesNumericWithItsShortcutsBothWays() {
        val config = JSONObject("""{ "type": "counter", "value": { "decimals": 0, "unit": "glasses" }, "items": [ { "name": "Glass", "value": 1 }, { "name": "Half", "value": 0.5 } ] }""")

        val next = CounterAtV73.config("tracking", config)

        assertEquals("numeric", next.getString("type"))
        assertEquals(listOf("Glass" to 1.0, "− Glass" to -1.0, "Half" to 0.5, "− Half" to -0.5), next.getJSONArray("items").shortcuts())
        assertEquals("glasses", next.getJSONArray("items").getJSONObject(1).getString("unit"))
        assertEquals(listOf("glasses"), next.getJSONArray("units").let { u -> (0 until u.length()).map { u.getString(it) } })
        assertFalse(next.getJSONObject("value").has("unit"))
        // The half needs one decimal
        assertEquals(1, next.getJSONObject("value").getInt("decimals"))
    }

    @Test
    fun aCounterThatCouldNotGoDownHasNoMinusTwins() {
        val config = JSONObject("""{ "type": "counter", "allow_decrement": false, "items": [ { "name": "Cup" } ] }""")

        val next = CounterAtV73.config("tracking", config)

        assertFalse(next.has("allow_decrement"))
        assertFalse(next.has("units"))
        assertEquals(listOf("Cup" to 1.0), next.getJSONArray("items").shortcuts())
        assertEquals(0, next.getJSONObject("value").getInt("decimals"))
    }

    @Test
    fun aNegativeOrZeroStepCountsByItsSize() {
        val config = JSONObject("""{ "type": "counter", "items": [ { "name": "Down", "value": -2 }, { "name": "Zero", "value": 0 } ] }""")

        assertEquals(listOf("Down" to 2.0, "− Down" to -2.0, "Zero" to 1.0, "− Zero" to -1.0),
            CounterAtV73.config("tracking", config).getJSONArray("items").shortcuts())
    }

    @Test
    fun anotherToolIsLeftAsItIs() {
        val numeric = JSONObject("""{ "type": "numeric", "value": { "decimals": 1 } }""")
        assertSame(numeric, CounterAtV73.config("tracking", numeric))
        val notes = JSONObject("""{ "type": "counter" }""")
        assertSame(notes, CounterAtV73.config("notes", notes))
    }

    @Test
    fun theBackupsEntriesTakeTheCountersUnit() {
        val backup = JSONObject().put("tool_instances", JSONArray().put(JSONObject()
            .put("id", "t").put("tooltype", "tracking")
            .put("config_json", """{ "type": "counter", "value": { "unit": "glasses" }, "items": [ { "name": "Glass", "value": 1 } ] }""")))
            .put("tool_data", JSONArray()
                .put(JSONObject().put("tool_instance_id", "t").put("data", """{ "value": 1 }"""))
                .put(JSONObject().put("tool_instance_id", "other").put("data", """{ "value": 3 }""")))

        CounterAtV73.backup(backup)

        val entries = backup.getJSONArray("tool_data")
        assertEquals("glasses", JSONObject(entries.getJSONObject(0).getString("data")).getString("unit"))
        assertFalse(JSONObject(entries.getJSONObject(1).getString("data")).has("unit"))
        assertEquals("numeric", JSONObject(backup.getJSONArray("tool_instances").getJSONObject(0).getString("config_json")).getString("type"))
    }
}
