package app.treelune.core.guide

/** A piece of a line, with the emphasis its marks gave it: the theme draws each its own way (ThemeContract.emphasis). */
data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/**
 * One block of a Guide text, as [GuideMarkup] reads it, its text in [Run]s.
 */
sealed class GuideBlock {
    /** A subtitle, from a line `## Title`. */
    data class Heading(val runs: List<Run>) : GuideBlock()

    /** A short line of a list, from `- text`, with the icon `{name}` that may begin it: the one its button shows. */
    data class Item(val runs: List<Run>, val icon: String?) : GuideBlock()

    /** Any other line. */
    data class Paragraph(val runs: List<Run>) : GuideBlock()
}

/**
 * The markup of the app's texts, a small part of Markdown, read by one reader for the Guide, a
 * tool type's usage and an identity's texts (docs/design/incarnations.md): `#`, `##` or `### `
 * begins a subtitle, `- ` or `* ` a line of a list, and `{name}` right after it the Lucide icon of
 * that name. Every other line is a paragraph; empty lines only separate. Inside a line,
 * `**bold**` and `*italic*` cut it into runs, their marks taken off; `***both***` is both. A
 * tool type's usage is written in it too, which the AI reads as Markdown. scripts/check_guide.py
 * keeps every icon named an icon of the vocabulary, and braces anywhere else out.
 */
object GuideMarkup {

    private val ICON = Regex("""^\{([a-z0-9-]+)\}\s*""")
    private val HEADING = Regex("""^#{1,3} """)
    private val BOLD = Regex("""\*\*(\S(?:.*?\S)?)\*\*""")
    // A star between two letters or digits (4*5) is no mark
    private val ITALIC = Regex("""(?<![\w*])\*(\S(?:.*?\S)?)\*(?![\w*])""")

    /** What stands for a bold mark taken off while the italic is looked for: neither a star nor a letter. */
    private const val TAKEN = '\u0000'

    fun parse(text: String): List<GuideBlock> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when {
            HEADING.containsMatchIn(line) -> GuideBlock.Heading(runs(line.replaceFirst(HEADING, "").trim()))
            line.startsWith("- ") || line.startsWith("* ") -> {
                val rest = line.substring(2).trim()
                val icon = ICON.find(rest)
                GuideBlock.Item(runs(icon?.let { rest.substring(it.range.last + 1) } ?: rest), icon?.groupValues?.get(1))
            }
            else -> GuideBlock.Paragraph(runs(line))
        }
    }

    /**
     * [line] cut into runs of the same emphasis. The bold is found first, so that `***both***`
     * reads as an italic inside a bold; its marks are then masked, so that the italic does not
     * take one of their stars for its own.
     */
    fun runs(line: String): List<Run> {
        val n = line.length
        val bold = BooleanArray(n)
        val italic = BooleanArray(n)
        val mark = BooleanArray(n)
        val masked = StringBuilder(line)
        BOLD.findAll(line).forEach { m ->
            val inner = m.groups[1]!!.range
            for (i in inner) bold[i] = true
            for (i in listOf(m.range.first, m.range.first + 1, m.range.last - 1, m.range.last)) {
                mark[i] = true
                masked[i] = TAKEN
            }
        }
        ITALIC.findAll(masked).forEach { m ->
            for (i in m.groups[1]!!.range) italic[i] = true
            mark[m.range.first] = true
            mark[m.range.last] = true
        }
        val runs = mutableListOf<Run>()
        val piece = StringBuilder()
        var current: Pair<Boolean, Boolean>? = null
        for (i in 0 until n) {
            if (mark[i]) continue
            val emphasis = bold[i] to italic[i]
            if (emphasis != current && piece.isNotEmpty()) {
                runs += Run(piece.toString(), current!!.first, current.second)
                piece.clear()
            }
            current = emphasis
            piece.append(line[i])
        }
        if (piece.isNotEmpty()) runs += Run(piece.toString(), current!!.first, current.second)
        return runs
    }
}
