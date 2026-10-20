package app.treelune.core.history

import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import app.treelune.core.database.AppDatabase
import org.json.JSONObject

/**
 * A row of a covered table read and written whole, by its columns, whatever its table: the copy
 * the history keeps of it (a JSON object, column → value) and the row put back from that copy.
 *
 * Every call runs on the writer connection, inside the transaction of the write it serves when
 * there is one (Room keeps the connection with the coroutine), so a copy read before a write sees
 * exactly what the write replaces.
 */
internal object Rows {

    /** The rows of [table] whose [column] is [value], each as its copy; [column] is a column name, never a value. */
    suspend fun read(database: AppDatabase, table: CoveredTable, column: String, value: String): List<JSONObject> =
        database.useWriterConnection { connection ->
            connection.usePrepared("SELECT * FROM ${table.tableName} WHERE `$column` = ?") { statement ->
                statement.bindText(1, value)
                buildList { while (statement.step()) add(copyOf(statement)) }
            }
        }

    /** The row of [table] whose id is [id], as its copy; null when there is none. */
    suspend fun read(database: AppDatabase, table: CoveredTable, id: String): JSONObject? =
        read(database, table, table.idColumn, id).singleOrNull()

    /**
     * Puts [row] back in [table] as it is: an update of every column when a row of its id is
     * there, an insert when none is. Never an INSERT OR REPLACE, which deletes the row it replaces,
     * and with it by cascade what hangs from it.
     */
    suspend fun put(database: AppDatabase, table: CoveredTable, row: JSONObject) {
        val id = row.getString(table.idColumn)
        val columns = row.keys().asSequence().toList()
        database.useWriterConnection { connection ->
            val present = connection.usePrepared("SELECT 1 FROM ${table.tableName} WHERE `${table.idColumn}` = ?") {
                it.bindText(1, id)
                it.step()
            }
            val sql = if (present) {
                "UPDATE ${table.tableName} SET ${columns.joinToString { "`$it` = ?" }} WHERE `${table.idColumn}` = ?"
            } else {
                "INSERT INTO ${table.tableName} (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})"
            }
            connection.usePrepared(sql) { statement ->
                columns.forEachIndexed { i, column -> bind(statement, i + 1, row.opt(column)) }
                if (present) statement.bindText(columns.size + 1, id)
                statement.step()
            }
        }
    }

    /** Deletes the row of [table] whose id is [id]; what hangs from it goes by cascade, read before by the caller. */
    suspend fun delete(database: AppDatabase, table: CoveredTable, id: String) {
        database.useWriterConnection { connection ->
            connection.usePrepared("DELETE FROM ${table.tableName} WHERE `${table.idColumn}` = ?") {
                it.bindText(1, id)
                it.step()
            }
        }
    }

    private fun copyOf(statement: SQLiteStatement): JSONObject {
        val row = JSONObject()
        for (column in 0 until statement.getColumnCount()) {
            val name = statement.getColumnName(column)
            when (statement.getColumnType(column)) {
                SQLITE_DATA_NULL -> row.put(name, JSONObject.NULL)
                SQLITE_DATA_INTEGER -> row.put(name, statement.getLong(column))
                SQLITE_DATA_FLOAT -> row.put(name, statement.getDouble(column))
                // No covered table holds bytes: one met is a column this copy cannot keep
                SQLITE_DATA_BLOB -> throw IllegalStateException("column $name holds bytes, which the history does not copy")
                else -> row.put(name, statement.getText(column))
            }
        }
        return row
    }

    private fun bind(statement: SQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> statement.bindNull(index)
            is String -> statement.bindText(index, value)
            is Int, is Long -> statement.bindLong(index, (value as Number).toLong())
            is Number -> statement.bindDouble(index, value.toDouble())
            is Boolean -> statement.bindLong(index, if (value) 1 else 0)
            else -> throw IllegalArgumentException("a copy's value of ${value::class.simpleName} at $index")
        }
    }
}
