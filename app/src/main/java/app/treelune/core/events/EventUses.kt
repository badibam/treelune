package app.treelune.core.events

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.tasks.TaskSteps
import app.treelune.core.triggers.Triggers
import org.json.JSONArray

/**
 * What uses an event (docs/CORE.md, « Events »): the Tasks and routines whose triggers listen to
 * it, the Tasks a step of which emits it, the tools, routines and Tasks that emit it where they
 * tell something or end (EventPoints) — by name, for the refusal of its deletion.
 */
object EventUses {

    suspend fun of(context: Context, eventId: String): List<String> {
        val database = AppDatabase.getDatabase(context)
        fun listens(json: String?) = runCatching { Triggers.read(json).any { it is Triggers.Trigger.EventEmitted && it.eventId == eventId } }.getOrDefault(false)
        fun emits(stepsJson: String) = JSONArray(stepsJson).let { steps ->
            (0 until steps.length()).any { steps.optJSONObject(it)?.let { step -> step.optString(TaskSteps.KIND) == TaskSteps.EMIT_EVENT && step.optString(TaskSteps.EVENT_ID) == eventId } == true }
        }
        // A tool's config names it where it emits it (EventPoints)
        val tools = database.toolInstanceDao().getAllToolInstances().filter { tool ->
            app.treelune.core.fields.ReferenceTarget.referenceOf(org.json.JSONObject(tool.config_json).opt(EventPoints.KEY))?.id == eventId
        }
        return database.taskDao().getAll().filter { listens(it.triggersJson) || emits(it.stepsJson) || it.emitEvent == eventId }.map { it.name } +
            database.aiDao().getAllAutomations().filter { listens(it.triggersJson) || it.emitEvent == eventId }.map { it.name } +
            tools.map { org.json.JSONObject(it.config_json).optString("name") }
    }
}
