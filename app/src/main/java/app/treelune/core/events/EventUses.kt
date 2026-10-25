package app.treelune.core.events

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskSteps
import app.treelune.core.triggers.Triggers
import org.json.JSONArray

/**
 * What uses an event (docs/CORE.md, « Events »): the Tasks and routines whose triggers listen to
 * it, the Tasks a step of which emits it — by name, for the refusal of its deletion.
 */
object EventUses {

    suspend fun of(context: Context, eventId: String): List<String> {
        val database = AppDatabase.getDatabase(context)
        fun listens(json: String?) = runCatching { Triggers.read(json).any { it is Triggers.Trigger.EventEmitted && it.eventId == eventId } }.getOrDefault(false)
        fun emits(stepsJson: String) = JSONArray(stepsJson).let { steps ->
            (0 until steps.length()).any { steps.optJSONObject(it)?.let { step -> step.optString(TaskSteps.KIND) == TaskSteps.EMIT_EVENT && step.optString(TaskSteps.EVENT_ID) == eventId } == true }
        }
        return database.taskDao().getAll().filter { listens(it.triggersJson) || emits(it.stepsJson) }.map { it.name } +
            database.aiDao().getAllAutomations().filter { listens(it.triggersJson) }.map { it.name }
    }
}
