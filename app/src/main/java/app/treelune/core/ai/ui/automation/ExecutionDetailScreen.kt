package app.treelune.core.ai.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.domain.AIState
import app.treelune.core.ai.domain.Phase
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.ai.ui.chat.ChatMessageBubble
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*

/**
 * ExecutionDetailScreen - Display automation execution message history
 *
 * Shows:
 * - Page header with BACK button
 * - Once the run is over, its summary by its AI and the app's facts beside it (RunEnd)
 * - Full conversation history in read-only mode
 * - All messages displayed with ChatMessageBubble
 *
 * Read-only mode:
 * - No RichComposer (no user input)
 * - No actions on messages
 * - No validation/communication module interactions
 * - Messages displayed for audit and transparency
 *
 * Opening it reads the run: it no longer waits (docs/design/waiting-sources.md).
 *
 * Usage: Accessed from ExecutionCard VIEW button in AutomationScreen, and from the notification
 * of a run that waits
 */
@Composable
fun ExecutionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Observe messages for this session
    val messages by AIOrchestrator.observeMessages(sessionId).collectAsState(initial = emptyList())

    // How the run ended and what its AI said of it, read once on opening; opening reads it
    var end by remember(sessionId) { mutableStateOf<Pair<app.treelune.core.ai.data.SessionEndReason?, String?>?>(null) }
    LaunchedEffect(sessionId) {
        val coordinator = app.treelune.core.coordinator.Coordinator(context)
        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId))
        val session = result.data?.get("session") as? Map<*, *>
        if (session == null) {
            app.treelune.core.utils.LogManager.aiUI("ExecutionDetailScreen: run $sessionId not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        end = (session["end_reason"] as String?)?.let { app.treelune.core.ai.data.SessionEndReason.valueOf(it) } to session["summary"] as String?
        val seen = coordinator.processUserAction("ai_sessions.seen", mapOf("session_id" to sessionId))
        if (seen.status != app.treelune.core.commands.CommandStatus.SUCCESS) app.treelune.core.utils.LogManager.aiUI("ExecutionDetailScreen: run $sessionId not marked read: ${seen.error}", "ERROR")
    }

    // Create a static AIState for read-only display (IDLE, no interactions)
    val readOnlyAIState = remember {
        AIState(
            sessionId = sessionId,
            phase = Phase.CLOSED,
            sessionType = null,
            totalRoundtrips = 0,
            lastEventTime = 0L,
            lastUserInteractionTime = 0L,
            waitingContext = null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("automation_execution_detail_title"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onNavigateBack
        )

        // Messages list (read-only)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            // The chat's button floats at the bottom: room for it after the last message
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = app.treelune.core.ui.chatButtonEnd()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            // Over: the summary first, then what the app counted; nothing while it runs
            end?.takeIf { (reason, _) -> reason != null && reason !in app.treelune.core.ai.data.SessionEndReason.resumable }?.let { (reason, summary) ->
                item(key = "run_end") {
                    UI.Card(type = CardType.DEFAULT) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(UI.Space.M),
                            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
                        ) {
                            UI.Text(text = summary ?: s.shared("run_summary_none"), type = TextType.BODY)
                            UI.Text(
                                text = app.treelune.core.ai.orchestration.RunEnd.line(app.treelune.core.ai.orchestration.RunEnd.facts(messages, reason), s),
                                type = TextType.CAPTION
                            )
                        }
                    }
                }
            }
            itemsIndexed(messages) { index, message ->
                // Determine if this is the last AI message (for styling)
                val isLastAI = message.sender == MessageSender.AI &&
                              index == messages.indexOfLast { it.sender == MessageSender.AI }

                // Get previous AI message for context
                val previousAI = if (index > 0) {
                    messages.subList(0, index).lastOrNull { it.sender == MessageSender.AI }
                } else null

                ChatMessageBubble(
                    message = message,
                    aiState = readOnlyAIState,  // Static state, no interactions
                    isLastAIMessage = isLastAI,
                    previousAIMessage = previousAI
                )
            }
        }
    }
}
