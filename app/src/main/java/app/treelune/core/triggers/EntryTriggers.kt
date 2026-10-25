package app.treelune.core.triggers

import android.content.Context
import app.treelune.core.coordinator.Chains
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskRun
import app.treelune.core.tasks.TaskRunner
import app.treelune.core.utils.LogManager

/**
 * What the entries a step wrote start (docs/CORE.md, « Triggers »): each Task switched on whose
 * triggers listen to one of them, once per entry and trigger it passes, the run receiving the
 * entry. Called once the step is done — by the coordinator for an operation, by the history for
 * an undo or a redo — inside the context of whoever wrote, so that a run started continues the
 * writer's chain (Chains), refused past its cap.
 *
 * A long operation (an import, a backup restored, the demo installed) starts nothing: the app
 * reloads data, nobody notes it.
 */
object EntryTriggers {

    /** What [written] gives starts what listens to it; [written] is read only when something listens. */
    suspend fun dispatch(context: Context, written: suspend () -> List<EntryWrites.EntryWrite>) {
        val tasks = AppDatabase.getDatabase(context).taskDao().getAll().filter { it.isEnabled && it.triggersJson != null }
        if (tasks.isEmpty()) return
        val writes = written()
        if (writes.isEmpty()) return
        for (task in tasks) {
            val triggers = try {
                Triggers.read(task.triggersJson)
            } catch (e: IllegalArgumentException) {
                LogManager.service("Task ${task.id}: triggers not read: ${e.message}", "ERROR", e)
                continue
            }
            for (write in writes) {
                // One run per entry, whichever of its triggers it passes first
                val heard = triggers.filterIsInstance<Triggers.Trigger.EntryWritten>().any { trigger ->
                    trigger.toolInstanceId == write.toolInstanceId && write.write in trigger.writes && EntryWrites.passes(context, write, trigger.filters)
                }
                if (!heard) continue
                val chainId = when (val admission = Chains.admit(context, task.name)) {
                    is Chains.Admission.Capped -> continue
                    is Chains.Admission.Continues -> admission.chainId
                    Chains.Admission.Starts -> null
                }
                TaskRunner.start(context, task, TaskRun.Trigger.ENTRY, System.currentTimeMillis(), awaited = false, chainId = chainId,
                    received = TaskRun.Received(write.entryId, write.toolInstanceId, write.write))
            }
        }
    }
}
