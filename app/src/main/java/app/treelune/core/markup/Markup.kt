package app.treelune.core.markup

/** A piece of a line, with the emphasis its marks gave it: the theme draws each its own way (ThemeContract.emphasis). */
data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/**
 * One block of a text, as [Markup] reads it, its text in [Run]s.
 */
sealed class MarkupBlock {
    /** A subtitle, from a line `## Title`. */
    data class Heading(val runs: List<Run>) : MarkupBlock()

    /** A line of a list, from `- text`, with the icon `{name}` that may begin it in an app's text: the one its button shows. */
    data class Item(val runs: List<Run>, val icon: String?) : MarkupBlock()

    /** A line of a numbered list, from `1. text`: [number] is its place in its list, whatever was typed. */
    data class Numbered(val runs: List<Run>, val number: Int) : MarkupBlock()

    /** Any other line. */
    data class Paragraph(val runs: List<Run>) : MarkupBlock()
}

/**
 * The markup of the app's long texts, a small part of Markdown, read by one reader for the Guide,
 * a tool type's usage, an identity's texts and the user's long texts (docs/UI.md, « Long texts »):
 * `#`, `##` or `### ` begins a subtitle, `- ` or `* ` a line of a list, `1. ` a line of a numbered
 * list. Every other line is a paragraph; empty lines only separate. Inside a line, `**bold**` and
 * `*italic*` cut it into runs, their marks taken off; `***both***` is both.
 *
 * The icon `{name}` right after a list's mark, the Lucide icon of that name, is read only in the
 * app's own texts (the Guide, a tool type's usage): in the user's, a brace stays a brace.
 * scripts/check_guide.py keeps every icon of the app's texts an icon of the vocabulary, and braces
 * anywhere else out.
 *
 * The text is never rewritten: a numbered line takes its number from its place among the numbered
 * lines that come one after the other, empty lines between them included, so that a step inserted
 * in the middle needs no renumbering. Any other line between them begins a new list.
 */
object Markup {

    private val ICON = Regex("""^\{([a-z0-9-]+)\}\s*""")
    private val HEADING = Regex("""^#{1,3} """)
    private val NUMBERED = Regex("""^\d{1,9}\. """)
    private val BOLD = Regex("""\*\*(\S(?:.*?\S)?)\*\*""")
    // A star between two letters or digits (4*5) is no mark
    private val ITALIC = Regex("""(?<![\w*])\*(\S(?:.*?\S)?)\*(?![\w*])""")

    /** What stands for a bold mark taken off while the italic is looked for: neither a star nor a letter. */
    private const val TAKEN = '\u0000'

    /** [text] in blocks; [icons] for an app's text, where a list line may begin with its icon. */
    fun parse(text: String, icons: Boolean): List<MarkupBlock> {
        var number = 0
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val block = when {
                HEADING.containsMatchIn(line) -> MarkupBlock.Heading(runs(line.replaceFirst(HEADING, "").trim()))
                isBullet(line) -> {
                    val rest = line.substring(2).trim()
                    val icon = if (icons) ICON.find(rest) else null
                    MarkupBlock.Item(runs(icon?.let { rest.substring(it.range.last + 1) } ?: rest), icon?.groupValues?.get(1))
                }
                NUMBERED.containsMatchIn(line) -> MarkupBlock.Numbered(runs(line.replaceFirst(NUMBERED, "").trim()), number + 1)
                else -> MarkupBlock.Paragraph(runs(line))
            }
            number = if (block is MarkupBlock.Numbered) block.number else 0
            block
        }
    }

    /**
     * [text] on one line, its marks taken off, for where it is shown cut short (a tile, a cell of
     * a table): its blocks one after the other, a space between two.
     */
    fun plain(text: String): String = parse(text, icons = false).joinToString(" ") { words(it) }

    /**
     * [text] without its marks, a line for each of its blocks, for where it is shown as plain
     * text (a notification): a numbered line keeps the number it is read with.
     */
    fun unmarked(text: String): String = parse(text, icons = false).joinToString("\n") { block ->
        if (block is MarkupBlock.Numbered) "${block.number}. ${words(block)}" else words(block)
    }

    /** The words of [block], its runs without their emphasis. */
    private fun words(block: MarkupBlock): String {
        val runs = when (block) {
            is MarkupBlock.Heading -> block.runs
            is MarkupBlock.Item -> block.runs
            is MarkupBlock.Numbered -> block.runs
            is MarkupBlock.Paragraph -> block.runs
        }
        return runs.joinToString("") { it.text }
    }

    private fun isBullet(line: String) = line.startsWith("- ") || line.startsWith("* ")

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
