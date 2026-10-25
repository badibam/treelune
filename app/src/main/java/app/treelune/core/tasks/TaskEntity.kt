package app.treelune.core.tasks

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import app.treelune.core.database.entities.Zone

/**
 * A Task (docs/CORE.md, « Tasks »): steps the app runs itself, without an AI, in a zone, shown as a
 * tile of its group's section as a routine is. Its look, its group, its switch, its schedule and
 * what it does about missed runs are its settings (TaskSettings); its steps a list (TaskSteps); its
 * access a mask (AccessMask) its steps are held to. Deleted with its zone.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zone_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["zone_id"])]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "icon_color") val iconColor: String?,
    val texture: String?,
    @ColumnInfo(name = "display_mode") val displayMode: String,
    val group: String?,
    @ColumnInfo(name = "grid_x") val gridX: Int,
    @ColumnInfo(name = "grid_y") val gridY: Int,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    /** ScheduleConfig as JSON; null for a Task run on demand alone */
    @ColumnInfo(name = "schedule_json") val scheduleJson: String?,
    /** TaskSettings.LIMITED or UNLIMITED with a schedule; null without */
    @ColumnInfo(name = "catch_up") val catchUp: String?,
    @ColumnInfo(name = "catch_up_window") val catchUpWindow: Long?,
    @ColumnInfo(name = "dismiss_older_instances") val dismissOlderInstances: Boolean,
    /** Which of its runs wait for the user once ended (AutomationSettings.NOTIFY_*) */
    @ColumnInfo(name = "notify_end") val notifyEnd: String,
    /** AccessMask's stored form; empty, everything */
    @ColumnInfo(name = "access_json") val accessJson: String,
    /** Its steps, TaskSteps' stored form */
    @ColumnInfo(name = "steps_json") val stepsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * One run of a Task: when it was asked and what for (its trigger, the instant its readings
 * resolve against), the step it stands at, what each step done gave, and where it stands. A run
 * waiting on a deferred API keeps the job it follows and when to ask again, and outlives the app.
 */
@Entity(
    tableName = "task_runs",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["task_id"]), Index(value = ["state"])]
)
data class TaskRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    /** TaskRun.Trigger's key: manual, scheduled, ai */
    val trigger: String,
    /** The instant its readings resolve against: its occurrence's for a scheduled run, its start's otherwise */
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    /** TaskRun.State's key */
    val state: String,
    /** The index of the step it runs or waits on; the number of steps once done */
    val step: Int,
    /** What each step reached gave, TaskRun.StepResult's stored form, in their order */
    @ColumnInfo(name = "results_json") val resultsJson: String,
    /** While waiting: the API followed, the job's id, when to ask again, at what pace, until when */
    @ColumnInfo(name = "wait_json") val waitJson: String?,
    /** When the user read its end; null while it waits to be read */
    @ColumnInfo(name = "seen_at") val seenAt: Long?
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE zone_id = :zoneId")
    suspend fun getByZone(zoneId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET grid_x = :gridX, grid_y = :gridY WHERE id = :id")
    suspend fun updatePosition(id: String, gridX: Int, gridY: Int)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM task_runs WHERE id = :id")
    suspend fun getRun(id: String): TaskRunEntity?

    @Query("SELECT * FROM task_runs WHERE task_id = :taskId ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRuns(taskId: String, limit: Int): List<TaskRunEntity>

    @Query("SELECT * FROM task_runs WHERE state IN (:states)")
    suspend fun getRunsIn(states: List<String>): List<TaskRunEntity>

    /** The last run of [taskId] its schedule started, whatever its end: where its next occurrence is searched from. */
    @Query("SELECT * FROM task_runs WHERE task_id = :taskId AND trigger = 'scheduled' ORDER BY scheduled_at DESC LIMIT 1")
    suspend fun getLastScheduledRun(taskId: String): TaskRunEntity?

    @Query("SELECT * FROM task_runs WHERE seen_at IS NULL AND ended_at IS NOT NULL")
    suspend fun getUnseenRuns(): List<TaskRunEntity>

    @Insert
    suspend fun insertRun(run: TaskRunEntity)

    @Update
    suspend fun updateRun(run: TaskRunEntity)

    @Query("UPDATE task_runs SET seen_at = :at WHERE id = :id AND seen_at IS NULL")
    suspend fun markSeen(id: String, at: Long)
}
