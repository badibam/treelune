package app.treelune.core.versioning

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.MigrationDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The migration 107 -> 108 run on the app's SQLite: a Task's routine step and the history's copy
 * of it hold blocks, no longer their input `data`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunRoutineBlocksAtV108MigrationTest {

    private fun one(database: MigrationDatabase, sql: String): String =
        database.query(sql).use { cursor -> assertTrue(sql, cursor.moveToFirst()); cursor.getString(0) }

    @Test
    fun aBaseAtV107_givesItsRoutineStepsBlocks() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "v107.db").apply { delete() }
        val steps = """[{"kind":"run_routine","routine_id":"r","inputs":{"data":{"received":{}}}}]"""
        BundledSQLiteDriver().open(file.path).use { connection ->
            val database = MigrationDatabase(connection)
            listOf(
                "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, steps_json TEXT NOT NULL)",
                "CREATE TABLE history_rows (id INTEGER PRIMARY KEY AUTOINCREMENT, table_name TEXT NOT NULL, before TEXT, after TEXT)",
                "INSERT INTO tasks VALUES ('t', '$steps')",
                "INSERT INTO history_rows (table_name, before, after) VALUES ('tasks', NULL, '${JSONObject().put("id", "t").put("steps_json", steps)}')"
            ).forEach { database.execSQL(it) }

            AppDatabase.MIGRATION_107_108.migrate(connection)

            val step = JSONArray(one(database, "SELECT steps_json FROM tasks")).getJSONObject(0)
            assertEquals("entry", step.getJSONArray("blocks").getJSONObject(0).getString("type"))
            val copy = JSONArray(JSONObject(one(database, "SELECT after FROM history_rows")).getString("steps_json")).getJSONObject(0)
            assertEquals("entry", copy.getJSONArray("blocks").getJSONObject(0).getString("type"))
        }
        file.delete()
    }
}
