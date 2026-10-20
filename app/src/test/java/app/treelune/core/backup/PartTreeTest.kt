package app.treelune.core.backup

import app.treelune.core.backup.PartElement.Kind
import app.treelune.core.ui.exports.Ticks
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the export's screen shows and does (docs/design/share.md, « Les écrans »): the zones in
 * the home screen's order under their group, their elements under their tool group, the entries
 * counted; a zone ticked takes all it holds, an element ticked takes its zone, a zone unticked
 * lets all of it go.
 */
class PartTreeTest {

    private fun tool(id: String, zone: String, name: String, group: String?, x: Int, y: Int) = JSONObject()
        .put("id", id).put("zone_id", zone).put("tooltype", "tracking").put("grid_x", x).put("grid_y", y)
        .put("config_json", JSONObject().put("name", name).apply { group?.let { put("group", it) } }.toString())

    /**
     * Home groups « Perso » then « Sport »: Course (Sport), Cuisine (Perso, tool groups Repas and
     * Analyse: Meals and Foods in Repas, Chart ungrouped, a variable in Analyse, a routine), Loose
     * (no group).
     */
    private fun backup(): JSONObject = JSONObject()
        .put("metadata", JSONObject())
        .put("data", JSONObject()
            .put("app_settings_categories", JSONArray().put(JSONObject().put("category", "main_screen")
                .put("settings", JSONObject().put("zone_groups", JSONArray(listOf("Perso", "Sport"))).toString())))
            .put("zones", JSONArray()
                .put(JSONObject().put("id", "loose").put("name", "Loose").put("grid_x", 0).put("grid_y", 0))
                .put(JSONObject().put("id", "course").put("name", "Course").put("group", "Sport").put("grid_x", 0).put("grid_y", 0))
                .put(JSONObject().put("id", "cuisine").put("name", "Cuisine").put("group", "Perso").put("grid_x", 0).put("grid_y", 0)
                    .put("tool_groups", JSONArray(listOf("Repas", "Analyse")).toString())))
            .put("tool_instances", JSONArray()
                .put(tool("chart", "cuisine", "Calories", null, 0, 0))
                .put(tool("foods", "cuisine", "Aliments", "Repas", 2, 0))
                .put(tool("meals", "cuisine", "Repas", "Repas", 0, 0)))
            .put("tool_data", JSONArray()
                .put(JSONObject().put("id", "e1").put("tool_instance_id", "meals"))
                .put(JSONObject().put("id", "e2").put("tool_instance_id", "meals")))
            .put("variables", JSONArray().put(JSONObject().put("id", "v1").put("zone_id", "cuisine").put("name", "objectif").put("group", "Analyse").put("order_index", 0)))
            .put("automations", JSONArray().put(JSONObject().put("id", "a1").put("zone_id", "cuisine").put("name", "Bilan").put("seed_session_id", "seed")
                .put("provider_id", JSONObject.NULL).put("identity_id", JSONObject.NULL))))

    @Test
    fun `the zones follow the home screen's groups, their elements their tool groups`() {
        val tree = PartTree.of(backup())
        assertEquals(listOf("cuisine", "course", "loose"), tree.zones.map { it.id })
        val cuisine = tree.zones.first()
        assertEquals(listOf("meals", "foods", "v1", "chart", "a1"), cuisine.elements.map { it.id })
        assertEquals(listOf("Repas", "Repas", "Analyse", null, null), cuisine.elements.map { it.group })
        assertEquals(2, cuisine.elements.first { it.id == "meals" }.entries)
    }

    @Test
    fun `the tree reads back from what the service answers`() {
        val tree = PartTree.of(backup())
        assertEquals(tree, PartTree.read(JSONObject(tree.toJson().toString())))
    }

    @Test
    fun `a zone ticked takes all it holds, its tools without their entries, and unticked lets it go`() {
        val cuisine = PartTree.of(backup()).zones.first()
        val nothing = PartSelection(emptySet(), emptyMap(), emptySet(), emptySet())
        val all = Ticks.zone(nothing, cuisine, true)
        assertEquals(setOf("cuisine"), all.zones)
        assertEquals(mapOf("meals" to false, "foods" to false, "chart" to false), all.tools)
        assertEquals(setOf("v1"), all.variables)
        assertEquals(setOf("a1"), all.automations)
        assertEquals(nothing, Ticks.zone(all, cuisine, false))
    }

    @Test
    fun `an element ticked takes its zone, unticked leaves it`() {
        val cuisine = PartTree.of(backup()).zones.first()
        val meals = cuisine.elements.first { it.id == "meals" }
        val one = Ticks.element(PartSelection(emptySet(), emptyMap(), emptySet(), emptySet()), cuisine, meals, true)
        assertTrue("cuisine" in one.zones)
        val none = Ticks.element(one, cuisine, meals, false)
        assertTrue("cuisine" in none.zones)
        assertFalse("meals" in none.tools)
        assertEquals(Kind.TOOL, meals.kind)
    }
}
