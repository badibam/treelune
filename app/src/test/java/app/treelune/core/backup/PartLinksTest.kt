package app.treelune.core.backup

import app.treelune.core.backup.PartElement.Kind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the links of a part promise (docs/DATA.md, « A part of the app »): a kept element naming
 * one left out is found wherever its id stands, blocks the file unless it is a routine's or goes
 * to an identity, and nothing ticked holds no link.
 */
class PartLinksTest {

    private fun tool(id: String, zone: String, name: String, config: JSONObject = JSONObject()) =
        JSONObject().put("id", id).put("zone_id", zone).put("config_json", config.put("name", name).toString())

    /**
     * Cuisine (z1): Meals, whose entries designate Foods' entries, Foods, a chart of Weight, a
     * variable reading Foods, a routine with a provider, an identity, an access to Sport and a
     * message pointing at Weight. Sport (z2): Weight.
     */
    private fun backup(): JSONObject = JSONObject()
        .put("metadata", JSONObject().put("export_version", 90))
        .put("data", JSONObject()
            .put("zones", JSONArray()
                .put(JSONObject().put("id", "z1").put("name", "Cuisine"))
                .put(JSONObject().put("id", "z2").put("name", "Sport")))
            .put("tool_instances", JSONArray()
                .put(tool("meals", "z1", "Repas"))
                .put(tool("foods", "z1", "Aliments"))
                .put(tool("chart", "z1", "Calories", JSONObject().put("series", JSONArray().put(JSONObject().put("source", JSONObject().put("kind", "TOOL_INSTANCE").put("id", "weight"))))))
                .put(tool("weight", "z2", "Poids")))
            .put("tool_data", JSONArray()
                .put(JSONObject().put("id", "e1").put("tool_instance_id", "meals").put("data", JSONObject().put("food", JSONObject().put("kind", "ENTRY").put("id", "e2")).toString()))
                .put(JSONObject().put("id", "e2").put("tool_instance_id", "foods").put("data", "{}"))
                .put(JSONObject().put("id", "e3").put("tool_instance_id", "weight").put("data", "{}")))
            .put("variables", JSONArray()
                .put(JSONObject().put("id", "v1").put("zone_id", "z1").put("name", "objectif").put("definition_json", JSONObject().put("formula", "sum(foods.kcal)").toString())))
            .put("automations", JSONArray()
                .put(JSONObject().put("id", "a1").put("zone_id", "z1").put("seed_session_id", "seed").put("provider_id", "p1").put("identity_id", "i1")
                    .put("access_json", JSONArray().put(JSONObject().put("target", JSONObject().put("kind", "ZONE").put("id", "z2")).put("level", "read")).toString())))
            .put("session_messages", JSONArray()
                .put(JSONObject().put("id", "m1").put("session_id", "seed").put("rich_content_json", "{\"pointer\":\"weight\"}"))))

    private fun links(zones: Set<String>, tools: Map<String, Boolean>, variables: Set<String> = emptySet(), automations: Set<String> = emptySet()) =
        PartLinks.of(backup(), PartSelection(zones, tools, variables, automations, emptySet()))

    private fun link(from: Kind, fromId: String, to: Kind, toId: String) = PartLink(PartElement(from, fromId), PartElement(to, toId))

    @Test
    fun `a chart drawn from a tool left out blocks, the tool ticked without its entries does not`() {
        assertEquals(setOf(link(Kind.TOOL, "chart", Kind.TOOL, "weight")), links(setOf("z1"), mapOf("chart" to false)))
        assertTrue(links(setOf("z1"), mapOf("chart" to false)).single().blocking)
        assertEquals(emptySet<PartLink>(), links(setOf("z1", "z2"), mapOf("chart" to false, "weight" to false)))
    }

    @Test
    fun `a formula reading a tool left out blocks`() {
        assertEquals(setOf(link(Kind.VARIABLE, "v1", Kind.TOOL, "foods")), links(setOf("z1"), emptyMap(), setOf("v1")))
    }

    @Test
    fun `entries designating entries need those entries, the tool alone is not enough`() {
        assertEquals(setOf(link(Kind.ENTRIES, "meals", Kind.ENTRIES, "foods")), links(setOf("z1"), mapOf("meals" to true, "foods" to false)))
        assertEquals(emptySet<PartLink>(), links(setOf("z1"), mapOf("meals" to true, "foods" to true)))
        // Without its entries, Meals names no entry
        assertEquals(emptySet<PartLink>(), links(setOf("z1"), mapOf("meals" to false)))
    }

    @Test
    fun `a routine's links only warn, its provider and identity among them`() {
        val found = links(setOf("z1"), emptyMap(), automations = setOf("a1"))
        assertEquals(setOf(
            link(Kind.ROUTINE, "a1", Kind.ZONE, "z2"),
            link(Kind.ROUTINE, "a1", Kind.TOOL, "weight"),
            link(Kind.ROUTINE, "a1", Kind.PROVIDER, "p1"),
            link(Kind.ROUTINE, "a1", Kind.IDENTITY, "i1")
        ), found)
        assertFalse(found.any { it.blocking })
    }

    @Test
    fun `a zone or a routine without its identity only warns, naming both, and none once it is ticked`() {
        val document = backup()
        val data = document.getJSONObject("data")
        data.getJSONArray("zones").getJSONObject(1).put("identity_id", "i1")
        data.put("identities", JSONArray().put(JSONObject().put("id", "i1").put("name", "Coach")))
        val sport = PartSelection(setOf("z2"), emptyMap(), emptySet(), emptySet(), emptySet())
        val left = PartLinks.of(document, sport)
        assertEquals(setOf(link(Kind.ZONE, "z2", Kind.IDENTITY, "i1")), left)
        assertFalse(left.single().blocking)
        assertEquals("Sport without Coach", PartLinks.text(left.single(), { PartLinks.name(document, it) }, { mapOf("share_link_zone_identity" to "%1\$s without %2\$s").getValue(it) }))
        assertEquals(emptySet<PartLink>(), PartLinks.of(document, sport.copy(identities = setOf("i1"))))
        // The routine's identity ticked, its provider alone is left
        val routine = PartLinks.of(document, PartSelection(setOf("z1", "z2"), mapOf("weight" to false), emptySet(), setOf("a1"), setOf("i1")))
        assertEquals(setOf(link(Kind.ROUTINE, "a1", Kind.PROVIDER, "p1")), routine)
    }

    @Test
    fun `an id only counts as a whole word`() {
        val document = backup()
        document.getJSONObject("data").getJSONArray("tool_instances").getJSONObject(2)
            .put("config_json", JSONObject().put("name", "Calories").put("note", "weight_2 xweight weight-old").toString())
        assertEquals(emptySet<PartLink>(), PartLinks.of(document, PartSelection(setOf("z1"), mapOf("chart" to false), emptySet(), emptySet(), emptySet())))
    }

    @Test
    fun `a blocking link says what to tick by the names the user knows`() {
        val text = { key: String -> mapOf(
            "share_link_element_to_element" to "%1\$s reads %2\$s",
            "share_link_entries_to_entries" to "%1\$s entries designate %2\$s entries"
        ).getValue(key) }
        assertEquals("Calories reads Poids", PartLinks.text(link(Kind.TOOL, "chart", Kind.TOOL, "weight"), { PartLinks.name(backup(), it) }, text))
        assertEquals("Repas entries designate Aliments entries", PartLinks.text(link(Kind.ENTRIES, "meals", Kind.ENTRIES, "foods"), { PartLinks.name(backup(), it) }, text))
    }
}
