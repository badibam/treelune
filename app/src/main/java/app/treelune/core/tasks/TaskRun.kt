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
        EVENT("event");

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
     * their [types]; the [entryId] it created; the [raw] answer's start; why it failed, [error].
     */
    data class StepResult(
        val summary: String,
        val values: Map<String, Any?> = emptyMap(),
        val types: Map<String, String> = emptyMap(),
        val entryId: String? = null,
        val raw: String? = null,
        val error: String? = null,
        val at: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("summary", summary)
            if (values.isNotEmpty()) put("values", JsonUtils.toJSONObject(values))
            if (types.isNotEmpty()) put("types", JSONObject(types))
            entryId?.let { put("entry_id", it) }
            raw?.let { put("raw", it) }
            error?.let { put("error", it) }
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

        companion object {
            fun of(json: String): Received = JSONObject(json).let {
                if (it.has("event_id")) Event(it.getString("event_id"), JsonUtils.toMap(it.optJSONObject("values") ?: JSONObject()))
                else Entry(it.getString("entry_id"), it.getString("tool_instance_id"), app.treelune.core.triggers.Triggers.Write.of(it.getString("write")))
            }
        }
    }

    fun results(json: String): List<StepResult> = JSONArray(json).let { array -> (0 until array.length()).map { StepResult.of(array.getJSONObject(it)) } }

    fun resultsJson(results: List<StepResult>): String = JSONArray(results.map { it.toJson() }).toString()

    /**
     * A run waiting on a deferred API: the [apiId] and the [jobId] it follows, when it asks again
     * ([nextAt]) and after how long it asks the time after ([interval], doubling up to its
     * ceiling), and when it gives up ([deadline]).
     */
    data class Wait(val apiId: String, val jobId: String, val nextAt: Long, val interval: Long, val deadline: Long) {
        fun toJson(): String = JSONObject().put("api_id", apiId).put("job_id", jobId).put("next_at", nextAt).put("interval", interval).put("deadline", deadline).toString()

        /** The wait after one more answer saying it is not ready, asked at [now]. */
        fun again(now: Long): Wait = (interval * 2).coerceAtMost(LONGEST_INTERVAL).let { copy(nextAt = now + it, interval = it) }

        companion object {
            /** The first time a job is asked about, after it started: a minute. */
            const val FIRST_INTERVAL = 60_000L
            /** The longest between two asks: half an hour. */
            const val LONGEST_INTERVAL = 30 * 60_000L

            fun of(json: String) = JSONObject(json).let { Wait(it.getString("api_id"), it.getString("job_id"), it.getLong("next_at"), it.getLong("interval"), it.getLong("deadline")) }
        }
    }
}
