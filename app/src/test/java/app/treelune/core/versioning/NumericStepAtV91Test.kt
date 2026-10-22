package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A number's step, which nothing read, leaves every field that held it; a scale's stays. */
class NumericStepAtV91Test {

    @Test
    fun `a tool's users' fields and a numeric tracking's value lose their step, a scale keeps its own`() {
        val config = """{"type": "numeric", "value": {"decimals": 1, "step": 0.5}, "extra_fields": [
            {"name": "n", "display_name": "N", "type": "NUMERIC", "config": {"decimals": 0, "step": 2}},
            {"name": "s", "display_name": "S", "type": "SCALE", "config": {"min": 1, "max": 5, "step": 1}}]}"""
        val next = JSONObject(NumericStepAtV91.toolConfig("tracking", config)!!)
        assertEquals("""{"decimals":1}""", next.getJSONObject("value").toString())
        val fields = next.getJSONArray("extra_fields")
        assertEquals("""{"decimals":0}""", fields.getJSONObject(0).getJSONObject("config").toString())
        assertEquals("""{"min":1,"max":5,"step":1}""", fields.getJSONObject(1).getJSONObject("config").toString())
    }

    @Test
    fun `a scale tracking's value and a config without a step are left alone`() {
        assertNull(NumericStepAtV91.toolConfig("tracking", """{"type": "scale", "value": {"min": 1, "max": 5, "step": 1}}"""))
        assertNull(NumericStepAtV91.toolConfig("notes", """{"name": "n"}"""))
    }

    @Test
    fun `a goal's entered criterion is found where it is held`() {
        val config = """{"criteria": [{"key": "k", "name": "Km", "field": {"type": "NUMERIC", "config": {"decimals": 1, "step": 0.1}}}]}"""
        val next = JSONObject(NumericStepAtV91.toolConfig("goal", config)!!)
        assertEquals("""{"decimals":1}""", next.getJSONArray("criteria").getJSONObject(0).getJSONObject("field").getJSONObject("config").toString())
    }
}
