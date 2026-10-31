package app.treelune.core.readouts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.fields.settings.SettingDefaults
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.fields.settings.rememberSettingsPages
import app.treelune.core.navigation.Place
import app.treelune.core.readouts.ReadoutRows
import app.treelune.core.readouts.ReadoutSettings
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A readout's config (Place.ReadoutConfig), as a Task's: its look and its group, then its sentence,
 * its values, what it opens, its colours and the places it rises to (ReadoutSettings), its colours'
 * conditions comparing its values (ReadoutRows). Nothing exists before Save. A new readout's target
 * is prefilled, while it has none, with the tool its first reading reads: shown, and changed at
 * will. Leaving with changes not saved asks first.
 *
 * @param onDone Called once the readout is saved, deleted, or left
 */
@Composable
fun ReadoutConfigScreen(place: Place.ReadoutConfig, onDone: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val nodes = remember { ReadoutSettings.nodes(context) }

    var settings by rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(SettingDefaults.of(nodes)) }
    var asLoaded by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(place.readoutId) {
        if (asLoaded != null) return@LaunchedEffect
        if (place.readoutId != null) {
            val result = coordinator.processUserAction("readouts.get", mapOf("readout_id" to place.readoutId))
            if (result.status != CommandStatus.SUCCESS) {
                UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
                onDone()
                return@LaunchedEffect
            }
            settings = JsonUtils.toJSONObject(((result.data?.get("readout") as Map<*, *>)["settings"] as Map<*, *>).entries.associate { it.key.toString() to it.value })
        }
        asLoaded = settings.toString()
    }

    // A new readout opens, until one is chosen, the tool its first reading reads
    val firstTool = settings.optJSONArray(ReadoutSettings.VALUES)?.let { values ->
        (0 until values.length()).firstNotNullOfOrNull { i ->
            values.optJSONObject(i)?.optJSONObject(ReadoutSettings.VALUE)?.optJSONObject("reading")?.optJSONObject("selection")?.optJSONObject("target")
                ?.takeIf { it.optString("kind") == "TOOL_INSTANCE" && it.optString("id").isNotEmpty() }
        }
    }?.toString()
    LaunchedEffect(firstTool) {
        if (place.readoutId != null || firstTool == null || settings.has(ReadoutSettings.TARGET)) return@LaunchedEffect
        settings = JSONObject(settings.toString()).put(ReadoutSettings.TARGET, JSONObject(firstTool))
    }

    // The groups of its zone
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(place.zoneId) {
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to place.zoneId))
        groups = ((result.data?.get("zone") as? Map<*, *>)?.get("tool_groups") as? List<*>).orEmpty().mapNotNull { it as? String }
    }

    // Compared as values: an object rebuilt by the form may hold its keys in another order
    val changed = asLoaded?.let { JsonUtils.toMap(settings) != JsonUtils.toMap(JSONObject(it)) } == true
    fun leave() = if (changed) leaving = true else onDone()
    androidx.activity.compose.BackHandler(enabled = changed) { leaving = true }

    fun save() = scope.launch {
        saving = true
        val params = JsonUtils.toMap(settings)
        val result = if (place.readoutId == null) {
            coordinator.processUserAction("readouts.create", params + mapOf("zone_id" to place.zoneId))
        } else {
            // A setting the form took out is given null, for the service to remove it
            val stored = JSONObject(asLoaded!!)
            val removed = stored.keys().asSequence().filter { !settings.has(it) }.associateWith { null }
            coordinator.processUserAction("readouts.update", params + removed + mapOf("readout_id" to place.readoutId))
        }
        saving = false
        if (result.status == CommandStatus.SUCCESS) onDone() else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
    }

    val scroll = rememberScrollState()
    val pages = rememberSettingsPages()
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.L).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            // Its name as saved, not as being typed; a new readout has none yet
            title = asLoaded?.let { JSONObject(it).optString("name") }?.takeIf { it.isNotBlank() } ?: s.shared("readout_new_name"),
            subtitle = s.shared("readout_display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = { leave() }
        )
        if (asLoaded == null) {
            UI.Text(s.shared("message_loading"), TextType.BODY)
            return@Column
        }
        val editors = app.treelune.core.look.ui.lookEditors(ReadoutSettings.LOOK, settings, s) { settings = it } + mapOf(
            "group" to app.treelune.core.tools.ui.groupEditor(groups, s.shared("label_group"))
        )
        SettingsForm(nodes, settings, { settings = it }, context, editors, rows = ReadoutRows, scroll = scroll, pages = pages)
        // The actions on the root page alone: under one, the back arrow goes up (SettingsPages)
        if (!pages.atRoot) return@Column

        UI.ToolConfigActions(
            isEditing = place.readoutId != null,
            onSave = { save() },
            onCancel = { leave() },
            // The button asks for confirmation itself
            onDelete = place.readoutId?.let { readoutId ->
                {
                    scope.launch {
                        val result = coordinator.processUserAction("readouts.delete", mapOf("readout_id" to readoutId))
                        if (result.status == CommandStatus.SUCCESS) onDone() else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
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
