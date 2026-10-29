package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.ai.scheduling.CatchUpPolicy
import app.treelune.core.database.AppDatabase
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.ScheduleConfig
import kotlinx.serialization.json.Json

/**
 * The Tasks at each tick of the scheduler (CoreScheduler): a run cut by the app stopping said
 * interrupted, a run waiting on a deferred API asked where its job stands, then each Task whose
 * schedule has an occurrence due run — the routine's rules for its missed runs (CatchUpPolicy),
 * one run of a Task at a time: an occurrence due while a run waits is run once it ends.
 */
object TaskScheduler {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun tick(context: Context) {
        val dao = AppDatabase.getDatabase(context).taskDao()
        val now = System.currentTimeMillis()
        dao.getRunsIn(listOf(TaskRun.State.RUNNING.key)).filter { !TaskRunner.isActive(it.id) }.forEach { TaskRunner.interrupted(context, it) }
        // A run waiting its turn whose Task runs nothing (the app stopped meanwhile) takes it
        val going = dao.getRunsIn(listOf(TaskRun.State.RUNNING.key, TaskRun.State.WAITING.key)).map { it.taskId }.toSet()
        dao.getRunsIn(listOf(TaskRun.State.QUEUED.key)).map { it.taskId }.distinct().filter { it !in going }.forEach { TaskRunner.next(context, it) }
        dao.getRunsIn(listOf(TaskRun.State.WAITING.key)).forEach { run ->
            try {
                TaskRunner.poll(context, run, now)
            } catch (e: Exception) {
                LogManager.service("TaskScheduler: run ${run.id} not asked: ${e.message}", "ERROR", e)
            }
        }
        val busy = dao.getRunsIn(listOf(TaskRun.State.RUNNING.key, TaskRun.State.WAITING.key)).map { it.taskId }.toSet()
        for (task in dao.getAll().filter { it.isEnabled && it.scheduleJson != null && it.id !in busy }) {
            try {
                val due = due(context, task, System.currentTimeMillis()) ?: continue
                TaskRunner.start(context, task, TaskRun.Trigger.SCHEDULED, due, awaited = true)
            } catch (e: Exception) {
                LogManager.service("TaskScheduler: task ${task.id} not run: ${e.message}", "ERROR", e)
            }
        }
    }

    /** The occurrence of [task]'s schedule due at [now], the one its missed runs' rules pick; null when none is. */
    suspend fun due(context: Context, task: TaskEntity, now: Long): Long? {
        val schedule = json.decodeFromString<ScheduleConfig>(task.scheduleJson ?: return null)
        val from = searchStart(context, task, now)
        val next = ScheduleCalculator.calculateNextExecution(pattern = schedule.pattern, fromTimestamp = from) ?: return null
        if (next > now) return null
        return if (task.dismissOlderInstances) CatchUpPolicy.lastDueOccurrence(schedule, from, now, next) else next
    }

    /** When [task] runs next by its schedule; null when it will not (off, without a schedule, its schedule over). */
    suspend fun nextAt(context: Context, task: TaskEntity): Long? {
        if (!task.isEnabled) return null
        val schedule = json.decodeFromString<ScheduleConfig>(task.scheduleJson ?: return null)
        return ScheduleCalculator.calculateNextExecution(pattern = schedule.pattern, fromTimestamp = searchStart(context, task, System.currentTimeMillis()))
    }

    /** Where the next occurrence is searched from: the last scheduled run's, or the Task's last change, the window pulling it forward. */
    private suspend fun searchStart(context: Context, task: TaskEntity, now: Long): Long = CatchUpPolicy.searchStart(
        lastExecutionTime = AppDatabase.getDatabase(context).taskDao().getLastScheduledRun(task.id)?.scheduledAt ?: 0L,
        routineUpdatedAt = task.updatedAt,
        catchUpWindow = task.catchUpWindow,
        now = now
    )
}
