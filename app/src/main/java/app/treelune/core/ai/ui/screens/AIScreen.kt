package app.treelune.core.ai.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.treelune.core.ui.MessageSegmentsSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.treelune.core.ai.data.*
import app.treelune.core.ai.domain.Phase
import app.treelune.core.ai.domain.WaitingContext
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.ai.ui.components.RichComposer
import app.treelune.core.ai.ui.components.SessionStatusBar
import app.treelune.core.commands.CommandStatus
import app.treelune.core.strings.Strings
import app.treelune.core.ui.sound.scrollEndSound
import app.treelune.core.ui.*
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * AI Screen - The interface of the sessions one reads (CHAT, AUTOMATION)
 *
 * Architecture:
 * - CHAT: Interactive conversation with user, message sending, validation toggles
 * - AUTOMATION: Read-only viewer for routine executions with interrupt capability
 * A SEED session holds a routine's message, written in the routine's config
 * (RoutineConfigScreen): it is never shown here.
 *
 * This is a pure Composable (not a Dialog) for maximum flexibility:
 * - Used by AIFloatingChat as Dialog wrapper for active sessions
 * - Can be embedded anywhere in the app
 */
@Composable
fun AIScreen(
    sessionId: String,
    onClose: () -> Unit,
    onOpenHistory: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }

    // Observe AI state for active session detection
    val aiState by AIOrchestrator.currentState.collectAsState()

    // Determine if this is the active session
    val isActiveSession = aiState.sessionId == sessionId

    // Session state
    var session by remember(sessionId) { mutableStateOf<AISession?>(null) }
    var isLoadingSession by remember(sessionId) { mutableStateOf(!isActiveSession) }
    var errorMessage by remember(sessionId) { mutableStateOf<String?>(null) }

    // Load session data - ONLY if not the active session (to avoid race condition)
    LaunchedEffect(sessionId, isActiveSession, aiState.sessionType) {
        if (!isActiveSession) {
            // Non-active session: load from DB
            try {
                isLoadingSession = true
                val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId))
                if (result.status == CommandStatus.SUCCESS) {
                    @Suppress("UNCHECKED_CAST")
                    val sessionData = result.data?.get("session") as? Map<String, Any>
                    if (sessionData != null) {
                        session = AISession(
                            id = sessionData["id"] as String,
                            name = sessionData["name"] as String,
                            type = SessionType.valueOf(sessionData["type"] as String),
                            validation = SessionValidation.fromMap(sessionData),
                            reasoning = SessionReasoning.fromMap(sessionData[SessionReasoning.KEY] as? Map<*, *>),
                            waitingStateJson = sessionData["waiting_state_json"] as? String,
                            routineId = sessionData["automation_id"] as? String,
                            scheduledExecutionTime = (sessionData["scheduled_execution_time"] as? Number)?.toLong(),
                            providerId = sessionData["provider_id"] as String?,
                            providerSessionId = sessionData["provider_session_id"] as String,
                            createdAt = (sessionData["created_at"] as Number).toLong(),
                            lastActivity = (sessionData["last_activity"] as Number).toLong(),
                            messages = emptyList(),
                            isActive = sessionData["is_active"] as? Boolean ?: false
                        )
                        LogManager.aiUI("AIScreen loaded non-active session from DB: ${session?.name} (type=${session?.type})")
                    } else {
                        errorMessage = s.shared("ai_error_session_not_found")
                    }
                } else {
                    errorMessage = result.error ?: s.shared("ai_error_load_session")
                }
            } catch (e: Exception) {
                LogManager.aiUI("Failed to load session $sessionId: ${e.message}", "ERROR")
                errorMessage = s.shared("ai_error_load_session")
            } finally {
                isLoadingSession = false
            }
        } else {
            // Active session: use reactive type from aiState (immediate, no DB read needed)
            if (aiState.sessionType != null) {
                session = AISession(
                    id = sessionId,
                    name = "", // Name not needed for routing
                    type = aiState.sessionType!!,
                    waitingStateJson = null,
                    routineId = null,
                    scheduledExecutionTime = null,
                    providerId = null,
                    providerSessionId = "",
                    createdAt = System.currentTimeMillis(),
                    lastActivity = System.currentTimeMillis(),
                    messages = emptyList(),
                    isActive = true
                )
                isLoadingSession = false
                LogManager.aiUI("AIScreen using active session type from aiState: ${aiState.sessionType}")
                // Its name and its routine, which the headers show, read once it is routed
                val stored = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId)).data?.get("session") as? Map<*, *>
                if (stored != null) session = session?.copy(name = stored["name"] as String, routineId = stored["automation_id"] as? String)
            }
        }
    }

    // Loading state
    if (isLoadingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY
            )
        }
        return
    }

    // Error state
    if (session == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
            ) {
                UI.Text(
                    text = errorMessage ?: s.shared("ai_error_session_not_found"),
                    type = TextType.BODY
                )
                UI.ActionButton(
                    action = ButtonAction.BACK,
                    display = ButtonDisplay.LABEL,
                    onClick = onClose
                )
            }
        }
        return
    }

    // Route according to session type
    when (session!!.type) {
        SessionType.CHAT -> {
            ChatMode(
                session = session!!,
                onClose = onClose,
                onOpenHistory = onOpenHistory
            )
        }
        SessionType.SEED -> throw IllegalStateException("Seed session ${session!!.id} opened as a conversation: a routine's message is written in its config")
        SessionType.AUTOMATION -> {
            RoutineMode(
                session = session!!,
                onClose = onClose,
                onOpenHistory = onOpenHistory
            )
        }
    }
}

/**
 * CHAT mode - Interactive conversation
 */
@Composable
private fun ChatMode(
    session: AISession,
    onClose: () -> Unit,
    onOpenHistory: (() -> Unit)?
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // On screen, the chat is seen: its AI messages no longer mark the chat's button
    app.treelune.core.ai.orchestration.ChatSeen(session.id)

    // Local states
    var segments by remember { mutableStateOf<List<MessageSegment>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }

    // The draft on mount: what was being written, or what the chat was opened with
    LaunchedEffect(session.id) {
        val draft = AIOrchestrator.getDraftMessage(session.id)
        if (draft.isNotEmpty()) {
            segments = draft
            LogManager.aiUI("ChatMode loaded draft: ${draft.size} segments", "DEBUG")
        }
    }

    // Save draft whenever segments change
    LaunchedEffect(segments) {
        AIOrchestrator.saveDraftMessage(session.id, segments)
    }

    // Observe AIState from orchestrator
    val aiState by AIOrchestrator.currentState.collectAsState()

    // Derive UI states from aiState.phase
    val isLoading = aiState.phase in listOf(
        Phase.CALLING_AI,
        Phase.EXECUTING_ENRICHMENTS,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.PARSING_AI_RESPONSE,
        Phase.RETRYING_AFTER_FORMAT_ERROR,
        Phase.RETRYING_AFTER_ACTION_FAILURE
    )

    // Disable composer during processing, waiting phases, and interrupt
    // Only IDLE phase allows sending new messages
    val isComposerEnabled = aiState.phase !in listOf(
        Phase.CALLING_AI,
        Phase.EXECUTING_ENRICHMENTS,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.PARSING_AI_RESPONSE,
        Phase.WAITING_VALIDATION,
        Phase.WAITING_COMMUNICATION_RESPONSE,
        Phase.WAITING_DATA_CONFIRMATION,
        Phase.WAITING_NETWORK_RETRY,
        Phase.RETRYING_AFTER_FORMAT_ERROR,
        Phase.RETRYING_AFTER_ACTION_FAILURE,
        Phase.PREPARING_CONTINUATION,
        Phase.INTERRUPTED,
        Phase.AWAITING_SESSION_CLOSURE,
        Phase.CLOSED
    )

    // A pending communication module the user chose to answer by message frees the composer;
    // the choice lasts while the module waits
    var freeReply by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(aiState.phase) {
        if (aiState.phase != Phase.WAITING_COMMUNICATION_RESPONSE) freeReply = false
    }
    val composerEnabled = isComposerEnabled || (freeReply && aiState.phase == Phase.WAITING_COMMUNICATION_RESPONSE)

    // Who it speaks as, fixed at its first message, and whether it can still go on: a session
    // whose identity or provider is gone stays readable, its composer replaced by why
    val identities = app.treelune.core.identities.ui.rememberIdentities()
    val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }
    var identityId by remember(session.id) { mutableStateOf<String?>(null) }
    var fixed by remember(session.id) { mutableStateOf(true) }
    var stopped by remember(session.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(session.id, aiState.phase, identities) {
        val result = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to session.id))
        val data = result.data?.get("session") as? Map<*, *> ?: return@LaunchedEffect
        identityId = data["identity_id"] as? String
        fixed = (result.data?.get("messages") as? List<*>).orEmpty().any { (it as Map<*, *>)["sender"] == MessageSender.USER.name }
        // None yet: its first message takes the default, or says there is none
        val providerKept = (data["provider_id"] as String?)?.let { providerId ->
            coordinator.processUserAction("ai_provider_config.get", mapOf("provider_id" to providerId)).status == CommandStatus.SUCCESS
        } ?: true
        stopped = when {
            identities != null && identityId != null && identities.none { it.id == identityId } -> s.shared("identity_session_gone")
            // A conversation not begun says a missing provider when its first message is sent
            fixed && !providerKept -> s.shared("ai_session_provider_gone")
            else -> null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        ChatHeader(
            session = session,
            onClose = onClose,
            onOpenHistory = onOpenHistory,
            onShowStats = { showStats = true },
            onStopSession = {
                scope.launch {
                    AIOrchestrator.stopActiveSession()
                }
            },
            identity = {
                app.treelune.core.identities.ui.IdentityChip(identities.orEmpty(), identityId, fixed) { chosen ->
                    scope.launch {
                        val result = coordinator.processUserAction("ai_sessions.set_identity",
                            mapOf("session_id" to session.id, "identity_id" to (chosen ?: org.json.JSONObject.NULL)))
                        if (result.status == CommandStatus.SUCCESS) identityId = chosen
                        else errorMessage = result.error
                    }
                }
            }
        )

        // Stats dialog
        if (showStats) {
            app.treelune.core.ai.ui.SessionStatsDialog(
                sessionId = session.id,
                sessionName = session.name,
                onDismiss = { showStats = false }
            )
        }

        // Messages area (includes inline validation/communication)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChatMessageList(
                aiState = aiState,
                modifier = Modifier.fillMaxSize(),
                freeReply = freeReply,
                onFreeReply = { freeReply = true }
            )
        }

        // Composer, or why the session cannot go on
        if (stopped != null) UI.Card(type = CardType.DEFAULT) {
            Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.L)) {
                UI.Text(text = stopped!!, type = TextType.BODY)
            }
        } else UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier.padding(UI.Space.S)
            ) {
                UI.RichComposer(
                    segments = segments,
                    onSegmentsChange = { segments = it },
                    onSend = { richMessage ->
                        scope.launch {
                            try {
                                AIOrchestrator.sendMessage(richMessage)
                                segments = emptyList() // Clear composer on success
                                AIOrchestrator.clearDraftMessage(session.id) // Clear draft on success
                            } catch (e: Exception) {
                                errorMessage = e.message ?: s.shared("ai_error_send_message")
                                LogManager.aiUI("ChatMode sendMessage failed: ${e.message}", "ERROR", e)
                            }
                        }
                    },
                    placeholder = s.shared("ai_composer_placeholder"),
                    sessionId = session.id,
                    enabled = composerEnabled,
                    statusContent = {
                        SessionStatusBar(
                            phase = aiState.phase,
                            sessionType = session.type,
                            context = context
                        )
                    }
                )
            }
        }
    }

    // Error display
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * AUTOMATION mode - Execution viewer (read-only)
 */
@Composable
private fun RoutineMode(
    session: AISession,
    onClose: () -> Unit,
    onOpenHistory: (() -> Unit)?
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    // Local state
    var showChatOptionsDialog by rememberSaveable { mutableStateOf(false) }

    // Observe AIState from orchestrator
    val aiState by AIOrchestrator.currentState.collectAsState()

    // Determine if this routine is the active session
    val isActiveSession = aiState.sessionId == session.id

    // Chat options dialog
    if (showChatOptionsDialog) {
        ChatOptionsDialog(
            onDismiss = { showChatOptionsDialog = false },
            onInterruptAndChat = {
                showChatOptionsDialog = false
                scope.launch {
                    AIOrchestrator.startNewChatSession()
                }
            },
            onChatAfter = {
                showChatOptionsDialog = false
                scope.launch {
                    AIOrchestrator.requestChatSession()
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        RoutineHeader(
            session = session,
            aiState = aiState,
            isActiveSession = isActiveSession,
            onClose = onClose,
            onOpenHistory = onOpenHistory,
            onChatRequest = { showChatOptionsDialog = true },
            onStop = {
                scope.launch {
                    AIOrchestrator.stopActiveSession()
                }
            }
        )

        // Messages area (read-only)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChatMessageList(
                aiState = aiState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Status bar (always visible at bottom)
        SessionStatusBar(
            phase = aiState.phase,
            sessionType = session.type,
            context = context
        )
    }
}

// ================================================================================================
// Headers (3 variants)
// ================================================================================================

/**
 * Chat options dialog - Shown when user clicks Chat button during routine
 * Allows choosing between interrupting immediately or waiting for completion
 */
@Composable
private fun ChatOptionsDialog(
    onDismiss: () -> Unit,
    onInterruptAndChat: () -> Unit,
    onChatAfter: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        app.treelune.core.ui.ToastLayer()
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollEndSound()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
            ) {
                // Header
                UI.Text(
                    text = s.shared("ai_chat_options_dialog_title"),
                    type = TextType.TITLE
                )

                // Description
                UI.Text(
                    text = s.shared("ai_chat_options_dialog_description"),
                    type = TextType.BODY
                )

                // Option A: Interrupt immediately
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInterruptAndChat() }
                            .padding(UI.Space.M),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                    ) {
                        UI.Text(
                            text = s.shared("ai_chat_option_interrupt_title"),
                            type = TextType.SUBTITLE
                        )
                        UI.Text(
                            text = s.shared("ai_chat_option_interrupt_description"),
                            type = TextType.CAPTION
                        )
                    }
                }

                // Option B: Wait for completion
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatAfter() }
                            .padding(UI.Space.M),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                    ) {
                        UI.Text(
                            text = s.shared("ai_chat_option_after_title"),
                            type = TextType.SUBTITLE
                        )
                        UI.Text(
                            text = s.shared("ai_chat_option_after_description"),
                            type = TextType.CAPTION
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chat header - Session controls, validation toggle, stats. Its buttons in order: [identity]
 * (who speaks), the history, the stats, the settings, stop, close.
 */
@Composable
private fun ChatHeader(
    session: AISession,
    onClose: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    onShowStats: () -> Unit,
    onStopSession: () -> Unit,
    identity: @Composable () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showStopConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSessionSettings by rememberSaveable { mutableStateOf(false) }
    // What the session adds to the protections, as stored: the active session reaches this
    // header built from the AI's state alone, without its settings
    var validation by remember(session.id) { mutableStateOf(session.validation) }
    // The reasoning it asks of its model, null for its provider's (SessionReasoning)
    var reasoning by remember(session.id) { mutableStateOf(session.reasoning) }
    // The provider it calls, null before its first message
    var providerId by remember(session.id) { mutableStateOf(session.providerId) }
    // Its name, renamed by a touch on it
    var title by remember(session.id, session.name) { mutableStateOf(session.name) }
    val renameScope = rememberCoroutineScope()
    LaunchedEffect(session.id) {
        val result = app.treelune.core.coordinator.Coordinator(context).processUserAction("ai_sessions.get_session", mapOf("session_id" to session.id))
        val stored = result.data?.get("session") as? Map<*, *>
        if (result.status == CommandStatus.SUCCESS && stored != null) {
            validation = SessionValidation.fromMap(stored)
            reasoning = SessionReasoning.fromMap(stored[SessionReasoning.KEY] as? Map<*, *>)
            providerId = stored["provider_id"] as String?
        } else LogManager.aiUI("ChatHeader: session ${session.id} not read: ${result.error}", "ERROR")
    }

    // Stop confirmation dialog
    if (showStopConfirmation) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                showStopConfirmation = false
                onStopSession()
            },
            onCancel = { showStopConfirmation = false }
        ) {
            Column(
                modifier = Modifier.padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.L)
            ) {
                UI.Text(
                    text = s.shared("ai_session_stop_title"),
                    type = TextType.TITLE
                )
                UI.Text(
                    text = s.shared("ai_session_stop_message"),
                    type = TextType.BODY
                )
            }
        }
    }

    // Session settings dialog
    if (showSessionSettings) {
        val scope = rememberCoroutineScope()
        val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }

        app.treelune.core.ai.ui.chat.SessionSettingsDialog(
            validation = validation,
            providerId = providerId,
            reasoning = reasoning,
            onDismiss = { showSessionSettings = false },
            onValidationChange = { chosen ->
                scope.launch {
                    val result = coordinator.processUserAction(
                        "ai_sessions.update_validation",
                        mapOf("session_id" to session.id) + chosen.toMap()
                    )
                    if (result.status == CommandStatus.SUCCESS) {
                        validation = chosen
                        LogManager.aiUI("Session validation updated: $chosen", "INFO")
                    } else {
                        UI.Toast(result.error ?: s.shared("ai_error_toggle_validation"), Duration.LONG)
                    }
                }
            },
            onProviderChange = { chosen ->
                scope.launch {
                    val result = coordinator.processUserAction("ai_sessions.set_provider", mapOf("session_id" to session.id, "provider_id" to chosen))
                    if (result.status == CommandStatus.SUCCESS) {
                        providerId = chosen
                        if (result.data?.get("reasoning_dropped") == true) reasoning = null
                        LogManager.aiUI("Session provider now $chosen", "INFO")
                    } else {
                        UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
                    }
                }
            },
            onReasoningChange = { chosen ->
                scope.launch {
                    val result = coordinator.processUserAction(
                        "ai_sessions.update_reasoning",
                        mapOf("session_id" to session.id, SessionReasoning.KEY to chosen?.toMap())
                    )
                    // Refused, the setting stays as it was, and the provider's words say why
                    if (result.status == CommandStatus.SUCCESS) {
                        reasoning = chosen
                        LogManager.aiUI("Session reasoning updated: $chosen", "INFO")
                    } else {
                        UI.Toast(result.error ?: s.shared("session_reasoning_error"), Duration.LONG)
                    }
                }
            }
        )
    }

    // Its name in the breadcrumb of a place opened over it (an identity's page)
    SideEffect { app.treelune.core.navigation.Navigator.name(app.treelune.core.navigation.Place.Chat, title) }

    // Header row; the phase is said over the composer (SessionStatusBar)
    TitledHeaderBar(title, onRename = { name ->
        renameScope.launch {
            val result = app.treelune.core.coordinator.Coordinator(context).processUserAction("ai_sessions.rename", mapOf("session_id" to session.id, "name" to name))
            if (result.status == CommandStatus.SUCCESS) title = name
            else UI.Toast(result.error ?: s.shared("error_rename_failed"), Duration.LONG)
        }
    }) {

        // Who speaks: its identity, the app's AI otherwise
        identity()

        // The past chats, to resume one
        onOpenHistory?.let {
            UI.ActionButton(
                action = ButtonAction.HISTORY,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = it
            )
        }

        // What the conversation cost
        UI.ActionButton(
            action = ButtonAction.STATS,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onShowStats
        )

        // Its settings, all on one dialog
        UI.ActionButton(
            action = ButtonAction.CONFIGURE,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = { showSessionSettings = true }
        )

        // Stop button
        UI.ActionButton(
            action = ButtonAction.STOP,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = { showStopConfirmation = true }
        )

        // Close button
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onClose
        )
    }
}

/**
 * The band at the top of a session's view: its buttons in a row at the right, its [title] on the
 * line under them, cut to that line, so that the buttons keep their room at any size. Touched,
 * the title opens a field to rename what it names, [onRename] given the new name.
 */
@Composable
private fun TitledHeaderBar(title: String, onRename: ((String) -> Unit)?, buttons: @Composable RowScope.() -> Unit) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    UI.HeaderBar {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons
            )
            Box(modifier = Modifier.fillMaxWidth().then(if (onRename != null) Modifier.clickable { renaming = true } else Modifier)) {
                UI.Text(text = title, type = TextType.TITLE, maxLines = 1)
            }
        }
    }
    if (renaming && onRename != null) {
        val context = LocalContext.current
        val s = remember { Strings.`for`(context = context) }
        var name by rememberSaveable { mutableStateOf(title) }
        UI.Dialog(
            type = DialogType.EDIT,
            onConfirm = { renaming = false; if (name.trim() != title) onRename(name.trim()) },
            onCancel = { renaming = false },
            confirmEnabled = name.isNotBlank()
        ) {
            Box(modifier = Modifier.padding(UI.Space.L)) {
                UI.FormField(label = s.shared("label_name"), value = name, onChange = { name = it }, required = true)
            }
        }
    }
}

/**
 * Routine header - Simplified execution header with controls
 */
@Composable
private fun RoutineHeader(
    session: AISession,
    aiState: app.treelune.core.ai.domain.AIState,
    isActiveSession: Boolean,
    onClose: () -> Unit,
    onOpenHistory: (() -> Unit)?,
    onChatRequest: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    // The routine's name once read, renamed by a touch on it; that it runs is said at the bottom (SessionStatusBar)
    var title by remember(session.id) { mutableStateOf(session.name) }
    LaunchedEffect(session.routineId) {
        val id = session.routineId ?: return@LaunchedEffect
        val result = app.treelune.core.coordinator.Coordinator(context).processUserAction("automations.get", mapOf("automation_id" to id))
        ((result.data?.get("automation") as? Map<*, *>)?.get("name") as? String)?.let { title = it }
    }

    TitledHeaderBar(title, onRename = session.routineId?.let { id ->
        { name: String ->
            scope.launch {
                val result = app.treelune.core.coordinator.Coordinator(context).processUserAction("automations.update", mapOf("automation_id" to id, "name" to name))
                if (result.status == CommandStatus.SUCCESS) title = name
                else UI.Toast(result.error ?: s.shared("error_rename_failed"), Duration.LONG)
            }
            Unit
        }
    }) {

        // The past chats, to resume one
        onOpenHistory?.let {
            UI.ActionButton(
                action = ButtonAction.HISTORY,
                display = ButtonDisplay.ICON,
                size = Size.M,
                onClick = it
            )
        }

        // Stop button
        UI.ActionButton(
            action = ButtonAction.STOP,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onStop
        )

        // Chat button - Opens dialog with interrupt/wait options
        UI.ActionButton(
            action = ButtonAction.AI_CHAT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onChatRequest
        )

        // Close button
        UI.ActionButton(
            action = ButtonAction.CANCEL,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = onClose
        )
    }
}

// ================================================================================================
// Shared Components (copied from AIFloatingChat, will be extracted to separate file later)
// ================================================================================================

/**
 * Message list - shared by CHAT and AUTOMATION modes
 * Made public for use in AIFloatingChat routine view
 */
@Composable
fun ChatMessageList(
    aiState: app.treelune.core.ai.domain.AIState,
    modifier: Modifier = Modifier,
    freeReply: Boolean = false,
    onFreeReply: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val listState = rememberLazyListState()

    // Observe messages for active session
    val messages by remember(aiState.sessionId) {
        if (aiState.sessionId != null) {
            AIOrchestrator.observeMessages(aiState.sessionId!!)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Determine if loading spinner should show (for all session types)
    val showLoadingSpinner = aiState.phase in listOf(
        Phase.CALLING_AI,
        Phase.EXECUTING_ENRICHMENTS,
        Phase.EXECUTING_DATA_QUERIES,
        Phase.EXECUTING_ACTIONS,
        Phase.PARSING_AI_RESPONSE,
        Phase.WAITING_NETWORK_RETRY
    )

    // Determine if interrupt button should show (CHAT only, for long AI calls)
    val showInterruptButton = aiState.sessionType == SessionType.CHAT && aiState.phase in listOf(
        Phase.CALLING_AI,
        Phase.WAITING_NETWORK_RETRY
    )

    // Find last AI message index for inline validation/communication display
    val lastAIMessageIndex = messages.indexOfLast { it.sender == MessageSender.AI }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-scroll when spinner/interrupt button appears
    LaunchedEffect(showLoadingSpinner, showInterruptButton) {
        if ((showLoadingSpinner || showInterruptButton) && messages.isNotEmpty()) {
            // Calculate total items: messages + spinner (if shown) + interrupt button (if shown)
            val totalItems = messages.size + (if (showLoadingSpinner) 1 else 0) + (if (showInterruptButton) 1 else 0)
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Auto-scroll when communication module or validation appears
    LaunchedEffect(aiState.waitingContext) {
        if (aiState.waitingContext != null && lastAIMessageIndex >= 0) {
            listState.animateScrollToItem(lastAIMessageIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = UI.Space.EDGE, vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        if (messages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    UI.Text(
                        text = s.shared("ai_chat_start_conversation"),
                        type = TextType.BODY
                    )
                }
            }
        } else {
            itemsIndexed(messages) { index, message ->
                // The AI message a system message answers: the question of a module's response,
                // the commands of the results of its actions
                val previousAIMessage = when {
                    message.sender == MessageSender.SYSTEM && message.textContent?.startsWith(s.shared("ai_module_response_prefix")) == true ->
                        messages.subList(0, index).lastOrNull { it.sender == MessageSender.AI && it.aiMessage?.communicationModule != null }
                    message.systemMessage?.type == app.treelune.core.ai.data.SystemMessageType.ACTIONS_EXECUTED ->
                        messages.subList(0, index).lastOrNull { it.sender == MessageSender.AI && it.aiMessage?.actionCommands != null }
                    else -> null
                }

                app.treelune.core.ai.ui.chat.ChatMessageBubble(
                    message = message,
                    aiState = aiState,
                    isLastAIMessage = index == lastAIMessageIndex,
                    previousAIMessage = previousAIMessage,
                    freeReply = freeReply,
                    onFreeReply = onFreeReply
                )
            }

            // AI thinking indicator (spinner + optional interrupt button)
            if (showLoadingSpinner) {
                item {
                    app.treelune.core.ai.ui.chat.AILoadingSpinner()
                }
            }

            if (showInterruptButton) {
                item {
                    app.treelune.core.ai.ui.chat.ChatInterruptButton()
                }
            }
        }
    }
}

