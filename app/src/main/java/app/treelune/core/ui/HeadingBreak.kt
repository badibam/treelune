package app.treelune.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

/**
 * How a heading (a tile's name, a section's title) is broken into lines: between words, so that
 * « Day's calories » is not written « Day's calor- / ies » to even its lines. A word wider than
 * its line is cut at its syllables all the same, never at any letter (docs/THEMES.md, the traps):
 * the heading is laid out between words first, and once a line of it ends inside a word, laid out
 * again with hyphenation.
 *
 * A theme passes [style] and [onTextLayout] to the BasicText that draws the heading.
 */
class HeadingBreak internal constructor(private val text: AnnotatedString) {
    private var hyphenated by mutableStateOf(false)

    /** [style] broken between words, or hyphenated once a word did not hold on its line. */
    fun style(style: TextStyle): TextStyle =
        if (hyphenated) style.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)
        else style.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading)

    /**
     * Reads the lines laid between words: one that ends inside a word asks for the syllables, and
     * so does the last one cut by the ellipsis inside a word (« Week of train… » on its two lines),
     * which the syllables may let hold.
     */
    val onTextLayout: (TextLayoutResult) -> Unit = { layout ->
        if (!hyphenated) {
            val last = layout.lineCount - 1
            val cut = (0 until last).any { cutInsideWord(layout.getLineEnd(it)) } ||
                (last >= 0 && layout.isLineEllipsized(last) && cutInsideWord(layout.getLineEnd(last, visibleEnd = true)))
            if (cut) hyphenated = true
        }
    }

    /** Whether a line ending at [end] (exclusive) cuts a word: a letter on both sides of it. */
    private fun cutInsideWord(end: Int): Boolean {
        if (end <= 0 || end >= text.length) return false
        val before = text[end - 1]
        val after = text[end]
        return !before.isWhitespace() && !after.isWhitespace() && before != '-' && before != '/'
    }
}

/** The [HeadingBreak] of [text], laid out between words again when the text changes. */
@Composable
fun rememberHeadingBreak(text: AnnotatedString): HeadingBreak = remember(text) { HeadingBreak(text) }
