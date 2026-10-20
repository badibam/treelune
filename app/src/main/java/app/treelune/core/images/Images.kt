package app.treelune.core.images

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import app.treelune.core.database.AppDatabase
import app.treelune.core.utils.LogManager
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The files of the images the app keeps (ImageEntity describes each), one per image, named by its
 * id and its format's extension. What is kept is what a model sees: never the original.
 *
 * Three folders, by who owns the file:
 * - `files/attachments/`: a message's images, deleted with their session by the service that
 *   deletes it; a file there without its row is a hard stop or a forgotten deletion (WARN);
 * - `files/attachments/entries/`: an entry's images; an entry goes by many paths (itself, its
 *   tool, its zone), its row by cascade, its file at the next startup sweep (INFO);
 * - `files/attachments/pending/`: an image prepared in a form, owned by no row until the entry
 *   is written, which moves it into `entries/`; one the form never wrote is swept (INFO).
 */
object Images {

    /** The longest side an image is reduced to: every model reads it without reducing it again. */
    const val LONG_SIDE = 1568

    /** The JPEG quality a photo is written at: 200 to 400 KB. */
    const val JPEG_QUALITY = 85

    private const val DIR = "attachments"
    private const val ENTRIES = "entries"
    private const val PENDING = "pending"
    private const val PART = ".part"

    /** The folder of the messages' images, holding the two others. */
    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun entriesDir(context: Context): File = File(dir(context), ENTRIES)

    fun pendingDir(context: Context): File = File(dir(context), PENDING)

    /** The file of [image], in its owner's folder. */
    fun file(context: Context, image: ImageEntity): File =
        File(if (image.entryId != null) entriesDir(context) else dir(context), image.imageFormat.fileName(image.id))

    /** The file of a message's image [id], written as [format]. */
    fun sessionFile(context: Context, id: String, format: ImageFormat): File = File(dir(context), format.fileName(id))

    /** The file prepared as image [id] in a form, none written by any entry yet; null when there is none. */
    fun pendingFile(context: Context, id: String): File? =
        ImageFormat.entries.map { File(pendingDir(context), it.fileName(id)) }.firstOrNull { it.exists() }

    /**
     * The file showing image [id] wherever it is: its row's, or the one a form prepared. Null when
     * neither exists: a row without its file is an error the caller says.
     */
    suspend fun locate(context: Context, id: String): File? {
        AppDatabase.getDatabase(context).imageDao().getById(id)?.let { return file(context, it).takeIf { it.exists() } }
        return pendingFile(context, id)
    }

    /**
     * [bytes] written as [target]: first as `<name>.part`, then renamed, so a final file is always
     * complete.
     */
    fun write(target: File, bytes: ByteArray) {
        target.parentFile!!.mkdirs()
        val part = File(target.parentFile, target.name + PART)
        part.writeBytes(bytes)
        check(part.renameTo(target)) { "Image ${target.name}: ${part.name} not renamed" }
    }

    /**
     * [source] copied as [target], through a part renamed, as [write] does.
     */
    fun copy(source: File, target: File) {
        target.parentFile!!.mkdirs()
        val part = File(target.parentFile, target.name + PART)
        source.copyTo(part, overwrite = true)
        check(part.renameTo(target)) { "Image ${target.name}: ${part.name} not renamed" }
    }

    /**
     * The file of a message's image as base64, for a request to the model.
     *
     * @throws IllegalStateException if its file is missing: a row without its file is an error
     */
    fun base64(context: Context, id: String, format: ImageFormat): String {
        val file = sessionFile(context, id, format)
        check(file.exists()) { "Image $id: its file is missing" }
        return java.util.Base64.getEncoder().encodeToString(file.readBytes())
    }

    /** The file of [image] deleted; one already gone is no error. */
    fun delete(context: Context, image: ImageEntity) = deleteFile(file(context, image))

    fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) LogManager.service("Image file ${file.name} not deleted", "ERROR")
    }

    /**
     * An image ready to keep: its bytes in [format], its size in pixels, and whether its source
     * moved, of which only the first frame is kept.
     */
    class Prepared(val bytes: ByteArray, val width: Int, val height: Int, val format: ImageFormat, val animated: Boolean)

    /**
     * The image at [uri] prepared to be kept: turned upright as the camera noted it, reduced to
     * [LONG_SIDE] on its longest side (never enlarged), written in the format it keeps
     * ([formatFor]). Writing it anew leaves every metadata tag behind, the GPS position among them.
     * An animated image keeps its first frame, which is what Android decodes.
     *
     * @throws IllegalArgumentException if [uri] is not an image Android can read
     */
    fun prepare(resolver: ContentResolver, uri: Uri): Prepared {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image: $uri" }

        val head = resolver.openInputStream(uri).use { stream -> stream?.let { readHead(it) } } ?: ByteArray(0)
        val orientation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } ?: ExifInterface.ORIENTATION_NORMAL

        // Decoded at a power-of-two fraction that keeps it at least as large as needed, so a
        // 50 MP photo never sits whole in memory
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalArgumentException("Image not decoded: $uri")

        val (width, height) = reducedSize(decoded.width, decoded.height)
        val matrix = orientationMatrix(orientation).apply {
            preScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()

        val format = formatFor(bounds.outMimeType, upright.hasAlpha())
        val bytes = ByteArrayOutputStream().use { out ->
            // The quality is the JPEG's; a PNG is written without loss whatever it says
            upright.compress(format.compress, JPEG_QUALITY, out)
            out.toByteArray()
        }
        val prepared = Prepared(bytes, upright.width, upright.height, format, animated(head))
        upright.recycle()
        return prepared
    }

    /**
     * The format an image of [mimeType] keeps: a JPEG stays a JPEG and a PNG a PNG; any other
     * (WebP, HEIC, GIF) becomes a PNG when it has transparency ([hasAlpha]), a JPEG otherwise.
     */
    fun formatFor(mimeType: String?, hasAlpha: Boolean): ImageFormat = when (mimeType) {
        "image/jpeg" -> ImageFormat.JPEG
        "image/png" -> ImageFormat.PNG
        else -> if (hasAlpha) ImageFormat.PNG else ImageFormat.JPEG
    }

    /** How much of a file [animated] reads: the marks it looks for sit near the start. */
    private const val HEAD_BYTES = 64 * 1024

    /** The first [HEAD_BYTES] of [stream], fewer for a shorter file. */
    private fun readHead(stream: java.io.InputStream): ByteArray {
        val buffer = ByteArray(HEAD_BYTES)
        var read = 0
        while (read < buffer.size) {
            val n = stream.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    /**
     * Whether the file starting with [head] is animated: a GIF with its looping extension, a WebP
     * whose header sets its animation flag, a PNG with an animation chunk (APNG).
     */
    fun animated(head: ByteArray): Boolean {
        fun at(offset: Int, text: String) = head.size >= offset + text.length &&
            text.indices.all { head[offset + it] == text[it].code.toByte() }
        fun contains(text: String) = (0..head.size - text.length).any { at(it, text) }
        return when {
            at(0, "GIF8") -> contains("NETSCAPE2.0") || contains("ANIMEXTS1.0")
            // RIFF....WEBPVP8X, then a byte of flags whose bit 1 is the animation
            at(0, "RIFF") && at(8, "WEBPVP8X") -> head.size > 20 && (head[20].toInt() and 0x02) != 0
            at(1, "PNG") -> contains("acTL")
            else -> false
        }
    }

    /** The size [width] × [height] takes once its longest side is at most [LONG_SIDE]. */
    fun reducedSize(width: Int, height: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= LONG_SIDE) return width to height
        val ratio = LONG_SIDE.toDouble() / longest
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }

    /** The largest power of two dividing [width] × [height] while its longest side stays at least [LONG_SIDE]. */
    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= LONG_SIDE) sample *= 2
        return sample
    }

    /** The turn and the mirror an EXIF orientation asks for. */
    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
    }

    /** Where a backup's images wait while its tables are imported: beside the folder, never swept. */
    fun importDir(context: Context): File = File(context.filesDir, "$DIR.import")

    /**
     * A backup's images, set aside flat in [staging], sorted the way the folder holds them: an
     * entry's into `entries/`, by [entryImages], the names of their files.
     */
    fun sortImport(staging: File, entryImages: Collection<String>) {
        if (entryImages.isEmpty()) return
        val entries = File(staging, ENTRIES).apply { mkdirs() }
        entryImages.forEach { name ->
            val file = File(staging, name)
            if (file.exists()) check(file.renameTo(File(entries, name))) { "The backup's image $name could not be moved into place" }
        }
    }

    /**
     * The folder emptied, and given [replacement]'s files when there is one (a backup's, set
     * aside while its tables were imported).
     */
    fun replaceDir(context: Context, replacement: File?) {
        val dir = dir(context)
        check(!dir.exists() || dir.deleteRecursively()) { "The images' folder could not be emptied" }
        if (replacement != null && replacement.exists()) check(replacement.renameTo(dir)) { "The backup's images could not be moved into place" }
    }

    /** What the startup sweep removes from one folder: the parts left, and the images no row names. */
    data class Sweep(val parts: List<String>, val orphans: List<String>)

    /**
     * The files of a folder, by [names], that no image of [expected] (the names of their files)
     * is: a `.part` is a write the app was killed in; an image file without a row is one whose
     * owner went. Anything else in the folder, its subfolders among them, is left alone.
     */
    fun sweep(names: List<String>, expected: Set<String>): Sweep = Sweep(
        parts = names.filter { it.endsWith(PART) },
        orphans = names.filter { name -> ImageFormat.ofExtension(name.substringAfterLast('.', "")) != null && name !in expected }
    )

    @Volatile private var swept = false

    /**
     * The folders confronted with the table once per process, before any screen: nothing can be
     * joining an image or writing an entry then. Each file removed is logged: INFO for a part, a
     * form's image never written and an entry's image whose entry went, cases the design expects;
     * WARN by its name for a message's image without a row, which comes from a hard stop or a
     * deletion path that forgot the file. The rows of the images their entry no longer names go
     * too, with their files.
     *
     * What the history of writes still names stays, row and file: an image whose row a step keeps,
     * an image a copy of an entry names (docs/design/undo-history.md) — an undo puts them back.
     */
    suspend fun sweepOnce(context: Context) {
        if (swept) return
        swept = true
        val dao = AppDatabase.getDatabase(context).imageDao()

        // The app's own write, at its start: noted by the history, never a step
        val writes = app.treelune.core.history.Writes(context)
        val noted = app.treelune.core.history.Step(app.treelune.core.coordinator.Source.SYSTEM, isStep = false, sessionId = null)
        val history = AppDatabase.getDatabase(context).historyDao()
        val kept = history.keptImages().toSet()
        dao.unnamedByTheirEntry().filterNot { history.entriesName(it.id) }.forEach { image ->
            kotlinx.coroutines.withContext(noted) { writes.deleteImage(image.id) }
            delete(context, image)
            LogManager.service("Startup sweep: image ${image.id} no longer named by its entry ${image.entryId}, removed", "INFO")
        }

        val images = dao.getAll()
        fun names(owned: List<ImageEntity>) = owned.map { it.imageFormat.fileName(it.id) }.toSet()
        // A file is named by its image's id and its format's extension
        fun keptByHistory(name: String) = name.substringBeforeLast('.') in kept

        dir(context).list()?.toList()?.let { found ->
            val sweep = sweep(found, names(images.filter { it.sessionId != null }))
            sweep.parts.forEach { name ->
                File(dir(context), name).delete()
                LogManager.service("Startup sweep: interrupted image write $name removed", "INFO")
            }
            sweep.orphans.filterNot(::keptByHistory).forEach { name ->
                File(dir(context), name).delete()
                LogManager.service("Startup sweep: image file $name named by no row, removed", "WARN")
            }
        }
        entriesDir(context).list()?.toList()?.let { found ->
            val sweep = sweep(found, names(images.filter { it.entryId != null }))
            (sweep.parts + sweep.orphans.filterNot(::keptByHistory)).forEach { name ->
                File(entriesDir(context), name).delete()
                LogManager.service("Startup sweep: entry image file $name, its entry or its write gone, removed", "INFO")
            }
        }
        pendingDir(context).list()?.toList()?.takeIf { it.isNotEmpty() }?.let { found ->
            pendingDir(context).deleteRecursively()
            LogManager.service("Startup sweep: ${found.size} images prepared in a form and never written, removed", "INFO")
        }
    }
}
