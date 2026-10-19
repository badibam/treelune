package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the images to their v82 form (docs/design/image-field.md): `attached_images`, the images
 * of messages, becomes `images`, the table of the images of messages and entries, each row owned
 * by a session or an entry and noting its format. Every image kept so far is a message's, written
 * as JPEG; the files stay where they are.
 *
 * Shared by the database migration (STATEMENTS) and the backup import (backup).
 */
object ImagesAtV82 {

    /** The migration's statements, run in order on the v81 tables. */
    val STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS images (
            id TEXT NOT NULL,
            session_id TEXT,
            entry_id TEXT,
            format TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            width INTEGER NOT NULL,
            height INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(entry_id) REFERENCES tool_data(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "INSERT INTO images (id, session_id, entry_id, format, size_bytes, width, height, created_at) " +
            "SELECT id, session_id, NULL, 'JPEG', size_bytes, width, height, created_at FROM attached_images",
        "DROP TABLE attached_images",
        "CREATE INDEX IF NOT EXISTS index_images_session_id ON images(session_id)",
        "CREATE INDEX IF NOT EXISTS index_images_entry_id ON images(entry_id)"
    )

    /** The backup's [data] with its images under their v82 key. */
    fun backup(data: JSONObject) {
        val images = data.optJSONArray("attached_images") ?: return
        data.remove("attached_images")
        data.put("images", JSONArray().apply {
            for (i in 0 until images.length()) put(JSONObject(images.getJSONObject(i).toString()).put("format", "JPEG"))
        })
    }
}
