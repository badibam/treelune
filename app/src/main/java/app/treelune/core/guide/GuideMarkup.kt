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
 * The markup of the Guide's texts (docs/design/user-journey.md, « Les textes »), a small part of
 * Markdown: `## ` begins a subtitle, `- ` a line of a list, and `{name}` right after it the Lucide
 * icon of that name. Every other line is a paragraph; empty lines only separate. A tool type's
 * usage is written in it too, which the AI reads as Markdown. scripts/check_guide.py keeps every
 * icon named an icon of the vocabulary, and braces anywhere else out.
 */
object GuideMarkup {

    private val ICON = Regex("""^\{([a-z0-9-]+)\}\s*""")

    fun parse(text: String): List<GuideBlock> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when {
            line.startsWith("## ") -> GuideBlock.Heading(line.removePrefix("## ").trim())
            line.startsWith("- ") -> {
                val rest = line.removePrefix("- ").trim()
                val icon = ICON.find(rest)
                GuideBlock.Item(icon?.let { rest.substring(it.range.last + 1) } ?: rest, icon?.groupValues?.get(1))
            }
            else -> GuideBlock.Paragraph(line)
        }
    }
}
