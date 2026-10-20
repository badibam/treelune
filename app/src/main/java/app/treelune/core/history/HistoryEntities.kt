package app.treelune.core.history

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A step of the history (docs/design/undo-history.md): one operation asked from outside, or the
 * app's own writes noted under one (isStep false). Its rows are in history_rows.
 *
 * Steps are ordered by their time, then by the order they were written in (rowid).
 */
@Entity(tableName = "history_steps", indices = [Index(value = ["at"])])
data class HistoryStepEntity(
    @PrimaryKey val id: String,
    /** A Source's name: who asked for it */
    val source: String,
    /** False for the app's own writes, asked by no one: noted, never shown nor undone */
    @ColumnInfo(name = "is_step") val isStep: Boolean,
    val at: Long,
    /** The AI's session whose command it is, null for any other */
    @ColumnInfo(name = "session_id") val sessionId: String?,
    /** What it did, in words, written once it is done (the service's verbalize()) and never again */
    val phrase: String?,
    /** DONE or UNDONE (HistoryState) */
    val state: String,
    /** A Source's name: who last undid or redid it; null while never undone */
    @ColumnInfo(name = "state_by") val stateBy: String?,
    @ColumnInfo(name = "state_at") val stateAt: Long?
)

/** The two states of a step: done, or undone. */
object HistoryState {
    const val DONE = "DONE"
    const val UNDONE = "UNDONE"
}

/**
 * A row a step touched, whole before and after it: [before] null for a creation, [after] null
 * for a deletion. A row is the JSON object of its columns, as the table holds them (Rows).
 */
@Entity(
    tableName = "history_rows",
    foreignKeys = [ForeignKey(entity = HistoryStepEntity::class, parentColumns = ["id"], childColumns = ["step_id"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["step_id"]),
        Index(value = ["table_name", "row_id"]),
        Index(value = ["tool_instance_id"]),
        Index(value = ["zone_id"])
    ]
)
data class HistoryRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "step_id") val stepId: String,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** The tool it concerns, for the history of one tool; null when it concerns none */
    @ColumnInfo(name = "tool_instance_id") val toolInstanceId: String?,
    /** The zone it concerns, for the history of one zone; null when it concerns none */
    @ColumnInfo(name = "zone_id") val zoneId: String?,
    val before: String?,
    val after: String?,
    /** The room it takes: its two copies, and the file of an image it names */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_steps WHERE id = :id")
    suspend fun getStep(id: String): HistoryStepEntity?

    /** Every step and the app's noted writes, oldest first. */
    @Query("SELECT * FROM history_steps ORDER BY at ASC, rowid ASC")
    suspend fun allSteps(): List<HistoryStepEntity>

    @Query("SELECT * FROM history_rows WHERE step_id = :stepId ORDER BY id ASC")
    suspend fun getRows(stepId: String): List<HistoryRowEntity>

    /** The rows that touched one row of a table, oldest first. */
    @Query("SELECT * FROM history_rows WHERE table_name = :table AND row_id = :rowId ORDER BY id ASC")
    suspend fun getRowsOf(table: String, rowId: String): List<HistoryRowEntity>
}
