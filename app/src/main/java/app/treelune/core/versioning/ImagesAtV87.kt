package app.treelune.core.versioning

/**
 * Brings the images to their v87 form (docs/DATA.md, « IMAGE »): a tool can own an image too, one
 * its config holds (a step's images), under `tool_instance_id`, its row going with the tool by
 * cascade. SQLite adds no foreign key to a table in place: the table is made again, every image
 * kept so far a message's or an entry's.
 *
 * A backup before v87 needs nothing: its images name no tool, and a column absent is null.
 */
object ImagesAtV87 {

    /** The migration's statements, run in order on the v86 tables. */
    val STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS images_new (
            id TEXT NOT NULL,
            session_id TEXT,
            entry_id TEXT,
            tool_instance_id TEXT,
            format TEXT NOT NULL,
            size_bytes INTEGER NOT NULL,
            width INTEGER NOT NULL,
            height INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(session_id) REFERENCES ai_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(entry_id) REFERENCES tool_data(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(tool_instance_id) REFERENCES tool_instances(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "INSERT INTO images_new (id, session_id, entry_id, tool_instance_id, format, size_bytes, width, height, created_at) " +
            "SELECT id, session_id, entry_id, NULL, format, size_bytes, width, height, created_at FROM images",
        "DROP TABLE images",
        "ALTER TABLE images_new RENAME TO images",
        "CREATE INDEX IF NOT EXISTS index_images_session_id ON images(session_id)",
        "CREATE INDEX IF NOT EXISTS index_images_entry_id ON images(entry_id)",
        "CREATE INDEX IF NOT EXISTS index_images_tool_instance_id ON images(tool_instance_id)"
    )
}
