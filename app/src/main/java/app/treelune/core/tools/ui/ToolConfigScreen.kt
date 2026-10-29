package app.treelune.core.tools.ui

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
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.coordinator.mapSingleData
import app.treelune.core.fields.settings.SettingEditor
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeContract
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.LoadState
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.GroupSelector
import app.treelune.core.ui.components.IconSelector
import app.treelune.core.ui.rememberLoadOnce
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import app.treelune.core.ui.ButtonType

/**
 * The config screen of any tool (docs/DATA.md): the form of its
 * declaration (ToolConfigSettings), with the parts the core draws itself: the icon picker, the
 * zone's tool groups.
 *
 * It saves through the service and shows its refusal. A change that loses recorded data is
 * refused by the service until the user agrees: the screen shows what it costs, and sends it
 * again with the agreement.
 *
 * @param existingToolId The tool being edited; null to create one in [zoneId]
 * @param onDone Called once the tool is saved or deleted
 */
@Composable
fun ToolConfigScreen(
    toolType: ToolTypeContract,
    tooltype: String,
    zoneId: String,
    existingToolId: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val nodes = remember(toolType) { ToolConfigSettings.nodes(toolType, context) }
    val isEditing = existingToolId != null

    // A new tool starts from its declared defaults, in the group it was asked for
    var config by rememberSaveable(stateSaver = JsonObjectSaver) {
        mutableStateOf(ToolConfigSettings.defaults(toolType, context))
    }
    var currentZoneId by rememberSaveable { mutableStateOf(zoneId) }
    var isSaving by remember { mutableStateOf(false) }
    // What the change costs, once the service refused it for that: values removed, entries deleted
    // What the save would do to the recorded entries, as the service counted it (its "migration")
    var pendingMigration by rememberSaveable { mutableStateOf<String?>(null) }
    // The fields the change makes required, with the number of entries without a value for each,
    // once the service refused it for that; and the values the user gives them
    var pendingFill by rememberSaveable { mutableStateOf<Map<String, Int>?>(null) }
    var fill by rememberSaveable(stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    // The config as it was before any change, and whether leaving with changes is being asked
    var asLoaded by rememberSaveable { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }

    val configLoad = rememberLoadOnce(existingToolId) {
        if (existingToolId == null) return@rememberLoadOnce true
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to existingToolId))
        @Suppress("UNCHECKED_CAST")
        val loaded = result.mapSingleData("tool_instance") { it }?.get("config") as? Map<String, Any?>
        if (!result.isSuccess || loaded == null) {
            UI.Toast(result.error ?: s.shared("tools_config_error_not_found"), Duration.LONG)
            return@rememberLoadOnce false
        }
        config = JsonUtils.toJSONObject(loaded)
        true
    }

    // The groups of the zone the tool is in
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(currentZoneId) {
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to currentZoneId))
        groups = ((result.data?.get("zone") as? Map<*, *>)?.get("tool_groups") as? List<*>).orEmpty().mapNotNull { it as? String }
    }

    fun save(confirmed: Boolean) {
        isSaving = true
        scope.launch {
            val configMap = JsonUtils.toMap(config)
            val result = if (existingToolId != null) {
                coordinator.processUserAction("tools.update", buildMap {
                    put("tool_instance_id", existingToolId)
                    put("config", configMap)
                    if (currentZoneId != zoneId) put("zone_id", currentZoneId)
                    if (confirmed) put("confirm_migration", true)
                    if (fill.length() > 0) put("fill_values", mapOf("data" to JsonUtils.toMap(fill)))
                })
            } else {
                coordinator.processUserAction("tools.create", mapOf("zone_id" to currentZoneId, "tooltype" to tooltype, "config" to configMap))
            }
            isSaving = false
            val migration = result.data?.get("migration") as? Map<*, *>
            val missing = ((migration?.get("missing_values") as? Map<*, *>)?.get("data") as? Map<*, *>).orEmpty()
                .map { (field, count) -> field.toString() to (count as Number).toInt() }.toMap()
            when {
                result.isSuccess -> {
                    // Moved: its page and what leads to it now stand in its new zone
                    if (existingToolId != null && currentZoneId != zoneId) app.treelune.core.navigation.Navigator.moved(
                        app.treelune.core.navigation.Place.Tool(existingToolId, zoneId), app.treelune.core.navigation.Place.Tool(existingToolId, currentZoneId))
                    onDone()
                }
                // Refused for values the user can give: asked for them
                missing.isNotEmpty() -> pendingFill = missing
                // Refused for what it loses, and for nothing else: the user decides
                migration != null && !confirmed -> pendingMigration = JsonUtils.toJSONObject(migration.entries.associate { it.key.toString() to it.value }).toString()
                else -> UI.Toast(result.error ?: s.shared("tools_config_error_save"), Duration.LONG)
            }
        }
    }

    LaunchedEffect(configLoad) {
        if (configLoad == LoadState.LOADED && asLoaded == null) asLoaded = config.toString()
    }
    // Compared as values: an object rebuilt by the form may hold its keys in another order
    val changed = asLoaded?.let { JsonUtils.toMap(config) != JsonUtils.toMap(JSONObject(it)) || currentZoneId != zoneId } == true
    fun leave() = if (changed) leaving = true else onCancel()
    // Before the form's own, which goes up its pages first
    androidx.activity.compose.BackHandler(enabled = changed) { leaving = true }

    if (configLoad == LoadState.LOADING) {
        UI.Text(s.shared("tools_loading_config"), TextType.BODY)
        return
    }

    // Its icon, colour and texture, as every look's (Looks), shown on its type's icon for none
    val editors = app.treelune.core.look.ui.lookEditors(app.treelune.core.tools.ToolConfigSettings.look(toolType), config, s) { config = it } + mapOf(
        "group" to groupEditor(groups, s.shared("label_group"))
    )

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = UI.Space.L)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            // The tool's name as saved, not as being typed; a new tool has none yet
            title = asLoaded?.let { JSONObject(it).optString("name") }?.takeIf { it.isNotBlank() } ?: s.shared("tools_config_new_tool"),
            subtitle = toolType.getDisplayName(context),
            leftButton = ButtonAction.BACK,
            onLeftClick = { leave() }
        )

        // Values given for a former version of the change answer nothing about this one
        val pages = app.treelune.core.fields.settings.rememberSettingsPages()
        SettingsForm(nodes, config, { config = it; fill = JSONObject() }, context, editors, rows = toolType.getRowFields(), scroll = scroll, pages = pages) {
            // The zone is the tool's place, not a setting of its config: offered once the tool
            // exists, on the root page alone
            if (isEditing) {
                app.treelune.core.ui.selectors.ZoneChoice(
                    label = s.shared("label_zone"),
                    selected = currentZoneId,
                    onSelect = { id ->
                        if (id != currentZoneId) {
                            currentZoneId = id
                            // Its group stays in the zone it leaves: the selector offers the new zone's
                            config = JSONObject(config.toString()).apply { remove("group") }
                        }
                    },
                    required = false
                )
            }
        }

        // The actions on the root page alone: under it, the back arrow goes up (SettingsPages)
        if (pages.atRoot) UI.ToolConfigActions(
            isEditing = isEditing,
            onSave = { save(confirmed = false) },
            onCancel = { leave() },
            // The button asks for confirmation itself
            onDelete = existingToolId?.let { toolId ->
                {
                    scope.launch {
                        val result = coordinator.processUserAction("tools.delete", mapOf("tool_instance_id" to toolId))
                        if (result.isSuccess) onDone()
                        else UI.Toast(result.error ?: s.shared("tools_config_error_save"), Duration.LONG)
                    }
                }
            },
            saveEnabled = !isSaving && configLoad == LoadState.LOADED,
            // The tool named as it is stored, its entries going with it
            deleteMessage = s.shared("tools_delete_confirm").format(asLoaded?.let { JSONObject(it).optString("name") }.orEmpty())
        )
        // Acts on the tool's entries, not its config: a tool being created has none
        if (pages.atRoot && existingToolId != null) UI.Button(type = ButtonType.SECONDARY, onClick = { exporting = true }) {
            UI.Text(s.shared("tools_config_export_entries"), TextType.LABEL)
        }
    }

    if (exporting && existingToolId != null) app.treelune.core.ui.exports.EntriesExportDialog(existingToolId) { exporting = false }

    if (leaving) UI.Dialog(
        type = app.treelune.core.ui.DialogType.CONFIRM,
        onConfirm = { leaving = false; onCancel() },
        onCancel = { leaving = false }
    ) {
        UI.Text(s.shared("settings_leave_unsaved"), TextType.BODY)
    }

    pendingFill?.let { missing ->
        // The fields as the new config makes them, for their inputs
        val fields = remember(config) { toolType.getEntryFields(config, context).data.associateBy { it.definition.name } }
        // A field's default value is proposed, once, for the entries that lack a value
        LaunchedEffect(missing) {
            val defaults = missing.keys.mapNotNull { name -> fields[name]?.definition?.defaultValue?.let { name to it } }
                .filter { (name, _) -> !fill.has(name) }
            if (defaults.isNotEmpty()) fill = JSONObject(fill.toString()).apply { defaults.forEach { (name, value) -> put(name, JSONObject.wrap(value)) } }
        }
        UI.Dialog(
            type = app.treelune.core.ui.DialogType.CONFIRM,
            onConfirm = {
                pendingFill = null
                save(confirmed = false)
            },
            onCancel = { pendingFill = null },
            confirmEnabled = missing.keys.all { fill.has(it) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("migration_fill_title"), TextType.SUBTITLE)
                missing.forEach { (name, count) ->
                    val field = fields[name]?.definition ?: return@forEach
                    UI.Text(s.shared("migration_fill_message").format(count, field.displayName), TextType.BODY)
                    app.treelune.core.fields.FieldInput(field, fill.opt(name)?.takeIf { it != JSONObject.NULL }, { value ->
                        fill = JSONObject(fill.toString()).apply { if (value == null) remove(name) else put(name, value) }
                    }, context)
                }
            }
        }
    }

    pendingMigration?.let { json ->
        val migration = remember(json) { JSONObject(json) }
        // The user's fields whose meaning changes, and the names the former ones would take
        val changedMeaning = remember(json) { migration.optJSONArray("changed_meaning")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty() }
        val before = remember(asLoaded) { asLoaded?.let { JSONObject(it).optJSONArray("extra_fields") } ?: JSONArray() }
        val today = remember { app.treelune.core.utils.DateUtils.formatDateForDisplay(System.currentTimeMillis()) }
        fun keptName(name: String) = s.shared("migration_kept_name").format(name, today)
        val keptNames = changedMeaning.mapNotNull { key ->
            (0 until before.length()).map { before.getJSONObject(it) }.firstOrNull { it.optString("name") == key }?.optString("display_name")?.let { s.shared("quoted").format(keptName(it)) }
        }
        UI.Dialog(
            type = app.treelune.core.ui.DialogType.CONFIRM,
            onConfirm = {
                pendingMigration = null
                save(confirmed = true)
            },
            onCancel = { pendingMigration = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("migration_confirm_title"), TextType.SUBTITLE)
                // Only what it does: each count that is not zero, each reason a rule of the tool gives
                val parts = app.treelune.core.fields.migration.MigrationCounts.parts(JsonUtils.toMap(migration), s::shared)
                if (parts.isNotEmpty()) UI.Text(s.shared("migration_confirm_counts").format(parts.joinToString(", ")), TextType.BODY)
                // The other tools whose filters the change leaves aiming at an option gone
                val aimedBy = migration.optJSONArray("aimed_by")?.let { a -> (0 until a.length()).map { s.shared("quoted").format(a.getString(it)) } }.orEmpty()
                if (aimedBy.isNotEmpty()) UI.Text(s.shared("migration_filters_aimed").format(aimedBy.joinToString(", ")), TextType.BODY)
                // A change of meaning can leave the past alone: the edited field takes a new key
                if (keptNames.isNotEmpty()) {
                    UI.Text(s.shared("migration_new_field_offer").format(keptNames.joinToString(", ")), TextType.BODY)
                    UI.Button(type = ButtonType.DEFAULT, onClick = {
                        pendingMigration = null
                        config = JSONObject(config.toString()).put("extra_fields",
                            app.treelune.core.fields.migration.NewFieldInstead.apply(before, config.optJSONArray("extra_fields") ?: JSONArray(), changedMeaning, ::keptName))
                        save(confirmed = false)
                    }) {
                        UI.Text(s.shared("migration_new_field"), TextType.LABEL)
                    }
                }
            }
        }
    }
}

/** The groups of the zone the tool (or the Task) is in, or none. */
internal fun groupEditor(groups: List<String>, label: String) = object : SettingEditor {
    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        GroupSelector(availableGroups = groups, selectedGroup = value as? String, onGroupSelected = { onChange(it) }, label = label)
    }
}
