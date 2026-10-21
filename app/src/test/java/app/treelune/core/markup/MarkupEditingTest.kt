package app.treelune.core.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The help of a long text's input (docs/UI.md, « Long texts »): a list continued on a new line,
 * the two list buttons, bold and italic around a selection. A `|` in a text below is the cursor,
 * `[` and `]` the edges of a selection.
 */
class MarkupEditingTest {

    /** [marked] read: its text without the cursor or the selection's edges, and where they are. */
    private fun input(marked: String): MarkedInput {
        val cursor = marked.indexOf('|')
        if (cursor >= 0) return MarkedInput(marked.removeRange(cursor, cursor + 1), cursor, cursor)
        val start = marked.indexOf('[')
        val end = marked.indexOf(']') - 1
        return MarkedInput(marked.replace("[", "").replace("]", ""), start, end)
    }

    /** [input] written back with its cursor or its selection's edges. */
    private fun shown(input: MarkedInput): String =
        if (input.start == input.end) input.text.substring(0, input.start) + "|" + input.text.substring(input.start)
        else input.text.substring(0, input.start) + "[" + input.text.substring(input.start, input.end) + "]" + input.text.substring(input.end)

    /** [before] with a new line typed at its cursor, then continued. */
    private fun enter(before: String): String? {
        val at = input(before)
        val typed = MarkedInput(at.text.substring(0, at.start) + "\n" + at.text.substring(at.start), at.start + 1, at.start + 1)
        return MarkupEditing.continueList(at.text, typed)?.let(::shown)
    }

    @Test
    fun `a new line after an item begins the next one`() {
        assertEquals("- milk\n- |", enter("- milk|"))
        assertEquals("* milk\n* |", enter("* milk|"))
        assertEquals("Shopping\n  - milk\n  - |", enter("Shopping\n  - milk|"))
        assertEquals("2. Mix\n3. |", enter("2. Mix|"))
        assertEquals("9. Mix\n10. |", enter("9. Mix|"))
    }

    @Test
    fun `a new line inside an item carries the rest of it into the next one`() {
        assertEquals("- milk\n- |and eggs", enter("- milk|and eggs"))
    }

    @Test
    fun `a new line on an empty item takes its mark off and ends the list`() {
        assertEquals("- milk\n|", enter("- milk\n- |"))
        assertEquals("1. Mix\n|\nafter", enter("1. Mix\n2. |\nafter"))
    }

    @Test
    fun `a new line anywhere else is kept as typed`() {
        assertNull(enter("A sentence|"))
        assertNull(enter("-|"))
        // Inside the mark itself
        assertNull(enter("-| milk"))
        // Two characters typed at once (a paste) are no new line
        assertNull(MarkupEditing.continueList("- a", MarkedInput("- a\nb", 5, 5)))
    }

    private fun list(marked: String, kind: ListKind) = shown(MarkupEditing.toggleList(input(marked), kind))

    @Test
    fun `a list's button sets its mark on the cursor's line, and takes it off`() {
        assertEquals("- milk|", list("milk|", ListKind.BULLET))
        assertEquals("mi|lk", list("- mi|lk", ListKind.BULLET))
        assertEquals("1. milk|", list("milk|", ListKind.NUMBERED))
        assertEquals("- |", list("|", ListKind.BULLET))
    }

    @Test
    fun `a list's button puts its own mark in place of the other list's`() {
        assertEquals("1. milk|", list("- milk|", ListKind.NUMBERED))
        assertEquals("- milk|", list("3. milk|", ListKind.BULLET))
    }

    @Test
    fun `a numbered line follows the number above it`() {
        assertEquals("1. Mix\n2. Bake|", list("1. Mix\nBake|", ListKind.NUMBERED))
    }

    @Test
    fun `a list's button sets every line the selection touches, blank lines left alone`() {
        assertEquals("- [milk\n\n- eggs]", list("[milk\n\neggs]", ListKind.BULLET))
        assertEquals("1. [Mix\n2. Bake\n3. Ea]t", list("[Mix\nBake\nEa]t", ListKind.NUMBERED))
        // All marked already: taken off
        assertEquals("[milk\neggs]", list("[- milk\n- eggs]", ListKind.BULLET))
        // One not marked: all marked
        assertEquals("[- milk\n- eggs]", list("[- milk\neggs]", ListKind.BULLET))
    }

    @Test
    fun `a selection ending at the head of a line does not take that line`() {
        assertEquals("- [milk\n]eggs", list("[milk\n]eggs", ListKind.BULLET))
    }

    private fun emphasis(marked: String, mark: EmphasisMark) = shown(MarkupEditing.toggleEmphasis(input(marked), mark))

    @Test
    fun `bold and italic put their marks around the selection, which keeps its words`() {
        assertEquals("a **[word]** here", emphasis("a [word] here", EmphasisMark.BOLD))
        assertEquals("a *[word]* here", emphasis("a [word] here", EmphasisMark.ITALIC))
    }

    @Test
    fun `the spaces at the selection's edges stay outside the marks`() {
        assertEquals("a[ **word** ]here", emphasis("a[ word ]here", EmphasisMark.BOLD))
    }

    @Test
    fun `a selection inside the marks, or holding them, has them taken off`() {
        assertEquals("a [word] here", emphasis("a **[word]** here", EmphasisMark.BOLD))
        assertEquals("a [word] here", emphasis("a *[word]* here", EmphasisMark.ITALIC))
        assertEquals("a [word] here", emphasis("a [**word**] here", EmphasisMark.BOLD))
    }

    @Test
    fun `bold and italic stack, and each is taken off alone`() {
        assertEquals("***[word]***", emphasis("**[word]**", EmphasisMark.ITALIC))
        assertEquals("*[word]*", emphasis("***[word]***", EmphasisMark.BOLD))
        assertEquals("**[word]**", emphasis("***[word]***", EmphasisMark.ITALIC))
    }

    @Test
    fun `over several lines, each line's part is marked, a list's mark left out`() {
        assertEquals("[- **milk**\n- **eggs]**", emphasis("[- milk\n- eggs]", EmphasisMark.BOLD))
        assertEquals("[- milk\n- eggs]", emphasis("[- **milk**\n- **eggs**]", EmphasisMark.BOLD))
    }

    @Test
    fun `what the marks write reads back as the emphasis`() {
        val bold = MarkupEditing.toggleEmphasis(input("a [word] here"), EmphasisMark.BOLD)
        assertEquals(listOf(Run("a "), Run("word", bold = true), Run(" here")), Markup.runs(bold.text))
    }
}
