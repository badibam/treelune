package app.treelune.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.treelune.core.database.entities.LogEntry

/**
 * DAO for log entries
 *
 * Provides queries for:
 * - Inserting new log entries
 * - Fetching logs with filters (level, time range)
 * - Clearing old logs
 *
 * Logs are always ordered by timestamp DESC (newest first)
 */
@Dao
interface LogDao {

    /**
     * Insert a new log entry
     */
    @Insert
    suspend fun insertLog(log: LogEntry)

    /**
     * Get logs filtered by time range and optional tag pattern
     *
     * @param sinceTimestamp Minimum timestamp (logs newer than this)
     * @param tagPattern Tag pattern for LIKE query (e.g., "ai%" for all AI tags, "%" for all tags)
     * @return List of logs ordered by timestamp DESC (newest first)
     */
    @Query("""
        SELECT * FROM log_entries
        WHERE timestamp >= :sinceTimestamp
          AND LOWER(tag) LIKE LOWER(:tagPattern)
          AND level IN (:levels)
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getLogsFiltered(
        sinceTimestamp: Long,
        tagPattern: String,
        levels: List<String>,
        limit: Int
    ): List<LogEntry>

    /**
     * Delete all logs
     * For testing or manual cleanup
     */

    /**
     * Count the logs of the given levels.
     *
     * The chatty levels and the ones worth keeping are purged against separate ceilings, so a
     * busy minute of DEBUG cannot push yesterday's error out of the table.
     */
    @Query("SELECT COUNT(*) FROM log_entries WHERE level IN (:levels)")
    suspend fun getLogCountForLevels(levels: List<String>): Int

    /**
     * Deletes the logs of the given levels beyond the [keep] most recent, ordered by time then by
     * row: lines written within the same millisecond are told apart, so a purge always reaches
     * its ceiling. Returns how many went.
     */
    @Query("""
        DELETE FROM log_entries
        WHERE level IN (:levels) AND id NOT IN (
            SELECT id FROM log_entries WHERE level IN (:levels) ORDER BY timestamp DESC, id DESC LIMIT :keep
        )
    """)
    suspend fun deleteBeyondForLevels(levels: List<String>, keep: Int): Int

    @Query("DELETE FROM log_entries")
    suspend fun deleteAllLogs()
}
