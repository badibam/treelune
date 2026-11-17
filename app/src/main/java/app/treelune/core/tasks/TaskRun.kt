package app.treelune.core.tasks

import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/** A Task's run as its row stores it (TaskRunEntity), read. */
object TaskRun {

    /** What started a run. */
    enum class Trigger(val key: String) {
        MANUAL("manual"), SCHEDULED("scheduled"), AI("ai"),
        /** An entry written that one of its triggers listens to (Triggers) */
        ENTRY("entry"),
        /** An event emitted that one of its triggers listens to (Triggers) */
        EVENT("event"),
        /** A condition one of its triggers names become true (ConditionTriggers) */
        CONDITION("condition");

        companion object {
            fun of(key: String) = entries.first { it.key == key }
        }
    }

    /** Where a run stands. */
    enum class State(val key: String) {
        /** Asked while another run of its Task went on: started once that one ends */
        QUEUED("queued"),
        /** Its steps are being run */
        RUNNING("running"),
        /** On a deferred API's job, asked again until it is ready */
        WAITING("waiting"),
        DONE("done"),
        FAILED("failed"),
        CANCELLED("cancelled"),
        /** Found running when the app started again: its step may or may not have been done, and is not run twice */
        INTERRUPTED("interrupted");

        val ended: Boolean get() = this != QUEUED && this != RUNNING && this != WAITING

        companion object {
            fun of(key: String) = entries.first { it.key == key }
        }
    }

    /**
     * What one step gave: [summary], what it did said in words; the API's [values] by name and
     * their [types]; the [entryId] it created; the [raw] answer's start; why it failed, [error];
     * why it did not run, [skipped] (StepConditions), said in words; how many elements a
     * repeated step was made for, [repeated].
     */
    data class StepResult(
        val summary: String,
        val values: Map<String, Any?> = emptyMap(),
        val types: Map<String, String> = emptyMap(),
        val entryId: String? = null,
        val raw: String? = null,
        val error: String? = null,
        val skipped: String? = null,
        val repeated: Int? = null,
        val at: Long,
        /** Why the run stops after this step, done (« Stop the Task » of a create_entry's guard); null, it goes on. */
        val stopped: String? = null
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("summary", summary)
            if (values.isNotEmpty()) put("values", JsonUtils.toJSONObject(values))
            if (types.isNotEmpty()) put("types", JSONObject(types))
            entryId?.let { put("entry_id", it) }
            raw?.let { put("raw", it) }
            error?.let { put("error", it) }
            skipped?.let { put("skipped", it) }
            repeated?.let { put("repeated", it) }
            stopped?.let { put("stopped", it) }
            put("at", at)
        }

        companion object {
            fun of(json: JSONObject) = StepResult(
                summary = json.getString("summary"),
                values = json.optJSONObject("values")?.let { JsonUtils.toMap(it) }.orEmpty(),
                types = json.optJSONObject("types")?.let { types -> types.keys().asSequence().associateWith { types.getString(it) } }.orEmpty(),
                entryId = json.optString("entry_id").ifEmpty { null },
                raw = json.optString("raw").ifEmpty { null },
                error = json.optString("error").ifEmpty { null },
                skipped = json.optString("skipped").ifEmpty { null },
                stopped = json.optString("stopped").ifEmpty { null },
                repeated = if (json.has("repeated")) json.getInt("repeated") else null,
                at = json.getLong("at")
            )
        }
    }

    /** What a run started by a trigger received (Triggers), which its steps read (TaskSteps.Input.Received). */
    sealed interface Received {
        fun toJson(): String

        /** The entry [entryId] of [toolInstanceId], and what became of it, [write]: read as it is when a step runs, deleted said so. */
        data class Entry(val entryId: String, val toolInstanceId: String, val write: app.treelune.core.triggers.Triggers.Write) : Received {
            override fun toJson(): String = JSONObject().put("entry_id", entryId).put("tool_instance_id", toolInstanceId).put("write", write.key).toString()
        }

        /** The event [eventId] emitted, and the [values] it carried, by name. */
        data class Event(val eventId: String, val values: Map<String, Any?>) : Received {
            override fun toJson(): String = JSONObject().put("event_id", eventId).put("values", JsonUtils.toJSONObject(values)).toString()
        }

        /**
         * The [condition] become true, and the [values] read on its sides when it did, `left` and
         * `right` (a pair for between, none for absent and present); [said], them in words
         * ("82.4 > 80"), as the run's notification says them.
         */
        data class Condition(val condition: JSONObject, val values: Map<String, Any?>, val said: String) : Received {
            override fun toJson(): String = JSONObject().put("condition", condition).put("values", JsonUtils.toJSONObject(values)).put("said", said).toString()
        }

        companion object {
            fun of(json: String): Received = JSONObject(json).let {
                if (it.has("event_id")) Event(it.getString("event_id"), JsonUtils.toMap(it.optJSONObject("values") ?: JSONObject()))
                else if (it.has("condition")) Condition(it.getJSONObject("condition"), JsonUtils.toMap(it.optJSONObject("values") ?: JSONObject()), it.getString("said"))
                else Entry(it.getString("entry_id"), it.getString("tool_instance_id"), app.treelune.core.triggers.Triggers.Write.of(it.getString("write")))
            }
        }
    }

    fun results(json: String): List<StepResult> = JSONArray(json).let { array -> (0 until array.length()).map { StepResult.of(array.getJSONObject(it)) } }

    fun resultsJson(results: List<StepResult>): String = JSONArray(results.map { it.toJson() }).toString()

    /**
     * A run waiting on a deferred API: the [apiId] and the [jobId] it follows, when it first asks
     * ([nextAt]), when it last asked ([askedAt], null before), and when it gives up ([deadline]).
     * From then on it asks at each tick of the
     * scheduler: every minute with the app open, at each ring of its alarm with the app closed,
     * which wakes the phone whether a run waits or not.
     */
    data class Wait(val apiId: String, val jobId: String, val nextAt: Long, val deadline: Long, val askedAt: Long? = null) {
        fun toJson(): String = JSONObject().put("api_id", apiId).put("job_id", jobId).put("next_at", nextAt).put("deadline", deadline)
            .apply { askedAt?.let { put("asked_at", it) } }.toString()

        companion object {
            /** The first time a job is asked about, after it started: a minute. */
            const val FIRST_ASK = 60_000L

            fun of(json: String) = JSONObject(json).let {
                Wait(it.getString("api_id"), it.getString("job_id"), it.getLong("next_at"), it.getLong("deadline"), if (it.has("asked_at")) it.getLong("asked_at") else null)
            }
        }
    }
}
