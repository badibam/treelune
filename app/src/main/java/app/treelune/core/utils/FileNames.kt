package app.treelune.core.utils

/**
 * The one place a text of the app (a tool's name, a zone's) becomes the name of a file: every
 * file the app names from what the user wrote goes through [clean], never through a replacement
 * written at its call site.
 */
object FileNames {

    /** What a file system of a phone, a computer or a cloud folder may refuse in a name. */
    private val FORBIDDEN = Regex("[/\\\\:*?\"<>|\\p{Cntrl}]")

    /** Names a Windows computer refuses whatever their extension, copied there from the phone. */
    private val RESERVED = setOf("con", "prn", "aux", "nul") + (1..9).flatMap { listOf("com$it", "lpt$it") }

    /** Long enough for any name the app shows; a file name holds 255 bytes, a date and an extension go beside it. */
    private const val MAX_LENGTH = 80

    /**
     * [text] as the start of a file name: each forbidden character an underscore, the runs of
     * spaces one, the spaces and dots around it removed, [MAX_LENGTH] characters at most, a
     * reserved name followed by an underscore.
     *
     * @throws IllegalArgumentException when nothing of [text] is left to name a file
     */
    fun clean(text: String): String {
        val cleaned = text.replace(FORBIDDEN, "_").replace(Regex("\\s+"), " ").trim(' ', '.').take(MAX_LENGTH).trimEnd(' ', '.')
        require(cleaned.isNotEmpty() && cleaned.any { it != '_' }) { "Nothing of \"$text\" names a file" }
        return if (cleaned.lowercase() in RESERVED) "${cleaned}_" else cleaned
    }

    /**
     * The name [base].[extension] among [taken] (a folder's names), followed by `_2`, `_3`… when
     * it is taken: a file is never written over.
     */
    fun unique(base: String, extension: String, taken: Set<String>): String =
        generateSequence(1) { it + 1 }.map { n -> if (n == 1) "$base.$extension" else "${base}_$n.$extension" }.first { it !in taken }
}
