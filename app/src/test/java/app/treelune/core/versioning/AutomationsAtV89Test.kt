package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the v89 migration promises (AutomationsAtV89): a routine becomes a tile of its section,
 * in EXTENDED, the card it was, under the section's tools and variables, one under the other in
 * the order they were made; its icon `bot`.
 */
class AutomationsAtV89Test {

    @Test
    fun routinesGoUnderTheirSectionsToolsAndVariables_inTheOrderMade() {
        val data = JSONObject()
            .put("tool_instances", JSONArray().put(JSONObject().put("zone_id", "z").put("grid_y", 0)
                .put("config_json", JSONObject().put("display_mode", "LINE").put("group", "Evening").toString())))
            .put("variables", JSONArray().put(JSONObject().put("zone_id", "z").put("group", "Evening").put("grid_y", 1).put("display_mode", "CONDENSED")))
            .put("automations", JSONArray()
                .put(JSONObject().put("id", "late").put("zone_id", "z").put("group", "Evening").put("created_at", 20))
                .put(JSONObject().put("id", "early").put("zone_id", "z").put("group", "Evening").put("created_at", 10))
                .put(JSONObject().put("id", "loose").put("zone_id", "z").put("created_at", 5)))
        AutomationsAtV89.backup(data)
        val byId = (0 until data.getJSONArray("automations").length()).map { data.getJSONArray("automations").getJSONObject(it) }.associateBy { it.getString("id") }
        // The variable, CONDENSED from row 1, ends at row 3; each routine two rows tall
        assertEquals(3, byId.getValue("early").getInt("grid_y"))
        assertEquals(5, byId.getValue("late").getInt("grid_y"))
        assertEquals(0, byId.getValue("loose").getInt("grid_y"))
        assertEquals("EXTENDED", byId.getValue("early").getString("display_mode"))
        assertEquals("bot", byId.getValue("early").getString("icon_name"))
        assertEquals(0, byId.getValue("early").getInt("grid_x"))
    }
}
