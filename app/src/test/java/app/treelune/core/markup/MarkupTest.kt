package app.treelune.core.markup

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a long text is read: subtitles, lines of a list with or without their icon, paragraphs, and the emphasis inside a line. */
class MarkupTest {

    private fun plain(text: String) = listOf(Run(text))

    @Test
    fun `subtitles, list lines and paragraphs, empty lines dropped`() {
        val text = "## The steps\nEach element is a step.\n\n- {pause} Pause: stops the clock.\n- +15 s: a longer timer.\n"
        assertEquals(
            listOf(
                MarkupBlock.Heading(plain("The steps")),
                MarkupBlock.Paragraph(plain("Each element is a step.")),
                MarkupBlock.Item(plain("Pause: stops the clock."), "pause"),
                MarkupBlock.Item(plain("+15 s: a longer timer."), null)
            ),
            Markup.parse(text, icons = true)
        )
    }

    @Test
    fun `an icon is read only at the head of a list line`() {
        assertEquals(listOf(MarkupBlock.Paragraph(plain("{pause} Pause"))), Markup.parse("{pause} Pause", icons = true))
        assertEquals(listOf(MarkupBlock.Item(plain("Pause then {play}"), null)), Markup.parse("- Pause then {play}", icons = true))
    }

    @Test
    fun `a line without its space after the mark is a paragraph`() {
        assertEquals(listOf(MarkupBlock.Paragraph(plain("##Title")), MarkupBlock.Paragraph(plain("-dash"))), Markup.parse("##Title\n-dash", icons = true))
    }

    @Test
    fun `an identity's Markdown reads with its heading levels, star lists and emphasis`() {
        val text = "# Voix\n### Consigne\n* **Registre.** Oral *très* relâché, 4*5 = 20\n- a_b_c"
        assertEquals(
            listOf(
                MarkupBlock.Heading(plain("Voix")),
                MarkupBlock.Heading(plain("Consigne")),
                MarkupBlock.Item(
                    listOf(Run("Registre.", bold = true), Run(" Oral "), Run("très", italic = true), Run(" relâché, 4*5 = 20")),
                    null
                ),
                MarkupBlock.Item(plain("a_b_c"), null)
            ),
            Markup.parse(text, icons = false)
        )
    }

    @Test
    fun `bold and italic nest, either way, and three stars are both`() {
        assertEquals(listOf(Run("both", bold = true, italic = true)), Markup.runs("***both***"))
        assertEquals(
            listOf(Run("a ", bold = true), Run("b", bold = true, italic = true), Run(" c", bold = true)),
            Markup.runs("**a *b* c**")
        )
        assertEquals(
            listOf(Run("a ", italic = true), Run("b", bold = true, italic = true), Run(" c", italic = true)),
            Markup.runs("*a **b** c*")
        )
    }

    @Test
    fun `a lone star or a star between digits stays as written`() {
        assertEquals(plain("5 * 3 and 2*2"), Markup.runs("5 * 3 and 2*2"))
        assertEquals(plain("**not closed"), Markup.runs("**not closed"))
    }

    @Test
    fun `in the user's text, a brace after a list mark stays a brace`() {
        assertEquals(listOf(MarkupBlock.Item(plain("{pause} Pause"), null)), Markup.parse("- {pause} Pause", icons = false))
    }

    @Test
    fun `numbered lines take their place in their list, whatever was typed, empty lines between them included`() {
        val text = "1. Preheat\n1. Mix\n\n7. Bake\nThen:\n3. Serve\n- garnish\n2. Eat"
        assertEquals(
            listOf(
                MarkupBlock.Numbered(plain("Preheat"), 1),
                MarkupBlock.Numbered(plain("Mix"), 2),
                MarkupBlock.Numbered(plain("Bake"), 3),
                MarkupBlock.Paragraph(plain("Then:")),
                MarkupBlock.Numbered(plain("Serve"), 1),
                MarkupBlock.Item(plain("garnish"), null),
                MarkupBlock.Numbered(plain("Eat"), 1)
            ),
            Markup.parse(text, icons = false)
        )
    }

    @Test
    fun `a number without its dot and space is a paragraph`() {
        assertEquals(listOf(MarkupBlock.Paragraph(plain("2024 was long")), MarkupBlock.Paragraph(plain("3.5 kg"))), Markup.parse("2024 was long\n3.5 kg", icons = false))
    }

    @Test
    fun `on one line, the marks are taken off`() {
        assertEquals("Shopping milk and eggs Bake **slowly", Markup.plain("## Shopping\n- milk *and* eggs\n\n1. **Bake** **slowly"))
    }
}
