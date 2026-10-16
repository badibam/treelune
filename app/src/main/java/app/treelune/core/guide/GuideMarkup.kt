package app.treelune.core.guide

/**
 * One block of a Guide text, as [GuideMarkup] reads it.
 */
sealed class GuideBlock {
    /** A subtitle, from a line `## Title`. */
    data class Heading(val text: String) : GuideBlock()

    /** A short line of a list, from `- text`, with the icon `{name}` that may begin it: the one its button shows. */
    data class Item(val text: String, val icon: String?) : GuideBlock()

    /** Any other line. */
    data class Paragraph(val text: String) : GuideBlock()
}

/**
 * The markup of the app's texts, a small part of Markdown, read by one reader for the Guide, a
 * tool type's usage and an identity's texts (docs/design/incarnations.md): `#`, `##` or `### `
 * begins a subtitle, `- ` or `* ` a line of a list, and `{name}` right after it the Lucide icon of
 * that name. Every other line is a paragraph; empty lines only separate. Emphasis (`**bold**`,
 * `*italic*`) is read and its marks taken off: the themes draw plain text. A tool type's usage is
 * written in it too, which the AI reads as Markdown. scripts/check_guide.py keeps every icon named
 * an icon of the vocabulary, and braces anywhere else out.
 */
object GuideMarkup {

    private val ICON = Regex("""^\{([a-z0-9-]+)\}\s*""")
    private val HEADING = Regex("""^#{1,3} """)
    private val BOLD = Regex("""\*\*(\S(?:.*?\S)?)\*\*""")
    private val ITALIC = Regex("""(?<![\w*])\*(\S(?:.*?\S)?)\*(?![\w*])""")

    fun parse(text: String): List<GuideBlock> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when {
            HEADING.containsMatchIn(line) -> GuideBlock.Heading(plain(line.replaceFirst(HEADING, "").trim()))
            line.startsWith("- ") || line.startsWith("* ") -> {
                val rest = line.substring(2).trim()
                val icon = ICON.find(rest)
                GuideBlock.Item(plain(icon?.let { rest.substring(it.range.last + 1) } ?: rest), icon?.groupValues?.get(1))
            }
            else -> GuideBlock.Paragraph(plain(line))
        }
    }

    /** [line] without its emphasis marks. */
    private fun plain(line: String): String = line.replace(BOLD, "$1").replace(ITALIC, "$1")
}
