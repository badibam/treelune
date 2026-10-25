package app.treelune.core.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.tasks.TaskRun
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.chatButtonSpace
import app.treelune.core.utils.FormatUtils
import kotlinx.coroutines.launch

/**
 * One run of a Task (Place.TaskRun): when and why it started, where it stands, then each step
 * reached — what it did, the values an API gave, the entry it created, why it failed, the start
 * of the raw answer. Opening it reads its end; a run going on or waiting can be cancelled.
 *
 * @param onTaskName Names its Task in the breadcrumb, opened from a notification with nothing under it
 */
@Composable
fun TaskRunScreen(place: Place.TaskRun, onTaskName: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var run by remember { mutableStateOf<Map<*, *>?>(null) }

    suspend fun load() {
        val result = coordinator.processUserAction("tasks.get_run", mapOf("run_id" to place.runId))
        if (result.status != CommandStatus.SUCCESS) {
            UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
            Navigator.drop(place)
            return
        }
        run = result.data?.get("run") as Map<*, *>
    }
    LaunchedEffect(place.runId) {
        coordinator.processUserAction("tasks.seen", mapOf("run_id" to place.runId))
        (coordinator.processUserAction("tasks.get", mapOf("task_id" to place.taskId)).data?.get("task") as? Map<*, *>)
            ?.let { onTaskName(app.treelune.core.tasks.Task.fromResult(it).name) }
        load()
        app.treelune.core.utils.DataChangeNotifier.changes.collect { if (it is app.treelune.core.utils.DataChangeEvent.ZonesChanged) load() }
    }
    val shown = run ?: run {
        UI.Text(s.shared("message_loading"), TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
        return
    }
    val state = TaskRun.State.of(shown["state"] as String)

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = stateLabel(state, s),
            subtitle = listOf(
                FormatUtils.formatRelativeTime((shown["started_at"] as Number).toLong(), context),
                s.shared("task_trigger_${shown["trigger"]}")
            ).joinToString(" · "),
            icon = null,
            leftButton = app.treelune.core.ui.ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = { }
        )
        (shown["next_poll_at"] as? Number)?.let { UI.Text(s.shared("task_run_next_poll").format(FormatUtils.formatRelativeTime(it.toLong(), context)), TextType.CAPTION) }
        if (!state.ended) UI.Button(type = ButtonType.DANGER, onClick = {
            scope.launch { coordinator.processUserAction("tasks.cancel", mapOf("run_id" to place.runId)); load() }
        }) { UI.Text(s.shared("task_run_cancel"), TextType.BODY) }

        (shown["results"] as List<*>).filterIsInstance<Map<*, *>>().forEachIndexed { index, result ->
            UI.Card(type = CardType.DEFAULT, size = Size.M) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    UI.Text(s.shared("task_step_line").format(index + 1, result["summary"]), TextType.STRONG)
                    (result["values"] as? Map<*, *>)?.forEach { (name, value) -> UI.Text(s.shared("label_value").format(name, value), TextType.BODY) }
                    (result["entry_id"] as? String)?.let { UI.Text(s.shared("task_run_entry_created"), TextType.CAPTION) }
                    (result["error"] as? String)?.let { UI.Text(it, TextType.ERROR) }
                    (result["raw"] as? String)?.let { raw ->
                        UI.Text(s.shared("api_try_raw"), TextType.LABEL)
                        UI.Text(raw, TextType.CAPTION)
                    }
                }
            }
        }
    }
}
