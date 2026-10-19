package app.treelune.core.images

import android.graphics.Bitmap

/**
 * What a kept image's file is written as. A photo stays a JPEG; a PNG stays a PNG, without loss,
 * its transparency kept: a screenshot or a diagram would blur as a JPEG. Any other format becomes
 * one of the two (Images.formatFor).
 */
enum class ImageFormat(val extension: String, val mediaType: String, val compress: Bitmap.CompressFormat) {
    JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", "image/png", Bitmap.CompressFormat.PNG);

    /** The name of the file of image [id]. */
    fun fileName(id: String): String = "$id.$extension"

    companion object {
        /** The format whose files end with [extension], null for any other. */
        fun ofExtension(extension: String): ImageFormat? = entries.firstOrNull { it.extension == extension }
    }
}
