package app.treelune.core.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.treelune.core.ai.data.MessageSegment
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.files.ImageRow
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * A sent message that holds images, in its order: each run of text and blocks as the screen reads
 * it (rememberDisplayText), each image as a thumbnail between them.
 */
@Composable
fun RichMessageWithImages(message: RichMessage) {
    // The message cut where its images are: runs of other segments, and the images themselves
    val pieces = remember(message) {
        buildList<Any> {
            val run = mutableListOf<MessageSegment>()
            for (segment in message.segments) {
                if (segment is MessageSegment.Image) {
                    if (run.isNotEmpty()) { add(RichMessage(run.toList())); run.clear() }
                    add(segment)
                } else run.add(segment)
            }
            if (run.isNotEmpty()) add(RichMessage(run.toList()))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        pieces.forEach { piece ->
            when (piece) {
                is MessageSegment.Image -> ImageRow(listOf(piece.imageId))
                is RichMessage -> rememberDisplayText(piece).takeIf { it.isNotEmpty() }?.let { UI.Text(it, TextType.BODY) }
            }
        }
    }
}
