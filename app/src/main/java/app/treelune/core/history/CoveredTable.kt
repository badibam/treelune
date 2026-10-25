package app.treelune.core.history

/**
 * The tables the history covers (docs/DATA.md, « History of writes »), in the order
 * a restoration puts them back: a parent before the rows that hang from it.
 *
 * A session, its messages and its files are covered only when the session is a routine's
 * message (SessionType.SEED), a row following what it belongs to; a conversation's are written by
 * the same door, and left out of the history.
 *
 * @property tableName Its name in the database
 * @property idColumn The column that identifies a row
 * @property children What SQLite deletes with one of its rows (onDelete = CASCADE), which a deletion
 *           reads before: the table, and its column naming the parent
 */
enum class CoveredTable(
    val tableName: String,
    val idColumn: String = "id",
    val children: List<Pair<String, String>> = emptyList()
) {
    IDENTITIES("identities"),
    ZONES("zones", children = listOf("tool_instances" to "zone_id", "variables" to "zone_id", "events" to "zone_id")),
    TOOL_INSTANCES("tool_instances", children = listOf("tool_data" to "tool_instance_id", "files" to "tool_instance_id")),
    TOOL_DATA("tool_data", children = listOf("files" to "entry_id")),
    VARIABLES("variables"),
    EVENTS("events"),
    AUTOMATIONS("automations"),
    AI_SESSIONS("ai_sessions", children = listOf("session_messages" to "session_id", "files" to "session_id")),
    SESSION_MESSAGES("session_messages"),
    FILES("files"),
    MCP_CLIENTS("mcp_clients"),
    APP_SETTINGS("app_settings_categories", idColumn = "category");

    companion object {
        /** The covered table named [tableName] in the database. */
        fun of(tableName: String): CoveredTable =
            entries.firstOrNull { it.tableName == tableName } ?: throw IllegalArgumentException("table $tableName is not covered by the history")

        /**
         * The tables SQLite deletes with a covered row that the history leaves out: a connector
         * client's tokens, which are not restored with it.
         */
        val CHILDREN_LEFT_OUT = setOf("mcp_clients" to "mcp_tokens")
    }
}
