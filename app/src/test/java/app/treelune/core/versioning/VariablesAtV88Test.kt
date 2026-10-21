package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What the v88 migration promises (VariablesAtV88): a variable becomes a tile of its section,
 * one per row in its card's order, under the section's tools; its label is its name, which the
 * card showed; its icon `variable`, its mode LINE; `order_index` goes.
 */
class VariablesAtV88Test {

    private fun tool(zone: String, group: String?, y: Int, mode: String) = JSONObject()
        .put("zone_id", zone).put("grid_y", y)
        .put("config_json", JSONObject().put("display_mode", mode).apply { group?.let { put("group", it) } }.toString())

    private fun variable(id: String, zone: String, group: String?, order: Int) = JSONObject()
        .put("id", id).put("zone_id", zone).put("name", "n_$id").put("order_index", order).apply { group?.let { put("group", it) } }

    @Test
    fun variablesGoUnderTheirSectionsTools_inTheirOrder() {
        val data = JSONObject()
            .put("tool_instances", JSONArray()
                .put(tool("z", "Meals", 0, "LINE"))
                .put(tool("z", "Meals", 1, "CONDENSED"))
                .put(tool("z", null, 0, "LINE")))
            .put("variables", JSONArray()
                .put(variable("b", "z", "Meals", 1))
                .put(variable("a", "z", "Meals", 0))
                .put(variable("c", "z", null, 0))
                .put(variable("d", "y", null, 0)))
        VariablesAtV88.backup(data)
        val byId = (0 until data.getJSONArray("variables").length()).map { data.getJSONArray("variables").getJSONObject(it) }.associateBy { it.getString("id") }
        // Meals ends under its CONDENSED tool, two rows tall from row 1
        assertEquals(3, byId.getValue("a").getInt("grid_y"))
        assertEquals(4, byId.getValue("b").getInt("grid_y"))
        assertEquals(1, byId.getValue("c").getInt("grid_y"))
        // A zone without tools starts at the top
        assertEquals(0, byId.getValue("d").getInt("grid_y"))
        val a = byId.getValue("a")
        assertEquals("n_a", a.getString("label"))
        assertEquals("variable", a.getString("icon_name"))
        assertEquals("LINE", a.getString("display_mode"))
        assertEquals(0, a.getInt("grid_x"))
        assertFalse(a.has("order_index"))
    }
}
