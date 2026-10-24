package app.treelune.core.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What is particular to an image the app keeps (docs/DATA.md, « FILE »): it is reduced when it is
 * added, to what a model sees, never kept as the original. Its file lives with the others (Files).
 */
object Images {

    /** The longest side an image is reduced to: every model reads it without reducing it again. */
    const val LONG_SIDE = 1568

    /** The JPEG quality a photo is written at: 200 to 400 KB. */
    const val JPEG_QUALITY = 85

    /**
     * An image ready to keep: its bytes in [format], its size in pixels, and whether its source
     * moved, of which only the first frame is kept.
     */
    class Prepared(val bytes: ByteArray, val width: Int, val height: Int, val format: FileFormat, val animated: Boolean)

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
            upright.compress(compressFormat(format), JPEG_QUALITY, out)
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
    fun formatFor(mimeType: String?, hasAlpha: Boolean): FileFormat = when (mimeType) {
        "image/jpeg" -> FileFormat.JPEG
        "image/png" -> FileFormat.PNG
        else -> if (hasAlpha) FileFormat.PNG else FileFormat.JPEG
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

    /** How an image of [format] is compressed. */
    private fun compressFormat(format: FileFormat): Bitmap.CompressFormat = when (format) {
        FileFormat.JPEG -> Bitmap.CompressFormat.JPEG
        FileFormat.PNG -> Bitmap.CompressFormat.PNG
        else -> throw IllegalArgumentException("$format is no image format")
    }

    /**
     * The file of a message's image as base64, for a request to the model.
     *
     * @throws IllegalStateException if its file is missing: a row without its file is an error
     */
    fun base64(context: Context, id: String, format: FileFormat): String {
        val file = Files.sessionFile(context, id, format)
        check(file.exists()) { "Image $id: its file is missing" }
        return java.util.Base64.getEncoder().encodeToString(file.readBytes())
    }
}
