package app.treelune.core.ai.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A routine's message in blocks (AutomationMessage): read back in the form it was written, its
 * pointers read as the screen's are, and what a routine's message cannot hold refused.
 */
class AutomationMessageTest {

    private fun text(key: String) = when (key) {
        "automation_message_block_unreadable", "automation_message_block_type" -> "$key %1\$d %2\$s"
        else -> key
    }

    private fun textBlock(text: String) = JSONObject().put("type", "text").put("text", text)

    private fun pointerBlock(kind: String, id: String, entries: Boolean = true) = JSONObject().put("type", "pointer").put("pointer", JSONObject()
        .put("selection", JSONObject()
            .put("target", JSONObject().put("kind", kind).put("id", id))
            .put("period", JSONObject().put("start", JSONObject().put("relative", JSONObject().put("unit", "DAY").put("offset", -7).put("edge", "START")))))
        .put("attach", JSONObject().put("config", false).put("entries", entries)))

    @Test
    fun aMessageReadsBackInTheFormItWasWritten() {
        val blocks = JSONArray().put(textBlock("Sum up the week of ")).put(pointerBlock("TOOL_INSTANCE", "meals")).put(textBlock(" in two lines."))
        val message = AutomationMessage.fromBlocks(blocks, ::text)
        assertEquals(3, message.segments.size)
        assertTrue(message.segments[1] is MessageSegment.EnrichmentBlock)
        assertEquals(blocks.toString(), AutomationMessage.toBlocks(message).toString())
        // As the screen stores it, and so as a run reads it
        assertEquals(message, RichMessage.fromJson(message.toJson()))
    }

    @Test
    fun aBlockOfAnotherTypeIsRefusedWithItsPlace() {
        val image = JSONArray().put(textBlock("Look: ")).put(JSONObject().put("type", "image").put("image_id", "i1"))
        val refused = assertThrows(IllegalArgumentException::class.java) { AutomationMessage.fromBlocks(image, ::text) }
        assertEquals("automation_message_block_type 1 image", refused.message)
    }

    @Test
    fun aPointerThatDoesNotFitItsTargetIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { AutomationMessage.fromBlocks(JSONArray().put(pointerBlock("VARIABLE", "kcal")), ::text) }
        val noSelection = JSONObject().put("type", "pointer").put("pointer", JSONObject().put("attach", JSONObject()))
        assertThrows(IllegalArgumentException::class.java) { AutomationMessage.fromBlocks(JSONArray().put(noSelection), ::text) }
        assertThrows(IllegalArgumentException::class.java) { AutomationMessage.fromBlocks(JSONArray().put(JSONObject().put("type", "text")), ::text) }
    }

    @Test
    fun aFileJoinedToAMessageIsNoRoutinesMessage() {
        val file = RichMessage(listOf(MessageSegment.EnrichmentBlock(EnrichmentType.FILE, "{}")))
        assertThrows(IllegalStateException::class.java) { AutomationMessage.toBlocks(file) }
    }
}
