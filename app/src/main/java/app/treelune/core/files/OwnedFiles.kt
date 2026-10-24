package app.treelune.core.files

import android.content.Context
import android.graphics.BitmapFactory
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ImageField
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.utils.LogManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * The images of an entry or of a tool kept in step with its IMAGE values at each write
 * (docs/DATA.md, « IMAGE »): the ids its values hold after the write are compared with the images
 * it owns. An entry holds them in its IMAGE fields, a tool in the IMAGE settings of its config (a
 * step's images).
 *
 * - An id the owner owns stays.
 * - An id prepared in a form (files.prepare_image) becomes the owner's: its file moves from the
 *   pending folder to the entries' one, and its row is written.
 * - An id of an image another owns, a photo of the conversation the AI files, an image of another
 *   entry, of the tool a copy is made from, is copied: the copy belongs to the owner, under a new
 *   id written in its place.
 * - Any other id refuses the write, naming it.
 * - An image the owner owned that no value holds any more is deleted, its row with the owner's
 *   write, its file after.
 *
 * The files are placed before the transaction that writes the owner and the rows: an interruption
 * leaves a file no row names, which the startup sweep removes. A write that fails puts the files
 * back where they were ([undo]).
 */
class OwnedFiles private constructor(
    private val context: Context,
    private val owner: Owner,
    /** Whether a copy's new id was written in place of what was sent, in the values planned */
    val rewritten: Boolean,
    private val moves: List<Pair<String, File>>,
    private val copies: List<Pair<FileEntity, String>>,
    private val removed: List<FileEntity>
) {
    /** Who owns the images: an entry or a tool. */
    sealed interface Owner {
        val id: String
        data class Entry(override val id: String) : Owner
        data class Tool(override val id: String) : Owner
    }

    /** The files placed by [place], and where a moved one came from. */
    private val placed = mutableListOf<Pair<File, File?>>()

    /** The rows [place] wrote the files of, written by [store]. */
    private val rows = mutableListOf<FileEntity>()

    /** Whether the write changes nothing of the owner's images. */
    val unchanged: Boolean get() = moves.isEmpty() && copies.isEmpty() && removed.isEmpty()

    private val entryId = (owner as? Owner.Entry)?.id
    private val toolInstanceId = (owner as? Owner.Tool)?.id

    private fun owned(image: FileEntity) = image.copy(sessionId = null, entryId = entryId, toolInstanceId = toolInstanceId)

    /** The files of the new images put in the entries' folder, before the transaction. */
    fun place() {
        val now = System.currentTimeMillis()
        val dir = Files.entriesDir(context).apply { mkdirs() }
        moves.forEach { (id, pending) ->
            val target = File(dir, pending.name)
            check(pending.renameTo(target)) { "Image $id: not moved from the pending folder" }
            placed.add(target to pending)
            val format = FileFormat.ofExtension(pending.extension)?.takeIf { it.kind == FileKind.IMAGE } ?: error("File $id: ${pending.name} is no image file")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            rows.add(FileEntity.image(id, null, entryId, toolInstanceId, format, target.length(), bounds.outWidth, bounds.outHeight, now))
        }
        copies.forEach { (source, newId) ->
            val copy = owned(source).copy(id = newId, createdAt = now)
            val target = Files.file(context, copy)
            Files.copy(Files.file(context, source), target)
            placed.add(target to null)
            rows.add(copy)
        }
    }

    /** The rows of the new images written and the removed ones deleted, inside the owner's transaction, after the owner. */
    suspend fun store() {
        val writes = app.treelune.core.history.Writes(context)
        rows.forEach { writes.insertFile(it) }
        removed.forEach { writes.deleteFile(it.id) }
    }

    /** Once the transaction is done: what changed, logged. */
    fun done() {
        // The files of the removed images stay: the history keeps their rows, an undo puts them
        // back, and the startup sweep deletes a file once nothing names it (Files.sweepOnce)
        if (!unchanged) LogManager.service("$owner: ${rows.size} images added, ${removed.size} removed", "DEBUG")
    }

    /** The write failed: a moved file back in the pending folder, a copy deleted. */
    fun undo() {
        placed.forEach { (target, from) ->
            if (from != null) target.renameTo(from) else Files.deleteFile(target)
        }
        placed.clear()
    }

    /** What a write does to its owner's images, or why it is refused. */
    sealed interface Plan {
        class Ready(val files: OwnedFiles) : Plan
        class Refused(val error: String) : Plan
    }

    /** Where an image list stands: the object that holds it and its key there. */
    data class Slot(val values: JSONObject, val key: String)

    companion object {

        /** The slots of an entry's IMAGE fields among [declared] (in [data]) and [user] (in [extra]). */
        fun entrySlots(data: JSONObject, declared: List<FieldDefinition>, extra: JSONObject?, user: List<FieldDefinition>): List<Slot> =
            listOf(data to declared, extra to user).flatMap { (values, fields) ->
                if (values == null) emptyList() else fields.filter { it.type == FieldType.IMAGE }.map { Slot(values, it.name) }
            }

        /**
         * The slots of the IMAGE settings [nodes] declare in [config], down its groups, its lists
         * of groups and every case of its variants (a case not chosen still holds what it holds).
         */
        fun configSlots(nodes: List<SettingNode>, config: JSONObject): List<Slot> = nodes.flatMap { node ->
            when (node) {
                is SettingNode.Field -> if (node.definition.type == FieldType.IMAGE) listOf(Slot(config, node.definition.name)) else emptyList()
                is SettingNode.Group -> config.optJSONObject(node.name)?.let { configSlots(node.nodes, it) }.orEmpty()
                is SettingNode.ListOf -> (node.item as? SettingNode.Item.Of)?.let { item ->
                    config.optJSONArray(node.name)?.let { list ->
                        (0 until list.length()).mapNotNull { list.optJSONObject(it) }.flatMap { configSlots(item.nodes, it) }
                    }
                }.orEmpty()
                is SettingNode.Variant -> node.cases.values.flatMap { configSlots(it, config) }
                is SettingNode.Section -> configSlots(node.nodes, config)
                is SettingNode.Condition, is SettingNode.Term, is SettingNode.Selection,
                is SettingNode.Period, is SettingNode.Instants -> emptyList()
            }
        }

        /**
         * What writing [owner] with the image lists at [slots] does to its images. A copy's new id
         * is written in its slot, in place ([OwnedFiles.rewritten]).
         *
         * @param notFound The refusal of an id that names no image, given that id
         */
        suspend fun plan(context: Context, owner: Owner, slots: List<Slot>, notFound: (String) -> String): Plan {
            val dao = AppDatabase.getDatabase(context).fileDao()
            val owned = when (owner) {
                is Owner.Entry -> dao.getForEntry(owner.id)
                is Owner.Tool -> dao.getForTool(owner.id)
            }.associateBy { it.id }

            val moves = mutableListOf<Pair<String, File>>()
            val copies = mutableListOf<Pair<FileEntity, String>>()
            val held = mutableSetOf<String>()
            var rewritten = false
            for ((values, key) in slots.distinct().filter { (values, key) -> values.has(key) && !values.isNull(key) }) {
                val ids = ImageField.ids(values.get(key))
                val written = ids.map { id ->
                    when {
                        id in owned -> id
                        moves.any { it.first == id } -> id
                        else -> Files.pendingFile(context, id)?.let { file -> moves.add(id to file); id }
                            ?: dao.getById(id)?.let { source -> UUID.randomUUID().toString().also { copies.add(source to it) } }
                            ?: return Plan.Refused(notFound(id))
                    }
                }
                held.addAll(written)
                if (written != ids) { values.put(key, JSONArray(written)); rewritten = true }
            }
            val removed = owned.values.filter { it.id !in held }
            return Plan.Ready(OwnedFiles(context, owner, rewritten, moves, copies, removed))
        }
    }
}
