package app.treelune.core.ai.ui.routine

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.data.Routine
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
class RoutineActions(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit
) {
    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    fun run(routine: Routine) = scope.launch {
        val result = coordinator.processUserAction("automations.execute_manual", mapOf("automation_id" to routine.id))
        onMessage(if (result.status == CommandStatus.SUCCESS) s.shared("automation_execution_triggered") else result.error ?: s.shared("error_operation_failed"))
    }

    fun cancel(sessionId: String) = scope.launch { AIOrchestrator.cancelQueuedSession(sessionId) }

    fun chat(routine: Routine) = scope.launch {
        val seed = AIOrchestrator.loadSeedMessages(routine.seedSessionId)
        val prefill = seed.firstOrNull { it.sender == app.treelune.core.ai.data.MessageSender.USER }?.richContent?.segments ?: emptyList()
        app.treelune.core.ai.orchestration.ChatRequests.open(prefill)
    }

    fun configure(routine: Routine) = Navigator.push(Place.RoutineConfig(routine.zoneId, routine.id, null))

    fun history(routine: Routine) = Navigator.push(Place.Routine(routine.id, routine.zoneId))

    fun toggle(routine: Routine, enabled: Boolean) = scope.launch {
        val result = coordinator.processUserAction(if (enabled) "automations.enable" else "automations.disable", mapOf("automation_id" to routine.id))
        if (result.status != CommandStatus.SUCCESS) onMessage(result.error ?: s.shared("error_operation_failed"))
    }
}

@Composable
fun rememberRoutineActions(onMessage: (String) -> Unit): RoutineActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(scope) { RoutineActions(context, scope, onMessage) }
}

/**
 * Where a routine stands, in one line, the first that holds: its run waiting in the queue and
 * its place there, switched off, its next run, run on demand alone, its triggers; and the queued
 * run, taken back by its tile's and its page's Cancel. What keeps it from running, [problem] (no
 * provider), does not take the line: it says what the routine is set to do, under a warning icon.
 */
data class RoutineState(val line: String, val queuedSessionId: String?, val problem: String? = null)

@Composable
fun rememberRoutineState(routine: Routine): RoutineState {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Next execution, computed by the scheduler itself so the tile shows what will actually run
    var nextExecution by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(routine.id, routine.isEnabled, routine.schedule, routine.updatedAt) {
        nextExecution = if (routine.isEnabled && routine.schedule != null) {
            withContext(Dispatchers.IO) { nextExecutionLine(context, routine.id) }
        } else null
    }

    // A run of this routine waiting in the queue: the queue knows sessions, the session its routine
    val queuedSessions by AIOrchestrator.queuedSessions.collectAsState()
    var queued by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(queuedSessions, routine.id) {
        val coordinator = Coordinator(context)
        queued = queuedSessions.filter { it.sessionType == SessionType.AUTOMATION }.firstOrNull { session ->
            val result = coordinator.processUserAction("ai_sessions.get", mapOf("id" to session.sessionId))
            result.status == CommandStatus.SUCCESS && result.data?.get("automation_id") == routine.id
        }?.sessionId
    }

    // What starts it: by hand alone, its schedule, its triggers
    val starts = when {
        routine.schedule == null && (routine.triggers == 0) -> s.shared("automation_status_manual")
        routine.schedule != null && (routine.triggers > 0) ->
            "${scheduleSummary(routine.schedule, s)} + ${s.shared("automation_triggers_count").format(routine.triggers)}"
        routine.schedule != null -> scheduleSummary(routine.schedule, s)
        else -> s.shared("automation_triggers_count").format(routine.triggers)
    }
    // Neither on nor run until it has one
    val problem = if (routine.providerId == null) s.shared("automation_status_no_provider") else null
    val line = when {
        queued != null -> s.shared("ai_automation_queued_badge").format(queuedSessions.indexOfFirst { it.sessionId == queued } + 1)
        // Off for want of a provider, not by choice: what it would do once given one
        problem != null -> starts
        !routine.isEnabled -> s.shared("label_disabled")
        nextExecution != null -> nextExecution!!
        else -> starts
    }
    return RoutineState(line, queued, problem)
}

/**
 * A routine's state and its switch, on one line: the state takes what the switch leaves. When
 * something keeps it from running, its page says it in full under a warning icon
 * ([withProblem]); a tile, too narrow to lose an icon's width beside its switch (« Toutes /
 * s les… »), writes its state in the warning's colour instead, the problem its description.
 */
@Composable
fun RoutineStateLine(routine: Routine, state: RoutineState, actions: RoutineActions, withProblem: Boolean = false) {
    if (withProblem && state.problem != null) UI.Text(state.problem, TextType.CAPTION)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
        if (withProblem && state.problem != null) UI.Icon(iconName = "triangle-alert", size = 16.dp, contentDescription = state.problem, tint = UI.statusColor(app.treelune.core.ui.StatusColor.WARNING))
        Box(modifier = Modifier.weight(1f).then(if (state.problem != null) Modifier.semantics { contentDescription = state.problem } else Modifier)) {
            UI.Text(state.line, if (!withProblem && state.problem != null) TextType.WARNING else TextType.CAPTION, maxLines = 2)
        }
        UI.BooleanField(label = "", value = routine.isEnabled, onValueChange = { actions.toggle(routine, it) })
    }
}

/**
 * A routine's buttons across the whole width: run it now (Cancel while its run waits in the
 * queue), a conversation started with its message, and on its tile its page, the history
 * ([toPage]); its page opens its config from its header.
 */
@Composable
fun RoutineButtons(routine: Routine, state: RoutineState, actions: RoutineActions, toPage: Boolean = true) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val queued = state.queuedSessionId
        if (queued != null) {
            UI.Button(type = ButtonType.DANGER, size = Size.S, onClick = { actions.cancel(queued) }) {
                UI.Text(s.shared("ai_automation_cancel_execution"), TextType.LABEL)
            }
        } else {
            UI.ActionButton(action = ButtonAction.START, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.run(routine) })
        }
        UI.ActionButton(action = ButtonAction.AI_CHAT, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.chat(routine) })
        if (toPage) UI.ActionButton(action = ButtonAction.VIEW, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.history(routine) })
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
fun RoutineTile(routine: Routine, actions: RoutineActions) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val state = rememberRoutineState(routine)
    UI.LookCard(
        look = routine.look,
        subtitle = s.shared("automation_display_name"),
        seed = routine.id,
        waiting = LocalWaiting.current.routine(routine.id),
        running = LocalRunning.current.routine(routine.id),
        onClick = { actions.history(routine) },
        onLongClick = { actions.configure(routine) },
        summary = { RoutineStateLine(routine, state, actions) },
        glance = { RoutineStateLine(routine, state, actions) },
        body = { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                RoutineButtons(routine, state, actions)
                if (rows == null || rows > 1) LastRuns(routine)
            }
        }
    )
}

/** Its last three runs: when and how each ended, what its AI said of it; a touch opens it. */
@Composable
private fun LastRuns(routine: Routine) {
    val context = LocalContext.current
    var runs by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    LaunchedEffect(routine.id, routine.lastExecutionId) {
        val result = Coordinator(context).processUserAction("ai_sessions.list_sessions_for_automation",
            mapOf("automation_id" to routine.id, "limit" to 3, "page" to 1))
        runs = (result.data?.get("sessions") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    runs.forEach { run ->
        val sessionId = run["id"] as String
        val at = (run["scheduled_execution_time"] as? Long) ?: (run["created_at"] as? Long) ?: 0L
        val end = (run["end_reason"] as? String).toEndReasonDisplayString(context)
        val waits = run["waits"] as? Boolean ?: false
        TileLine(
            text = listOf(app.treelune.core.utils.FormatUtils.formatRelativeTimeShort(at, context), end).joinToString(" · "),
            modifier = Modifier.fillMaxWidth().clickable { Navigator.push(Place.Execution(sessionId, routine.id, routine.zoneId)) },
            secondary = (run["summary"] as? String)?.takeIf { it.isNotBlank() },
            trailing = if (waits) { { UI.WaitingMark() } } else null
        )
    }
}

/**
 * The line saying when the routine [routineId] runs next ("Next: in 2 h"), read through
 * routines.next_execution; null when it will not run, the error's text when it cannot be read.
 */
internal suspend fun nextExecutionLine(context: android.content.Context, routineId: String): String? {
    val s = Strings.`for`(context = context)
    val result = Coordinator(context).processUserAction("automations.next_execution", mapOf("automation_id" to routineId))
    if (result.status != CommandStatus.SUCCESS) return result.error ?: s.shared("error_automation_load_failed")
    val at = result.data?.get("next_execution_at") as? Long ?: return null
    return s.shared("automation_next_execution").format(app.treelune.core.utils.FormatUtils.formatRelativeTime(at, context))
}
