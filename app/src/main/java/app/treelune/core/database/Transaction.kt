package app.treelune.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs [block] as one write transaction, the DAO calls made inside it included: all of it is
 * written, or none. A transaction opened inside another is a savepoint of the outer one.
 */
suspend fun <R> AppDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
