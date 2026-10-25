package app.treelune.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID

/**
 * A file of the phone set aside for a form (docs/DATA.md, « Kept files »): its kind told from its
 * media type, or from its name's extension when the type says nothing, held to the kinds its place
 * takes, then written into the pending folder under a new id, owned by no row until the entry or
 * the tool that holds it is written (OwnedFiles). Its name, when it came with one, waits beside it
 * (Files.pendingName).
 *
 * - An image is reduced as a photo is (Images.prepare), or kept as the original, byte for byte,
 *   when asked (Images.original); the name of a photo is left behind.
 * - An audio or a video is copied as it came, never encoded again, held to its kind's size, and
 *   refused when Android cannot play it (FileDescription.read).
 * - A text is read as UTF-8, its byte order mark left out; a file that is not text is refused.
 */
object FilePreparation {

    /** A file set aside, or why it could not be. */
    sealed interface Result {
        /** Set aside as [id]; [animated] for an image that moved, of which only the first frame is kept. */
        class Done(val id: String, val description: FileDescription, val animated: Boolean) : Result
        class Refused(val message: String) : Result
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    private val TEXT_EXTENSIONS = setOf("txt", "csv", "tsv", "md", "json")
    private val TEXT_TYPES = setOf("application/json", "application/csv", "application/vnd.ms-excel")

    /**
     * The media types the phone's picker offers for [kinds]: what it lets the user choose, the
     * kind of what comes back being checked again on its way in.
     */
    fun pickerTypes(kinds: Collection<FileKind>): Array<String> = kinds.flatMap { kind ->
        when (kind) {
            FileKind.IMAGE -> listOf("image/*")
            FileKind.AUDIO -> listOf("audio/*", "application/ogg")
            FileKind.VIDEO -> listOf("video/mp4")
            FileKind.TEXT -> listOf("text/*") + TEXT_TYPES
        }
    }.distinct().toTypedArray()

    /**
     * The file at [uri] set aside, if it is of one of [kinds]; an image kept whole when [original];
     * [name], when given, its name rather than the one the phone gives.
     *
     * @param text The shared strings, for a refusal
     */
    fun prepare(context: Context, uri: Uri, kinds: Collection<FileKind>, original: Boolean = false, name: String? = null, text: (String) -> String): Result {
        val resolver = context.contentResolver
        val (shown, knownSize) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) (if (cursor.isNull(0)) null else cursor.getString(0)) to (if (cursor.isNull(1)) null else cursor.getLong(1))
            else null to null
        } ?: (uri.lastPathSegment to null)
        // A name given (an upload's, asked with its address) says what the file is better than its path
        val name = name ?: shown
        val mediaType = resolver.getType(uri)?.lowercase()
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val media = FileFormat.ofMedia(mediaType, name)

        val kind = when {
            mediaType?.startsWith("image/") == true || (mediaType == null && extension in IMAGE_EXTENSIONS) -> FileKind.IMAGE
            media != null -> media.kind
            mediaType?.startsWith("text/") == true || mediaType in TEXT_TYPES || extension in TEXT_EXTENSIONS -> FileKind.TEXT
            else -> null
        }
        if (kind == null || kind !in kinds) {
            return Result.Refused(text("file_error_not_accepted").format(name ?: uri.toString(), kinds.joinToString(", ") { text("file_kind_${it.name.lowercase()}") }))
        }

        val id = UUID.randomUUID().toString()
        val dir = Files.pendingDir(context).apply { mkdirs() }
        return when (kind) {
            FileKind.IMAGE -> if (original) try {
                val (format, bytes) = Images.original(resolver, uri, mediaType, name)
                val target = File(dir, format.fileName(id))
                Files.write(target, bytes)
                try {
                    Result.Done(id, FileDescription.read(target, format, null), animated = Images.animated(bytes.copyOf(minOf(bytes.size, 64 * 1024))))
                } catch (e: IllegalArgumentException) {
                    Files.deleteFile(target)
                    Result.Refused(text("image_error_not_read").format(e.message ?: ""))
                }
            } catch (e: IllegalArgumentException) {
                Result.Refused(text("image_error_original_format").format(e.message ?: ""))
            } else try {
                val prepared = Images.prepare(resolver, uri)
                Files.write(File(dir, prepared.format.fileName(id)), prepared.bytes)
                Result.Done(id, FileDescription(prepared.format, null, prepared.bytes.size.toLong(), width = prepared.width, height = prepared.height), prepared.animated)
            } catch (e: Exception) {
                Result.Refused(text("image_error_not_read").format(e.message ?: ""))
            }
            FileKind.AUDIO, FileKind.VIDEO -> {
                val format = media!!
                val max = kind.maxBytes!!
                if (knownSize != null && knownSize > max) return Result.Refused(tooLarge(name, kind, max, text))
                val target = File(dir, format.fileName(id))
                val copied = try {
                    copyAtMost(resolver.openInputStream(uri) ?: return Result.Refused(text("file_error_not_readable").format(name ?: "", "")), target, max)
                } catch (e: java.io.IOException) {
                    return Result.Refused(text("file_error_not_readable").format(name ?: "", e.message ?: ""))
                }
                if (copied == null) return Result.Refused(tooLarge(name, kind, max, text))
                try {
                    val description = FileDescription.read(target, format, name)
                    name?.let { Files.pendingName(context, id).writeText(it, Charsets.UTF_8) }
                    Result.Done(id, description, animated = false)
                } catch (e: IllegalArgumentException) {
                    Files.deleteFile(target)
                    Result.Refused(text("file_error_not_readable").format(name ?: "", e.message ?: ""))
                }
            }
            FileKind.TEXT -> {
                val content = when (val read = readText(context, uri, name, text)) {
                    is TextRead.Refused -> return Result.Refused(read.message)
                    is TextRead.Read -> read.content
                }
                val bytes = content.toByteArray(Charsets.UTF_8)
                Files.write(File(dir, FileFormat.TEXT.fileName(id)), bytes)
                name?.let { Files.pendingName(context, id).writeText(it, Charsets.UTF_8) }
                Result.Done(id, FileDescription(FileFormat.TEXT, name, bytes.size.toLong(), lineCount = FileLines.count(content)), animated = false)
            }
        }
    }

    /** A text read from the phone, or why it is none. */
    sealed interface TextRead {
        class Read(val content: String) : TextRead
        class Refused(val message: String) : TextRead
    }

    /**
     * The text at [uri], named [name], read as UTF-8 whole, its byte order mark left out: refused
     * when it is not UTF-8, or holds a NUL character, the mark of a file that is not text.
     */
    fun readText(context: Context, uri: Uri, name: String?, text: (String) -> String): TextRead {
        val shown = name ?: uri.toString()
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return TextRead.Refused(text("import_error_file").format(shown))
            val content = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
            if (content.contains('\u0000')) TextRead.Refused(text("file_error_not_text").format(shown)) else TextRead.Read(content)
        } catch (e: CharacterCodingException) {
            TextRead.Refused(text("file_error_encoding").format(shown))
        } catch (e: java.io.IOException) {
            TextRead.Refused(text("import_error_file").format(e.message ?: shown))
        }
    }

    /**
     * [input] copied into [target] through a part renamed, closed after; the bytes copied, or null
     * once they pass [max], the part then deleted.
     */
    private fun copyAtMost(input: java.io.InputStream, target: File, max: Long): Long? {
        val part = File(target.parentFile, target.name + ".part")
        var copied = 0L
        input.use { source ->
            part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    copied += n
                    if (copied > max) {
                        out.close()
                        part.delete()
                        return null
                    }
                    out.write(buffer, 0, n)
                }
            }
        }
        check(part.renameTo(target)) { "File ${target.name}: ${part.name} not renamed" }
        return copied
    }

    private fun tooLarge(name: String?, kind: FileKind, max: Long, text: (String) -> String) =
        text("file_error_too_large").format(name ?: "", text("file_kind_${kind.name.lowercase()}"), max / (1024 * 1024))
}
