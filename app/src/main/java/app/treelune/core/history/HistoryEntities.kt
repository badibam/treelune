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

/** A table a step touched, and the tool and zone of its rows there. */
data class StepPlace(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "tool_instance_id") val toolInstanceId: String?,
    @ColumnInfo(name = "zone_id") val zoneId: String?
)

/** A step, or the app's noted writes, with its time and the room its rows take. */
data class StepSize(
    val id: String,
    @ColumnInfo(name = "is_step") val isStep: Boolean,
    val at: Long,
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

    /**
     * The steps — never the app's own writes — that touched the tool [toolInstanceId] or the zone
     * [zoneId] (either or neither given), between [from] and [to], the most recent first.
     */
    @Query("""
        SELECT * FROM history_steps s WHERE s.is_step = 1 AND s.at BETWEEN :from AND :to
        AND (:toolInstanceId IS NULL AND :zoneId IS NULL OR EXISTS (
            SELECT 1 FROM history_rows r WHERE r.step_id = s.id
            AND (:toolInstanceId IS NULL OR r.tool_instance_id = :toolInstanceId)
            AND (:zoneId IS NULL OR r.zone_id = :zoneId)))
        ORDER BY s.at DESC, s.rowid DESC LIMIT :limit
    """)
    suspend fun listSteps(toolInstanceId: String?, zoneId: String?, from: Long, to: Long, limit: Int): List<HistoryStepEntity>

    /** The tools, zones and tables a step touched, for what an undo tells the screens. */
    @Query("SELECT DISTINCT table_name, tool_instance_id, zone_id FROM history_rows WHERE step_id = :stepId")
    suspend fun placesOf(stepId: String): List<StepPlace>

    /** The room the history takes, its copies and the files of the images it keeps. */
    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM history_rows")
    suspend fun totalSize(): Long

    /** When the oldest step still kept was made; null for a history with no step. */
    @Query("SELECT MIN(at) FROM history_steps WHERE is_step = 1")
    suspend fun oldestStep(): Long?

    /** The images a copy of the history keeps, whose files stay while it does. */
    @Query("SELECT DISTINCT row_id FROM history_rows WHERE table_name = 'images'")
    suspend fun keptImages(): List<String>

    /** Whether an entry's copy of the history names [imageId]: an id is a UUID, found in a copy's text only as a value. */
    @Query("SELECT EXISTS (SELECT 1 FROM history_rows WHERE table_name = 'tool_data' AND (before LIKE '%' || :imageId || '%' OR after LIKE '%' || :imageId || '%'))")
    suspend fun entriesName(imageId: String): Boolean

    /** Each step and noted write with the room its rows take, the most recent first. */
    @Query("""
        SELECT s.id AS id, s.is_step AS is_step, s.at AS at, COALESCE(SUM(r.size_bytes), 0) AS size_bytes
        FROM history_steps s LEFT JOIN history_rows r ON r.step_id = s.id
        GROUP BY s.id ORDER BY s.at DESC, s.rowid DESC
    """)
    suspend fun sizes(): List<StepSize>

    @Query("DELETE FROM history_steps WHERE id IN (:ids)")
    suspend fun deleteSteps(ids: List<String>)

    /** Empties the history: a backup restored replaces the data it was about. */
    @Query("DELETE FROM history_steps")
    suspend fun clear()

    /** The rows that touched one row of a table, oldest first. */
    @Query("SELECT * FROM history_rows WHERE table_name = :table AND row_id = :rowId ORDER BY id ASC")
    suspend fun getRowsOf(table: String, rowId: String): List<HistoryRowEntity>
}
