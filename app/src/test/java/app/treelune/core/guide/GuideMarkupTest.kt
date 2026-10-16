package app.treelune.core.guide

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a Guide text is read: subtitles, lines of a list with or without their icon, paragraphs. */
class GuideMarkupTest {

    @Test
    fun `subtitles, list lines and paragraphs, empty lines dropped`() {
        val text = "## The steps\nEach element is a step.\n\n- {pause} Pause: stops the clock.\n- +15 s: a longer timer.\n"
        assertEquals(
            listOf(
                GuideBlock.Heading("The steps"),
                GuideBlock.Paragraph("Each element is a step."),
                GuideBlock.Item("Pause: stops the clock.", "pause"),
                GuideBlock.Item("+15 s: a longer timer.", null)
            ),
            GuideMarkup.parse(text)
        )
    }

    @Test
    fun `an icon is read only at the head of a list line`() {
        assertEquals(listOf(GuideBlock.Paragraph("{pause} Pause")), GuideMarkup.parse("{pause} Pause"))
        assertEquals(listOf(GuideBlock.Item("Pause then {play}", null)), GuideMarkup.parse("- Pause then {play}"))
    }

    @Test
    fun `a line without its space after the mark is a paragraph`() {
        assertEquals(listOf(GuideBlock.Paragraph("##Title"), GuideBlock.Paragraph("-dash")), GuideMarkup.parse("##Title\n-dash"))
    }

    @Test
    fun `an identity's Markdown reads with its heading levels and star lists, emphasis taken off`() {
        val text = "# Voix\n### Consigne\n* **Registre.** Oral *très* relâché, 4*5 = 20\n- a_b_c"
        assertEquals(
            listOf(
                GuideBlock.Heading("Voix"),
                GuideBlock.Heading("Consigne"),
                GuideBlock.Item("Registre. Oral très relâché, 4*5 = 20", null),
                GuideBlock.Item("a_b_c", null)
            ),
            GuideMarkup.parse(text)
        )
    }
}
