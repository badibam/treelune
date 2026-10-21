package app.treelune.core.images

import android.content.Context
import android.graphics.BitmapFactory
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ImageField
import app.treelune.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * An entry's images kept in step with its IMAGE fields at each write (docs/DATA.md, « IMAGE »):
 * the ids its fields hold after the write are compared with the images the entry owns.
 *
 * - An id the entry owns stays.
 * - An id prepared in a form (files.prepare_image) becomes the entry's: its file moves from the
 *   pending folder to the entries' one, and its row is written.
 * - An id of an image another owns, a photo of the conversation the AI files or an image of
 *   another entry, is copied: the copy belongs to the entry, under a new id written in its place.
 * - Any other id refuses the write, naming it.
 * - An image the entry owned that no field holds any more is deleted, its row with the entry's
 *   write, its file after.
 *
 * The files are placed before the transaction that writes the entry and the rows: an interruption
 * leaves a file no row names, which the startup sweep removes. A write that fails puts the files
 * back where they were ([undo]).
 */
class EntryImages private constructor(
    private val context: Context,
    private val entryId: String,
    /** The entry's fields as they will be stored, a copy's new id in place of what was sent */
    val dataJson: String,
    val extraJson: String?,
    private val moves: List<Pair<String, File>>,
    private val copies: List<Pair<ImageEntity, String>>,
    private val removed: List<ImageEntity>
) {
    /** The files placed by [place], and where a moved one came from. */
    private val placed = mutableListOf<Pair<File, File?>>()

    /** The rows [place] wrote the files of, written by [store]. */
    private val rows = mutableListOf<ImageEntity>()

    /** Whether the write changes nothing of the entry's images. */
    val unchanged: Boolean get() = moves.isEmpty() && copies.isEmpty() && removed.isEmpty()

    /** The files of the new images put in the entries' folder, before the transaction. */
    fun place() {
        val now = System.currentTimeMillis()
        val dir = Images.entriesDir(context).apply { mkdirs() }
        moves.forEach { (id, pending) ->
            val target = File(dir, pending.name)
            check(pending.renameTo(target)) { "Image $id: not moved from the pending folder" }
            placed.add(target to pending)
            val format = ImageFormat.ofExtension(pending.extension) ?: error("Image $id: ${pending.name} is no image file")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            rows.add(ImageEntity(id, null, entryId, format.name, target.length(), bounds.outWidth, bounds.outHeight, now))
        }
        copies.forEach { (source, newId) ->
            val copy = source.copy(id = newId, sessionId = null, entryId = entryId, createdAt = now)
            val target = Images.file(context, copy)
            Images.copy(Images.file(context, source), target)
            placed.add(target to null)
            rows.add(copy)
        }
    }

    /** The rows of the new images written and the removed ones deleted, inside the entry's transaction, after the entry. */
    suspend fun store() {
        val writes = app.treelune.core.history.Writes(context)
        rows.forEach { writes.insertImage(it) }
        removed.forEach { writes.deleteImage(it.id) }
    }

    /** Once the transaction is done: what changed, logged. */
    fun done() {
        // The files of the removed images stay: the history keeps their rows, an undo puts them
        // back, and the startup sweep deletes a file once nothing names it (Images.sweepOnce)
        if (!unchanged) LogManager.service("Entry $entryId: ${rows.size} images added, ${removed.size} removed", "DEBUG")
    }

    /** The write failed: a moved file back in the pending folder, a copy deleted. */
    fun undo() {
        placed.forEach { (target, from) ->
            if (from != null) target.renameTo(from) else Images.deleteFile(target)
        }
        placed.clear()
    }

    /** What an entry's write does to its images, or why it is refused. */
    sealed interface Plan {
        class Ready(val images: EntryImages) : Plan
        class Refused(val error: String) : Plan
    }

    companion object {

        /**
         * What writing entry [entryId] with [dataJson] and [extraJson] does to its images, its
         * IMAGE fields among [declared] (in data) and [user] (in extra).
         *
         * @param notFound The refusal of an id that names no image, given that id
         */
        suspend fun plan(
            context: Context,
            entryId: String,
            declared: List<FieldDefinition>,
            user: List<FieldDefinition>,
            dataJson: String,
            extraJson: String?,
            notFound: (String) -> String
        ): Plan {
            val dao = AppDatabase.getDatabase(context).imageDao()
            val owned = dao.getForEntry(entryId).associateBy { it.id }
            val data = JSONObject(dataJson)
            val extra = extraJson?.let { JSONObject(it) }
            val imageFields = listOf(data to declared, extra to user)
                .flatMap { (values, fields) -> fields.filter { it.type == FieldType.IMAGE }.map { values to it.name } }
                .filter { (values, name) -> values != null && values.has(name) && !values.isNull(name) }

            val moves = mutableListOf<Pair<String, File>>()
            val copies = mutableListOf<Pair<ImageEntity, String>>()
            val held = mutableSetOf<String>()
            var rewritten = false
            for ((values, name) in imageFields) {
                val ids = ImageField.ids(values!!.get(name))
                val written = ids.map { id ->
                    when {
                        id in owned -> id
                        moves.any { it.first == id } -> id
                        else -> Images.pendingFile(context, id)?.let { file -> moves.add(id to file); id }
                            ?: dao.getById(id)?.let { source -> UUID.randomUUID().toString().also { copies.add(source to it) } }
                            ?: return Plan.Refused(notFound(id))
                    }
                }
                held.addAll(written)
                if (written != ids) { values.put(name, JSONArray(written)); rewritten = true }
            }
            val removed = owned.values.filter { it.id !in held }
            // The fields as they came when no copy changed an id
            return Plan.Ready(EntryImages(
                context, entryId,
                if (rewritten) data.toString() else dataJson,
                if (rewritten) extra?.toString() else extraJson,
                moves, copies, removed
            ))
        }
    }
}
