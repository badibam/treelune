package app.treelune.core.tasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.fields.settings.SettingDefaults
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.fields.settings.rememberSettingsPages
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.tasks.Task
import app.treelune.core.tasks.TaskSettings
import app.treelune.core.tasks.TaskSteps
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Task's config (Place.TaskConfig), as a tool's: its look and its group, then its steps, each
 * summed up in a line and opened in place to be edited, added by their kind, moved and removed,
 * then the rest of its settings in their sections (TaskSettings.sections). Nothing exists before
 * Save, which writes the settings and the steps together, creating the Task when it has no id
 * yet; leaving with changes not saved asks first.
 *
 * @param onDone Called once the Task is saved, or left
 * @param onDeleted Called once the Task is deleted, its page gone with it
 */
@Composable
fun TaskConfigScreen(place: Place.TaskConfig, onDone: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val head = remember { TaskSettings.head(context) }
    val sections = remember { TaskSettings.sections(context) }

    // A new Task starts from its declared defaults, in the group it was asked for; what it does
    // about missed runs goes with a schedule only, which it does not have yet
    var settings by rememberSaveable(stateSaver = JsonObjectSaver) {
        mutableStateOf(SettingDefaults.of(TaskSettings.nodes(context)).apply {
            remove("catch_up")
            place.group?.let { put("group", it) }
        })
    }
    var stepsText by rememberSaveable { mutableStateOf("[]") }
    val steps = remember(stepsText) { JSONArray(stepsText) }
    var zoneId by rememberSaveable { mutableStateOf(place.zoneId) }
    // The settings and the steps as read, to tell a change; and whether leaving with one is asked
    var asLoaded by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var outputs by remember { mutableStateOf<List<List<TaskSteps.Output>>>(emptyList()) }
    var received by remember { mutableStateOf(Receivable(null, null)) }

    fun snapshot() = JSONObject().put("settings", settings).put("steps", steps).put("zone_id", zoneId).toString()

    LaunchedEffect(place.taskId) {
        if (asLoaded != null) return@LaunchedEffect
        if (place.taskId != null) {
            val result = coordinator.processUserAction("tasks.get", mapOf("task_id" to place.taskId))
            if (result.status != CommandStatus.SUCCESS) {
                UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
                onDone()
                return@LaunchedEffect
            }
            val read = Task.fromResult(result.data?.get("task") as Map<*, *>)
            settings = JSONObject(read.settings.toString())
            stepsText = read.steps.toString()
        }
        asLoaded = snapshot()
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
    LaunchedEffect(stepsText) {
        val result = coordinator.processUserAction("tasks.outputs", mapOf("steps" to JsonUtils.toList(steps)))
        outputs = (result.data?.get("outputs") as? List<*>).orEmpty().map { list ->
            (list as List<*>).map { it as Map<*, *> }.map { TaskSteps.Output(it["value"] as String?, it["type"] as String) }
        }
    }

    // The groups of the zone the Task is in
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(zoneId) {
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
        groups = ((result.data?.get("zone") as? Map<*, *>)?.get("tool_groups") as? List<*>).orEmpty().mapNotNull { it as? String }
    }

    // Compared as values: an object rebuilt by the form may hold its keys in another order
    val changed = asLoaded?.let { JsonUtils.toMap(JSONObject(snapshot())) != JsonUtils.toMap(JSONObject(it)) } == true
    fun leave() = if (changed) leaving = true else onDone()
    // Before the forms' own, which go up their pages first
    androidx.activity.compose.BackHandler(enabled = changed) { leaving = true }

    fun edit(next: JSONArray) {
        stepsText = next.toString()
    }
    fun save() = scope.launch {
        saving = true
        val params = JsonUtils.toMap(settings) + mapOf("steps" to JsonUtils.toList(steps))
        val result = if (place.taskId == null) {
            coordinator.processUserAction("tasks.create", params + mapOf("zone_id" to place.zoneId))
        } else {
            // A setting the form took out is given null, for the service to remove it
            val stored = JSONObject(asLoaded!!).getJSONObject("settings")
            val removed = stored.keys().asSequence().filter { !settings.has(it) }.associateWith { null }
            coordinator.processUserAction("tasks.update", params + removed + buildMap {
                put("task_id", place.taskId)
                if (zoneId != place.zoneId) put("zone_id", zoneId)
            })
        }
        saving = false
        if (result.status == CommandStatus.SUCCESS) onDone()
        else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
    }

    val scroll = rememberScrollState()
    // Each part of the settings opens its pages in its own place; one open, the rest waits for its return
    val headPages = rememberSettingsPages()
    val sectionPages = rememberSettingsPages()
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.L).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            // The Task's name as saved, not as being typed; a new Task has none yet
            title = asLoaded?.let { JSONObject(it).getJSONObject("settings").optString("name") }?.takeIf { it.isNotBlank() } ?: s.shared("task_new_name"),
            subtitle = s.shared("task_display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = { leave() }
        )
        if (asLoaded == null) {
            UI.Text(s.shared("message_loading"), TextType.BODY)
            return@Column
        }

        if (sectionPages.atRoot) {
            val editors = app.treelune.core.look.ui.lookEditors(TaskSettings.LOOK, settings, s) { settings = it } + mapOf(
                "group" to app.treelune.core.tools.ui.groupEditor(groups, s.shared("label_group"))
            )
            SettingsForm(head, settings, { settings = it }, context, editors, scroll = scroll, pages = headPages) {
                // The zone is its place, not a setting: offered once the Task exists
                if (place.taskId != null) app.treelune.core.ui.selectors.ZoneChoice(
                    label = s.shared("label_zone"),
                    selected = zoneId,
                    onSelect = { id ->
                        if (id != zoneId) {
                            zoneId = id
                            // Its group stays in the zone it leaves: the selector offers the new zone's
                            settings = JSONObject(settings.toString()).apply { remove("group") }
                        }
                    },
                    required = false
                )
            }
        }
        if (!headPages.atRoot) return@Column

        if (sectionPages.atRoot) {
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
                            open = steps.length()
                            adding = false
                        }) { UI.Text(s.shared("task_kind_$kind"), TextType.BODY) }
                    }
                }
            } else UI.Button(type = ButtonType.SECONDARY, onClick = { adding = true }) { UI.Text(s.shared("task_step_add"), TextType.BODY) }
        }

        SettingsForm(sections, settings, { settings = it }, context, scroll = scroll, pages = sectionPages)
        // The actions on the root pages alone: under one, the back arrow goes up (SettingsPages)
        if (!sectionPages.atRoot) return@Column

        UI.ToolConfigActions(
            isEditing = place.taskId != null,
            onSave = { save() },
            onCancel = { leave() },
            // The button asks for confirmation itself
            onDelete = place.taskId?.let { taskId ->
                {
                    scope.launch {
                        val result = coordinator.processUserAction("tasks.delete", mapOf("task_id" to taskId))
                        if (result.status == CommandStatus.SUCCESS) onDeleted() else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
                    }
                }
            },
            saveEnabled = !saving
        )
    }

    if (leaving) UI.Dialog(type = app.treelune.core.ui.DialogType.CONFIRM, onConfirm = { leaving = false; onDone() }, onCancel = { leaving = false }) {
        UI.Text(s.shared("settings_leave_unsaved"), TextType.BODY)
    }
}

/** [steps] with the step at [from] put at [to]. */
private fun moved(steps: JSONArray, from: Int, to: Int): JSONArray {
    val list = (0 until steps.length()).map { steps.get(it) }.toMutableList()
    list.add(to, list.removeAt(from))
    return JSONArray(list)
}
