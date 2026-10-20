package app.treelune.core.ai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.treelune.core.ai.data.*
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.strings.Strings
import app.treelune.core.ui.sound.scrollEndSound
import app.treelune.core.utils.LogManager
import app.treelune.core.ui.*
import kotlinx.coroutines.launch

/**
 * Shared chat components extracted from AIFloatingChat
 * Used by both AIScreen and AIFloatingChat
 */

/**
 * Individual message bubble - themed UI.MessageBubble
 *
 * V2: Uses aiState.waitingContext for inline validation/communication display
 * A long press copies what the bubble shows, in words (copyText); a touch does nothing of it
 *
 * @param previousAIMessage Optional previous AI message for displaying communication module question with response
 * @param freeReply Whether the user chose to answer the pending module by message
 * @param onFreeReply Frees the composer for that message
 */
@Composable
fun ChatMessageBubble(
    message: SessionMessage,
    aiState: app.treelune.core.ai.domain.AIState,
    isLastAIMessage: Boolean = false,
    previousAIMessage: SessionMessage? = null,
    freeReply: Boolean = false,
    onFreeReply: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // A user message's blocks name their targets as they are now
    val richText = message.richContent?.let { app.treelune.core.ai.ui.components.rememberDisplayText(it) }
    // The actions waiting for the user's validation, shown under the last AI message
    val waitingActions = (aiState.waitingContext as? app.treelune.core.ai.domain.WaitingContext.Validation)
        ?.takeIf { isLastAIMessage }?.validationContext?.verbalizedActions.orEmpty()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val onMessageLongPress = {
        val text = copyText(message, richText, waitingActions, s)
        if (text.isNotEmpty()) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            UI.copy(context, text)
        }
    }

    // Preserve original alignment logic
    val alignment = when (message.sender) {
        MessageSender.USER -> Alignment.CenterEnd
        MessageSender.AI -> Alignment.CenterStart
        MessageSender.SYSTEM -> Alignment.Center
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        // Delegate to themed MessageBubble for appearance; a long press anywhere on it copies it,
        // a touch going on to what is inside (a button, the details)
        Box(modifier = Modifier.pointerInput(message.id) { detectTapGestures(onLongPress = { onMessageLongPress() }) }) {
            UI.MessageBubble(sender = message.sender) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(UI.Space.S)
                ) {
                    // Sender indicator
                    UI.Text(
                        text = when (message.sender) {
                            MessageSender.USER -> s.shared("ai_sender_user")
                            MessageSender.AI -> s.shared("ai_sender_ai")
                            MessageSender.SYSTEM -> s.shared("ai_sender_system")
                        },
                        type = TextType.LABEL
                    )

                    // Message content
                    when {
                        message.richContent != null -> {
                            // Rich message with segments (use UI-friendly version without IDs);
                            // one with images shows each where the user put it
                            if (message.richContent.imageIds.isEmpty()) UI.Text(
                                text = richText ?: "",
                                type = TextType.BODY
                            )
                            else app.treelune.core.ai.ui.components.RichMessageWithImages(message.richContent)
                        }
                        message.textContent != null -> {
                            // Check if this is a communication module response
                            val responsePrefix = s.shared("ai_module_response_prefix")
                            val isCommunicationResponse = message.sender == MessageSender.SYSTEM &&
                                message.textContent.startsWith(responsePrefix)

                            // If it's a response and we have the question from previous AI message, show it
                            val answered = previousAIMessage?.aiMessage?.communicationModule
                            if (isCommunicationResponse && answered != null) {
                                // The answer by the fields it answers, under the question the
                                // AI's preText asked
                                Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                                    UI.Text(
                                        text = previousAIMessage.aiMessage.preText,
                                        type = TextType.CAPTION
                                    )
                                    app.treelune.core.ai.ui.components.CommunicationAnswer(
                                        module = answered,
                                        answer = message.textContent.removePrefix(responsePrefix).trim()
                                    )
                                }
                            } else {
                                // Normal text content
                                UI.Text(
                                    text = message.textContent,
                                    type = TextType.BODY
                                )
                            }
                        }
                        message.aiMessage != null -> {
                            // AI message with preText
                            UI.Text(
                                text = message.aiMessage.preText,
                                type = TextType.BODY
                            )

                            // Communication module (inline, only on last AI message)
                            message.aiMessage.communicationModule?.let { module ->
                                val shouldShow = isLastAIMessage &&
                                    aiState.waitingContext is app.treelune.core.ai.domain.WaitingContext.Communication

                                // A module no longer waiting shows what it asked, read only:
                                // what became of it (answered, replaced, cancelled) follows it
                                if (!shouldShow) {
                                    Spacer(modifier = Modifier.height(UI.Space.S))
                                    app.treelune.core.ai.ui.components.CommunicationQuestions(module)
                                }
                                if (shouldShow) {
                                    Spacer(modifier = Modifier.height(UI.Space.S))
                                    app.treelune.core.ai.ui.components.CommunicationModuleCard(
                                        module = module,
                                        onResponse = { response, note ->
                                            AIOrchestrator.resumeWithResponse(response, note)
                                        },
                                        onCancel = {
                                            AIOrchestrator.cancelCommunication()
                                        },
                                        freeReply = freeReply,
                                        onFreeReply = onFreeReply
                                    )
                                }
                            }

                            // Validation UI (inline, only on last AI message)
                            if (isLastAIMessage && aiState.waitingContext is app.treelune.core.ai.domain.WaitingContext.Validation) {
                                val validationCtx = aiState.waitingContext as app.treelune.core.ai.domain.WaitingContext.Validation
                                Spacer(modifier = Modifier.height(UI.Space.S))
                                app.treelune.core.ai.ui.ValidationUI(
                                    context = validationCtx.validationContext,
                                    onValidate = {
                                        AIOrchestrator.resumeWithValidation(true)
                                    },
                                    onRefuse = {
                                        AIOrchestrator.resumeWithValidation(false)
                                    }
                                )
                            }
                        }
                        // An AI response the app could not take: the message after it says why
                        message.sender == MessageSender.AI && message.aiMessage == null -> {
                            UI.Text(
                                text = s.shared("ai_response_refused"),
                                type = TextType.CAPTION
                            )
                        }
                        message.systemMessage != null -> {
                            // System message: show summary + command details
                            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                                // Summary
                                UI.Text(
                                    text = message.systemMessage.summary,
                                    type = TextType.BODY
                                )

                                // The technical side, folded: what the AI was told, or the raw cause
                                message.systemMessage.details?.let { details -> SystemDetails(message.id, details) }

                                // Data above the size threshold waiting for the user's decision
                                val dataWaiting = aiState.waitingContext as? app.treelune.core.ai.domain.WaitingContext.DataConfirmation
                                if (dataWaiting != null && dataWaiting.messageId == message.id) {
                                    DataConfirmationCard(
                                        dataChars = dataWaiting.dataChars,
                                        maxDataChars = dataWaiting.maxDataChars,
                                        alwaysSend = dataWaiting.alwaysSend,
                                        onSend = { AIOrchestrator.resumeWithDataConfirmation(true) },
                                        onRefuse = { AIOrchestrator.resumeWithDataConfirmation(false) }
                                    )
                                }

                                // Command results details (if present)
                                if (message.systemMessage.commandResults.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(UI.Space.XS))

                                    // The AI's commands these results answer, one per action result, in
                                    // order: an action that wrote entries shows them by their fields
                                    val results = message.systemMessage.commandResults
                                    val commands = previousAIMessage?.aiMessage?.actionCommands.orEmpty()
                                    val actionCount = results.count { it.isActionCommand }
                                    val commandOf: Map<app.treelune.core.ai.data.CommandResult, app.treelune.core.ai.data.DataCommand> =
                                        if (commands.size == actionCount) results.filter { it.isActionCommand }.zip(commands).toMap()
                                        else {
                                            if (actionCount > 0 && previousAIMessage != null) LogManager.aiUI("Action results ($actionCount) do not match the AI's commands (${commands.size})", "WARN")
                                            emptyMap()
                                        }

                                    results.forEach { commandResult ->
                                        UI.Card(type = CardType.DEFAULT) {
                                            Column(
                                                modifier = Modifier.padding(UI.Space.S),
                                                verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                                            ) {
                                                // Status icon + details
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    UI.Icon(
                                                        iconName = if (commandResult.status == CommandStatus.SUCCESS) "check" else "x",
                                                        size = 20.dp
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        // Verbalized description
                                                        commandResult.details?.let {
                                                            UI.Text(
                                                                text = it,
                                                                type = TextType.BODY
                                                            )
                                                        }

                                                        // What an action wrote: its entries by their fields. What it
                                                        // returns besides is the AI's, its line above saying what it did
                                                        val written = commandOf[commandResult]?.takeIf {
                                                            commandResult.status == CommandStatus.SUCCESS && it.params["entries"] != null
                                                        }
                                                        if (written != null) WrittenEntries(written)

                                                        // A file the AI exported, handed on from here
                                                        val exported = (commandResult.data?.get("uri") as? String)?.takeIf {
                                                            commandResult.command == "exports.csv" && commandResult.status == CommandStatus.SUCCESS
                                                        }
                                                        if (exported != null) {
                                                            val context = LocalContext.current
                                                            // The file and its count, as the export's own window says them
                                                            (commandResult.data?.get("file_name") as? String)?.let {
                                                                UI.Text(s.shared("export_csv_written").format(it), TextType.CAPTION)
                                                            }
                                                            (commandResult.data?.get("entries") as? Number)?.let {
                                                                UI.Text(s.shared("export_csv_entries").format(it.toInt()), TextType.CAPTION)
                                                            }
                                                            UI.Button(type = app.treelune.core.ui.ButtonType.SECONDARY, onClick = { app.treelune.core.ui.exports.shareFile(context, exported, "text/csv") }) {
                                                                UI.Text(s.shared("file_share"), TextType.LABEL)
                                                            }
                                                        }

                                                        // Error (if failed)
                                                        commandResult.error?.let { error ->
                                                            UI.Text(
                                                                text = s.shared("ai_error_label").format(error),
                                                                type = TextType.CAPTION
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI loading spinner (just the spinner, no buttons)
 * Can be used in both CHAT and AUTOMATION contexts
 */
@Composable
fun AILoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        UI.AIThinkingIndicator()
    }
}

/**
 * Chat interrupt button with state management
 * Shows "Interrupting..." message after user clicks interrupt
 * CHAT only - not for AUTOMATION
 */
@Composable
fun ChatInterruptButton() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    var isInterrupting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isInterrupting) {
            // Show "Interrupting..." message after button clicked
            UI.Text(
                text = s.shared("ai_status_interrupting"),
                type = TextType.CAPTION
            )
        } else {
            // Show interrupt button
            UI.ActionButton(
                action = ButtonAction.INTERRUPT,
                display = ButtonDisplay.LABEL,
                size = Size.S,
                onClick = {
                    isInterrupting = true
                    scope.launch {
                        AIOrchestrator.interruptActiveRound()
                    }
                }
            )
        }
    }
}

/**
 * Session settings dialog - validation toggle, the provider the conversation calls and the
 * reasoning it asks of its model
 *
 * @param providerId The provider it calls; null for a conversation not begun, which takes the default
 */
@Composable
fun SessionSettingsDialog(
    validation: SessionValidation,
    providerId: String?,
    reasoning: SessionReasoning?,
    onDismiss: () -> Unit,
    onValidationChange: (SessionValidation) -> Unit,
    onProviderChange: (String) -> Unit,
    onReasoningChange: (SessionReasoning?) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Each setting is stored as it is touched: OK closes, with nothing left to confirm or undo
    UI.Dialog(type = DialogType.INFO, onConfirm = onDismiss, onCancel = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            UI.Text(text = s.shared("ai_settings_session"), type = TextType.TITLE, fillMaxWidth = true)

            // What this conversation adds to the protections: each box a level, for every object of it
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(text = s.shared("session_validation_title"), type = TextType.HEADING)
                UI.Checkbox(checked = validation.app, onCheckedChange = { onValidationChange(validation.copy(app = it)) }, label = s.shared("session_validation_app"))
                UI.Checkbox(checked = validation.zones, onCheckedChange = { onValidationChange(validation.copy(zones = it)) }, label = s.shared("session_validation_zones"))
                UI.Checkbox(checked = validation.data, onCheckedChange = { onValidationChange(validation.copy(data = it)) }, label = s.shared("session_validation_data"))
                UI.Text(text = s.shared("session_validation_help"), type = TextType.CAPTION)
            }

            // The providers, and the one it calls: its own, or the default it would take
            var providers by remember { mutableStateOf<List<Map<*, *>>?>(null) }
            LaunchedEffect(Unit) {
                providers = (app.treelune.core.coordinator.Coordinator(context).processUserAction("ai_provider_config.list")
                    .data?.get("providers") as? List<*>).orEmpty().map { it as Map<*, *> }
            }
            val calls = providerId ?: providers?.firstOrNull { it["is_default"] == true }?.get("id") as String?
            SessionProviderSettings(providers, calls, onProviderChange)
            calls?.let { SessionReasoningSettings(it, reasoning, onReasoningChange) }
        }
    }
}

/**
 * The provider this conversation calls, among those created: changed, its next message goes to the
 * new one, and its own reasoning goes with the change (ai_sessions.set_provider).
 */
@Composable
private fun SessionProviderSettings(providers: List<Map<*, *>>?, calls: String?, onProviderChange: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(text = s.shared("session_provider_title"), type = TextType.HEADING)
        when {
            providers == null -> UI.Text(s.shared("message_loading"), TextType.CAPTION)
            providers.isEmpty() -> UI.Text(s.shared("ai_error_no_provider_configured"), TextType.CAPTION)
            else -> {
                val labels = providers.associate { app.treelune.core.ai.ui.screens.providerLabel(it) to (it["id"] as String) }
                UI.FormSelection(
                    label = s.shared("label_ai_provider"),
                    options = labels.keys.toList(),
                    selected = labels.entries.find { it.value == calls }?.key ?: "",
                    onSelect = { chosen -> labels[chosen]?.takeIf { it != calls }?.let(onProviderChange) },
                    required = true
                )
            }
        }
        UI.Text(text = s.shared("session_provider_help"), type = TextType.CAPTION)
    }
}

/**
 * The reasoning this conversation asks of its model: its provider's setting, or its own among
 * what the model offers, listed by its provider (AIClient.modelReasoning) as the provider's
 * config screen lists it. The choices follow that screen's: thinking off takes an effort among
 * those it accepts, never the model's default.
 */
@Composable
private fun SessionReasoningSettings(providerId: String, reasoning: SessionReasoning?, onReasoningChange: (SessionReasoning?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var model by remember(providerId) { mutableStateOf<app.treelune.core.ai.providers.ModelReasoning?>(null) }
    LaunchedEffect(providerId) { model = app.treelune.core.ai.providers.AIClient(context).modelReasoning(providerId) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(text = s.shared("session_reasoning_title"), type = TextType.HEADING)
        val known = model
        val offered = known?.reasoning
        when {
            known == null -> UI.Text(s.shared("ai_provider_fetching_models"), TextType.CAPTION)
            known.error != null -> UI.Text(known.error, TextType.CAPTION)
            offered == null -> UI.Text(s.shared("ai_provider_no_reasoning"), TextType.CAPTION)
            else -> {
                UI.Switch(
                    checked = reasoning == null,
                    // Its own setting starts from the provider's
                    onCheckedChange = { follow -> onReasoningChange(if (follow) null else known.providerSetting ?: SessionReasoning(false, null)) },
                    label = s.shared("session_reasoning_follow_provider")
                )
                if (reasoning != null) {
                    offered.thinkingOff?.let { off ->
                        UI.Switch(
                            checked = reasoning.thinkingOff,
                            // An effort thinking off does not accept goes with the switch
                            onCheckedChange = { checked ->
                                val kept = reasoning.effort?.takeIf { !checked || off.efforts == null || it in off.efforts }
                                onReasoningChange(SessionReasoning(checked, kept))
                            },
                            label = s.shared("ai_provider_thinking_off")
                        )
                    }
                    if (offered.efforts.isNotEmpty()) {
                        val levels = if (reasoning.thinkingOff) offered.thinkingOff?.efforts ?: offered.efforts else offered.efforts
                        val required = reasoning.thinkingOff || offered.effortRequired
                        val byDefault = s.shared("ai_provider_effort_default")
                        UI.FormSelection(
                            label = s.shared("ai_provider_effort"),
                            options = if (required) levels else listOf(byDefault) + levels,
                            selected = reasoning.effort ?: if (required) "" else byDefault,
                            onSelect = { chosen -> onReasoningChange(reasoning.copy(effort = chosen.takeIf { it != byDefault })) },
                            required = required
                        )
                    }
                }
            }
        }
        UI.Text(text = s.shared("session_reasoning_help"), type = TextType.CAPTION)
    }
}

/**
 * Asks whether data above the CHAT size threshold goes to the AI. For the tools sent always
 * ([alwaysSend]), the message above already says what is asked and that it holds for the
 * session: the card only titles it and answers.
 */
/**
 * A system message's [details], folded under a "Details" line that a touch opens and closes; kept
 * open per message across a recreation.
 */
/**
 * What a bubble shows, as text to paste elsewhere: the user's message; the AI's text, the
 * questions of its module and the actions waiting for validation ([waitingActions]); a system
 * message's summary, its command lines (✓ or ✗, what was done, what came back, the error) and
 * its details. The entries an action wrote, shown by their fields, are not copied.
 */
private fun copyText(
    message: SessionMessage,
    richText: String?,
    waitingActions: List<app.treelune.core.ai.validation.VerbalizedAction>,
    s: app.treelune.core.strings.StringsContext
): String = buildList {
    when {
        richText != null -> add(richText)
        message.textContent != null -> add(message.textContent)
        message.aiMessage != null -> {
            add(message.aiMessage.preText)
            message.aiMessage.communicationModule?.let { module ->
                add((listOf(s.shared(if (module.fields.isEmpty()) "ai_module_validation_title" else "ai_module_question_title")) +
                    module.fields.map { "- ${it.definition.displayName}" }).joinToString("\n"))
            }
            if (waitingActions.isNotEmpty()) add(waitingActions.joinToString("\n") { "- ${it.description}" })
        }
        message.sender == MessageSender.AI -> add(s.shared("ai_response_refused"))
        message.systemMessage != null -> {
            add(message.systemMessage.summary)
            message.systemMessage.commandResults.takeIf { it.isNotEmpty() }?.let { results ->
                add(results.joinToString("\n") { result ->
                    listOfNotNull(
                        (if (result.status == CommandStatus.SUCCESS) "✓ " else "✗ ") + (result.details ?: result.command),
                        result.error?.let { "  " + s.shared("ai_error_label").format(it) }
                    ).joinToString("\n")
                })
            }
            message.systemMessage.details?.let { add(it) }
        }
    }
}.filter { it.isNotBlank() }.joinToString("\n\n")

@Composable
private fun SystemDetails(messageId: String, details: String) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var open by androidx.compose.runtime.saveable.rememberSaveable(messageId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        Row(
            modifier = Modifier.clickable { open = !open },
            horizontalArrangement = Arrangement.spacedBy(UI.Space.XS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(text = s.shared("ai_system_details"), type = TextType.CAPTION)
            UI.Icon(if (open) "chevron-up" else "chevron-down", size = 16.dp)
        }
        if (open) UI.Text(text = details, type = TextType.CAPTION)
    }
}

@Composable
private fun DataConfirmationCard(
    dataChars: Int,
    maxDataChars: Int,
    alwaysSend: Boolean,
    onSend: () -> Unit,
    onRefuse: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    UI.Card(type = CardType.DEFAULT) {
        Column(
            modifier = Modifier.padding(UI.Space.M),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            UI.Text(text = s.shared(if (alwaysSend) "ai_always_send_title" else "ai_data_confirmation_title"), type = TextType.SUBTITLE)
            if (!alwaysSend) UI.Text(
                text = s.shared("ai_data_confirmation_text").format(dataChars, maxDataChars),
                type = TextType.BODY
            )
            app.treelune.core.ui.components.ButtonRow {
                UI.Button(type = ButtonType.PRIMARY, size = Size.M, onClick = onSend) {
                    UI.Text(text = s.shared("ai_data_confirmation_send"), type = TextType.BODY)
                }
                UI.Button(type = ButtonType.DEFAULT, size = Size.M, onClick = onRefuse) {
                    UI.Text(text = s.shared("ai_data_confirmation_refuse"), type = TextType.BODY)
                }
            }
        }
    }
}

/**
 * The entries an action of the AI wrote, each value shown by its field as in the validation
 * request (ProposedEntries), read from the command with its tool's fields as they are now.
 */
@Composable
private fun WrittenEntries(action: app.treelune.core.ai.data.DataCommand) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // null while read; a tool that cannot be read (deleted since) says so
    var failed by remember(action) { mutableStateOf(false) }
    val entries by produceState<List<app.treelune.core.ai.validation.ProposedEntry>?>(initialValue = null, action) {
        value = try {
            app.treelune.core.ai.validation.ProposedEntries.read(action, context)
        } catch (e: Exception) {
            LogManager.aiUI("Written entries not read: ${e.message}", "WARN")
            failed = true
            emptyList()
        }
    }
    if (failed) {
        UI.Text(text = s.shared("ai_written_entries_unreadable"), type = TextType.CAPTION)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        entries?.forEach { entry -> app.treelune.core.ai.ui.ProposedEntryItem(entry) }
    }
}
