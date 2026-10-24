package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FileField
import app.treelune.core.fields.ReferenceName
import app.treelune.core.fields.loadReferenceNames
import app.treelune.core.files.FileKind
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.terms.Term
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * A step's files read when a session starts (docs/TOOLS.md, « Session (Sequence) »): each term
 * gives its fixed files, or what the field it follows holds then (readings.read), which the run
 * keeps as it starts, so a field changed meanwhile does not change the session. A step that ends
 * with its medium becomes a timer of the medium's length, or a step ended by hand when it has
 * nothing to play. What a step read from is noted with it, by its name then, or as gone.
 */
object SequenceMedia {

    /** The steps of [steps] with their files read at [now]; [deleted] is what says a source gone. */
    suspend fun resolve(context: Context, steps: List<PlannedStep>, now: Long, deleted: String): List<PlannedStep> {
        // A field followed by several steps, as a block repeats them, is read once
        val read = HashMap<String, Read>()
        suspend fun once(term: JSONObject?): Read? {
            val reading = term?.optJSONObject(Term.Kind.READING.key) ?: return null
            return read.getOrPut(reading.toString()) { follow(context, reading, now, deleted) }
        }
        val dao = AppDatabase.getDatabase(context).fileDao()
        return steps.map { step ->
            val terms = step.terms ?: return@map step
            val images = once(terms.images)
            val followed = once(terms.media)
            val mediaId = followed?.ids?.firstOrNull() ?: SequencePlan.fixed(terms.media).firstOrNull()
            val media = mediaId?.let { dao.getById(it) }
                ?.takeIf { it.kind == FileKind.AUDIO.name || it.kind == FileKind.VIDEO.name }
                ?.let { row -> row.durationMs?.let { StepMedia(row.id, row.kind, it) } }
            val end = when {
                step.end != StepEnd.MEDIA -> step.end
                media != null -> StepEnd.TIMED
                else -> StepEnd.MANUAL
            }
            step.copy(
                end = end,
                duration = if (step.end == StepEnd.MEDIA) media?.durationMs else step.duration,
                images = images?.ids ?: step.images,
                media = media,
                source = followed?.source ?: images?.source,
                terms = null
            )
        }
    }

    /** What a followed field gave: its files, and where they came from, by name or as gone. */
    private class Read(val ids: List<String>, val source: String)

    /** The field [reading] follows, read at [now]. */
    private suspend fun follow(context: Context, reading: JSONObject, now: Long, deleted: String): Read {
        val target = reading.optJSONObject("selection")?.optJSONObject("target")?.let { Reference.fromJson(it) }
        val designated = target?.takeIf { it.kind == ReferenceKind.ENTRY }?.copy(field = reading.optString("field").takeIf { it.isNotEmpty() })
        val source = designated?.let {
            try {
                (loadReferenceNames(listOf(it), context)[it] as? ReferenceName.Named)?.name
            } catch (e: IllegalStateException) {
                LogManager.service("Sequence: the name of $it not read: ${e.message}", "WARN")
                null
            }
        } ?: deleted
        val result = Coordinator(context).processUserAction("readings.read", app.treelune.core.utils.JsonUtils.toMap(JSONObject(reading.toString()).put("reference", now)))
        // A source gone, or a field left empty, plays nothing: the step says where it read
        val ids = if (result.isSuccess) FileField.ids(result.data?.get("value")) else {
            LogManager.service("Sequence: a step's field not read: ${result.error}", "WARN")
            emptyList()
        }
        return Read(ids, source)
    }
}
