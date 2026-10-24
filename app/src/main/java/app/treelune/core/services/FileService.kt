package app.treelune.core.services

import android.content.Context
import app.treelune.core.files.FileEntity
import app.treelune.core.files.FileFormat
import app.treelune.core.files.FileKind
import app.treelune.core.files.Files
import app.treelune.core.files.fileKind
import app.treelune.core.files.Images
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.database.AppDatabase
import app.treelune.core.strings.Strings
import org.json.JSONObject
import java.util.UUID

/**
 * The files joined to messages (docs/DATA.md, « Import »): text read once
 * when it is joined, kept with its session (AttachedFileEntity).
 *
 * - attach: `session_id`, `name`, `mime_type`, `content`: the file kept; its `id` and `line_count`.
 *   A content that is not text (a NUL character in it) is refused.
 * - read: `id`, and `start_line` (from 1) and `lines` to read a part: that part's text, with the
 *   file's name, type, size and line count. Without them, the whole file.
 * - delete: `id`: a file taken off the composer before its message went.
 *
 * And the images (docs/AI.md, « Images », and docs/DATA.md, « IMAGE »), a reduced file each
 * (Images), described by a row (FileEntity):
 * - attach_image: `session_id`, `uri` (the photo taken or picked): the image prepared and kept
 *   with the session, its file written before its row; its `id`, `width`, `height`,
 *   `size_bytes`, `format` and `animated` (only its first frame kept).
 * - prepare_image: `uri`: the image prepared for an entry's IMAGE field, its file set aside
 *   without a row until the entry is written (ToolDataService, OwnedFiles); the same answer.
 * - show_images: `session_id`, `ids`: images of entries brought into the conversation, as if the
 *   user had joined them, for the model to see them (the AI's IMAGES): each copied as an image
 *   of the session, one already the session's kept as it is. Refused for a model that does not
 *   read images, or of which nothing says it does. Answer: `images` (each one's `id` in the
 *   conversation, its `source_id`, `width` and `height`) and `image_ids`, the conversation's.
 */
class FileService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val dao = AppDatabase.getDatabase(context).fileDao()
    private val writes = app.treelune.core.history.Writes(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "attach" -> attach(params)
            "read" -> read(params)
            "delete" -> {
                val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
                val file = dao.getById(id) ?: return OperationResult.error(s.shared("file_error_not_found").format(id))
                writes.deleteFile(id)
                // A routine's message's file the history keeps stays, for an undo to put it back
                if (file.id !in AppDatabase.getDatabase(context).historyDao().keptFiles()) Files.delete(context, file)
                OperationResult.success()
            }
            "attach_image" -> attachImage(params)
            "prepare_image" -> prepareImage(params)
            "show_images" -> showImages(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun attachImage(params: JSONObject): OperationResult {
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("session_id"))
        val prepared = when (val done = prepared(params)) {
            is Preparation.Failed -> return OperationResult.error(done.error)
            is Preparation.Done -> done.image
        }
        val image = FileEntity.image(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            entryId = null,
            toolInstanceId = null,
            format = prepared.format,
            sizeBytes = prepared.bytes.size.toLong(),
            width = prepared.width,
            height = prepared.height,
            createdAt = System.currentTimeMillis()
        )
        // The file first: an interruption between the two leaves a file no row names, which the
        // startup sweep removes, never a row without its file
        Files.write(Files.file(context, image), prepared.bytes)
        writes.insertFile(image)
        return OperationResult.success(answer(image.id, prepared))
    }

    /**
     * An image prepared for an entry: its file in the folder of the images no entry holds yet,
     * where the entry's write finds it by its id, and the startup sweep removes it if the form
     * is never saved.
     */
    private fun prepareImage(params: JSONObject): OperationResult {
        val prepared = when (val done = prepared(params)) {
            is Preparation.Failed -> return OperationResult.error(done.error)
            is Preparation.Done -> done.image
        }
        val id = UUID.randomUUID().toString()
        Files.write(java.io.File(Files.pendingDir(context), prepared.format.fileName(id)), prepared.bytes)
        return OperationResult.success(answer(id, prepared))
    }

    private suspend fun showImages(params: JSONObject): OperationResult {
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("image_error_no_conversation"))
        val ids = params.optJSONArray("ids")?.let { array -> (0 until array.length()).map { array.getString(it) } }
            ?.takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("ids"))
        // Never shown to a model that would not see them: it would answer on images it never saw
        val reads = app.treelune.core.ai.providers.ImageInput.sessionModelReadsImages(context, sessionId)
        app.treelune.core.ai.providers.ImageInput.refusal(ids.size, reads) { s.shared(it) }?.let { return OperationResult.error(it) }
        val sources = ids.map { id -> dao.getById(id) ?: return OperationResult.error(s.shared("image_error_not_found").format(id)) }
        val now = System.currentTimeMillis()
        val shown = sources.map { source ->
            if (source.sessionId == sessionId) source
            else source.copy(id = UUID.randomUUID().toString(), sessionId = sessionId, entryId = null, createdAt = now).also { copy ->
                // The file first, as for any image: an interruption leaves a file the sweep removes
                Files.copy(Files.file(context, source), Files.file(context, copy))
                writes.insertFile(copy)
            }
        }
        return OperationResult.success(mapOf(
            "images" to shown.zip(sources).map { (image, source) ->
                mapOf("id" to image.id, "source_id" to source.id, "width" to image.width!!, "height" to image.height!!)
            },
            "image_ids" to shown.map { it.id }
        ))
    }

    /** An image prepared, or why it could not be. */
    private sealed interface Preparation {
        class Done(val image: Images.Prepared) : Preparation
        class Failed(val error: String) : Preparation
    }

    /** The image at the `uri` of [params] prepared. */
    private fun prepared(params: JSONObject): Preparation {
        val uri = params.optString("uri").takeIf { it.isNotEmpty() }
            ?: return Preparation.Failed(s.shared("service_error_missing_required_params").format("uri"))
        return try {
            Preparation.Done(Images.prepare(context.contentResolver, android.net.Uri.parse(uri)))
        } catch (e: Exception) {
            Preparation.Failed(s.shared("image_error_not_read").format(e.message ?: ""))
        }
    }

    private fun answer(id: String, prepared: Images.Prepared): Map<String, Any> = mapOf(
        "id" to id, "width" to prepared.width, "height" to prepared.height, "size_bytes" to prepared.bytes.size.toLong(),
        "format" to prepared.format.name, "animated" to prepared.animated
    )

    private suspend fun attach(params: JSONObject): OperationResult {
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("session_id"))
        val name = params.optString("name").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("name"))
        val content = params.optString("content")
        if (content.contains('\u0000')) return OperationResult.error(s.shared("file_error_not_text").format(name))
        val bytes = content.toByteArray(Charsets.UTF_8)
        val file = FileEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            entryId = null,
            toolInstanceId = null,
            kind = FileKind.TEXT.name,
            format = FileFormat.TEXT.name,
            name = name,
            sizeBytes = bytes.size.toLong(),
            width = null,
            height = null,
            durationMs = null,
            lineCount = FileLines.count(content),
            createdAt = System.currentTimeMillis()
        )
        // The file first, as for an image: an interruption leaves a file the sweep removes
        Files.write(Files.file(context, file), bytes)
        writes.insertFile(file)
        return OperationResult.success(mapOf("id" to file.id, "line_count" to file.lineCount!!, "size_bytes" to file.sizeBytes))
    }

    private suspend fun read(params: JSONObject): OperationResult {
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val file = dao.getById(id)?.takeIf { it.fileKind == FileKind.TEXT } ?: return OperationResult.error(s.shared("file_error_not_found").format(id))
        val start = if (params.has("start_line")) params.getInt("start_line") else 1
        val count = if (params.has("lines")) params.getInt("lines") else null
        if (start < 1 || (count != null && count < 1)) return OperationResult.error(s.shared("file_error_range").format(start, count ?: 0))
        val part = FileLines.window(Files.text(context, file), start, count)
        return OperationResult.success(mapOf(
            "id" to file.id,
            "name" to file.name!!,
            "line_count" to file.lineCount!!,
            "size_bytes" to file.sizeBytes,
            "start_line" to start,
            "lines" to part.lines,
            "text" to part.text
        ))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "attach" -> s.shared("action_verbalize_files_attach")
            "read" -> s.shared("action_verbalize_files_read")
            "delete" -> s.shared("action_verbalize_files_delete")
            "attach_image" -> s.shared("action_verbalize_files_attach_image")
            "prepare_image" -> s.shared("action_verbalize_files_prepare_image")
            "show_images" -> s.shared("action_verbalize_files_show_images")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}

/** A file's text counted and cut by lines, whatever ends them (\n, \r\n, \r). */
object FileLines {

    /** A part of a file: its text, and how many lines it holds. */
    data class Part(val text: String, val lines: Int)

    private val BREAK = Regex("\r\n|\r|\n")

    /** The lines of [content]; a last line break closes the last line, it does not open one. */
    fun split(content: String): List<String> =
        if (content.isEmpty()) emptyList() else content.split(BREAK).let { if (it.last().isEmpty()) it.dropLast(1) else it }

    fun count(content: String): Int = split(content).size

    /** The [count] lines from line [start] (from 1), all the rest when [count] is null; nothing past the end. */
    fun window(content: String, start: Int, count: Int?): Part {
        val all = split(content)
        val from = (start - 1).coerceAtMost(all.size)
        val to = if (count == null) all.size else (from + count).coerceAtMost(all.size)
        val kept = all.subList(from, to)
        return Part(kept.joinToString("\n"), kept.size)
    }
}
