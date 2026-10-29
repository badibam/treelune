package app.treelune.core.ai.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A routine's message that says nothing is not saved nor run (AIScreen, AIOrchestrator.isSeedEmpty). */
class RichMessageEmptyTest {

    @Test
    fun noSegmentOrBlankTextIsEmpty() {
        assertTrue(RichMessage(emptyList()).isEmpty)
        assertTrue(RichMessage(listOf(MessageSegment.Text("  \n"))).isEmpty)
        assertTrue(RichMessage.fromJson("""{"segments":[]}""")!!.isEmpty)
    }

    @Test
    fun aTextOrAPointerAloneIsAMessage() {
        assertFalse(RichMessage(listOf(MessageSegment.Text("Sum up the day"))).isEmpty)
        assertFalse(RichMessage.fromJson("""{"segments":[{"type":"enrichment","enrichment_type":"POINTER","config":"{}"}]}""")!!.isEmpty)
    }
}
