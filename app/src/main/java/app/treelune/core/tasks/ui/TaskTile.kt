package app.treelune.core.tasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tasks.Task
import app.treelune.core.tasks.TaskRun
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Duration
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileLine
import app.treelune.core.utils.FormatUtils
import kotlinx.coroutines.launch

/** A run as tasks.runs gives it, what a line of it shows. */
internal data class RunLine(val id: String, val state: TaskRun.State, val startedAt: Long, val waits: Boolean, val error: String?) {
    companion object {
        fun of(map: Map<*, *>) = RunLine(
            id = map["id"] as String,
            state = TaskRun.State.of(map["state"] as String),
            startedAt = (map["started_at"] as Number).toLong(),
            waits = map["waits"] == true,
            error = (map["results"] as List<*>).filterIsInstance<Map<*, *>>().lastOrNull()?.get("error") as? String
        )
    }
}

/** A run's state in words. */
internal fun stateLabel(state: TaskRun.State, s: StringsContext) = s.shared("task_run_state_${state.key}")

/**
 * Where a Task stands, in one line: its last run going on or waiting, off, its next run, run on
 * demand alone; and its switch.
 */
@Composable
internal fun TaskStateLine(task: Task, last: RunLine?, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var next by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(task.id, task.updatedAt, task.isEnabled, last?.id) {
        val result = Coordinator(context).processUserAction("tasks.next_execution", mapOf("task_id" to task.id))
        next = (result.data?.get("next_execution_at") as? Number)?.toLong()
    }
    val line = when {
        last != null && !last.state.ended -> stateLabel(last.state, s)
        !task.isEnabled -> s.shared("label_disabled")
        next != null -> s.shared("automation_next_execution").format(FormatUtils.formatRelativeTime(next!!, context))
        else -> s.shared("automation_status_manual")
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { UI.Text(line, TextType.CAPTION, maxLines = 2) }
        UI.BooleanField(label = "", value = task.isEnabled, onValueChange = onToggle)
    }
}

/** What a Task's tile and its page do: run it now, switch it on or off. A refusal or a run asked is said by a toast. */
internal class TaskActions(private val context: android.content.Context, private val scope: kotlinx.coroutines.CoroutineScope) {
    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    fun run(task: Task) = scope.launch {
        val result = coordinator.processUserAction("tasks.run", mapOf("task_id" to task.id))
        UI.Toast(if (result.status == CommandStatus.SUCCESS) s.shared("task_run_started") else result.error ?: s.shared("error_operation_failed"), Duration.SHORT)
    }

    fun toggle(task: Task, enabled: Boolean) = scope.launch {
        val result = coordinator.processUserAction("tasks.update", mapOf("task_id" to task.id, "is_enabled" to enabled))
        if (result.status != CommandStatus.SUCCESS) UI.Toast(result.error ?: s.shared("error_operation_failed"), Duration.LONG)
    }
}

@Composable
internal fun rememberTaskActions(): TaskActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(scope) { TaskActions(context, scope) }
}

/** The last runs of [taskId], [limit] at most, read again when a run starts or ends (they say the zones changed). */
@Composable
internal fun rememberRuns(taskId: String, limit: Int): List<RunLine> {
    val context = LocalContext.current
    var runs by remember { mutableStateOf<List<RunLine>>(emptyList()) }
    LaunchedEffect(taskId) {
        suspend fun load() {
            val result = Coordinator(context).processUserAction("tasks.runs", mapOf("task_id" to taskId, "limit" to limit))
            runs = (result.data?.get("runs") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { RunLine.of(it) }
        }
        load()
        app.treelune.core.utils.DataChangeNotifier.changes.collect { if (it is app.treelune.core.utils.DataChangeEvent.ZonesChanged) load() }
    }
    return runs
}

/** A run in one line: when, how it stands or ended, why it failed; a touch opens it. */
@Composable
internal fun RunRow(run: RunLine, task: Task) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    TileLine(
        text = listOf(FormatUtils.formatRelativeTimeShort(run.startedAt, context), stateLabel(run.state, s)).joinToString(" · "),
        modifier = Modifier.fillMaxWidth().clickable { Navigator.push(Place.TaskRun(run.id, task.id, task.zoneId)) },
        secondary = run.error,
        trailing = if (run.waits) { { UI.WaitingMark() } } else null
    )
}

/**
 * A Task's tile among its section's tools (UI.LookCard), « Task » under its name: its state and
 * its switch from LINE on, its button to run it from EXTENDED, its last three runs in SQUARE. A
 * touch, or a long one, opens its page, which holds its steps and its settings.
 */
@Composable
fun TaskTile(task: Task) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val actions = rememberTaskActions()
    val runs = rememberRuns(task.id, 3)
    val last = runs.firstOrNull()
    val open = { Navigator.push(Place.Task(task.id, task.zoneId)) }
    UI.LookCard(
        look = task.look,
        subtitle = s.shared("task_display_name"),
        seed = task.id,
        waiting = runs.any { it.waits },
        running = last != null && !last.state.ended,
        onClick = open,
        onLongClick = open,
        summary = { TaskStateLine(task, last) { actions.toggle(task, it) } },
        glance = { TaskStateLine(task, last) { actions.toggle(task, it) } },
        body = { rows ->
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    UI.ActionButton(action = ButtonAction.START, display = ButtonDisplay.ICON, size = Size.S, onClick = { actions.run(task) })
                    UI.ActionButton(action = ButtonAction.VIEW, display = ButtonDisplay.ICON, size = Size.S, onClick = open)
                }
                if (rows == null || rows > 1) runs.forEach { RunRow(it, task) }
            }
        }
    )
}
