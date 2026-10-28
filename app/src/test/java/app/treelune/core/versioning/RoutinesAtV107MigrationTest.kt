package app.treelune.core.versioning

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.MigrationDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The migration 106 -> 107 run on the app's SQLite, over the tables it touches as they stood at
 * v106: the routines' table and the sessions' column renamed with their indices under Room's
 * names, the stored values rewritten, the history's copies following.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutinesAtV107MigrationTest {

    private fun names(database: MigrationDatabase, type: String): Set<String> =
        database.query("SELECT name FROM sqlite_master WHERE type = ?", arrayOf(type)).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun one(database: MigrationDatabase, sql: String): String =
        database.query(sql).use { cursor -> assertTrue(sql, cursor.moveToFirst()); cursor.getString(0) }

    @Test
    fun aBaseAtV106_namesItsRoutinesRoutine() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "v106.db").apply { delete() }
        BundledSQLiteDriver().open(file.path).use { connection ->
            val database = MigrationDatabase(connection)
            listOf(
                "CREATE TABLE automations (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, zone_id TEXT NOT NULL, seed_session_id TEXT NOT NULL, is_enabled INTEGER NOT NULL)",
                "CREATE INDEX index_automations_zone_id ON automations (zone_id)",
                "CREATE INDEX index_automations_is_enabled ON automations (is_enabled)",
                "CREATE INDEX index_automations_seed_session_id ON automations (seed_session_id)",
                "CREATE TABLE ai_sessions (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, automation_id TEXT)",
                "CREATE INDEX index_ai_sessions_automation_id ON ai_sessions (automation_id)",
                "CREATE TABLE session_messages (id TEXT NOT NULL PRIMARY KEY, system_message_json TEXT)",
                "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, steps_json TEXT NOT NULL)",
                "CREATE TABLE app_settings_categories (category TEXT NOT NULL PRIMARY KEY, settings TEXT NOT NULL)",
                "CREATE TABLE history_rows (id INTEGER PRIMARY KEY AUTOINCREMENT, table_name TEXT NOT NULL, before TEXT, after TEXT)",
                "INSERT INTO automations VALUES ('r', 'Weekly', 'z', 'seed', 1)",
                "INSERT INTO ai_sessions VALUES ('seed', 'SEED', NULL), ('run', 'AUTOMATION', 'r'), ('chat', 'CHAT', NULL)",
                """INSERT INTO session_messages VALUES ('m', '{"type":"AUTOMATION_NO_COMMANDS"}')""",
                """INSERT INTO tasks VALUES ('t', '[{"kind":"run_routine","automation_id":"r"}]')""",
                """INSERT INTO app_settings_categories VALUES ('ai_limits', '{"automation_max_autonomous_roundtrips":20,"automation_max_data_chars":100000}')""",
                """INSERT INTO history_rows (table_name, before, after) VALUES ('automations', NULL, '{"id":"r"}'), ('ai_sessions', NULL, '{"id":"run","type":"AUTOMATION","automation_id":"r"}')"""
            ).forEach { database.execSQL(it) }

            AppDatabase.MIGRATION_106_107.migrate(connection)

            val tables = names(database, "table")
            assertTrue("routines" in tables)
            assertFalse("automations" in tables)
            assertEquals(
                setOf("index_routines_zone_id", "index_routines_is_enabled", "index_routines_seed_session_id", "index_ai_sessions_routine_id"),
                names(database, "index").filter { it.startsWith("index_routines") || it.startsWith("index_ai_sessions") }.toSet()
            )
            assertEquals("Weekly", one(database, "SELECT name FROM routines WHERE id = 'r'"))
            assertEquals("ROUTINE_RUN|r", one(database, "SELECT type || '|' || routine_id FROM ai_sessions WHERE id = 'run'"))
            assertEquals("SEED", one(database, "SELECT type FROM ai_sessions WHERE id = 'seed'"))
            assertEquals("CHAT", one(database, "SELECT type FROM ai_sessions WHERE id = 'chat'"))
            assertEquals("ROUTINE_NO_COMMANDS", JSONObject(one(database, "SELECT system_message_json FROM session_messages")).getString("type"))
            assertEquals("r", JSONArray(one(database, "SELECT steps_json FROM tasks")).getJSONObject(0).getString("routine_id"))
            val limits = JSONObject(one(database, "SELECT settings FROM app_settings_categories WHERE category = 'ai_limits'"))
            assertEquals(20, limits.getInt("routine_max_autonomous_roundtrips"))
            assertEquals(100000, limits.getInt("routine_max_data_chars"))
            assertEquals("1", one(database, "SELECT COUNT(*) FROM history_rows WHERE table_name = 'routines'"))
            assertEquals("r", JSONObject(one(database, "SELECT after FROM history_rows WHERE table_name = 'ai_sessions'")).getString("routine_id"))
        }
        file.delete()
    }
}
