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
        val database = AppDatabase.getDatabase(context)
        val tasks = database.taskDao().getAll().filter { it.isEnabled && it.triggersJson != null }
        val routines = database.aiDao().getAllAutomations().filter { it.isEnabled && it.triggersJson != "[]" }
        if (tasks.isEmpty() && routines.isEmpty()) return
        val writes = written()
        if (writes.isEmpty()) return
        for (task in tasks) {
            for (write in heard(context, "Task ${task.id}", task.triggersJson, writes)) {
                val chainId = chainOf(context, task.name) ?: continue
                TaskRunner.start(context, task, TaskRun.Trigger.ENTRY, System.currentTimeMillis(), awaited = false, chainId = chainId.id,
                    received = TaskRun.Received.Entry(write.entryId, write.toolInstanceId, write.write))
            }
        }
        for (routine in routines) {
            for (write in heard(context, "Routine ${routine.id}", routine.triggersJson, writes)) {
                val chainId = chainOf(context, routine.name) ?: continue
                app.treelune.core.ai.orchestration.AIOrchestrator.executeAutomation(routine.id, scheduledFor = System.currentTimeMillis(),
                    trigger = app.treelune.core.ai.data.ExecutionTrigger.ENTRY, chainId = chainId.id, attached = attached(context, write))
            }
        }
    }

    /** The writes among [writes] the triggers stored in [triggersJson] hear: one per entry, whichever trigger it passes first. */
    private suspend fun heard(context: Context, owner: String, triggersJson: String?, writes: List<EntryWrites.EntryWrite>): List<EntryWrites.EntryWrite> {
        val triggers = try {
            Triggers.read(triggersJson).filterIsInstance<Triggers.Trigger.EntryWritten>()
        } catch (e: IllegalArgumentException) {
            LogManager.service("$owner: triggers not read: ${e.message}", "ERROR", e)
            return emptyList()
        }
        return writes.filter { write ->
            triggers.any { it.toolInstanceId == write.toolInstanceId && write.write in it.writes && EntryWrites.passes(context, write, it.filters) }
        }
    }

    /** The chain a run of [name] continues (its id null when it starts its own); null when it is refused past the cap. */
    private class Chain(val id: String?)

    private suspend fun chainOf(context: Context, name: String): Chain? = when (val admission = Chains.admit(context, name)) {
        is Chains.Admission.Capped -> null
        is Chains.Admission.Continues -> Chain(admission.chainId)
        Chains.Admission.Starts -> Chain(null)
    }

    /**
     * What a routine's run receives of [write], joined to its message: a line saying what
     * started it, then the entry attached as a pointer (read by the AI as any attached entry);
     * a deleted entry, which nothing can read any more, given as it was in the line itself.
     */
    private suspend fun attached(context: Context, write: EntryWrites.EntryWrite): List<app.treelune.core.ai.data.MessageSegment> {
        val s = app.treelune.core.strings.Strings.`for`(context = context)
        val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(write.toolInstanceId)
        val toolName = tool?.let { org.json.JSONObject(it.config_json).optString("name") } ?: write.toolInstanceId
        if (write.write == Triggers.Write.DELETED) {
            val copy = org.json.JSONObject()
            listOf("name", "timestamp", "data", "extra").forEach { key -> write.row.opt(key)?.takeIf { it != org.json.JSONObject.NULL }?.let { copy.put(key, it) } }
            return listOf(app.treelune.core.ai.data.MessageSegment.Text(s.shared("automation_run_entry_deleted").format(toolName, copy.toString())))
        }
        val pointer = app.treelune.core.ai.enrichments.PointerConfig(
            selection = app.treelune.core.selection.EntrySelection(target = app.treelune.core.selection.Reference(app.treelune.core.selection.ReferenceKind.ENTRY, write.entryId)),
            entries = true
        )
        return listOf(
            app.treelune.core.ai.data.MessageSegment.Text(s.shared("automation_run_entry_${write.write.key}").format(toolName)),
            app.treelune.core.ai.data.MessageSegment.EnrichmentBlock(app.treelune.core.ai.data.EnrichmentType.POINTER, pointer.toJson().toString())
        )
    }
}
