package app.treelune.tools.sequence

import org.json.JSONArray
import org.json.JSONObject

/** How a step ends (docs/TOOLS.md, « Session (Sequence) »). */
enum class StepEnd(val key: String) {
    /** The timer counts down, then the next step comes by itself. */
    TIMED("timed"),
    /** A stopwatch counts up until « Done ». */
    MANUAL("manual"),
    /** The timer counts down and sounds, then the stopwatch goes on in overtime until « Done ». */
    TIMED_THEN_MANUAL("timed_then_manual"),
    /**
     * The step lasts its sound or its video: a session read it when it starts, and goes through it
     * as a timer of that length; a step with nothing to play is ended by hand (SequenceMedia).
     */
    MEDIA("media");

    /** Whether the config gives it a duration; a step ending with its medium takes the medium's. */
    val timed: Boolean get() = this == TIMED || this == TIMED_THEN_MANUAL

    companion object {
        val KEYS = entries.map { it.key }
        fun of(key: String): StepEnd = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("unknown step end '$key'")
    }
}

/** The round of a block a step is done in: « Intervals 2 / 8 ». */
data class Round(val block: String, val round: Int, val of: Int)

/** A step's sound or video as a session plays it: the file, its kind, its length. */
data class StepMedia(val id: String, val kind: String, val durationMs: Long) {
    val isVideo: Boolean get() = kind == app.treelune.core.files.FileKind.VIDEO.name
}

/**
 * A step's files as its config holds them, each a term (the Terme brick): a fixed value, the
 * tool's own files, or one entry's field followed, read when a session starts (SequenceMedia).
 */
data class StepTerms(val images: JSONObject?, val media: JSONObject?)

/**
 * One step as a session goes through it: the config's step, with the rounds of the blocks
 * around it, outermost first.
 *
 * @property duration The timer's length, null for a step ended by hand
 * @property images Its images' ids, the first shown large: the tool's own, or those of the field it follows
 * @property media Its sound or its video, played during the step
 * @property source Where its files were read from, « Session of the 24 › Induction », or that
 *   what it followed is gone; null for files of its own
 * @property terms Its files as the config holds them, until a session reads them; never kept in a run
 */
data class PlannedStep(
    val name: String,
    val instruction: String?,
    val end: StepEnd,
    val duration: Long?,
    val rounds: List<Round>,
    val images: List<String> = emptyList(),
    val media: StepMedia? = null,
    val source: String? = null,
    val terms: StepTerms? = null
)

/**
 * A session's run of steps, unrolled from its config: each block repeated, a step marked
 * skip_last_round left out of its block's last round. The session copies it when it starts, so a
 * config changed meanwhile does not change it.
 *
 * The config's elements: a step {"kind": "step", "name", "instruction", "images", "media", "end",
 * "duration", "skip_last_round"}, its images and its medium each a term, or a block {"kind": "block", "name", "repeat", "steps"}; a block holds steps
 * or blocks, the latter steps only, stored without their kind.
 */
object SequencePlan {

    const val KIND = "kind"
    const val KIND_STEP = "step"
    const val KIND_BLOCK = "block"
    const val STEPS = "steps"
    const val NAME = "name"
    const val INSTRUCTION = "instruction"
    const val IMAGES = "images"
    const val MEDIA = "media"
    const val SOURCE = "source"
    const val END = "end"
    const val DURATION = "duration"
    const val SKIP_LAST_ROUND = "skip_last_round"
    const val REPEAT = "repeat"

    /** The deepest a block may sit: a block in a block holds steps only. */
    const val MAX_DEPTH = 2

    /** The steps of [config], in the order a session goes through them. */
    fun unroll(config: JSONObject): List<PlannedStep> =
        elements(config.optJSONArray(STEPS)).flatMap { unrollElement(it, depth = 0, rounds = emptyList()) }

    private fun unrollElement(element: JSONObject, depth: Int, rounds: List<Round>): List<PlannedStep> {
        // A block's steps at the deepest level are stored without their kind
        val kind = if (depth == MAX_DEPTH) KIND_STEP else element.optString(KIND)
        return when (kind) {
            KIND_STEP -> listOf(step(element, rounds))
            KIND_BLOCK -> {
                val name = element.optString(NAME)
                val repeat = element.optInt(REPEAT, 1)
                (1..repeat).flatMap { round ->
                    elements(element.optJSONArray(STEPS))
                        .filterNot { round == repeat && it.optBoolean(SKIP_LAST_ROUND, false) }
                        .flatMap { unrollElement(it, depth + 1, rounds + Round(name, round, repeat)) }
                }
            }
            else -> throw IllegalArgumentException("unknown element kind '$kind'")
        }
    }

    private fun step(element: JSONObject, rounds: List<Round>): PlannedStep {
        val end = StepEnd.of(element.optString(END))
        return PlannedStep(
            name = element.optString(NAME),
            instruction = element.optString(INSTRUCTION).takeIf { it.isNotBlank() },
            end = end,
            duration = if (end.timed) element.getLong(DURATION) else null,
            rounds = rounds,
            images = fixed(element.optJSONObject(IMAGES)),
            // A step with files to read has its terms until a session reads them; one without, none
            terms = StepTerms(element.optJSONObject(IMAGES), element.optJSONObject(MEDIA)).takeIf { it.images != null || it.media != null }
        )
    }

    /** The ids of a list of images, none when it is absent. */
    fun ids(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }

    /** The files a term holds as a fixed value; none for a term that follows a field, or none at all. */
    fun fixed(term: JSONObject?): List<String> = ids(term?.optJSONArray(app.treelune.core.terms.Term.Kind.CONSTANT.key))

    private fun elements(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }

    /** The run as the entry keeps it while the session runs. */
    fun toJson(steps: List<PlannedStep>): JSONArray = JSONArray().apply {
        steps.forEach { step ->
            put(JSONObject()
                .put(NAME, step.name)
                .put(INSTRUCTION, step.instruction ?: JSONObject.NULL)
                .put(END, step.end.key)
                .put(DURATION, step.duration ?: JSONObject.NULL)
                .put(IMAGES, JSONArray(step.images))
                .put(MEDIA, step.media?.let { JSONObject().put("id", it.id).put("kind", it.kind).put("duration_ms", it.durationMs) } ?: JSONObject.NULL)
                .put(SOURCE, step.source ?: JSONObject.NULL)
                .put("rounds", JSONArray().apply {
                    step.rounds.forEach { put(JSONObject().put("block", it.block).put("round", it.round).put("of", it.of)) }
                }))
        }
    }

    fun fromJson(array: JSONArray): List<PlannedStep> = (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val rounds = o.getJSONArray("rounds")
        PlannedStep(
            name = o.getString(NAME),
            instruction = if (o.isNull(INSTRUCTION)) null else o.getString(INSTRUCTION),
            end = StepEnd.of(o.getString(END)),
            duration = if (o.isNull(DURATION)) null else o.getLong(DURATION),
            rounds = (0 until rounds.length()).map { r ->
                rounds.getJSONObject(r).let { Round(it.getString("block"), it.getInt("round"), it.getInt("of")) }
            },
            // A run started before steps had images holds none, nor a medium
            images = ids(o.optJSONArray(IMAGES)),
            media = o.optJSONObject(MEDIA)?.let { StepMedia(it.getString("id"), it.getString("kind"), it.getLong("duration_ms")) },
            source = if (o.isNull(SOURCE)) null else o.optString(SOURCE).takeIf { it.isNotEmpty() }
        )
    }
}
