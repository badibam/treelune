package app.treelune.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpReadsTest {

    private val texts = object : McpReads.Texts {
        override fun entriesRefused(chars: Int, maxChars: Int) = "refused $chars/$maxChars"
        override fun entriesNotSliced() = "not sliced"
        override fun outside(from: Int, total: Int) = "outside $from/$total"
        override fun slice(from: Int, to: Int, total: Int) = "[$from-$to/$total next $to]"
        override fun lastSlice(from: Int, to: Int, total: Int) = "[$from-$to/$total end]"
    }

    private fun sent(held: McpReads.Held) = (held as McpReads.Held.Sent).text
    private fun refused(held: McpReads.Held) = (held as McpReads.Held.Refused).reason

    /** Every slice of [text] from the start, following each one's next start, and the text they hold together. */
    private fun allSlices(text: String, maxChars: Int): Pair<List<String>, String> {
        val slices = mutableListOf<String>()
        var from = 0
        while (true) {
            val slice = sent(McpReads.hold(text, maxChars, from, sliceable = true, texts = texts))
            slices += slice
            assertTrue("a slice holds to the threshold", slice.length <= maxChars)
            val head = slice.substringBefore("\n")
            if (head.endsWith("end]")) break
            from = head.substringAfter("next ").removeSuffix("]").toInt()
        }
        return slices to slices.joinToString("") { it.substringAfter("\n") }
    }

    @Test
    fun `a read within the threshold goes whole, with no head`() {
        assertEquals("abc", sent(McpReads.hold("abc", 100, null, sliceable = true, texts = texts)))
        assertEquals("abc", sent(McpReads.hold("abc", 100, null, sliceable = false, texts = texts)))
    }

    @Test
    fun `entries past the threshold are refused, never cut`() {
        val text = "x".repeat(500)
        assertEquals("refused 500/100", refused(McpReads.hold(text, 100, null, sliceable = false, texts = texts)))
    }

    @Test
    fun `entries take no start, even when they would fit`() {
        assertEquals("not sliced", refused(McpReads.hold("abc", 100, 0, sliceable = false, texts = texts)))
    }

    @Test
    fun `a single text past the threshold goes in slices that hold it all, in order`() {
        val text = (1..400).joinToString(" ") { "word$it" }
        val (slices, joined) = allSlices(text, 300)
        assertTrue(slices.size > 1)
        assertEquals(text, joined)
    }

    @Test
    fun `a slice ends at a paragraph when one lies in its second half`() {
        val paragraph = "a".repeat(150)
        val text = listOf(paragraph, paragraph, paragraph).joinToString("\n\n")
        val first = sent(McpReads.hold(text, 300, null, sliceable = true, texts = texts)).substringAfter("\n")
        assertEquals(paragraph + "\n\n", first)
    }

    @Test
    fun `a text with no break is cut where the room ends`() {
        val (_, joined) = allSlices("y".repeat(1000), 120)
        assertEquals("y".repeat(1000), joined)
    }

    @Test
    fun `a start outside the text is refused`() {
        assertEquals("outside 10/3", refused(McpReads.hold("abc", 100, 10, sliceable = true, texts = texts)))
        assertEquals("outside -1/3", refused(McpReads.hold("abc", 100, -1, sliceable = true, texts = texts)))
    }

    @Test
    fun `a start inside a short text gives its end as the last slice`() {
        assertEquals("[1-3/3 end]\nbc", sent(McpReads.hold("abc", 100, 1, sliceable = true, texts = texts)))
    }
}
