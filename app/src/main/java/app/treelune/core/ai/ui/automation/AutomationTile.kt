package app.treelune.core.ai.ui.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.data.Automation
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.ai.utils.toEndReasonDisplayString
import app.treelune.core.fields.settings.scheduleSummary
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.ui.components.TileLine
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a routine's tile and its page do, the same from both: run it now, take back its run
 * waiting in the queue, open a conversation started with its message, open its config, open its
 * history, switch it on or off. A refusal, or a run asked for, is said through
 * [onMessage].
 */
class AutomationActions(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit
) {
    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    fun run(automation: Automation) = scope.launch {
        val result = coordinator.processUserAction("automations.execute_manual", mapOf("automation_id" to automation.id))
        onMessage(if (result.status == CommandStatus.SUCCESS) s.shared("automation_execution_triggered") else result.error ?: s.shared("error_operation_failed"))
    }

    fun cancel(sessionId: String) = scope.launch { AIOrchestrator.cancelQueuedSession(sessionId) }

    fun chat(automation: Automation) = scope.launch {
        val seed = AIOrchestrator.loadSeedMessages(automation.seedSessionId)
        val prefill = seed.firstOrNull { it.sender == app.treelune.core.ai.data.MessageSender.USER }?.richContent?.segments ?: emptyList()
        app.treelune.core.ai.orchestration.ChatRequests.open(prefill)
    }

    fun configure(automation: Automation) = Navigator.push(Place.AutomationConfig(automation.zoneId, automation.id, null))

    fun history(automation: Automation) = Navigator.push(Place.Automation(automation.id, automation.zoneId))

    fun toggle(automation: Automation, enabled: Boolean) = scope.launch {
        val result = coordinator.processUserAction(if (enabled) "automations.enable" else "automations.disable", mapOf("automation_id" to automation.id))
        if (result.status != CommandStatus.SUCCESS) onMessage(result.error ?: s.shared("error_operation_failed"))
    }
}

@Composable
fun rememberAutomationActions(onMessage: (String) -> Unit): AutomationActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(scope) { AutomationActions(context, scope, onMessage) }
}

/**
 * Where a routine stands, in one line, the first that holds: its run waiting in the queue and
 * its place there, switched off, its next run, run on demand alone, its triggers; and the queued
 * run, taken back by its tile's and its page's Cancel. What keeps it from running, [problem] (no
 * provider), does not take the line: it says what the routine is set to do, under a warning icon.
 */
data class AutomationState(val line: String, val queuedSessionId: String?, val problem: String? = null)

@Composable
fun rememberAutomationState(automation: Automation): AutomationState {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Next execution, computed by the scheduler itself so the tile shows what will actually run
    var nextExecution by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(automation.id, automation.isEnabled, automation.schedule, automation.updatedAt) {
        nextExecution = if (automation.isEnabled && automation.schedule != null) {
            withContext(Dispatchers.IO) { nextExecutionLine(context, automation.id) }
        } else null
    }

    // A run of this routine waiting in the queue: the queue knows sessions, the session its routine
    val queuedSessions by AIOrchestrator.queuedSessions.collectAsState()
    var queued by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(queuedSessions, automation.id) {
        val coordinator = Coordinator(context)
        queued = queuedSessions.filter { it.sessionType == SessionType.AUTOMATION }.firstOrNull { session ->
            val result = coordinator.processUserAction("ai_sessions.get", mapOf("id" to session.sessionId))
            result.status == CommandStatus.SUCCESS && result.data?.get("automation_id") == automation.id
        }?.sessionId
    }

    // What starts it: by hand alone, its schedule, its triggers
    val starts = when {
        automation.schedule == null && (automation.triggers == 0) -> s.shared("automation_status_manual")
        automation.schedule != null && (automation.triggers > 0) ->
            "${scheduleSummary(automation.schedule, s)} + ${s.shared("automation_triggers_count").format(automation.triggers)}"
        automation.schedule != null -> scheduleSummary(automation.schedule, s)
        else -> s.shared("automation_triggers_count").format(automation.triggers)
    }
    // Neither on nor run until it has one
    val problem = if (automation.providerId == null) s.shared("automation_status_no_provider") else null
    val line = when {
        queued != null -> s.shared("ai_automation_queued_badge").format(queuedSessions.indexOfFirst { it.sessionId == queued } + 1)
        // Off for want of a provider, not by choice: what it would do once given one
        problem != null -> starts
        !automation.isEnabled -> s.shared("label_disabled")
        nextExecution != null -> nextExecution!!
        else -> starts
    }
    return AutomationState(line, queued, problem)
}

/**
 * A routine's state and its switch, on one line: the state takes what the switch leaves, under a
 * warning icon when something keeps it from running, which its page says in full ([withProblem]).
 */
@Composable
fun AutomationStateLine(automation: Automation, state: AutomationState, actions: AutomationActions, withProblem: Boolean = false) {
    if (withProblem && state.problem != null) UI.Text(state.problem, TextType.CAPTION)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
        if (state.problem != null) UI.Icon(iconName = "triangle-alert", size = 16.dp, contentDescription = state.problem, tint = UI.statusColor(app.treelune.core.ui.StatusColor.WARNING))
        Box(modifier = Modifier.weight(1f)) { UI.Text(state.line, TextType.CAPTION, maxLines = 2) }
        UI.BooleanField(label = "", value = automation.isEnabled, onValueChange = { actions.toggle(automation, it) })
    }
}

/**
 * A routine's buttons across the whole width: run it now (Cancel while its run waits in the
 * queue), a conversation started with its message, and on its tile its page, the history
 * ([toPage]); its page opens its config from its header.
 */
@Composable
fun AutomationButtons(automation: Automation, state: AutomationState, actions: AutomationActions, toPage: Boolean = true) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val queued = state.queuedSessionId
        if (queued != null) {
            UI.Button(type = ButtonType.DANGER, size = Size.S, onClick = { actions.cancel(queued) }) {
                UI.Text(s.shared("ai_automation_cancel_execution"), TextType.LABEL)
            }
        } else {
            UI.ActionButton(action = ButtonAction.START, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.run(automation) })
        }
        UI.ActionButton(action = ButtonAction.AI_CHAT, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.chat(automation) })
        if (toPage) UI.ActionButton(action = ButtonAction.VIEW, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.history(automation) })
    }
}

/**
 * A routine's tile among its section's tools (UI.LookCard), « Routine » under its name: its
 * state and its switch beside its header (LINE, EXTENDED, SQUARE) or under it (CONDENSED); its
 * buttons on a line of their own (EXTENDED, SQUARE); its last three runs under them (SQUARE), each
 * opening. A touch opens its page, its history; a long touch its config, as a tool's. A run of it
 * that waits to be read is marked on its icon, and its run going on now (LocalRunning).
 */
@Composable
fun AutomationTile(automation: Automation, actions: AutomationActions) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val state = rememberAutomationState(automation)
    UI.LookCard(
        look = automation.look,
        subtitle = s.shared("automation_display_name"),
        seed = automation.id,
        waiting = LocalWaiting.current.automation(automation.id),
        running = LocalRunning.current.automation(automation.id),
        onClick = { actions.history(automation) },
        onLongClick = { actions.configure(automation) },
        summary = { AutomationStateLine(automation, state, actions) },
        glance = { AutomationStateLine(automation, state, actions) },
        body = { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                AutomationButtons(automation, state, actions)
                if (rows == null || rows > 1) LastRuns(automation)
            }
        }
    )
}

/** Its last three runs: when and how each ended, what its AI said of it; a touch opens it. */
@Composable
private fun LastRuns(automation: Automation) {
    val context = LocalContext.current
    var runs by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    LaunchedEffect(automation.id, automation.lastExecutionId) {
        val result = Coordinator(context).processUserAction("ai_sessions.list_sessions_for_automation",
            mapOf("automation_id" to automation.id, "limit" to 3, "page" to 1))
        runs = (result.data?.get("sessions") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    runs.forEach { run ->
        val sessionId = run["id"] as String
        val at = (run["scheduled_execution_time"] as? Long) ?: (run["created_at"] as? Long) ?: 0L
        val end = (run["end_reason"] as? String).toEndReasonDisplayString(context)
        val waits = run["waits"] as? Boolean ?: false
        TileLine(
            text = listOf(app.treelune.core.utils.FormatUtils.formatRelativeTimeShort(at, context), end).joinToString(" · "),
            modifier = Modifier.fillMaxWidth().clickable { Navigator.push(Place.Execution(sessionId, automation.id, automation.zoneId)) },
            secondary = (run["summary"] as? String)?.takeIf { it.isNotBlank() },
            trailing = if (waits) { { UI.WaitingMark() } } else null
        )
    }
}

/**
 * The line saying when the automation [automationId] runs next ("Next: in 2 h"), read through
 * automations.next_execution; null when it will not run, the error's text when it cannot be read.
 */
internal suspend fun nextExecutionLine(context: android.content.Context, automationId: String): String? {
    val s = Strings.`for`(context = context)
    val result = Coordinator(context).processUserAction("automations.next_execution", mapOf("automation_id" to automationId))
    if (result.status != CommandStatus.SUCCESS) return result.error ?: s.shared("error_automation_load_failed")
    val at = result.data?.get("next_execution_at") as? Long ?: return null
    return s.shared("automation_next_execution").format(app.treelune.core.utils.FormatUtils.formatRelativeTime(at, context))
}
