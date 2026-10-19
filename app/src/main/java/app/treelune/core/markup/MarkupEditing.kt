package app.treelune.core.markup

/** A text with its selection, [start] to [end]; one place, the cursor, when they are equal. */
data class MarkedInput(val text: String, val start: Int, val end: Int)

/** The two lists the input sets on its lines (docs/design/text-formatting.md). */
enum class ListKind { BULLET, NUMBERED }

/** The two emphases the selection bar puts around a selection, by their mark. */
enum class EmphasisMark(val mark: String) { BOLD("**"), ITALIC("*") }

/**
 * The help of a long text's input, on the text as typed (Markup): never a rendering, only marks
 * written or taken off, so that the text stored stays what the user would have typed.
 *
 * - A new line after a line of a list begins with the list's mark, the next number for a numbered
 *   list; a new line on a line holding only its mark takes the mark off and ends the list.
 * - A list's button sets its mark on each line the selection touches, or takes it off when they
 *   all have it already.
 * - Bold and italic put their marks around the selection, line by line, or take them off when it
 *   is inside them already.
 */
object MarkupEditing {

    // A list's mark at the head of a line, the spaces before it kept
    private val BULLET = Regex("""^(\s*)([-*]) """)
    private val NUMBERED = Regex("""^(\s*)(\d{1,9})\. """)

    /** A replacement of [start] to [end] of a text by [with]. */
    private data class Change(val start: Int, val end: Int, val with: String)

    /**
     * What [typed] becomes when it is [before] with a new line typed, at its cursor, after a line
     * of a list; null when it is anything else, kept as typed.
     */
    fun continueList(before: String, typed: MarkedInput): MarkedInput? {
        val at = typed.start - 1
        if (typed.start != typed.end || at < 0 || typed.text.length != before.length + 1) return null
        if (typed.text[at] != '\n' || typed.text.removeRange(at, at + 1) != before) return null
        val lineStart = before.lastIndexOf('\n', at - 1) + 1
        val line = before.substring(lineStart, at)
        val bullet = BULLET.find(line)
        val numbered = NUMBERED.find(line)
        val mark = bullet ?: numbered ?: return null
        // A new line typed inside the mark itself is no new item
        if (at - lineStart < mark.range.last + 1) return null
        if (line.substring(mark.range.last + 1).isBlank()) {
            // An empty item: its mark goes, the new line with it, and the list ends there
            val text = before.substring(0, lineStart) + before.substring(at)
            return MarkedInput(text, lineStart, lineStart)
        }
        val next = if (bullet != null) "${bullet.groupValues[1]}${bullet.groupValues[2]} "
            else "${numbered!!.groupValues[1]}${numbered.groupValues[2].toLong() + 1}. "
        val text = typed.text.substring(0, at + 1) + next + typed.text.substring(at + 1)
        val cursor = at + 1 + next.length
        return MarkedInput(text, cursor, cursor)
    }

    /**
     * [input] with the mark of [kind] set on each line its selection touches, blank lines left
     * alone, in place of the other list's; taken off each instead when they all have it. A
     * numbered line is written with the number after the line above's, or 1: the reader numbers
     * by position anyway, but the text reads right as typed.
     */
    fun toggleList(input: MarkedInput, kind: ListKind): MarkedInput {
        val lines = touchedLines(input)
        val filled = lines.filter { (s, e) -> input.text.substring(s, e).isNotBlank() }
        if (filled.isEmpty()) {
            // A blank line, or none: the mark alone, where the cursor stands
            val (s, e) = lines.first()
            return apply(input, listOf(Change(s, e, markOf(kind, numberBefore(input.text, s) + 1))))
        }
        val own = if (kind == ListKind.BULLET) BULLET else NUMBERED
        val allMarked = filled.all { (s, e) -> own.containsMatchIn(input.text.substring(s, e)) }
        var number = numberBefore(input.text, filled.first().first)
        val changes = filled.mapNotNull { (s, e) ->
            val line = input.text.substring(s, e)
            val current = BULLET.find(line) ?: NUMBERED.find(line)
            val markEnd = s + (current?.range?.last?.plus(1) ?: 0)
            val indent = current?.groupValues?.get(1) ?: ""
            when {
                allMarked -> Change(s, markEnd, indent)
                // A line that has the mark already keeps it, its number giving the next
                own.containsMatchIn(line) -> { number = current!!.groupValues[2].toIntOrNull() ?: number; null }
                else -> Change(s, markEnd, indent + markOf(kind, ++number))
            }
        }
        return apply(input, changes)
    }

    /**
     * [input] with the marks of [emphasis] around its selection, line by line, leaving out the
     * spaces at its edges and a list's mark (a mark around it would break the list); taken off
     * instead when every part of it is inside them already, or has them at its own edges.
     */
    fun toggleEmphasis(input: MarkedInput, emphasis: EmphasisMark): MarkedInput {
        if (input.start == input.end) return input
        val text = input.text
        val parts = touchedLines(input).mapNotNull { (lineStart, lineEnd) ->
            val mark = (BULLET.find(text.substring(lineStart, lineEnd)) ?: NUMBERED.find(text.substring(lineStart, lineEnd)))
            var s = maxOf(input.start, lineStart + (mark?.range?.last?.plus(1) ?: 0))
            var e = minOf(input.end, lineEnd)
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (s < e) s to e else null
        }
        if (parts.isEmpty()) return input
        val wrapped = parts.map { (s, e) -> wrapping(text, s, e, emphasis) }
        val changes = if (wrapped.all { it != null }) wrapped.flatMap { it!! }
            else parts.zip(wrapped).filter { it.second == null }.flatMap { (part, _) ->
                listOf(Change(part.first, part.first, emphasis.mark), Change(part.second, part.second, emphasis.mark))
            }
        return apply(input, changes)
    }

    /**
     * The changes taking off the marks of [emphasis] that wrap [s] to [e], outside or at its own
     * edges, or null when it is not wrapped. Stars are counted on each side: one or three are an
     * italic, two or three a bold, so that taking off one leaves the other.
     */
    private fun wrapping(text: String, s: Int, e: Int, emphasis: EmphasisMark): List<Change>? {
        val m = emphasis.mark.length
        fun stars(from: Int, step: Int, limit: Int): Int {
            var n = 0
            var i = from
            while (i in 0 until text.length && (if (step > 0) i < limit else i >= limit) && text[i] == '*') { n++; i += step }
            return n
        }
        fun holds(n: Int) = if (emphasis == EmphasisMark.BOLD) n >= 2 else n % 2 == 1
        val outLeft = stars(s - 1, -1, 0)
        val outRight = stars(e, 1, text.length)
        if (holds(outLeft) && holds(outRight)) return listOf(Change(s - m, s, ""), Change(e, e + m, ""))
        val inLeft = stars(s, 1, e)
        val inRight = stars(e - 1, -1, s)
        if (e - s > 2 * m && holds(inLeft) && holds(inRight)) return listOf(Change(s, s + m, ""), Change(e - m, e, ""))
        return null
    }

    /** The mark of [kind], numbered [number] for a numbered list. */
    private fun markOf(kind: ListKind, number: Int) = if (kind == ListKind.BULLET) "- " else "$number. "

    /** The number of the numbered line just above [lineStart], or 0. */
    private fun numberBefore(text: String, lineStart: Int): Int {
        if (lineStart == 0) return 0
        val previousStart = text.lastIndexOf('\n', lineStart - 2) + 1
        return NUMBERED.find(text.substring(previousStart, lineStart - 1))?.groupValues?.get(2)?.toIntOrNull() ?: 0
    }

    /** The lines, start to end without their new line, that the selection of [input] touches. */
    private fun touchedLines(input: MarkedInput): List<Pair<Int, Int>> {
        val text = input.text
        val lines = mutableListOf<Pair<Int, Int>>()
        var start = text.lastIndexOf('\n', input.start - 1) + 1
        while (true) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            lines += start to end
            // A selection ending right after a new line does not take the line it ends on
            if (end >= input.end || end == text.length || (end + 1 == input.end && input.start != input.end)) break
            start = end + 1
        }
        return lines
    }

    /**
     * [changes], in order and apart, made to [input]: its selection follows the text it was on.
     * A place inside a replaced range goes to the end of its replacement. What is written at an
     * edge of the selection stays outside it: the selection keeps the words it held.
     */
    private fun apply(input: MarkedInput, changes: List<Change>): MarkedInput {
        val sorted = changes.sortedBy { it.start }
        val text = StringBuilder()
        var from = 0
        sorted.forEach { change ->
            text.append(input.text, from, change.start).append(change.with)
            from = change.end
        }
        text.append(input.text, from, input.text.length)
        fun moved(at: Int, after: Boolean): Int {
            var shift = 0
            for (change in sorted) {
                val inserted = change.start == change.end
                when {
                    change.end < at || (change.end == at && (!inserted || after)) -> shift += change.with.length - (change.end - change.start)
                    change.start < at && at < change.end -> return change.start + shift + change.with.length
                    else -> return at + shift
                }
            }
            return at + shift
        }
        val collapsed = input.start == input.end
        val start = moved(input.start, after = true)
        val end = if (collapsed) start else moved(input.end, after = false)
        return MarkedInput(text.toString(), start, maxOf(start, end))
    }
}
