package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * The AI providers brought to their v79 form (docs/design/ai-providers.md): the migration's
 * statements run on SQLite over the v78 tables, and a backup made before v79. What was configured
 * stays, under its id and name, of the kind its id named, the active one the default; what was
 * never configured goes, and a routine that named it is left without a provider, off.
 */
class ProvidersAtV79Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        listOf(
            "CREATE TABLE `ai_provider_configs` (`provider_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, `config_json` TEXT NOT NULL, `is_configured` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`provider_id`))",
            "CREATE TABLE `automations` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `zone_id` TEXT NOT NULL, `seed_session_id` TEXT NOT NULL, `schedule_json` TEXT, `trigger_ids_json` TEXT NOT NULL, `catch_up` TEXT, `catch_up_window` INTEGER, `dismiss_older_instances` INTEGER NOT NULL, `provider_id` TEXT NOT NULL, `is_enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `last_execution_id` TEXT, `execution_history_json` TEXT NOT NULL, `group` TEXT, `access_json` TEXT NOT NULL, `notify_end` TEXT NOT NULL, `identity_id` TEXT, PRIMARY KEY(`id`))",
            "CREATE TABLE `ai_sessions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `validate_app` INTEGER NOT NULL, `validate_zones` INTEGER NOT NULL, `validate_data` INTEGER NOT NULL, `reasoning_thinking_off` INTEGER, `reasoning_effort` TEXT, `phase` TEXT NOT NULL, `total_roundtrips` INTEGER NOT NULL, `last_event_time` INTEGER NOT NULL, `last_user_interaction_time` INTEGER NOT NULL, `automation_id` TEXT, `scheduled_execution_time` INTEGER, `provider_id` TEXT NOT NULL, `provider_session_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `last_activity` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `end_reason` TEXT, `seen_at` INTEGER, `summary` TEXT, `app_state_snapshot` TEXT, `identity_id` TEXT, PRIMARY KEY(`id`))",
            // Configured: Claude standard, the active one, and the Other server's economic variant
            "INSERT INTO ai_provider_configs VALUES ('claude_standard', 'Claude', '{\"model\":\"m\"}', 1, 1, 1, 2)",
            "INSERT INTO ai_provider_configs VALUES ('compatible_economic', 'Compatible OpenAI (économique)', '{}', 1, 0, 3, 4)",
            // Stored but not configured, and an id of no kind
            "INSERT INTO ai_provider_configs VALUES ('openai_standard', 'OpenAI', '{}', 0, 0, 5, 6)",
            "INSERT INTO ai_provider_configs VALUES ('claude', 'Old', '{}', 1, 0, 7, 8)",
            "INSERT INTO ai_provider_configs VALUES ('mistral_standard', 'Mistral', '{}', 1, 0, 9, 10)",
            routine("kept", "claude_standard"),
            routine("orphan", "openai_standard"),
            session("chat", "CHAT", "claude_standard"),
            session("gone", "CHAT", "deepseek_economic"),
            session("seed", "SEED", "")
        ).forEach { db.createStatement().execute(it) }
        ProvidersAtV79.STATEMENTS.forEach { db.createStatement().execute(it) }
    }

    @After
    fun tearDown() = db.close()

    private fun routine(id: String, provider: String) =
        "INSERT INTO automations VALUES ('$id', '$id', 'zone', 'seed-$id', NULL, '[]', NULL, NULL, 0, '$provider', 1, 1, 1, NULL, '[]', NULL, '[]', 'always', NULL)"

    private fun session(id: String, type: String, provider: String) =
        "INSERT INTO ai_sessions VALUES ('$id', '$id', '$type', 0, 0, 0, NULL, NULL, 'IDLE', 0, 0, 0, NULL, NULL, '$provider', '', 1, 1, 0, NULL, NULL, NULL, NULL, NULL)"

    private fun rows(sql: String): List<List<Any?>> = db.createStatement().executeQuery(sql).use { result ->
        val columns = result.metaData.columnCount
        buildList { while (result.next()) add((1..columns).map { result.getObject(it) }) }
    }

    @Test
    fun `the configured providers stay, of the kind their id named, the active one the default`() {
        assertEquals(
            listOf(
                listOf("claude", "claude", "Old", 0),
                listOf("claude_standard", "claude", "Claude", 1),
                listOf("compatible_economic", "compatible", "Compatible OpenAI (économique)", 0)
            ),
            rows("SELECT id, kind, name, is_default FROM ai_provider_configs ORDER BY id")
        )
        assertEquals(listOf(listOf("{\"model\":\"m\"}", 1, 2)), rows("SELECT config_json, created_at, updated_at FROM ai_provider_configs WHERE id = 'claude_standard'").map { it.map { v -> (v as? Number)?.toInt() ?: v } })
    }

    @Test
    fun `a routine keeps a provider that stays, and one that names a provider gone is left without, off`() {
        assertEquals(
            listOf(listOf("kept", "claude_standard", 1), listOf("orphan", null, 0)),
            rows("SELECT id, provider_id, is_enabled FROM automations ORDER BY id")
        )
    }

    @Test
    fun `a session keeps the id it named, a seed's empty one becomes none`() {
        assertEquals(
            listOf(listOf("chat", "claude_standard"), listOf("gone", "deepseek_economic"), listOf("seed", null)),
            rows("SELECT id, provider_id FROM ai_sessions ORDER BY id")
        )
    }

    @Test
    fun `two providers cannot share a name`() {
        val refused = runCatching {
            db.createStatement().execute("INSERT INTO ai_provider_configs VALUES ('x', 'claude', 'Claude', '{}', 0, 1, 1)")
        }
        assertTrue(refused.isFailure)
    }

    @Test
    fun `a backup made before v79 is brought to the same form`() {
        val data = JSONObject()
            .put("ai_provider_configs", JSONArray()
                .put(JSONObject().put("provider_id", "deepseek_standard").put("display_name", "DeepSeek").put("config_json", "{}")
                    .put("is_configured", true).put("is_active", true).put("created_at", 1).put("updated_at", 2))
                .put(JSONObject().put("provider_id", "openai_economic").put("display_name", "OpenAI (économique)").put("config_json", "{}")
                    .put("is_configured", false).put("is_active", false).put("created_at", 3).put("updated_at", 4)))
            .put("automations", JSONArray()
                .put(JSONObject().put("id", "a").put("provider_id", "deepseek_standard").put("is_enabled", true))
                .put(JSONObject().put("id", "b").put("provider_id", "openai_economic").put("is_enabled", true)))
            .put("ai_sessions", JSONArray()
                .put(JSONObject().put("id", "seed").put("provider_id", ""))
                .put(JSONObject().put("id", "chat").put("provider_id", "openai_economic")))

        ProvidersAtV79.backup(data)

        val providers = data.getJSONArray("ai_provider_configs")
        assertEquals(1, providers.length())
        val kept = providers.getJSONObject(0)
        assertEquals("deepseek_standard", kept.getString("id"))
        assertEquals("deepseek", kept.getString("kind"))
        assertEquals("DeepSeek", kept.getString("name"))
        assertTrue(kept.getBoolean("is_default"))
        assertFalse(kept.has("provider_id"))

        val routines = data.getJSONArray("automations")
        assertEquals("deepseek_standard", routines.getJSONObject(0).getString("provider_id"))
        assertTrue(routines.getJSONObject(1).isNull("provider_id"))
        assertFalse(routines.getJSONObject(1).getBoolean("is_enabled"))

        val sessions = data.getJSONArray("ai_sessions")
        assertTrue(sessions.getJSONObject(0).isNull("provider_id"))
        assertEquals("openai_economic", sessions.getJSONObject(1).getString("provider_id"))
    }

    @Test
    fun `the kind of an id is its prefix, none for an id of no kind`() {
        assertEquals("openai", ProvidersAtV79.kindOf("openai_standard"))
        assertEquals("compatible", ProvidersAtV79.kindOf("compatible_economic"))
        assertEquals("claude", ProvidersAtV79.kindOf("claude"))
        assertNull(ProvidersAtV79.kindOf("claudette"))
        assertNull(ProvidersAtV79.kindOf(""))
    }
}
