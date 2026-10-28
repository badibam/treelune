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
 * What the export's screen shows and does (docs/DATA.md, « A part of the app »): the zones in
 * the home screen's order under their group, their elements under their tool group, the entries
 * counted, the identities a zone and its routines speak as at its top; a zone ticked takes all it
 * holds, its identities included, an element ticked takes its zone, a routine its identity, a
 * zone unticked lets all of it go but an identity another zone ticked speaks as.
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
            .put("variables", JSONArray().put(JSONObject().put("id", "v1").put("zone_id", "cuisine").put("name", "objectif").put("label", "Objectif").put("group", "Analyse").put("grid_x", 0).put("grid_y", 0)))
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
        val tree = PartTree.of(backup())
        val cuisine = tree.zones.first()
        val nothing = PartSelection(emptySet(), emptyMap(), emptySet(), emptySet(), emptySet())
        val all = Ticks.zone(nothing, tree, cuisine, true)
        assertEquals(setOf("cuisine"), all.zones)
        assertEquals(mapOf("meals" to false, "foods" to false, "chart" to false), all.tools)
        assertEquals(setOf("v1"), all.variables)
        assertEquals(setOf("a1"), all.routines)
        assertEquals(nothing, Ticks.zone(all, tree, cuisine, false))
    }

    /** [backup] with Cuisine speaking as Coach, its routine as Sage, and Course as Coach too. */
    private fun spoken(): PartTree {
        val document = backup()
        val data = document.getJSONObject("data")
        val zones = data.getJSONArray("zones")
        zones.getJSONObject(1).put("identity_id", "coach")
        zones.getJSONObject(2).put("identity_id", "coach")
        data.getJSONArray("automations").getJSONObject(0).put("identity_id", "sage")
        data.put("identities", JSONArray().put(JSONObject().put("id", "sage").put("name", "Sage")).put(JSONObject().put("id", "coach").put("name", "Coach")))
        return PartTree.of(document)
    }

    @Test
    fun `a zone lists the identities it and its routines speak as, its own first`() {
        val tree = spoken()
        val cuisine = tree.zones.first()
        assertEquals(listOf("coach", "sage"), cuisine.elements.take(2).map { it.id })
        assertTrue(cuisine.elements.take(2).all { it.kind == Kind.IDENTITY && it.group == null })
        assertEquals("Sage", tree.name(PartElement(Kind.IDENTITY, "sage")))
        assertEquals(listOf("coach"), tree.zones.first { it.id == "course" }.elements.map { it.id })
        assertEquals(tree, PartTree.read(JSONObject(tree.toJson().toString())))
    }

    @Test
    fun `a zone ticked takes its identities, unticked keeps the one another zone ticked speaks as`() {
        val tree = spoken()
        val cuisine = tree.zones.first()
        val course = tree.zones.first { it.id == "course" }
        val nothing = PartSelection(emptySet(), emptyMap(), emptySet(), emptySet(), emptySet())
        val both = Ticks.zone(Ticks.zone(nothing, tree, cuisine, true), tree, course, true)
        assertEquals(setOf("coach", "sage"), both.identities)
        assertEquals(setOf("coach"), Ticks.zone(both, tree, cuisine, false).identities)
        assertEquals(nothing, Ticks.zone(Ticks.zone(both, tree, cuisine, false), tree, course, false))
        // A routine ticked alone takes the identity it speaks as, which unticks on its own
        val routine = Ticks.element(nothing, tree, cuisine, cuisine.elements.first { it.id == "a1" }, true)
        assertEquals(setOf("sage"), routine.identities)
        assertEquals(emptySet<String>(), Ticks.element(routine, tree, cuisine, cuisine.elements.first { it.id == "sage" }, false).identities)
    }

    @Test
    fun `an element ticked takes its zone, unticked leaves it`() {
        val tree = PartTree.of(backup())
        val cuisine = tree.zones.first()
        val meals = cuisine.elements.first { it.id == "meals" }
        val one = Ticks.element(PartSelection(emptySet(), emptyMap(), emptySet(), emptySet(), emptySet()), tree, cuisine, meals, true)
        assertTrue("cuisine" in one.zones)
        val none = Ticks.element(one, tree, cuisine, meals, false)
        assertTrue("cuisine" in none.zones)
        assertFalse("meals" in none.tools)
        assertEquals(Kind.TOOL, meals.kind)
    }

    @Test
    fun `a zone ticked takes the APIs its Tasks call, unticked keeps one another ticked Task calls`() {
        fun task(id: String) = PartTree.Element(Kind.TASK, id, id, null, null, 0)
        val weather = PartTree.Element(Kind.API, "weather", "Weather", null, null, 0)
        val balcony = PartTree.Zone("balcony", "Balcony", null, listOf(task("morning")))
        val garden = PartTree.Zone("garden", "Garden", null, listOf(task("frost")))
        val tree = PartTree(listOf(balcony, garden), setOf(
            PartLink(PartElement(Kind.TASK, "morning"), PartElement(Kind.API, "weather")),
            PartLink(PartElement(Kind.TASK, "frost"), PartElement(Kind.API, "weather"))
        ), apis = listOf(weather))
        val nothing = PartSelection(emptySet(), emptyMap(), emptySet(), emptySet(), emptySet())
        val one = Ticks.zone(nothing, tree, balcony, true)
        assertEquals(setOf("weather"), one.apis)
        assertEquals(emptySet<String>(), Ticks.zone(one, tree, balcony, false).apis)
        val both = Ticks.zone(one, tree, garden, true)
        assertEquals(setOf("weather"), Ticks.zone(both, tree, balcony, false).apis)
    }

    @Test
    fun `the demo is left out of a part, its API kept when a Task of the user's calls it`() {
        fun task(id: String) = PartTree.Element(Kind.TASK, id, id, null, null, 0)
        val demoApi = PartTree.Element(Kind.API, "demo-api", "Demo API", null, null, 0)
        val unused = PartTree.Element(Kind.API, "demo-unused", "Unused", null, null, 0)
        val demo = PartTree.Zone("demo-balcony", "Balcony", "Demo", listOf(task("demo-weather")))
        val mine = PartTree.Zone("mine", "Mine", null, listOf(task("call")))
        val tree = PartTree(listOf(demo, mine), setOf(
            PartLink(PartElement(Kind.TASK, "demo-weather"), PartElement(Kind.API, "demo-api")),
            PartLink(PartElement(Kind.TASK, "call"), PartElement(Kind.API, "demo-api")),
            PartLink(PartElement(Kind.TASK, "call"), PartElement(Kind.ZONE, "demo-balcony"))
        ), apis = listOf(demoApi, unused), events = listOf(PartTree.Event("demo-busy", "Busy", "demo-balcony"))).withoutDemo()
        assertEquals(listOf("mine"), tree.zones.map { it.id })
        assertEquals(listOf("demo-api"), tree.apis.map { it.id })
        assertEquals(setOf(PartLink(PartElement(Kind.TASK, "call"), PartElement(Kind.API, "demo-api"))), tree.links)
        assertTrue(tree.events.isEmpty())
        assertTrue(tree.demoLeftOut)
        assertEquals(tree, PartTree.read(JSONObject(tree.toJson().toString())))
    }
}
