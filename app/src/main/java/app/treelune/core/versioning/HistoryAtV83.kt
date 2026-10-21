package app.treelune.core.versioning

import app.treelune.core.config.AppSettings
import app.treelune.core.database.entities.AppSettingCategories
import org.json.JSONObject

/**
 * The history of writes (docs/DATA.md, « History of writes »): its two tables, empty, the history beginning
 * with the first write after the update; and its depth, joining the data settings: 30 days, 500 MB.
 * A backup carries no history: its import gains the depth alone (dataSettings, through
 * JsonTransformers.transformAppConfig).
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

    /** [settings] of [category], the data settings with the history's depth. */
    fun dataSettings(category: String, settings: JSONObject): JSONObject {
        if (category != AppSettingCategories.DATA) return settings
        val out = JSONObject(settings.toString())
        if (!out.has(AppSettings.DATA_HISTORY_DAYS)) out.put(AppSettings.DATA_HISTORY_DAYS, DAYS)
        if (!out.has(AppSettings.DATA_HISTORY_MEGABYTES)) out.put(AppSettings.DATA_HISTORY_MEGABYTES, MEGABYTES)
        return out
    }

    /** The depth a history starts with: also the defaults of a new installation (AppSettingsDefaults) */
    const val DAYS = 30
    const val MEGABYTES = 500
}
