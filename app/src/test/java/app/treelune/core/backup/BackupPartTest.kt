package app.treelune.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a part of the app shared promises (docs/DATA.md, « A part of the app »): the backup's
 * format marked partial, holding only what is ticked, a tool's entries only when asked, and never
 * the settings, the AI providers, the conversations; an identity only ticked, with a zone that
 * speaks as it.
 */
class BackupPartTest {

    private fun ids(data: JSONObject, table: String): List<String> =
        data.getJSONArray(table).let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("id") } }

    /** A part cut as the app cuts it, an API's key its secret setting. */
    private fun cut(document: JSONObject, selection: PartSelection): JSONObject = BackupPart.cut(document, selection, listOf("key"))

    private fun row(table: String, data: JSONObject, id: String): JSONObject {
        val array = data.getJSONArray(table)
        return (0 until array.length()).map { array.getJSONObject(it) }.first { it.getString("id") == id }
    }

    /**
     * Two zones: Cuisine (Meals with an image, Foods, a variable, a routine, a Task calling an API
     * and emitting an event of Sport, two tool groups) and Sport (Runs, and the event, which
     * carries a Foods entry).
     */
    private fun backup(): JSONObject = JSONObject()
        .put("metadata", JSONObject().put("export_version", 90).put("db_schema_version", 86))
        .put("data", JSONObject()
            .put("app_settings_categories", JSONArray().put(JSONObject().put("category", "ai").put("settings", "{}")))
            .put("ai_provider_configs", JSONArray().put(JSONObject().put("id", "p1")))
            .put("identities", JSONArray().put(JSONObject().put("id", "i1").put("name", "Chef")))
            .put("zones", JSONArray()
                .put(JSONObject().put("id", "z1").put("name", "Cuisine").put("identity_id", "i1").put("tool_groups", JSONArray(listOf("Repas", "Analyse")).toString()))
                .put(JSONObject().put("id", "z2").put("name", "Sport")))
            .put("tool_instances", JSONArray()
                .put(JSONObject().put("id", "meals").put("zone_id", "z1").put("tooltype", "tracking").put("config_json", JSONObject().put("group", "Repas").toString()))
                .put(JSONObject().put("id", "foods").put("zone_id", "z1").put("tooltype", "tracking").put("config_json", JSONObject().put("group", "Repas").toString()))
                .put(JSONObject().put("id", "runs").put("zone_id", "z2").put("tooltype", "tracking").put("config_json", "{}")))
            .put("tool_data", JSONArray()
                .put(JSONObject().put("id", "e1").put("tool_instance_id", "meals"))
                .put(JSONObject().put("id", "e2").put("tool_instance_id", "foods"))
                .put(JSONObject().put("id", "e3").put("tool_instance_id", "runs")))
            .put("variables", JSONArray().put(JSONObject().put("id", "v1").put("name", "kcal").put("zone_id", "z1").put("group", "Analyse")))
            .put("automations", JSONArray().put(JSONObject().put("id", "a1").put("name", "Bilan").put("zone_id", "z1").put("seed_session_id", "seed")
                .put("provider_id", "p1").put("identity_id", "i1").put("last_execution_id", "run").put("execution_history_json", "[\"run\"]")))
            .put("tasks", JSONArray().put(JSONObject().put("id", "t1").put("name", "Courses").put("zone_id", "z1").put("is_enabled", true)
                .put("steps_json", JSONArray().put(JSONObject().put("kind", "call_api").put("api_id", "api1"))
                    .put(JSONObject().put("kind", "create_entry").put("tool_instance_id", "foods")).toString())
                .put("emit_event", JSONObject().put("event_id", "ev1").toString())))
            .put("apis", JSONArray().put(JSONObject().put("id", "api1").put("name", "Prix")
                .put("config_json", JSONObject().put("url", "https://example.org").put("key", "sealed").toString())))
            .put("events", JSONArray().put(JSONObject().put("id", "ev1").put("name", "Courses faites").put("zone_id", "z2")
                .put("values_json", JSONArray().put(JSONObject().put("name", "list").put("type", "entry").put("tool_instance_id", "foods")).toString())))
            .put("ai_sessions", JSONArray().put(JSONObject().put("id", "seed")).put(JSONObject().put("id", "chat")).put(JSONObject().put("id", "run")))
            .put("session_messages", JSONArray()
                .put(JSONObject().put("id", "m1").put("session_id", "seed"))
                .put(JSONObject().put("id", "m2").put("session_id", "chat")))
            .put("files", JSONArray()
                .put(JSONObject().put("id", "img1").put("entry_id", "e1"))
                .put(JSONObject().put("id", "img2").put("entry_id", "e3"))
                .put(JSONObject().put("id", "img3").put("session_id", "chat"))
                .put(JSONObject().put("id", "f1").put("session_id", "chat").put("kind", "TEXT")))
            .put("missing_files", JSONArray(listOf("img1", "img2"))))

    @Test
    fun `a part is marked partial and keeps the backup's versions`() {
        val part = cut(backup(), PartSelection(setOf("z2"), emptyMap(), emptySet(), emptySet(), emptySet()))
        assertTrue(BackupPart.isPartial(part))
        assertFalse(BackupPart.isPartial(backup()))
        assertEquals(86, part.getJSONObject("metadata").getInt("db_schema_version"))
        assertEquals(90, part.getJSONObject("metadata").getInt("export_version"))
    }

    @Test
    fun `only what is ticked goes, a tool's entries and their images only when asked`() {
        val data = cut(backup(), PartSelection(setOf("z1"), mapOf("meals" to true, "foods" to false), emptySet(), emptySet(), emptySet())).getJSONObject("data")
        assertEquals(listOf("z1"), ids(data, "zones"))
        assertEquals(listOf("meals", "foods"), ids(data, "tool_instances"))
        assertEquals(listOf("e1"), ids(data, "tool_data"))
        assertEquals(listOf("img1"), ids(data, "files"))
        assertEquals(listOf("img1"), data.getJSONArray("missing_files").let { a -> (0 until a.length()).map { a.getString(it) } })
    }

    @Test
    fun `the settings, providers, conversations and joined files never go, an identity unticked neither`() {
        val data = cut(backup(), PartSelection(setOf("z1"), mapOf("meals" to true), setOf("v1"), setOf("a1"), emptySet())).getJSONObject("data")
        listOf("app_settings_categories", "ai_provider_configs").forEach { assertFalse(it, data.has(it)) }
        assertFalse("a conversation's joined text", "f1" in ids(data, "files"))
        assertEquals(emptyList<String>(), ids(data, "identities"))
        assertEquals(listOf("seed"), ids(data, "ai_sessions"))
        assertEquals(listOf("m1"), ids(data, "session_messages"))
        assertTrue(row("zones", data, "z1").isNull("identity_id"))
    }

    @Test
    fun `a routine goes with its message, without its provider, its identity or its runs`() {
        val routine = row("automations", cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), setOf("a1"), emptySet())).getJSONObject("data"), "a1")
        assertEquals("seed", routine.getString("seed_session_id"))
        listOf("provider_id", "identity_id", "last_execution_id").forEach { assertTrue(it, routine.isNull(it)) }
        assertEquals("[]", routine.getString("execution_history_json"))
    }

    @Test
    fun `an identity ticked goes, its zone and routine speaking as it`() {
        val data = cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), setOf("a1"), setOf("i1"))).getJSONObject("data")
        assertEquals(listOf("i1"), ids(data, "identities"))
        assertEquals("i1", row("zones", data, "z1").getString("identity_id"))
        val routine = row("automations", data, "a1")
        assertEquals("i1", routine.getString("identity_id"))
        assertTrue(routine.isNull("provider_id"))
    }

    @Test
    fun `an identity goes only with a zone that speaks as it, or one of its routines`() {
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z2"), emptyMap(), emptySet(), emptySet(), setOf("i1"))) }
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), emptySet(), setOf("i9"))) }
        // The zone speaking as another, its routine still as i1: the identity goes with the zone
        val whole = backup()
        whole.getJSONObject("data").getJSONArray("zones").getJSONObject(0).put("identity_id", JSONObject.NULL)
        assertEquals(listOf("i1"), ids(cut(whole, PartSelection(setOf("z1"), emptyMap(), emptySet(), emptySet(), setOf("i1"))).getJSONObject("data"), "identities"))
    }

    @Test
    fun `a zone keeps the tool groups its kept elements hold, an empty zone none`() {
        val tools = cut(backup(), PartSelection(setOf("z1"), mapOf("meals" to false), emptySet(), emptySet(), emptySet())).getJSONObject("data")
        assertEquals(JSONArray(listOf("Repas")).toString(), row("zones", tools, "z1").getString("tool_groups"))
        val variable = cut(backup(), PartSelection(setOf("z1"), emptyMap(), setOf("v1"), emptySet(), emptySet())).getJSONObject("data")
        assertEquals(JSONArray(listOf("Analyse")).toString(), row("zones", variable, "z1").getString("tool_groups"))
        val empty = cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), emptySet(), emptySet())).getJSONObject("data")
        assertFalse(row("zones", empty, "z1").has("tool_groups"))
    }

    @Test
    fun `an element without its zone, or one the backup does not hold, is refused`() {
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z1"), mapOf("runs" to false), emptySet(), emptySet(), emptySet())) }
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z1"), mapOf("gone" to false), emptySet(), emptySet(), emptySet())) }
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z9"), emptyMap(), emptySet(), emptySet(), emptySet())) }
    }

    @Test
    fun `the cut leaves the whole backup as it was`() {
        val whole = backup()
        val before = whole.toString()
        cut(whole, PartSelection(setOf("z1"), mapOf("meals" to true), setOf("v1"), setOf("a1"), emptySet()))
        assertEquals(before, whole.toString())
    }

    @Test
    fun `a selection reads back from its stored form`() {
        val selection = PartSelection(setOf("z1"), mapOf("meals" to true, "foods" to false), setOf("v1"), setOf("a1"), setOf("i1"), setOf("t1"), setOf("api1"))
        assertEquals(selection, PartSelection.read(JSONObject(selection.toJson().toString())))
    }

    @Test
    fun `a Task goes off, an API without its key, the event the Task names in the Task's zone`() {
        val data = cut(backup(), PartSelection(setOf("z1"), mapOf("foods" to false), emptySet(), emptySet(), emptySet(), setOf("t1"), setOf("api1"))).getJSONObject("data")

        assertEquals(listOf("t1"), ids(data, "tasks"))
        assertFalse(row("tasks", data, "t1").getBoolean("is_enabled"))
        val config = JSONObject(row("apis", data, "api1").getString("config_json"))
        assertFalse(config.has("key"))
        assertEquals("https://example.org", config.getString("url"))
        // Sport is not ticked: the event arrives where the Task that emits it does
        assertEquals(listOf("ev1"), ids(data, "events"))
        assertEquals("z1", row("events", data, "ev1").getString("zone_id"))
    }

    @Test
    fun `an event nothing ticked names stays out, one in a zone ticked keeps its zone`() {
        assertTrue(ids(cut(backup(), PartSelection(setOf("z1", "z2"), mapOf("foods" to false), emptySet(), emptySet(), emptySet())).getJSONObject("data"), "events").isEmpty())
        val data = cut(backup(), PartSelection(setOf("z1", "z2"), mapOf("foods" to false), emptySet(), emptySet(), emptySet(), setOf("t1"), setOf("api1"))).getJSONObject("data")
        assertEquals("z2", row("events", data, "ev1").getString("zone_id"))
    }

    @Test
    fun `a Task without its zone, or an API the backup does not hold, is refused`() {
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z2"), emptyMap(), emptySet(), emptySet(), emptySet(), setOf("t1"))) }
        assertThrows(IllegalArgumentException::class.java) { cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), emptySet(), emptySet(), apis = setOf("nope"))) }
    }

    @Test
    fun `a Task's tool, API and the tool of the event it carries block until ticked`() {
        val links = PartLinks.of(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), emptySet(), setOf("i1"), setOf("t1")))
        assertTrue(PartLink(PartElement(PartElement.Kind.TASK, "t1"), PartElement(PartElement.Kind.TOOL, "foods")) in links)
        assertTrue(PartLink(PartElement(PartElement.Kind.TASK, "t1"), PartElement(PartElement.Kind.API, "api1")) in links)
        assertTrue(PartLink(PartElement(PartElement.Kind.EVENT, "ev1"), PartElement(PartElement.Kind.TOOL, "foods")) in links)
        assertTrue(links.all { it.blocking })
        // Ticked, nothing is left; the event is carried, never ticked
        assertTrue(PartLinks.of(backup(), PartSelection(setOf("z1"), mapOf("foods" to false), emptySet(), emptySet(), setOf("i1"), setOf("t1"), setOf("api1"))).isEmpty())
    }
}
