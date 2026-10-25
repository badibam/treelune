package app.treelune.core.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.tasks.Task
import app.treelune.core.tasks.TaskSettings
import app.treelune.core.tasks.TaskSteps
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.chatButtonSpace
import app.treelune.core.ui.Duration
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Task's page (Place.Task): its state, its switch and its button to run it; its steps, each
 * summed up in a line and opened in place to be edited, added by their kind, moved and removed;
 * its settings; its last runs, each opening. Its steps and its settings are written together by
 * Save, which the service checks; leaving with changes not saved asks first.
 */
@Composable
fun TaskScreen(place: Place.Task, onName: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val actions = rememberTaskActions()

    var task by remember { mutableStateOf<Task?>(null) }
    var steps by remember { mutableStateOf(JSONArray()) }
    var settings by remember { mutableStateOf(JSONObject()) }
    var changed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var outputs by remember { mutableStateOf<List<List<app.treelune.core.tasks.TaskSteps.Output>>>(emptyList()) }
    var received by remember { mutableStateOf(Receivable(null, null)) }

    suspend fun load() {
        val result = coordinator.processUserAction("tasks.get", mapOf("task_id" to place.id))
        if (result.status != CommandStatus.SUCCESS) {
            UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
            Navigator.drop(place)
            return
        }
        val read = Task.fromResult(result.data?.get("task") as Map<*, *>)
        task = read
        if (!changed) {
            steps = JSONArray(read.steps.toString())
            settings = JSONObject(read.settings.toString())
        }
    }
    LaunchedEffect(place.id) {
        load()
        app.treelune.core.utils.DataChangeNotifier.changes.collect { if (it is app.treelune.core.utils.DataChangeEvent.ZonesChanged) load() }
    }
    // The fields of the entry a trigger gives, read again as the triggers change
    val triggers = app.treelune.core.triggers.Triggers.of(settings)?.toString()
    LaunchedEffect(triggers) {
        received = try {
            val read = app.treelune.core.triggers.Triggers.read(triggers)
            Receivable(TaskSteps.receivedFields(read, context, s), TaskSteps.receivedValues(read, context))
        } catch (e: IllegalArgumentException) {
            // A trigger the form is still filling gives nothing yet
            Receivable(null, null)
        }
    }
    // What each step gives the ones after it, read again as the steps change
    LaunchedEffect(steps.toString()) {
        val result = coordinator.processUserAction("tasks.outputs", mapOf("steps" to JsonUtils.toList(steps)))
        outputs = (result.data?.get("outputs") as? List<*>).orEmpty().map { list ->
            (list as List<*>).map { it as Map<*, *> }.map { TaskSteps.Output(it["value"] as String?, it["type"] as String) }
        }
    }

    val shown = task ?: run {
        UI.Text(s.shared("message_loading"), TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
        return
    }
    onName(shown.name)
    val leave = { if (changed) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    fun edit(next: JSONArray) {
        steps = next
        changed = true
    }
    fun save() = scope.launch {
        val params = JsonUtils.toMap(settings) + mapOf("task_id" to shown.id, "steps" to JsonUtils.toList(steps))
        // A setting the form took out is given null, for the service to remove it
        val removed = shown.settings.keys().asSequence().filter { !settings.has(it) }.associateWith { null }
        val result = coordinator.processUserAction("tasks.update", params + removed)
        if (result.status == CommandStatus.SUCCESS) {
            changed = false
            UI.Toast(s.shared("task_saved"), Duration.SHORT)
            load()
        } else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = shown.name,
            subtitle = s.shared("task_display_name"),
            icon = shown.look.iconName,
            iconColor = app.treelune.core.themes.IconColor.of(shown.look.iconColor),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = leave,
            onRightClick = { settingsOpen = !settingsOpen }
        )

        val runs = rememberRuns(shown.id, 20)
        UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                TaskStateLine(shown, runs.firstOrNull()) { actions.toggle(shown, it) }
                UI.Button(type = ButtonType.PRIMARY, onClick = { if (changed) UI.Toast(s.shared("task_save_first"), Duration.SHORT) else actions.run(shown) }) {
                    UI.Text(s.shared("task_run_now"), TextType.BODY)
                }
            }
        }

        if (settingsOpen) UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
                SettingsForm(remember { TaskSettings.nodes(context) }, settings, { settings = it; changed = true }, context)
            }
        }

        UI.Text(s.shared("task_steps"), TextType.HEADING)
        if (steps.length() == 0) UI.Text(s.shared("task_steps_empty"), TextType.CAPTION)
        for (index in 0 until steps.length()) {
            val step = steps.getJSONObject(index)
            UI.Card(type = CardType.DEFAULT, size = Size.M) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                        Box(modifier = Modifier.weight(1f).clickable { open = if (open == index) null else index }) {
                            UI.Text(s.shared("task_step_line").format(index + 1, s.shared("task_kind_${step.optString(TaskSteps.KIND)}")), TextType.STRONG)
                        }
                        if (index > 0) UI.ActionButton(action = ButtonAction.UP, display = ButtonDisplay.ICON, size = Size.S, onClick = { edit(moved(steps, index, index - 1)); open = null })
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, requireConfirmation = true, onClick = {
                            edit(JSONArray().apply { for (i in 0 until steps.length()) if (i != index) put(steps.get(i)) })
                            open = null
                        })
                    }
                    if (open == index) {
                        val earlier = (0 until index).map { it to outputs.getOrElse(it) { emptyList() } }
                        androidx.compose.runtime.CompositionLocalProvider(LocalReceived provides received) {
                            StepEditor(step, earlier) { next -> edit(JSONArray(steps.toString()).put(index, next)) }
                        }
                    }
                }
            }
        }
        if (adding) UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(s.shared("task_step_add_kind"), TextType.LABEL)
                TaskSteps.KINDS.forEach { kind ->
                    UI.Button(type = ButtonType.SECONDARY, onClick = {
                        edit(JSONArray(steps.toString()).put(JSONObject().put(TaskSteps.KIND, kind)))
                        open = steps.length() - 1
                        adding = false
                    }) { UI.Text(s.shared("task_kind_$kind"), TextType.BODY) }
                }
            }
        } else UI.Button(type = ButtonType.SECONDARY, onClick = { adding = true }) { UI.Text(s.shared("task_step_add"), TextType.BODY) }

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.SAVE, onClick = { save() })
            UI.ActionButton(action = ButtonAction.DELETE, requireConfirmation = true, onClick = {
                scope.launch {
                    val result = coordinator.processUserAction("tasks.delete", mapOf("task_id" to shown.id))
                    if (result.status == CommandStatus.SUCCESS) Navigator.drop(place) else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
                }
            })
        }

        UI.Text(s.shared("task_runs"), TextType.HEADING)
        if (runs.isEmpty()) UI.Text(s.shared("task_runs_empty"), TextType.CAPTION)
        runs.forEach { RunRow(it, shown) }
    }

    if (confirmLeave) UI.Dialog(type = app.treelune.core.ui.DialogType.CONFIRM, onConfirm = { confirmLeave = false; onBack() }, onCancel = { confirmLeave = false }) {
        UI.Text(s.shared("task_leave_unsaved"), TextType.BODY)
    }
}

/** [steps] with the step at [from] put at [to]. */
private fun moved(steps: JSONArray, from: Int, to: Int): JSONArray {
    val list = (0 until steps.length()).map { steps.get(it) }.toMutableList()
    list.add(to, list.removeAt(from))
    return JSONArray(list)
}
