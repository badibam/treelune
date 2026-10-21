package app.treelune.core.ai.orchestration

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The chat as a source of what waits (docs/BRICKS.md, « Waiting »): the active chat waits while
 * an AI message is newer than the moment it was last shown on screen (seen_at). Its mark is on the
 * chat's floating button (WaitingSource.CHAT); showing the chat ([ChatSeen]) makes it seen.
 */
object ChatWaiting {

    private val _waits = MutableStateFlow(false)
    /** A flow, not a Compose state: read from any snapshot (see AutoBackup.pending). */
    val waits: StateFlow<Boolean> = _waits

    /** [waits] read again from the sessions; left as it was when the read fails, which is logged. */
    suspend fun refresh(context: Context) {
        val result = Coordinator(context).processUserAction("ai_sessions.chat_waiting", emptyMap())
        if (!result.isSuccess) {
            LogManager.aiSession("ChatWaiting: not read: ${result.error}", "ERROR")
            return
        }
        _waits.value = result.data?.get("waits") as Boolean
    }

    /** The chat [sessionId] seen now: its mark goes. */
    suspend fun seen(context: Context, sessionId: String) {
        val result = Coordinator(context).processUserAction("ai_sessions.seen", mapOf("session_id" to sessionId))
        if (!result.isSuccess) {
            LogManager.aiSession("ChatWaiting: chat $sessionId not marked seen: ${result.error}", "ERROR")
            return
        }
        _waits.value = false
    }
}

/**
 * The chat [sessionId] seen while it is on screen with the app in the foreground: on showing, at
 * each new message, and on coming back to the app. A message that arrives while the app is in the
 * background stays unread until the app shows it.
 */
@Composable
fun ChatSeen(sessionId: String) {
    val context = LocalContext.current
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val messages by remember(sessionId) { AIOrchestrator.observeMessages(sessionId) }.collectAsState(initial = emptyList())
    LaunchedEffect(sessionId, messages.size, foreground) {
        if (foreground) ChatWaiting.seen(context, sessionId)
    }
}
