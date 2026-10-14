package app.treelune.core.ai.data

import app.treelune.core.ai.enrichments.PointerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * A routine's message as it is read and written outside the screen (docs/design/ai-routines.md):
 * a list of blocks, `{"type": "text", "text"}` and `{"type": "pointer", "pointer": {"selection",
 * "attach": {"config", "entries"}}}`, the pointer in the stored form of PointerConfig. The same
 * form both ways: what is read is sent back as it is.
 *
 * A file and an image belong to a session, which a routine's message is not sent in: neither is
 * taken, and the composer of its screen does not offer them.
 */
object AutomationMessage {

    const val TEXT = "text"
    const val POINTER = "pointer"

    /**
     * [message] as blocks.
     *
     * @throws IllegalStateException on a file or an image, which a routine's message never holds
     */
    fun toBlocks(message: RichMessage): JSONArray = JSONArray().apply {
        for (segment in message.segments) {
            put(when {
                segment is MessageSegment.Text -> JSONObject().put("type", TEXT).put("text", segment.content)
                segment is MessageSegment.EnrichmentBlock && segment.type == EnrichmentType.POINTER ->
                    JSONObject().put("type", POINTER).put("pointer", JSONObject(segment.config))
                else -> throw IllegalStateException("A routine's message holds a ${segment::class.simpleName}")
            })
        }
    }

    /**
     * The message [blocks] write, each pointer read as the screen's would be (PointerConfig, its
     * selection by EntrySelection) and stored in its own form.
     *
     * @param text The shared strings, for what does not read
     * @throws IllegalArgumentException naming the first block that does not read
     */
    fun fromBlocks(blocks: JSONArray, text: (String) -> String): RichMessage = RichMessage((0 until blocks.length()).map { i ->
        val block = blocks.optJSONObject(i) ?: throw IllegalArgumentException(text("automation_message_block_unreadable").format(i, blocks.opt(i)))
        when (val type = block.optString("type")) {
            TEXT -> MessageSegment.Text(block.optString("text").takeIf { block.has("text") }
                ?: throw IllegalArgumentException(text("automation_message_block_unreadable").format(i, block)))
            POINTER -> {
                val stored = block.optJSONObject("pointer") ?: throw IllegalArgumentException(text("automation_message_block_unreadable").format(i, block))
                val pointer = try {
                    PointerConfig.fromJson(stored, text)
                } catch (e: Exception) {
                    throw IllegalArgumentException(text("automation_message_block_unreadable").format(i, e.message ?: stored))
                }
                pointer.selection.problem(text)?.let { throw IllegalArgumentException(text("automation_message_block_unreadable").format(i, it)) }
                MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, pointer.toJson().toString())
            }
            else -> throw IllegalArgumentException(text("automation_message_block_type").format(i, type))
        }
    })
}
