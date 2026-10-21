package app.treelune.tools.sequence

import org.json.JSONArray
import org.json.JSONObject

/**
 * A finished session's steps as it keeps them (docs/DATA.md, « Config change »): each
 * step's name, what became of it, and the time it ran on the session's clock, copied from its run
 * when it ends, so that changing the tool's steps afterwards leaves the session as it was. Its
 * counts follow from them: correcting the session is changing a step's state. Pure.
 */
object SequenceSteps {

    const val DONE = "done"
    const val SKIPPED = "skipped"
    const val NOT_DONE = "not_done"
    val STATUSES = listOf(DONE, SKIPPED, NOT_DONE)

    /**
     * One step of a finished session; [time] null for a step not reached, or noted without the
     * screen; [rounds] the rounds of the blocks around it, « Intervals 2 / 8 », null outside one.
     */
    data class Kept(val name: String, val status: String, val time: Long?, val rounds: String? = null)

    /** The rounds of the blocks around [step], outermost first: « Intervals 2 / 8 · Set 1 / 3 ». */
    fun roundsOf(step: PlannedStep): String? = step.rounds.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "${it.block} ${it.round} / ${it.of}" }

    /**
     * The steps of [finished]: those left, done or skipped, with the time from their start to the
     * next one's (to the end for the last of a completed session); the others not done, no time.
     */
    fun of(finished: Step.Finished): List<Kept> {
        val run = finished.run
        // Each step left starts where its passage says; the one after the last starts at the run's
        // current step, or, every step gone through, at the end
        val starts = run.left.map { it.stepStart } + (if (finished.completed) run.clock(finished.endedAt) else run.stepStart)
        return run.steps.mapIndexed { i, step ->
            val left = run.left.getOrNull(i)
            if (left == null) Kept(step.name, NOT_DONE, null, roundsOf(step))
            else Kept(step.name, if (left.outcome == Outcome.DONE) DONE else SKIPPED, (starts[i + 1] - starts[i]).coerceAtLeast(0L), roundsOf(step))
        }
    }

    /** A session noted done without the screen: the tool's steps as they stand, all done, no time. */
    fun allDone(steps: List<PlannedStep>): List<Kept> = steps.map { Kept(it.name, DONE, null, roundsOf(it)) }

    /** What the steps say in counts, the session's state following them. */
    fun counts(steps: List<Kept>) = StepCounts(steps.count { it.status == DONE }, steps.count { it.status == SKIPPED }, steps.count { it.status == NOT_DONE })

    /** The stored form: a JSON list, in data.steps. */
    fun toJson(steps: List<Kept>): String = JSONArray(steps.map { step ->
        JSONObject().put("name", step.name).put("status", step.status).apply {
            step.time?.let { put("time", it) }
            step.rounds?.let { put("rounds", it) }
        }
    }).toString()

    /** The steps a session keeps, null for one that kept none (finished before they were kept). */
    fun read(json: String?): List<Kept>? {
        if (json.isNullOrBlank()) return null
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val step = array.getJSONObject(i)
            Kept(step.getString("name"), step.getString("status"), if (step.has("time")) step.getLong("time") else null,
                step.optString("rounds").takeIf { it.isNotEmpty() })
        }
    }

    /** [steps] with each state replaced by those of [statuses], one a step in order; null when they do not match. */
    fun corrected(steps: List<Kept>, statuses: List<String>): List<Kept>? {
        if (statuses.size != steps.size || statuses.any { it !in STATUSES }) return null
        return steps.mapIndexed { i, step -> step.copy(status = statuses[i]) }
    }
}
