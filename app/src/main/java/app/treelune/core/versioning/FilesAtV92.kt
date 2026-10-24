package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the kept files to their v92 form (docs/DATA.md, « FILE »): `images`, the table of the
 * images of messages, entries and tools, and `attached_files`, the texts joined to messages with
 * their content in the row, become one table, `files`, each row noting its kind (IMAGE, TEXT),
 * its format and what goes with its kind (an image's size in pixels, a text's lines, its name on
 * the phone). A text's content leaves the database for a file of its own, `<id>.txt` in the
 * messages' folder, which the migration writes before the rows ([textRows]); the images' files
 * stay where they are.
 *
 * Shared by the database migration and the backup import ([backup]).
 */
object FilesAtV92 {

    /** The table's statements, run first on the v91 tables. */
    val CREATE = listOf(
        """
        CREATE TABLE IF NOT EXISTS files (
            id TEXT NOT NULL,
            session_id TEXT,
            entry_id TEXT,
            tool_instance_id TEXT,
            kind TEXT NOT NULL,
            format TEXT NOT NULL,
            name TEXT,
            size_bytes INTEGER NOT NULL,
            width INTEGER,
            height INTEGER,
            duration_ms INTEGER,
            line_count INTEGER,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(entry_id) REFERENCES tool_data(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(tool_instance_id) REFERENCES tool_instances(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "INSERT INTO files (id, session_id, entry_id, tool_instance_id, kind, format, name, size_bytes, width, height, duration_ms, line_count, created_at) " +
            "SELECT id, session_id, entry_id, tool_instance_id, 'IMAGE', format, NULL, size_bytes, width, height, NULL, NULL, created_at FROM images",
        "DROP TABLE images",
        "CREATE INDEX IF NOT EXISTS index_files_session_id ON files(session_id)",
        "CREATE INDEX IF NOT EXISTS index_files_entry_id ON files(entry_id)",
        "CREATE INDEX IF NOT EXISTS index_files_tool_instance_id ON files(tool_instance_id)",
        // The history's copies of the images' rows name their new table; [copy] rewrites them
        "UPDATE history_rows SET table_name = 'files' WHERE table_name = 'images'"
    )

    /** A text's row inserted, once its file is written: id, session, name, size, lines, created. */
    const val INSERT_TEXT = "INSERT INTO files (id, session_id, entry_id, tool_instance_id, kind, format, name, size_bytes, width, height, duration_ms, line_count, created_at) " +
        "VALUES (?, ?, NULL, NULL, 'TEXT', 'TEXT', ?, ?, NULL, NULL, NULL, ?, ?)"

    /** The texts' table, gone once its rows are in `files`. */
    const val DROP_TEXTS = "DROP TABLE attached_files"

    /** A copy the history keeps of an image's row, in its v92 form. */
    fun copy(row: JSONObject): JSONObject = row.put("kind", "IMAGE").put("name", JSONObject.NULL)
        .put("duration_ms", JSONObject.NULL).put("line_count", JSONObject.NULL)

    /** The key under which a backup's text row carries its content until its file is written. */
    const val CONTENT = "content"

    /**
     * The backup's [data] with its files under their v92 key: its images and its texts in
     * `files`, each text carrying its [CONTENT] until the import writes it into its file
     * (BackupService.open); `missing_images` becomes `missing_files`.
     */
    fun backup(data: JSONObject) {
        val images = data.optJSONArray("images")
        val texts = data.optJSONArray("attached_files")
        if (images == null && texts == null) return
        data.remove("images")
        data.remove("attached_files")
        data.put("files", JSONArray().apply {
            images?.let { for (i in 0 until it.length()) put(copy(JSONObject(it.getJSONObject(i).toString()))) }
            texts?.let {
                for (i in 0 until it.length()) {
                    val text = it.getJSONObject(i)
                    put(JSONObject()
                        .put("id", text.getString("id"))
                        .put("session_id", text.getString("session_id"))
                        .put("kind", "TEXT")
                        .put("format", "TEXT")
                        .put("name", text.getString("name"))
                        .put("size_bytes", text.getLong("size_bytes"))
                        .put("line_count", text.getInt("line_count"))
                        .put("created_at", text.getLong("created_at"))
                        .put(CONTENT, text.getString("content")))
                }
            }
        })
        data.optJSONArray("missing_images")?.let { data.remove("missing_images"); data.put("missing_files", it) }
    }
}
