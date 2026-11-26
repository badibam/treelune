package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** What v124 promises: each variable a list of one value of key `value`, and whatever read it naming that key. */
class VariableValuesAtV124Test {

    @Test
    fun `a constant and a formula each become their one value, the series kept, a formula's variables given the key`() {
        val constant = JSONObject(VariableValuesAtV124.definition("""{"kind": "CONSTANT", "value": 2100, "field": {"type": "NUMERIC"}, "series": {"unit": "DAY"}}""")!!)
        val value = constant.getJSONArray("values").getJSONObject(0)
        assertEquals("value", value.getString("key"))
        assertEquals(2100, value.getInt("constant"))
        assertEquals("NUMERIC", value.getJSONObject("field").getString("type"))
        assertEquals("DAY", constant.getJSONObject("series").getString("unit"))
        assertFalse(constant.has("kind"))

        val formula = JSONObject(VariableValuesAtV124.definition(
            """{"kind": "FORMULA", "formula": "{var:a-1} - t", "terms": {"t": {"variable": "b-2"}}}""")!!)
        assertEquals("{var:a-1.value} - t", formula.getJSONArray("values").getJSONObject(0).getString("formula"))
        assertEquals("value", formula.getJSONObject("terms").getJSONObject("t").getString("value"))
        assertNull(VariableValuesAtV124.definition(formula.toString()))
    }

    @Test
    fun `a variable term anywhere names its value, a variable target left as it is`() {
        val config = JSONObject(VariableValuesAtV124.json(
            """{"columns": [{"term": {"variable": "v1"}}], "selection": {"target": {"kind": "VARIABLE", "id": "v1"}, "view": "series"}}""")!!)
        assertEquals("value", config.getJSONArray("columns").getJSONObject(0).getJSONObject("term").getString("value"))
        assertFalse(config.getJSONObject("selection").getJSONObject("target").has("value"))
        assertNull(VariableValuesAtV124.json("""{"name": "nothing to change"}"""))
    }
}
