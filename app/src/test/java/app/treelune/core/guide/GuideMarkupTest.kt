package app.treelune.core.guide

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a Guide text is read: subtitles, lines of a list with or without their icon, paragraphs, and the emphasis inside a line. */
class GuideMarkupTest {

    private fun plain(text: String) = listOf(Run(text))

    @Test
    fun `subtitles, list lines and paragraphs, empty lines dropped`() {
        val text = "## The steps\nEach element is a step.\n\n- {pause} Pause: stops the clock.\n- +15 s: a longer timer.\n"
        assertEquals(
            listOf(
                GuideBlock.Heading(plain("The steps")),
                GuideBlock.Paragraph(plain("Each element is a step.")),
                GuideBlock.Item(plain("Pause: stops the clock."), "pause"),
                GuideBlock.Item(plain("+15 s: a longer timer."), null)
            ),
            GuideMarkup.parse(text)
        )
    }

    @Test
    fun `an icon is read only at the head of a list line`() {
        assertEquals(listOf(GuideBlock.Paragraph(plain("{pause} Pause"))), GuideMarkup.parse("{pause} Pause"))
        assertEquals(listOf(GuideBlock.Item(plain("Pause then {play}"), null)), GuideMarkup.parse("- Pause then {play}"))
    }

    @Test
    fun `a line without its space after the mark is a paragraph`() {
        assertEquals(listOf(GuideBlock.Paragraph(plain("##Title")), GuideBlock.Paragraph(plain("-dash"))), GuideMarkup.parse("##Title\n-dash"))
    }

    @Test
    fun `an identity's Markdown reads with its heading levels, star lists and emphasis`() {
        val text = "# Voix\n### Consigne\n* **Registre.** Oral *très* relâché, 4*5 = 20\n- a_b_c"
        assertEquals(
            listOf(
                GuideBlock.Heading(plain("Voix")),
                GuideBlock.Heading(plain("Consigne")),
                GuideBlock.Item(
                    listOf(Run("Registre.", bold = true), Run(" Oral "), Run("très", italic = true), Run(" relâché, 4*5 = 20")),
                    null
                ),
                GuideBlock.Item(plain("a_b_c"), null)
            ),
            GuideMarkup.parse(text)
        )
    }

    @Test
    fun `bold and italic nest, either way, and three stars are both`() {
        assertEquals(listOf(Run("both", bold = true, italic = true)), GuideMarkup.runs("***both***"))
        assertEquals(
            listOf(Run("a ", bold = true), Run("b", bold = true, italic = true), Run(" c", bold = true)),
            GuideMarkup.runs("**a *b* c**")
        )
        assertEquals(
            listOf(Run("a ", italic = true), Run("b", bold = true, italic = true), Run(" c", italic = true)),
            GuideMarkup.runs("*a **b** c*")
        )
    }

    @Test
    fun `a lone star or a star between digits stays as written`() {
        assertEquals(plain("5 * 3 and 2*2"), GuideMarkup.runs("5 * 3 and 2*2"))
        assertEquals(plain("**not closed"), GuideMarkup.runs("**not closed"))
    }
}
