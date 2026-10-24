package app.treelune.core.files

/**
 * What a kept file is (docs/DATA.md, « Kept files »): its kind says how it is added, shown or
 * played, reduced, and what the AI reads of it.
 *
 * @property maxBytes The largest file of the kind set aside, refused beyond, saying so; null for an
 *           image, reduced on its way in, and for a text
 */
enum class FileKind(val maxBytes: Long?) {
    IMAGE(null),
    AUDIO(50L * 1024 * 1024),
    VIDEO(200L * 1024 * 1024),
    TEXT(null)
}

/**
 * What a kept file is written as, which says its kind. A photo stays a JPEG; a PNG stays a PNG,
 * without loss, its transparency kept: a screenshot or a diagram would blur as a JPEG. Any other
 * image format becomes one of the two (Images.formatFor), but for an image kept as the original,
 * byte for byte, in its own format among these (Images.original). An audio and a video are kept as they
 * came, never encoded again, in a format Android plays without anything added: their [mediaTypes]
 * and [extensions] are how a file is recognised. A text is kept as UTF-8, its name on the phone
 * saying what it holds ("aliments.csv").
 *
 * @property extension Its kept file's extension
 * @property mediaTypes The media types a file of this format comes as, the first the one it is given out as
 * @property extensions The extensions a file of this format comes with, its own first
 */
enum class FileFormat(val kind: FileKind, val extension: String, val mediaTypes: List<String>, val extensions: List<String> = listOf(extension)) {
    JPEG(FileKind.IMAGE, "jpg", listOf("image/jpeg")),
    PNG(FileKind.IMAGE, "png", listOf("image/png")),
    WEBP(FileKind.IMAGE, "webp", listOf("image/webp")),
    HEIC(FileKind.IMAGE, "heic", listOf("image/heic", "image/heif"), listOf("heic", "heif")),
    GIF(FileKind.IMAGE, "gif", listOf("image/gif")),
    MP3(FileKind.AUDIO, "mp3", listOf("audio/mpeg", "audio/mp3")),
    M4A(FileKind.AUDIO, "m4a", listOf("audio/mp4", "audio/x-m4a", "audio/m4a")),
    AAC(FileKind.AUDIO, "aac", listOf("audio/aac", "audio/aacp", "audio/x-aac")),
    OGG(FileKind.AUDIO, "ogg", listOf("audio/ogg", "audio/opus", "application/ogg"), listOf("ogg", "opus", "oga")),
    WAV(FileKind.AUDIO, "wav", listOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")),
    MP4(FileKind.VIDEO, "mp4", listOf("video/mp4")),
    TEXT(FileKind.TEXT, "txt", listOf("text/plain"));

    /** The media type a file of this format is given out as. */
    val mediaType: String get() = mediaTypes.first()

    /** The name of the file of file [id]. */
    fun fileName(id: String): String = "$id.$extension"

    companion object {
        /** The image format of an original that comes as [mediaType], named [name]; null for any other. */
        fun ofOriginalImage(mediaType: String?, name: String?): FileFormat? {
            val images = entries.filter { it.kind == FileKind.IMAGE }
            mediaType?.lowercase()?.let { type -> images.firstOrNull { type in it.mediaTypes }?.let { return it } }
            val extension = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return images.firstOrNull { extension in it.extensions || (it == JPEG && extension == "jpeg") }
        }

        /** The format whose kept files end with [extension], null for any other. */
        fun ofExtension(extension: String): FileFormat? = entries.firstOrNull { it.extension == extension }

        /**
         * The audio or video format of a file that comes as [mediaType], named [name]: its media
         * type first, its name's extension when the type says nothing precise; null when neither
         * is one of these formats.
         */
        fun ofMedia(mediaType: String?, name: String?): FileFormat? {
            val media = entries.filter { it.kind == FileKind.AUDIO || it.kind == FileKind.VIDEO }
            mediaType?.lowercase()?.let { type -> media.firstOrNull { type in it.mediaTypes }?.let { return it } }
            val extension = name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return media.firstOrNull { extension in it.extensions }
        }
    }
}
