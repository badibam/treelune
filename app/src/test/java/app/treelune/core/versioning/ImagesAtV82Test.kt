package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * The images brought to their v82 form (docs/design/image-field.md): the migration's statements
 * run on SQLite over the v81 table, and a backup made before v82. Every image kept so far stays,
 * a message's, owned by its session, written as JPEG.
 */
class ImagesAtV82Test {

    private lateinit var db: Connection

    @Before
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        listOf(
            "CREATE TABLE ai_sessions (id TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE tool_data (id TEXT NOT NULL, PRIMARY KEY(id))",
            "CREATE TABLE attached_images (id TEXT NOT NULL, session_id TEXT NOT NULL, size_bytes INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "INSERT INTO ai_sessions VALUES ('s')",
            "INSERT INTO attached_images VALUES ('a', 's', 300000, 1568, 1176, 10)",
            "INSERT INTO attached_images VALUES ('b', 's', 200000, 1176, 1568, 20)"
        ).forEach { db.createStatement().execute(it) }
        ImagesAtV82.STATEMENTS.forEach { db.createStatement().execute(it) }
    }

    @After
    fun tearDown() = db.close()

    private fun rows(sql: String): List<List<Any?>> = db.createStatement().executeQuery(sql).use { result ->
        val columns = result.metaData.columnCount
        buildList { while (result.next()) add((1..columns).map { result.getObject(it).let { v -> (v as? Number)?.toLong() ?: v } }) }
    }

    @Test
    fun `every image stays, its session's, as JPEG`() {
        assertEquals(
            listOf(
                listOf("a", "s", null, "JPEG", 300000L, 1568L, 1176L, 10L),
                listOf("b", "s", null, "JPEG", 200000L, 1176L, 1568L, 20L)
            ),
            rows("SELECT id, session_id, entry_id, format, size_bytes, width, height, created_at FROM images ORDER BY id")
        )
    }

    @Test
    fun `the old table is gone`() {
        assertEquals(emptyList<List<Any?>>(), rows("SELECT name FROM sqlite_master WHERE name = 'attached_images'"))
    }

    @Test
    fun `a backup's images move under their new key, each a JPEG`() {
        val data = JSONObject().put("attached_images", JSONArray().put(
            JSONObject().put("id", "a").put("session_id", "s").put("size_bytes", 1).put("width", 2).put("height", 3).put("created_at", 4)
        ))
        ImagesAtV82.backup(data)
        assertFalse(data.has("attached_images"))
        val image = data.getJSONArray("images").getJSONObject(0)
        assertEquals("s", image.getString("session_id"))
        assertEquals("JPEG", image.getString("format"))
        assertFalse(image.has("entry_id"))
    }
}
