package app.treelune.core.files

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * What is known of a kept file once it is set aside (docs/DATA.md, « Kept files »): its format,
 * which says its kind, its name on the phone when it came with one, its size, and what goes with
 * its kind — an image's size in pixels, an audio's or a video's length, a text's lines. A row
 * holds it with an owner (FileEntity); a file a form prepared, owned by no row yet, is described
 * from its file and its name beside it ([Files.inspect]).
 */
data class FileDescription(
    val format: FileFormat,
    val name: String?,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val lineCount: Int? = null
) {
    val kind: FileKind get() = format.kind

    /** Its row, owned by one of [sessionId], [entryId], [toolInstanceId]. */
    fun row(id: String, sessionId: String?, entryId: String?, toolInstanceId: String?, createdAt: Long) = FileEntity(
        id = id, sessionId = sessionId, entryId = entryId, toolInstanceId = toolInstanceId,
        kind = kind.name, format = format.name, name = name, sizeBytes = sizeBytes,
        width = width, height = height, durationMs = durationMs, lineCount = lineCount, createdAt = createdAt
    )

    companion object {
        /** A row's description. */
        fun of(row: FileEntity) = FileDescription(row.fileFormat, row.name, row.sizeBytes, row.width, row.height, row.durationMs, row.lineCount)

        /**
         * The description of [file], written as [format], named [name]: read from the file itself,
         * an image's bounds, a medium's length as Android reads it, a text's lines.
         *
         * @throws IllegalArgumentException if the file does not read as its kind: an image without
         *         bounds, a medium Android cannot read or without the track its kind needs
         */
        fun read(file: File, format: FileFormat, name: String?): FileDescription = when (format.kind) {
            FileKind.IMAGE -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "${file.name} is no image this phone reads" }
                // An original turned a quarter by its EXIF is as wide as it is shown, not as it is stored
                val turned = Images.quarterTurned(file)
                FileDescription(format, name, file.length(),
                    width = if (turned) bounds.outHeight else bounds.outWidth, height = if (turned) bounds.outWidth else bounds.outHeight)
            }
            FileKind.AUDIO, FileKind.VIDEO -> FileDescription(format, name, file.length(), durationMs = mediaDuration(file, format.kind))
            FileKind.TEXT -> FileDescription(format, name, file.length(), lineCount = FileLines.count(file.readText(Charsets.UTF_8)))
        }

        /**
         * The length of the audio or video [file] as Android reads it, which also tells a file it
         * can play: one it cannot open, without the track [kind] needs (a video without its
         * picture), or without a length, is refused.
         */
        private fun mediaDuration(file: File, kind: FileKind): Long {
            val retriever = MediaMetadataRetriever()
            try {
                try {
                    retriever.setDataSource(file.path)
                } catch (e: RuntimeException) {
                    throw IllegalArgumentException("${file.name} is not a medium Android reads: ${e.message}")
                }
                val track = if (kind == FileKind.VIDEO) MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO else MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                require(retriever.extractMetadata(track) == "yes") { "${file.name} holds no ${kind.name.lowercase()} track" }
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                require(duration != null && duration > 0) { "${file.name} has no length" }
                return duration
            } finally {
                retriever.release()
            }
        }
    }
}

/** A text's lines counted and cut, whatever ends them (\n, \r\n, \r). */
object FileLines {

    /** A part of a text: its lines, and how many it holds. */
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
