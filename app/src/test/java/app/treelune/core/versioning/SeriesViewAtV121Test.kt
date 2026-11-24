package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** What v121 promises of the stored selections: a variable's selection names its series as a view, nothing else changes. */
class SeriesViewAtV121Test {

    @Test
    fun `a selection of a variable names its series view, at any depth`() {
        val json = """{"layers": [{"y": {"reading": {"selection": {"target": {"kind": "VARIABLE", "id": "v1"}, "sort": []}, "reduction": "COUNT"}}}]}"""
        val out = JSONObject(SeriesViewAtV121.json(json)!!)
        val selection = out.getJSONArray("layers").getJSONObject(0).getJSONObject("y").getJSONObject("reading").getJSONObject("selection")
        assertEquals("series", selection.getString("view"))
    }

    @Test
    fun `a tool's selection and one already naming its view are left as they are`() {
        assertNull(SeriesViewAtV121.json("""{"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}}"""))
        assertNull(SeriesViewAtV121.json("""{"selection": {"target": {"kind": "VARIABLE", "id": "v1"}, "view": "series"}}"""))
    }

    @Test
    fun `a backup's readouts and variables are rewritten, other tables untouched`() {
        val data = JSONObject("""{
            "readouts": [{"id": "r", "settings_json": "{\"values\": [{\"value\": {\"reading\": {\"selection\": {\"target\": {\"kind\": \"VARIABLE\", \"id\": \"v\"}}}}}]}"}],
            "zones": [{"id": "z", "selection": {"target": {"kind": "VARIABLE", "id": "v"}}}]
        }""")
        SeriesViewAtV121.backup(data)
        assertEquals(true, data.getJSONArray("readouts").getJSONObject(0).getString("settings_json").contains("\"view\":\"series\""))
        assertFalse(data.getJSONArray("zones").getJSONObject(0).getJSONObject("selection").has("view"))
    }
}
