package app.treelune.core.database

import android.database.Cursor
import android.database.MatrixCursor
import androidx.room.migration.Migration
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

/**
 * The database as a migration writes and reads it, on the connection Room hands it: the app's
 * SQLite is its own (BundledSQLiteDriver), so Room gives a migration a connection, not a
 * SupportSQLiteDatabase. It keeps the two calls the migrations are written with.
 */
class MigrationDatabase(private val connection: SQLiteConnection) {

    /** Runs one statement. */
    fun execSQL(sql: String) = connection.execSQL(sql)

    /** Runs one statement with [args] bound in order: text, whole or decimal numbers, or null. */
    fun execSQL(sql: String, args: Array<out Any?>) {
        connection.prepare(sql).use { statement ->
            bind(statement, args)
            statement.step()
        }
    }

    /**
     * The rows of [sql], [args] bound as in [execSQL], read whole before the cursor is returned:
     * a migration may then rewrite the table it reads while it walks the rows.
     */
    fun query(sql: String, args: Array<out Any?> = emptyArray()): Cursor = connection.prepare(sql).use { statement ->
        bind(statement, args)
        val cursor = MatrixCursor(statement.getColumnNames().toTypedArray())
        while (statement.step()) {
            cursor.addRow(Array(statement.getColumnCount()) { column ->
                when (statement.getColumnType(column)) {
                    SQLITE_DATA_NULL -> null
                    SQLITE_DATA_INTEGER -> statement.getLong(column)
                    SQLITE_DATA_FLOAT -> statement.getDouble(column)
                    SQLITE_DATA_BLOB -> statement.getBlob(column)
                    else -> statement.getText(column)
                }
            })
        }
        cursor
    }

    private fun bind(statement: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, value ->
            val index = i + 1
            when (value) {
                null -> statement.bindNull(index)
                is String -> statement.bindText(index, value)
                is Int, is Long, is Short, is Byte -> statement.bindLong(index, (value as Number).toLong())
                is Float, is Double -> statement.bindDouble(index, (value as Number).toDouble())
                is Boolean -> statement.bindLong(index, if (value) 1 else 0)
                is ByteArray -> statement.bindBlob(index, value)
                else -> throw IllegalArgumentException("Cannot bind ${value::class.simpleName} at $index")
            }
        }
    }
}

/** A migration written against [MigrationDatabase], which Room runs on its connection. */
abstract class ConnectionMigration(startVersion: Int, endVersion: Int) : Migration(startVersion, endVersion) {
    final override fun migrate(connection: SQLiteConnection) = migrate(MigrationDatabase(connection))

    abstract fun migrate(database: MigrationDatabase)
}
