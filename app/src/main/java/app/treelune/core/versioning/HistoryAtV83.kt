package app.treelune.core.versioning

/**
 * The history of writes (docs/design/undo-history.md): its two tables, empty, the history beginning
 * with the first write after the update. A backup carries no history, so its import has nothing
 * to transform.
 */
object HistoryAtV83 {

    /** The migration's statements, run in order on the v82 tables. */
    val STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS history_steps (
            id TEXT NOT NULL,
            source TEXT NOT NULL,
            is_step INTEGER NOT NULL,
            at INTEGER NOT NULL,
            session_id TEXT,
            phrase TEXT,
            state TEXT NOT NULL,
            state_by TEXT,
            state_at INTEGER,
            PRIMARY KEY(id)
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_history_steps_at ON history_steps(at)",
        """
        CREATE TABLE IF NOT EXISTS history_rows (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            step_id TEXT NOT NULL,
            table_name TEXT NOT NULL,
            row_id TEXT NOT NULL,
            tool_instance_id TEXT,
            zone_id TEXT,
            before TEXT,
            after TEXT,
            size_bytes INTEGER NOT NULL,
            FOREIGN KEY(step_id) REFERENCES history_steps(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS index_history_rows_step_id ON history_rows(step_id)",
        "CREATE INDEX IF NOT EXISTS index_history_rows_table_name_row_id ON history_rows(table_name, row_id)",
        "CREATE INDEX IF NOT EXISTS index_history_rows_tool_instance_id ON history_rows(tool_instance_id)",
        "CREATE INDEX IF NOT EXISTS index_history_rows_zone_id ON history_rows(zone_id)"
    )
}
