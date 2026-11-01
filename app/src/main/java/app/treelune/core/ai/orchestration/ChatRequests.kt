package app.treelune.core.ai.orchestration

import app.treelune.core.ai.data.MessageSegment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks for a chat opened with a content — text and pointers in its composer, sent by the user
 * when they choose — from any screen: a routine's tile, an identity's choice. The
 * main screen, which holds the chat, opens it (AIOrchestrator.startNewChatSession).
 */
object ChatRequests {
    /**
     * A chat asked for: what its composer holds, the identity it speaks as (null for the app's
     * own AI), and the provider it calls (null for the default, taken at its first call).
     */
    data class Request(val prefill: List<MessageSegment>, val identityId: String? = null, val providerId: String? = null)

    private val _requests = MutableSharedFlow<Request>(extraBufferCapacity = 1)
    val requests: SharedFlow<Request> = _requests.asSharedFlow()

    fun open(prefill: List<MessageSegment>, identityId: String? = null, providerId: String? = null) {
        _requests.tryEmit(Request(prefill, identityId, providerId))
    }
}
