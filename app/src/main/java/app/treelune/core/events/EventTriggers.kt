package app.treelune.core.events

import android.content.Context
import app.treelune.core.coordinator.Chains
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskRun
import app.treelune.core.tasks.TaskRunner
import app.treelune.core.triggers.Triggers
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * What an event emitted starts (docs/CORE.md, « Events »): each Task and routine switched on
 * whose triggers listen to it, once each, the run receiving the values it carried. Called by
 * the emission, inside the emitter's context: a run started continues its chain (Chains),
 * refused past the cap.
 */
object EventTriggers {

    suspend fun dispatch(context: Context, event: EventEntity, values: JSONObject) {
        val database = AppDatabase.getDatabase(context)
        fun listens(json: String?, owner: String): Boolean = try {
            Triggers.read(json).any { it is Triggers.Trigger.EventEmitted && it.eventId == event.id }
        } catch (e: IllegalArgumentException) {
            LogManager.service("$owner: triggers not read: ${e.message}", "ERROR", e)
            false
        }
        val carried = JsonUtils.toMap(values)
        for (task in database.taskDao().getAll().filter { it.isEnabled && listens(it.triggersJson, "Task ${it.id}") }) {
            val chain = when (val admission = Chains.admit(context, task.name)) {
                is Chains.Admission.Capped -> continue
                is Chains.Admission.Continues -> admission.chainId
                Chains.Admission.Starts -> null
            }
            TaskRunner.start(context, task, TaskRun.Trigger.EVENT, System.currentTimeMillis(), awaited = false, chainId = chain,
                received = TaskRun.Received.Event(event.id, carried))
        }
        for (routine in database.aiDao().getAllRoutines().filter { it.isEnabled && listens(it.triggersJson, "Routine ${it.id}") }) {
            val chain = when (val admission = Chains.admit(context, routine.name)) {
                is Chains.Admission.Capped -> continue
                is Chains.Admission.Continues -> admission.chainId
                Chains.Admission.Starts -> null
            }
            app.treelune.core.ai.orchestration.AIOrchestrator.executeRoutine(routine.id, scheduledFor = System.currentTimeMillis(),
                trigger = app.treelune.core.ai.data.ExecutionTrigger.EVENT, chainId = chain, attached = attached(context, event, values))
        }
    }

    /**
     * What a routine's run receives of [event], joined to its message: a line naming it with the
     * values it carried, then each entry it carried attached as a pointer.
     */
    private fun attached(context: Context, event: EventEntity, values: JSONObject): List<app.treelune.core.ai.data.MessageSegment> {
        val s = app.treelune.core.strings.Strings.`for`(context = context)
        val declared = EventValues.read(event.valuesJson)
        val said = declared.joinToString("; ") { value -> "${value.name}: ${values.opt(value.name)}" }.ifEmpty { "-" }
        val pointers = declared.filter { it.type == EventValues.Type.ENTRY }.mapNotNull { values.optString(it.name).ifEmpty { null } }.map { id ->
            val pointer = app.treelune.core.ai.enrichments.PointerConfig(
                selection = app.treelune.core.selection.EntrySelection(target = app.treelune.core.selection.Reference(app.treelune.core.selection.ReferenceKind.ENTRY, id)),
                entries = true
            )
            app.treelune.core.ai.data.MessageSegment.EnrichmentBlock(app.treelune.core.ai.data.EnrichmentType.POINTER, pointer.toJson().toString())
        }
        return listOf(app.treelune.core.ai.data.MessageSegment.Text(s.shared("automation_run_event").format(event.name, said))) + pointers
    }
}
