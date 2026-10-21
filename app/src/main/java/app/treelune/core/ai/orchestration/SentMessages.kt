package app.treelune.core.ai.orchestration

import app.treelune.core.ai.data.RichMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The messages the user sends the AI, as they are stored. A message goes through the AI's
 * orchestrator, not the dispatcher, so PassedOperations never sees it: what the Guide's steps
 * that wait for a message listen to instead (docs/GUIDE.md).
 */
object SentMessages {

    private val sent = MutableSharedFlow<RichMessage>(extraBufferCapacity = 16)
    val flow: SharedFlow<RichMessage> = sent

    fun sent(message: RichMessage) {
        sent.tryEmit(message)
    }
}
