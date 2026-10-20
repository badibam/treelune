package app.treelune.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a part of the app shared promises (docs/design/share.md, « Le fichier »): the backup's
 * format marked partial, holding only what is ticked, a tool's entries only when asked, and never
 * the settings, the AI providers, the identities, the conversations.
 */
class BackupPartTest {

    private fun ids(data: JSONObject, table: String): List<String> =
        data.getJSONArray(table).let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("id") } }

    private fun row(table: String, data: JSONObject, id: String): JSONObject {
        val array = data.getJSONArray(table)
        return (0 until array.length()).map { array.getJSONObject(it) }.first { it.getString("id") == id }
    }

    /** Two zones: Cuisine (Meals with an image, Foods, a variable, a routine, two tool groups) and Sport (Runs). */
    private fun backup(): JSONObject = JSONObject()
        .put("metadata", JSONObject().put("export_version", 90).put("db_schema_version", 86))
        .put("data", JSONObject()
            .put("app_settings_categories", JSONArray().put(JSONObject().put("category", "ai").put("settings", "{}")))
            .put("ai_provider_configs", JSONArray().put(JSONObject().put("id", "p1")))
            .put("identities", JSONArray().put(JSONObject().put("id", "i1")))
            .put("attached_files", JSONArray().put(JSONObject().put("id", "f1").put("session_id", "chat")))
            .put("zones", JSONArray()
                .put(JSONObject().put("id", "z1").put("name", "Cuisine").put("identity_id", "i1").put("tool_groups", JSONArray(listOf("Repas", "Analyse")).toString()))
                .put(JSONObject().put("id", "z2").put("name", "Sport")))
            .put("tool_instances", JSONArray()
                .put(JSONObject().put("id", "meals").put("zone_id", "z1").put("config_json", JSONObject().put("group", "Repas").toString()))
                .put(JSONObject().put("id", "foods").put("zone_id", "z1").put("config_json", JSONObject().put("group", "Repas").toString()))
                .put(JSONObject().put("id", "runs").put("zone_id", "z2").put("config_json", "{}")))
            .put("tool_data", JSONArray()
                .put(JSONObject().put("id", "e1").put("tool_instance_id", "meals"))
                .put(JSONObject().put("id", "e2").put("tool_instance_id", "foods"))
                .put(JSONObject().put("id", "e3").put("tool_instance_id", "runs")))
            .put("variables", JSONArray().put(JSONObject().put("id", "v1").put("zone_id", "z1").put("group", "Analyse")))
            .put("automations", JSONArray().put(JSONObject().put("id", "a1").put("zone_id", "z1").put("seed_session_id", "seed")
                .put("provider_id", "p1").put("identity_id", "i1").put("last_execution_id", "run").put("execution_history_json", "[\"run\"]")))
            .put("ai_sessions", JSONArray().put(JSONObject().put("id", "seed")).put(JSONObject().put("id", "chat")).put(JSONObject().put("id", "run")))
            .put("session_messages", JSONArray()
                .put(JSONObject().put("id", "m1").put("session_id", "seed"))
                .put(JSONObject().put("id", "m2").put("session_id", "chat")))
            .put("images", JSONArray()
                .put(JSONObject().put("id", "img1").put("entry_id", "e1"))
                .put(JSONObject().put("id", "img2").put("entry_id", "e3"))
                .put(JSONObject().put("id", "img3").put("session_id", "chat")))
            .put("missing_images", JSONArray(listOf("img1", "img2"))))

    @Test
    fun `a part is marked partial and keeps the backup's versions`() {
        val part = BackupPart.cut(backup(), PartSelection(setOf("z2"), emptyMap(), emptySet(), emptySet()))
        assertTrue(BackupPart.isPartial(part))
        assertFalse(BackupPart.isPartial(backup()))
        assertEquals(86, part.getJSONObject("metadata").getInt("db_schema_version"))
        assertEquals(90, part.getJSONObject("metadata").getInt("export_version"))
    }

    @Test
    fun `only what is ticked goes, a tool's entries and their images only when asked`() {
        val data = BackupPart.cut(backup(), PartSelection(setOf("z1"), mapOf("meals" to true, "foods" to false), emptySet(), emptySet())).getJSONObject("data")
        assertEquals(listOf("z1"), ids(data, "zones"))
        assertEquals(listOf("meals", "foods"), ids(data, "tool_instances"))
        assertEquals(listOf("e1"), ids(data, "tool_data"))
        assertEquals(listOf("img1"), ids(data, "images"))
        assertEquals(listOf("img1"), data.getJSONArray("missing_images").let { a -> (0 until a.length()).map { a.getString(it) } })
    }

    @Test
    fun `the settings, providers, identities, conversations and joined files never go`() {
        val data = BackupPart.cut(backup(), PartSelection(setOf("z1"), mapOf("meals" to true), setOf("v1"), setOf("a1"))).getJSONObject("data")
        listOf("app_settings_categories", "ai_provider_configs", "identities", "attached_files").forEach { assertFalse(it, data.has(it)) }
        assertEquals(listOf("seed"), ids(data, "ai_sessions"))
        assertEquals(listOf("m1"), ids(data, "session_messages"))
        assertTrue(row("zones", data, "z1").isNull("identity_id"))
    }

    @Test
    fun `a routine goes with its message, without its provider, its identity or its runs`() {
        val routine = row("automations", BackupPart.cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), setOf("a1"))).getJSONObject("data"), "a1")
        assertEquals("seed", routine.getString("seed_session_id"))
        listOf("provider_id", "identity_id", "last_execution_id").forEach { assertTrue(it, routine.isNull(it)) }
        assertEquals("[]", routine.getString("execution_history_json"))
    }

    @Test
    fun `a zone keeps the tool groups its kept elements hold, an empty zone none`() {
        val tools = BackupPart.cut(backup(), PartSelection(setOf("z1"), mapOf("meals" to false), emptySet(), emptySet())).getJSONObject("data")
        assertEquals(JSONArray(listOf("Repas")).toString(), row("zones", tools, "z1").getString("tool_groups"))
        val variable = BackupPart.cut(backup(), PartSelection(setOf("z1"), emptyMap(), setOf("v1"), emptySet())).getJSONObject("data")
        assertEquals(JSONArray(listOf("Analyse")).toString(), row("zones", variable, "z1").getString("tool_groups"))
        val empty = BackupPart.cut(backup(), PartSelection(setOf("z1"), emptyMap(), emptySet(), emptySet())).getJSONObject("data")
        assertFalse(row("zones", empty, "z1").has("tool_groups"))
    }

    @Test
    fun `an element without its zone, or one the backup does not hold, is refused`() {
        assertThrows(IllegalArgumentException::class.java) { BackupPart.cut(backup(), PartSelection(setOf("z1"), mapOf("runs" to false), emptySet(), emptySet())) }
        assertThrows(IllegalArgumentException::class.java) { BackupPart.cut(backup(), PartSelection(setOf("z1"), mapOf("gone" to false), emptySet(), emptySet())) }
        assertThrows(IllegalArgumentException::class.java) { BackupPart.cut(backup(), PartSelection(setOf("z9"), emptyMap(), emptySet(), emptySet())) }
    }

    @Test
    fun `the cut leaves the whole backup as it was`() {
        val whole = backup()
        val before = whole.toString()
        BackupPart.cut(whole, PartSelection(setOf("z1"), mapOf("meals" to true), setOf("v1"), setOf("a1")))
        assertEquals(before, whole.toString())
    }

    @Test
    fun `a selection reads back from its stored form`() {
        val selection = PartSelection(setOf("z1"), mapOf("meals" to true, "foods" to false), setOf("v1"), setOf("a1"))
        assertEquals(selection, PartSelection.read(JSONObject(selection.toJson().toString())))
    }
}
