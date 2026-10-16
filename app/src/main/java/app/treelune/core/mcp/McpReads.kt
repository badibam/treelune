package app.treelune.core.mcp

/**
 * What an outside AI receives of a read, held to the connector's data threshold: a read is sent
 * whole or refused, never cut without a word. Cut silently, a read looks complete: the AI would
 * sum half the meals and believe it had them all.
 *
 * Two cases past the threshold:
 * - a read of several entries is refused, the refusal saying its size and how to ask for less
 *   (fewer fields, a shorter period, pages): the AI can narrow it, and narrowed it stays whole;
 * - any other read (one entry, a config, a routine's run) cannot be narrowed: it goes in slices,
 *   the AI giving where to start ([PARAM]) and the app filling up to the threshold, each slice
 *   saying where it lies in the whole and where the next one starts.
 *
 * Each slice reads the data again: what changed between two calls shows in the next slice.
 */
object McpReads {

    /** The parameter every read tool of the connector takes, besides its own: where a slice starts, in characters. */
    const val PARAM = "text_from"

    /** The texts of this rule, from the app's strings, so that the rule itself can be tested alone. */
    interface Texts {
        /** A read of several entries past the threshold: its size, the threshold, how to ask for less. */
        fun entriesRefused(chars: Int, maxChars: Int): String
        /** [PARAM] given to a read of several entries, which goes whole or not at all. */
        fun entriesNotSliced(): String
        /** [PARAM] at or past the end of the text, or negative. */
        fun outside(from: Int, total: Int): String
        /** The head of a slice that is not the last: where it lies, and where the next one starts. */
        fun slice(from: Int, to: Int, total: Int): String
        /** The head of the last slice. */
        fun lastSlice(from: Int, to: Int, total: Int): String
    }

    /** What goes to the outside AI. */
    sealed interface Held {
        data class Sent(val text: String) : Held
        data class Refused(val reason: String) : Held
    }

    /**
     * [text], the whole read, held to [maxChars].
     *
     * @param from Where the AI asked its slice to start, null when it did not
     * @param sliceable Whether the read may go in slices: false for a read of several entries
     */
    fun hold(text: String, maxChars: Int, from: Int?, sliceable: Boolean, texts: Texts): Held {
        if (!sliceable) return when {
            from != null -> Held.Refused(texts.entriesNotSliced())
            text.length <= maxChars -> Held.Sent(text)
            else -> Held.Refused(texts.entriesRefused(text.length, maxChars))
        }
        val start = from ?: 0
        if (start == 0 && text.length <= maxChars) return Held.Sent(text)
        if (start < 0 || start >= text.length) return Held.Refused(texts.outside(start, text.length))

        // The head takes its room from the threshold; written with the largest numbers it can hold
        val room = (maxChars - texts.slice(text.length, text.length, text.length).length - 1).coerceAtLeast(1)
        val end = sliceEnd(text, start, room)
        val head = if (end == text.length) texts.lastSlice(start, end, text.length) else texts.slice(start, end, text.length)
        return Held.Sent(head + "\n" + text.substring(start, end))
    }

    /**
     * Where a slice of [text] starting at [start] ends, within [room] characters: at the end of a
     * paragraph, else of a line, else of a word, if one lies in the second half of the room — so
     * that a slice holds most of what it could; else where the room ends.
     */
    private fun sliceEnd(text: String, start: Int, room: Int): Int {
        val limit = start + room
        if (limit >= text.length) return text.length
        val floor = start + room / 2
        for (separator in listOf("\n\n", "\n", " ")) {
            val at = text.lastIndexOf(separator, limit - separator.length)
            if (at >= floor) return at + separator.length
        }
        return limit
    }
}
