package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ReferenceName
import app.treelune.core.fields.loadReferenceNames
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.StringsContext
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * What makes a session, as its config's variant says (docs/TOOLS.md, « Session (Sequence) »):
 * its steps and blocks; a timer, its length asked at launch; or a sound or a video, played from
 * an entry chosen at launch among a selection's, in the field the config names.
 *
 * To the user a timer and a medium have no steps. Here each makes a single step out of what was
 * answered at launch ([Answer]), and everything after runs as for steps: the run, the gestures,
 * the service that plays it screen off, what the entry keeps. [steps] is the one place that
 * turns an answer into steps; starting and `complete` (« Done » on a planned session) both go through it.
 */
object SequenceVariants {

    // Config
    const val VARIANT = "variant"
    const val STEPS = "steps"
    const val TIMER = "timer"
    const val MEDIA = "media"
    val ALL = listOf(STEPS, TIMER, MEDIA)

    /** The timer's length proposed at launch, and taken by `complete` without one. */
    const val TIMER_DURATION = "timer_duration"
    /** The entries a medium is chosen among (SettingNode.Selection) */
    const val MEDIA_SOURCE = "media_source"
    /** The file field of those entries the medium is played from, by its path */
    const val MEDIA_FIELD = "media_field"
    /** The order those entries are offered in (SettingNode.Sort) */
    const val MEDIA_SORT = "media_sort"

    /** The timer proposed when the config sets none: ten minutes. */
    const val DEFAULT_TIMER = 600_000L

    /** The variant of [config]; steps for a config from before variants, its declared default. */
    fun of(config: JSONObject): String = config.optString(VARIANT).ifEmpty { STEPS }

    /** What was answered at launch: nothing for steps, a length for a timer, an entry for a medium. */
    sealed interface Answer {
        data object None : Answer
        /** [length] null: the config's own */
        data class Length(val length: Long?) : Answer
        data class Entry(val id: String?) : Answer
    }

    /** The steps of a session, or why it cannot start. */
    sealed interface Made {
        data class Steps(val steps: List<PlannedStep>) : Made
        data class Refused(val error: String) : Made
    }

    /**
     * The steps of a session of [config] answered [answer], their files read at [now]. A medium's
     * entry must be one of its selection's, holding a sound or a video in its field.
     */
    suspend fun steps(context: Context, config: JSONObject, answer: Answer, now: Long, s: StringsContext): Made {
        val deleted = s.shared("term_source_deleted")
        val planned = when (of(config)) {
            TIMER -> {
                val length = (answer as? Answer.Length)?.length ?: config.optLong(TIMER_DURATION, DEFAULT_TIMER)
                if (length <= 0) return Made.Refused(s.tool("error_timer_length"))
                listOf(PlannedStep(config.optString("name").ifEmpty { s.tool("display_name") }, null, StepEnd.TIMED, length, emptyList()))
            }
            MEDIA -> {
                val id = (answer as? Answer.Entry)?.id ?: return Made.Refused(s.tool("error_media_entry_missing"))
                // A selection whose tool cannot be read says why
                val step = try { mediaStep(context, config, id, s) } catch (e: IllegalStateException) { return Made.Refused(e.message ?: "") }
                listOf(step ?: return Made.Refused(s.tool("error_media_entry_outside")))
            }
            else -> SequencePlan.unroll(config)
        }
        val steps = SequenceMedia.resolve(context, planned, now, deleted)
        if (steps.isEmpty()) return Made.Refused(s.tool("error_no_steps"))
        if (of(config) == MEDIA && steps.single().media == null) return Made.Refused(s.tool("error_media_entry_no_file"))
        return Made.Steps(steps)
    }

    /**
     * The step that plays entry [id]'s field: named as the entry, lasting its medium, which a
     * reading of that one entry follows; null when the entry is not among the selection's.
     */
    private suspend fun mediaStep(context: Context, config: JSONObject, id: String, s: StringsContext): PlannedStep? {
        if (offered(context, config, id).isEmpty()) return null
        val entry = Reference(ReferenceKind.ENTRY, id)
        val name = (loadReferenceNames(listOf(entry), context)[entry] as? ReferenceName.Named)?.name ?: s.shared("term_source_deleted")
        val reading = JSONObject()
            .put("selection", JSONObject().put("target", entry.toJson()))
            .put("field", config.optString(MEDIA_FIELD))
            .put("reduction", "LAST")
        return PlannedStep(name, null, StepEnd.MEDIA, null, emptyList(), terms = StepTerms(null, JSONObject().put("reading", reading)))
    }

    /**
     * The entries a medium is chosen among, in its sort's order: its selection's, holding a file
     * in its field; with [only], that one entry alone if it is one of them.
     *
     * @throws IllegalStateException when the selection's tool cannot be read, its error as message
     */
    suspend fun offered(context: Context, config: JSONObject, only: String? = null): List<Map<String, Any?>> {
        val selection = config.optJSONObject(MEDIA_SOURCE) ?: return emptyList()
        val toolId = selection.optJSONObject("target")?.optString("id")?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val field = config.optString(MEDIA_FIELD).takeIf { it.isNotEmpty() } ?: return emptyList()
        val filters = JSONArray(selection.optJSONArray("filters")?.toString() ?: "[]")
            .put(JSONObject().put("left", JSONObject().put("field", field)).put("op", "present"))
        only?.let { filters.put(JSONObject().put("left", JSONObject().put("field", "id")).put("op", "=").put("right", JSONObject().put("constant", it))) }
        val result = Coordinator(context).processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolId,
            "filters" to JsonUtils.toValue(filters),
            "sort" to JsonUtils.toValue(config.optJSONArray(MEDIA_SORT) ?: JSONArray())
        ))
        if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
        @Suppress("UNCHECKED_CAST")
        return (result.data?.get("entries") as? List<Map<String, Any?>>).orEmpty()
    }
}
