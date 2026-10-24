package app.treelune.core.files

/**
 * What a kept file is (docs/DATA.md, « FILE »): its kind says how it is added, shown or played,
 * reduced, and what the AI reads of it.
 */
enum class FileKind { IMAGE, TEXT }

/**
 * What a kept file is written as, which says its kind. A photo stays a JPEG; a PNG stays a PNG,
 * without loss, its transparency kept: a screenshot or a diagram would blur as a JPEG. Any other
 * image format becomes one of the two (Images.formatFor). A text is kept as UTF-8, its name on the
 * phone saying what it holds ("aliments.csv").
 */
enum class FileFormat(val kind: FileKind, val extension: String, val mediaType: String) {
    JPEG(FileKind.IMAGE, "jpg", "image/jpeg"),
    PNG(FileKind.IMAGE, "png", "image/png"),
    TEXT(FileKind.TEXT, "txt", "text/plain");

    /** The name of the file of file [id]. */
    fun fileName(id: String): String = "$id.$extension"

    companion object {
        /** The format whose files end with [extension], null for any other. */
        fun ofExtension(extension: String): FileFormat? = entries.firstOrNull { it.extension == extension }
    }
}
