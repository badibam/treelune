package app.treelune.core.ai.ui.routine

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
import app.treelune.core.access.AccessMask
import app.treelune.core.ai.data.RoutineMessage
import app.treelune.core.ai.data.RoutineSettings
import app.treelune.core.ai.data.MessageSegment
import app.treelune.core.ai.data.RichMessage
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.ui.components.RichComposer
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.fields.settings.SettingDefaults
import app.treelune.core.fields.settings.SettingEditor
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.fields.settings.rememberSettingsPages
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * A routine's config (Place.RoutineConfig), as a tool's and a Task's: its look and its group,
 * then its message, written in blocks with its pointers, then the rest of its settings in their
 * sections (RoutineSettings.sections), the last ending with what its AI may reach. Its zone,
 * once it exists. Nothing exists before Save, which writes the settings, the access and the
 * message together, creating the routine when it has no id yet; leaving with changes not saved
 * asks first.
 *
 * @param onDone Called once the routine is saved, or left
 * @param onDeleted Called once the routine is deleted, its page gone with it
 */
@Composable
fun RoutineConfigScreen(place: Place.RoutineConfig, onDone: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val head = remember { RoutineSettings.head(context) }
    val sections = remember { RoutineSettings.sections(context) }
    val isEditing = place.routineId != null

    // A new routine starts from its declared defaults, its group chosen in its form; what it
    // does about missed runs goes with a schedule only, which it does not have yet
    var settings by rememberSaveable(stateSaver = JsonObjectSaver) {
        mutableStateOf(SettingDefaults.of(RoutineSettings.nodes(context)).apply {
            remove("catch_up")
        })
    }
    // The message in blocks (RoutineMessage) and the access, kept as text across a rotation
    var messageText by rememberSaveable { mutableStateOf("[]") }
    val segments = remember(messageText) { RoutineMessage.fromBlocks(JSONArray(messageText), s::shared).segments }
    var accessText by rememberSaveable { mutableStateOf(AccessMask().toJson().toString()) }
    val access = remember(accessText) { AccessMask.fromJson(accessText) }
    var zoneId by rememberSaveable { mutableStateOf(place.zoneId) }
    // All of it as read, to tell a change; and whether leaving with one is asked
    var asLoaded by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun snapshot() = JSONObject().put("settings", settings).put("message", JSONArray(messageText))
        .put("access", JSONArray(accessText)).put("zone_id", zoneId).toString()

    // The providers to choose among, and the identities its runs may speak as
    var providers by remember { mutableStateOf<List<Map<String, Any>>?>(null) }
    val identities = app.treelune.core.identities.ui.rememberIdentities()

    LaunchedEffect(place.routineId) {
        if (asLoaded != null) return@LaunchedEffect
        val listed = coordinator.processUserAction("ai_provider_config.list", emptyMap())
        @Suppress("UNCHECKED_CAST")
        providers = (listed.data?.get("providers") as? List<Map<String, Any>>).orEmpty()
        if (place.routineId != null) {
            val result = coordinator.processUserAction("routines.config", mapOf("routine_id" to place.routineId))
            if (result.status != CommandStatus.SUCCESS) {
                UI.Toast(result.error ?: s.shared("error_routine_not_found"), Duration.LONG)
                onDone()
                return@LaunchedEffect
            }
            @Suppress("UNCHECKED_CAST")
            settings = JsonUtils.toJSONObject(result.data?.get("settings") as Map<String, Any?>)
            messageText = JSONArray(result.data?.get("message") as List<*>).toString()
            accessText = JSONArray(result.data?.get("access") as List<*>).toString()
        } else {
            // A new routine starts with the default provider and its zone's identity, shown chosen
            val zone = coordinator.processUserAction("zones.get", mapOf("zone_id" to place.zoneId)).data?.get("zone") as? Map<*, *>
            settings = JSONObject(settings.toString()).apply {
                providers?.firstOrNull { it["is_default"] as? Boolean ?: false }?.let { put(RoutineSettings.PROVIDER_ID, it["id"] as String) }
                (zone?.get("identity_id") as? String)?.let { put(RoutineSettings.IDENTITY_ID, it) }
            }
        }
        asLoaded = snapshot()
    }

    // The groups of the zone it is in
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

    fun save() = scope.launch {
        // An empty message would leave the AI to make up what to do
        if (RichMessage(segments).isEmpty) {
            UI.Toast(s.shared("routine_message_empty"), Duration.LONG)
            return@launch
        }
        val provider = settings.optString(RoutineSettings.PROVIDER_ID).ifEmpty { null }
        // Without any provider, the routine is made off, to be given one later
        if (provider == null && !providers.isNullOrEmpty()) {
            UI.Toast(s.shared("error_param_provider_id_required"), Duration.LONG)
            return@launch
        }
        saving = true
        // Its provider and its identity always said, null for none: the service never takes a default
        val params = JsonUtils.toMap(settings) + mapOf(
            RoutineSettings.PROVIDER_ID to provider,
            RoutineSettings.IDENTITY_ID to settings.optString(RoutineSettings.IDENTITY_ID).ifEmpty { null },
            "message" to JsonUtils.toList(JSONArray(messageText)),
            "access" to JsonUtils.toList(JSONArray(accessText))
        )
        val result = if (place.routineId == null) {
            coordinator.processUserAction("routines.create", params + mapOf("zone_id" to zoneId) + (if (provider == null) mapOf("is_enabled" to false) else emptyMap()))
        } else {
            // A setting the form took out is given null, for the service to remove it
            val stored = JSONObject(asLoaded!!).getJSONObject("settings")
            val removed = stored.keys().asSequence().filter { !settings.has(it) }.associateWith { null }
            coordinator.processUserAction("routines.update", params + removed + buildMap {
                put("routine_id", place.routineId)
                if (zoneId != place.zoneId) put("zone_id", zoneId)
            })
        }
        saving = false
        if (result.status == CommandStatus.SUCCESS) {
            // Moved: its page and what leads to it now stand in its new zone
            if (place.routineId != null && zoneId != place.zoneId) app.treelune.core.navigation.Navigator.moved(
                app.treelune.core.navigation.Place.Routine(place.routineId, place.zoneId), app.treelune.core.navigation.Place.Routine(place.routineId, zoneId))
            onDone()
        }
        else UI.Toast(result.error ?: s.shared("routine_save_failed"), Duration.LONG)
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
            // The routine's name as saved, not as being typed; a new routine has none yet
            title = asLoaded?.let { JSONObject(it).getJSONObject("settings").optString("name") }?.takeIf { it.isNotBlank() } ?: s.shared("routine_new_name"),
            subtitle = s.shared("routine_display_name"),
            leftButton = ButtonAction.BACK,
            onLeftClick = { leave() }
        )
        if (asLoaded == null) {
            UI.Text(s.shared("message_loading"), TextType.BODY)
            return@Column
        }

        if (sectionPages.atRoot) {
            val editors = app.treelune.core.look.ui.lookEditors(RoutineSettings.LOOK, settings, s) { settings = it } + mapOf(
                "group" to app.treelune.core.tools.ui.groupEditor(groups, s.shared("label_group"))
            )
            SettingsForm(head, settings, { settings = it }, context, editors, scroll = scroll, pages = headPages) {
                // The zone is its place, not a setting: offered once the routine exists
                if (isEditing) app.treelune.core.ui.selectors.ZoneChoice(
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
            UI.Text(s.shared("routine_message"), TextType.HEADING)
            // The message, a seed session's template, written with its pointers; its periods stored as relative
            UI.RichComposer(
                segments = segments,
                onSegmentsChange = { next: List<MessageSegment> -> messageText = RoutineMessage.toBlocks(RichMessage(next)).toString() },
                onSend = { },
                placeholder = s.shared("routine_message_placeholder"),
                showEnrichmentButtons = true,
                showSendButton = false,
                sessionType = SessionType.SEED,
                // The page scrolls as a whole: the message takes the height it needs
                boundedBlocks = false
            )
        }

        SettingsForm(sections, settings, { settings = it }, context, providerEditors(providers, identities, s), scroll = scroll, pages = sectionPages)
        // The actions on the root pages alone: under one, the back arrow goes up (SettingsPages)
        if (!sectionPages.atRoot) return@Column

        app.treelune.core.access.ui.AccessCard(s.shared("routine_access_title"), s.shared("routine_access_help"), access) { accessText = it.toJson().toString() }

        UI.ToolConfigActions(
            isEditing = isEditing,
            onSave = { save() },
            onCancel = { leave() },
            // The button asks for confirmation itself
            onDelete = place.routineId?.let { routineId ->
                {
                    scope.launch {
                        val result = coordinator.processUserAction("routines.delete", mapOf("routine_id" to routineId))
                        if (result.status == CommandStatus.SUCCESS) onDeleted() else UI.Toast(result.error ?: s.shared("error_routine_delete_failed"), Duration.LONG)
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

/** Its provider among the user's (none yet said as such), and the identity its runs speak as, the app's AI for none. */
private fun providerEditors(
    providers: List<Map<String, Any>>?,
    identities: List<app.treelune.core.identities.ui.IdentityChoice>?,
    s: app.treelune.core.strings.StringsContext
): Map<String, SettingEditor> = mapOf(
    RoutineSettings.PROVIDER_ID to object : SettingEditor {
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            when {
                providers == null -> UI.Text(s.shared("message_loading"), TextType.CAPTION)
                providers.isEmpty() -> UI.Text(s.shared("routine_no_provider_yet"), TextType.CAPTION)
                else -> {
                    val names = providers.associate { app.treelune.core.ai.ui.screens.providerLabel(it) to (it["id"] as String) }
                    UI.FormSelection(
                        label = s.shared("label_ai_provider"),
                        options = names.keys.toList(),
                        selected = names.entries.find { it.value == value }?.key ?: "",
                        onSelect = { onChange(names[it]) },
                        required = true
                    )
                }
            }
        }
    },
    RoutineSettings.IDENTITY_ID to object : SettingEditor {
        @Composable
        override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
            app.treelune.core.identities.ui.IdentitySelector(identities = identities.orEmpty(), selected = value as? String,
                onSelect = { onChange(it) }, label = s.shared("label_identity"), noneLabel = s.shared("identity_app_ai"))
        }
    }
)
