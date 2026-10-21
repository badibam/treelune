package app.treelune.core.history

import android.content.Context
import androidx.room.useWriterConnection
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.AutomationEntity
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingsCategory
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.identities.IdentityEntity
import app.treelune.core.images.ImageEntity
import app.treelune.core.mcp.McpClientEntity
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.database.entities.VariableEntity
import app.treelune.core.database.entities.Zone
import app.treelune.core.database.transaction
import org.json.JSONObject

/**
 * The one door of every write to a covered table (docs/DATA.md, « History of writes »): it reads the rows a write is about to change, makes the write, reads them again, and
 * keeps both copies in the step of the operation running, all in one transaction. A check of the
 * sources (`scripts/check_history_writes.py`, run by `./run test`) refuses a write to these tables
 * made anywhere else.
 *
 * A deletion reads besides what SQLite deletes with the row (CoveredTable.children), down to the
 * last level, so that the undo puts it all back.
 *
 * What changes nothing the history keeps — a row written as it was, a conversation's row, a part
 * the app alone writes (Kept) — is written without a word to the history, and needs no step: the
 * app writes a session's activity or a client's last use outside any operation.
 */
class Writes(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val kept = Kept(context, database)

    // ── Zones ──

    suspend fun insertZone(zone: Zone) = write(CoveredTable.ZONES, zone.id) { database.zoneDao().insertZone(zone) }

    suspend fun updateZone(zone: Zone) = write(CoveredTable.ZONES, zone.id) { database.zoneDao().updateZone(zone) }

    /** Moves the zone [id] to its column and row on the home screen. */
    suspend fun moveZone(id: String, gridX: Int, gridY: Int) = write(CoveredTable.ZONES, id) { database.zoneDao().updatePosition(id, gridX, gridY) }

    suspend fun deleteZone(id: String) = delete(CoveredTable.ZONES, id) { database.zoneDao().deleteZoneById(id) }

    // ── Tools ──

    suspend fun insertTool(tool: ToolInstance) = write(CoveredTable.TOOL_INSTANCES, tool.id) { database.toolInstanceDao().insertToolInstance(tool) }

    suspend fun updateTool(tool: ToolInstance) = write(CoveredTable.TOOL_INSTANCES, tool.id) { database.toolInstanceDao().updateToolInstance(tool) }

    /** Moves the tool [id] to its column and row in its zone's grid. */
    suspend fun moveTool(id: String, gridX: Int, gridY: Int) =
        write(CoveredTable.TOOL_INSTANCES, id) { database.toolInstanceDao().updatePosition(id, gridX, gridY) }

    suspend fun deleteTool(id: String) = delete(CoveredTable.TOOL_INSTANCES, id) { database.toolInstanceDao().deleteToolInstanceById(id) }

    // ── Entries ──

    suspend fun insertEntry(entry: ToolDataEntity) = write(CoveredTable.TOOL_DATA, entry.id) { database.toolDataDao().insert(entry) }

    suspend fun updateEntry(entry: ToolDataEntity) = write(CoveredTable.TOOL_DATA, entry.id) { database.toolDataDao().update(entry) }

    suspend fun deleteEntry(id: String) = delete(CoveredTable.TOOL_DATA, id) { database.toolDataDao().deleteById(id) }

    /** Deletes every entry of the tool [toolInstanceId], each kept with what goes along with it. */
    suspend fun deleteEntriesOf(toolInstanceId: String) = database.transaction {
        val gone = Rows.read(database, CoveredTable.TOOL_DATA, "tool_instance_id", toolInstanceId)
            .flatMap { descent(CoveredTable.TOOL_DATA, it.getString("id")) }
        database.toolDataDao().deleteByToolInstance(toolInstanceId)
        record(gone)
    }

    // ── Variables ──

    suspend fun insertVariable(variable: VariableEntity) = write(CoveredTable.VARIABLES, variable.id) { database.variableDao().insert(variable) }

    suspend fun updateVariable(variable: VariableEntity) = write(CoveredTable.VARIABLES, variable.id) { database.variableDao().update(variable) }

    suspend fun deleteVariable(id: String) = delete(CoveredTable.VARIABLES, id) { database.variableDao().deleteById(id) }

    // ── Automations ──

    suspend fun insertAutomation(automation: AutomationEntity) =
        write(CoveredTable.AUTOMATIONS, automation.id) { database.aiDao().insertAutomation(automation) }

    suspend fun updateAutomation(automation: AutomationEntity) =
        write(CoveredTable.AUTOMATIONS, automation.id) { database.aiDao().updateAutomation(automation) }

    suspend fun setAutomationEnabled(id: String, enabled: Boolean, updatedAt: Long) =
        write(CoveredTable.AUTOMATIONS, id) { database.aiDao().setAutomationEnabled(id, enabled, updatedAt) }

    suspend fun deleteAutomation(id: String) = delete(CoveredTable.AUTOMATIONS, id) { database.aiDao().deleteAutomationById(id) }

    // ── Sessions and their messages: kept for a routine's message alone (covers) ──

    suspend fun insertSession(session: AISessionEntity) = write(CoveredTable.AI_SESSIONS, session.id) { database.aiDao().insertSession(session) }

    suspend fun insertMessage(message: SessionMessageEntity) =
        write(CoveredTable.SESSION_MESSAGES, message.id) { database.aiDao().insertMessage(message) }

    suspend fun updateMessage(message: SessionMessageEntity) =
        write(CoveredTable.SESSION_MESSAGES, message.id) { database.aiDao().updateMessage(message) }

    // ── Settings ──

    /** Writes the settings of [category], sealed as the database holds them. */
    suspend fun updateSettings(category: String, settings: String) =
        write(CoveredTable.APP_SETTINGS, category) { database.appSettingsCategoryDao().updateSettings(category, settings) }

    /**
     * Writes the defaults of a category that has no row yet. Not a word to the history: a category
     * without its row reads as its defaults, so writing them changes nothing a step could undo.
     */
    suspend fun writeDefaults(settings: AppSettingsCategory) = database.appSettingsCategoryDao().insertOrUpdateSettings(settings)

    // ── Identities ──

    suspend fun putIdentity(identity: IdentityEntity) = write(CoveredTable.IDENTITIES, identity.id) { database.identityDao().upsert(identity) }

    suspend fun deleteIdentity(id: String) = delete(CoveredTable.IDENTITIES, id) { database.identityDao().deleteById(id) }

    // ── The connector's clients ──

    suspend fun insertClient(client: McpClientEntity) = write(CoveredTable.MCP_CLIENTS, client.id) { database.mcpDao().insertClient(client) }

    /** Sets what the client [id] may reach, as the JSON of its mask. */
    suspend fun setClientAccess(id: String, access: String) = write(CoveredTable.MCP_CLIENTS, id) { database.mcpDao().setAccess(id, access) }

    suspend fun deleteClient(id: String) = delete(CoveredTable.MCP_CLIENTS, id) { database.mcpDao().deleteClient(id) }

    // ── Images ──

    suspend fun insertImage(image: ImageEntity) = write(CoveredTable.IMAGES, image.id) { database.imageDao().insert(image) }

    suspend fun deleteImage(id: String) = delete(CoveredTable.IMAGES, id) { database.imageDao().delete(id) }

    // ── A selection ──

    /**
     * Deletes from [tableName] each row whose id [query] selects (its first column), each kept
     * with what goes along with it: the demo's removal (DemoRemoval).
     */
    suspend fun deleteSelected(tableName: String, query: String) = database.transaction {
        val table = CoveredTable.of(tableName)
        val ids = database.useWriterConnection { connection ->
            connection.usePrepared(query) { statement -> buildList { while (statement.step()) add(statement.getText(0)) } }
        }
        ids.forEach { id -> delete(table, id) { Rows.delete(database, table, id) } }
    }

    // ── The door ──

    /** A row of [table] whose id is [id] written by [block], which creates, changes or deletes that row alone. */
    private suspend fun write(table: CoveredTable, id: String, block: suspend () -> Unit) = database.transaction {
        val before = Rows.read(database, table, id)
        val whereBefore = before?.let { where(table, it) }
        block()
        val after = Rows.read(database, table, id)
        val where = whereBefore ?: after?.let { where(table, it) } ?: return@transaction
        record(listOf(Touched(table, id, before, after, where, partsOf(table, (after ?: before)!!, where))))
    }

    /** The row of [table] whose id is [id] deleted by [block], with all that SQLite deletes along with it. */
    private suspend fun delete(table: CoveredTable, id: String, block: suspend () -> Unit) = database.transaction {
        val gone = descent(table, id)
        block()
        record(gone)
    }

    /**
     * One row a write touched: its copies before and after, where it stands (Where), and its
     * parts the app alone writes (Kept), read while its parents are there — an entry's, from its
     * tool's config, which a deletion takes along.
     */
    private data class Touched(
        val table: CoveredTable,
        val id: String,
        val before: JSONObject?,
        val after: JSONObject?,
        val where: Where,
        val parts: List<Kept.Part>
    )

    /** The tool and the zone a row concerns, for the history of one of them. */
    private data class Place(val toolInstanceId: String?, val zoneId: String?)

    /**
     * What a row's parents say of it, read while they are in the database — before a deletion,
     * which takes them along: the tool and zone it concerns, and whether the history keeps it.
     */
    private data class Where(val place: Place?, val covered: Boolean)

    private suspend fun where(table: CoveredTable, row: JSONObject) = Where(place(table, row), covers(table, row))

    /** [row]'s parts the app alone writes, for a row the history keeps; none for another. */
    private suspend fun partsOf(table: CoveredTable, row: JSONObject, where: Where) =
        if (where.covered) kept.of(table, row) else emptyList()

    /**
     * The row of [table] whose id is [id] and everything that hangs from it by cascade, parents
     * first, each as its copy before a deletion, with its place read while its parents are there.
     */
    private suspend fun descent(table: CoveredTable, id: String): List<Touched> {
        val row = Rows.read(database, table, id) ?: return emptyList()
        val where = where(table, row)
        return listOf(Touched(table, id, row, null, where, partsOf(table, row, where))) + childrenOf(table, row)
    }

    private suspend fun childrenOf(table: CoveredTable, row: JSONObject): List<Touched> =
        table.children.flatMap { (childTable, column) ->
            val child = CoveredTable.of(childTable)
            Rows.read(database, child, column, row.getString(table.idColumn)).flatMap { childRow ->
                val where = where(child, childRow)
                listOf(Touched(child, childRow.getString(child.idColumn), childRow, null, where, partsOf(child, childRow, where))) + childrenOf(child, childRow)
            }
        }

    /** Keeps in the step running what [touched] changed of what the history keeps. */
    private suspend fun record(touched: List<Touched>) {
        val changed = touched.mapNotNull { t ->
            if (!t.where.covered) return@mapNotNull null
            val parts = t.parts
            val unchanged = t.before != null && t.after != null &&
                kept.same(kept.without(t.before, parts), kept.without(t.after, parts))
            if (unchanged) null else t
        }
        if (changed.isEmpty()) return
        val step = currentStep()
        database.useWriterConnection { connection ->
            connection.usePrepared(
                "INSERT OR IGNORE INTO history_steps (id, source, is_step, at, session_id, phrase, state, state_by, state_at) VALUES (?, ?, ?, ?, ?, NULL, ?, NULL, NULL)"
            ) { s ->
                s.bindText(1, step.id)
                s.bindText(2, step.source.name)
                s.bindLong(3, if (step.isStep) 1 else 0)
                s.bindLong(4, step.at)
                step.sessionId?.let { s.bindText(5, it) } ?: s.bindNull(5)
                s.bindText(6, HistoryState.DONE)
                s.step()
            }
        }
        changed.forEach { t ->
            val before = t.before?.let { copyOf(t.table, it) }
            val after = t.after?.let { copyOf(t.table, it) }
            val size = (before?.length ?: 0).toLong() + (after?.length ?: 0) + fileSize(t)
            database.useWriterConnection { connection ->
                connection.usePrepared(
                    "INSERT INTO history_rows (step_id, table_name, row_id, tool_instance_id, zone_id, before, after, size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                ) { s ->
                    s.bindText(1, step.id)
                    s.bindText(2, t.table.tableName)
                    s.bindText(3, t.id)
                    t.where.place?.toolInstanceId?.let { s.bindText(4, it) } ?: s.bindNull(4)
                    t.where.place?.zoneId?.let { s.bindText(5, it) } ?: s.bindNull(5)
                    before?.let { s.bindText(6, it) } ?: s.bindNull(6)
                    after?.let { s.bindText(7, it) } ?: s.bindNull(7)
                    s.bindLong(8, size)
                    s.step()
                }
            }
        }
    }

    /** A row's copy as the history keeps it: whole, but for its secrets. */
    private fun copyOf(table: CoveredTable, row: JSONObject): String = kept.without(row, kept.secrets(table, row)).toString()

    /** The room the file of an image takes, kept on the disk while the history names its row; 0 for any other row. */
    private fun fileSize(t: Touched): Long =
        if (t.table == CoveredTable.IMAGES) (t.before ?: t.after)!!.optLong("size_bytes", 0) else 0

    /**
     * Whether the history keeps [row] of [table]: every covered row but a conversation's — a
     * session that is not a routine's message, its messages, its images — and the Guide's progress.
     */
    private suspend fun covers(table: CoveredTable, row: JSONObject): Boolean {
        return when (table) {
            CoveredTable.AI_SESSIONS -> row.optString("type") == SEED
            CoveredTable.SESSION_MESSAGES -> isSeed(row.getString("session_id"))
            CoveredTable.IMAGES -> row.isNull("session_id") || isSeed(row.getString("session_id"))
            CoveredTable.APP_SETTINGS -> row.getString("category") != app.treelune.core.database.entities.AppSettingCategories.GUIDE
            else -> true
        }
    }

    private suspend fun isSeed(sessionId: String): Boolean =
        Rows.read(database, CoveredTable.AI_SESSIONS, sessionId)?.optString("type") == SEED

    /** The tool and zone [row] of [table] concerns, read while its parents are in the database. */
    private suspend fun place(table: CoveredTable, row: JSONObject): Place? = when (table) {
        CoveredTable.ZONES -> Place(null, row.getString("id"))
        CoveredTable.TOOL_INSTANCES -> Place(row.getString("id"), row.getString("zone_id"))
        CoveredTable.TOOL_DATA -> toolPlace(row.getString("tool_instance_id"))
        CoveredTable.VARIABLES, CoveredTable.AUTOMATIONS -> Place(null, row.getString("zone_id"))
        CoveredTable.AI_SESSIONS -> sessionPlace(row.getString("id"))
        CoveredTable.SESSION_MESSAGES -> sessionPlace(row.getString("session_id"))
        CoveredTable.IMAGES -> if (!row.isNull("entry_id")) {
            Rows.read(database, CoveredTable.TOOL_DATA, row.getString("entry_id"))?.let { toolPlace(it.getString("tool_instance_id")) }
        } else sessionPlace(row.getString("session_id"))
        CoveredTable.IDENTITIES, CoveredTable.MCP_CLIENTS, CoveredTable.APP_SETTINGS -> null
    }

    private suspend fun toolPlace(toolInstanceId: String): Place =
        Place(toolInstanceId, Rows.read(database, CoveredTable.TOOL_INSTANCES, toolInstanceId)?.getString("zone_id"))

    /** A routine's message concerns the routine's zone. */
    private suspend fun sessionPlace(sessionId: String): Place? =
        Rows.read(database, CoveredTable.AUTOMATIONS, "seed_session_id", sessionId).firstOrNull()?.let { Place(null, it.getString("zone_id")) }

    private companion object {
        /** SessionType.SEED's name: a routine's message, kept as a session */
        const val SEED = "SEED"
    }
}
