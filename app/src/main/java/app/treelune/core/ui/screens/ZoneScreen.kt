package app.treelune.core.ui.screens

import app.treelune.core.utils.JsonUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.ui.UI
import app.treelune.core.ui.*
import app.treelune.core.strings.Strings
import app.treelune.core.database.entities.Zone
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.grid.ToolPositions
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.mapData
import app.treelune.core.coordinator.executeWithLoading
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Zone detail screen - shows zone information and its tools
 * Uses hybrid system: Compose layouts + UI.* visual components
 */
@Composable
fun ZoneScreen(zone: Zone) {
    val onBack = { Navigator.pop() }
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load tool instances via command pattern
    var toolInstances by remember { mutableStateOf<List<ToolInstance>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load automations for this zone
    var automations by remember { mutableStateOf<List<app.treelune.core.ai.data.Automation>>(emptyList()) }
    var isLoadingAutomations by remember { mutableStateOf(true) }
    // The zone's Tasks, among its tools as its routines are
    var tasks by remember { mutableStateOf<List<app.treelune.core.tasks.Task>>(emptyList()) }

    // The zone shows "loading" until its tools and automations are first read. A later reload
    // (an automation switched on, a tool changed or duplicated) keeps the content on screen and
    // updates it when the new data arrives: replaced by "loading", the whole screen would blink.
    var loadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, isLoadingAutomations) {
        if (!isLoading && !isLoadingAutomations) loadedOnce = true
    }
    
    // State for showing/hiding available tools list - persiste orientation changes
    var showAvailableToolsForGroup by rememberSaveable { mutableStateOf<String?>(null) } // null = hidden, "" = ungrouped, "group_name" = specific group

    val waiting = app.treelune.core.ui.LocalWaiting.current
    // A tool opened on top of the zone, on the oldest entry that waits when its tile was touched with one
    val openTool = { toolId: String, entry: app.treelune.core.tools.EntryToOpen? -> Navigator.push(Place.Tool(toolId, zone.id), entry) }
    val configureTool = { tool: ToolInstance -> Navigator.push(Place.ToolConfig(zone.id, tool.tooltype, tool.id, null)) }
    // What a routine's tile does (AutomationActions); a long touch opens its settings, read first
    val automationActions = app.treelune.core.ai.ui.automation.rememberAutomationActions { errorMessage = it }
    var automationSettings by remember { mutableStateOf<Map<String, Any>?>(null) }
    val openAutomationSettings = { automation: app.treelune.core.ai.data.Automation ->
        coroutineScope.launch {
            val result = coordinator.processUserAction("automations.get", mapOf("automation_id" to automation.id))
            @Suppress("UNCHECKED_CAST")
            if (result.status == CommandStatus.SUCCESS) automationSettings = result.data?.get("automation") as? Map<String, Any>
            else errorMessage = result.error
        }
        Unit
    }

    // State for automation creation dialog - with pre-selected group
    var showCreateAutomationDialog by rememberSaveable { mutableStateOf(false) }
    var preSelectedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    // What is being copied into the section of preSelectedGroup, its picker open
    var duplicating by rememberSaveable { mutableStateOf<app.treelune.core.ui.selectors.DuplicateType?>(null) }

    // The zone's tools, variables and automations, read through the dispatcher
    var variables by remember { mutableStateOf<List<app.treelune.core.ui.variables.VariableRow>>(emptyList()) }
    val loadTools: suspend () -> Unit = {
        coordinator.executeWithLoading(
            operation = "tools.list",
            params = mapOf(
                "zone_id" to zone.id,
                "include_config" to true,
                "include_position" to true
            ),
            onLoading = { isLoading = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            toolInstances = result.mapData("tool_instances") { toolInstanceOf(it) }
        }
    }
    val loadVariables: suspend () -> Unit = {
        val result = coordinator.processUserAction("variables.list", mapOf("zone_id" to zone.id))
        if (result.status == CommandStatus.SUCCESS) {
            variables = (result.data?.get("variables") as? List<*> ?: emptyList<Any>())
                .filterIsInstance<Map<*, *>>().map { app.treelune.core.ui.variables.VariableRow.of(it) }
        } else errorMessage = result.error
    }
    val loadAutomations: suspend () -> Unit = {
        coordinator.executeWithLoading(
            operation = "automations.list",
            params = mapOf("zone_id" to zone.id),
            onLoading = { isLoadingAutomations = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            val automationsList = result.data?.get("automations") as? List<*> ?: emptyList<Any>()
            automations = automationsList.map { app.treelune.core.ai.data.Automation.fromResult(it as Map<*, *>) }
        }
    }

    val loadTasks: suspend () -> Unit = {
        val result = coordinator.processUserAction("tasks.list", mapOf("zone_id" to zone.id))
        if (result.status == CommandStatus.SUCCESS) tasks = (result.data?.get("tasks") as List<*>).map { app.treelune.core.tasks.Task.fromResult(it as Map<*, *>) }
        else errorMessage = result.error
    }
    // A Task's config opened for a new one in a section: nothing is made before it is saved
    val createTask = { group: String? ->
        Navigator.push(Place.TaskConfig(zone.id, null, group))
        showAvailableToolsForGroup = null
    }

    // Tools, variables and automations share their section's grid (SectionGrid): a write to one
    // can move the others' tiles there (a size changed, a tile placed, one leaving), so a change to
    // any of them rereads all three. Variables change anywhere (one may read another); a zone
    // change includes an automation switched on, updated or deleted.
    LaunchedEffect(zone.id) {
        launch { loadTools() }
        launch { loadVariables() }
        launch { loadAutomations() }
        launch { loadTasks() }
        DataChangeNotifier.changes.collect { event ->
            val tiles = (event is DataChangeEvent.ToolsChanged && event.zoneId == zone.id) ||
                event is DataChangeEvent.ZonesChanged || event is DataChangeEvent.VariablesChanged
            if (tiles) {
                loadTools()
                loadVariables()
                loadAutomations()
                loadTasks()
            }
        }
    }
    // A variable opened on top of the zone: its id, "" for a new one, in the group chosen for it
    val openVariable = { id: String -> Navigator.push(Place.Variable(zone.id, id.ifEmpty { null }, preSelectedGroup)) }

    // Parse zone tool_groups from config
    val zoneToolGroups = remember(zone.tool_groups) {
        LogManager.ui("Parsing tool_groups for zone ${zone.id}: tool_groups = '${zone.tool_groups}'", "DEBUG")
        try {
            zone.tool_groups?.let {
                org.json.JSONArray(it).let { jsonArray ->
                    val groups = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                    LogManager.ui("Parsed ${groups.size} groups: $groups", "DEBUG")
                    groups
                }
            } ?: emptyList<String>().also {
                LogManager.ui("tool_groups is null, returning empty list", "DEBUG")
            }
        } catch (e: Exception) {
            LogManager.ui("Error parsing tool_groups: ${e.message}", "ERROR", e)
            emptyList()
        }
    }

    // The edit mode of the sections' grids, a section named by its group, "" for the ungrouped one
    val gridEditor = app.treelune.core.ui.components.rememberGridEditor(
        placeOperation = "zones.place_section",
        placeParams = mapOf("zone_id" to zone.id),
        // The section's tools and variables, laid on the same grid
        sectionTiles = { key ->
            val section = key.ifEmpty { null }
            toolInstances.filter { ToolPositions.section(it, zoneToolGroups) == section }.map { app.treelune.core.grid.SectionItem.of(it).tile } +
                variables.filter { ToolPositions.section(it.group, zoneToolGroups) == section }.map { it.tile } +
                automations.filter { ToolPositions.section(it.group, zoneToolGroups) == section }.map { it.tile } +
                tasks.filter { ToolPositions.section(it.group, zoneToolGroups) == section }.map { it.tile }
        },
        onError = { errorMessage = it }
    )

    app.treelune.core.ui.components.CloseEditOnLeave(gridEditor)

    // Leaving the zone, by the header's back button or the phone's back key (the header answers
    // both): a move in progress is cancelled, once asked
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (gridEditor.moving) confirmLeave = true else onBack() }
    
    // Edit mode: the grids of one section at a time, the bar under the screen while a tool moves
    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .chatButtonSpace()
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        // Header with back button, zone title and configure zone button
        UI.PageHeader(
            title = zone.name,
            subtitle = zone.description?.takeIf { it.isNotBlank() },
            icon = zone.icon_name,
            iconColor = app.treelune.core.themes.IconColor.of(zone.icon_color),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = leave,
            onRightClick = { Navigator.push(Place.ZoneConfig(zone.id)) }
        )

        // The identity its tile opens a conversation with, under its header
        val identities = app.treelune.core.identities.ui.rememberIdentities()
        identities?.firstOrNull { it.id == zone.identity_id }?.let { app.treelune.core.identities.ui.IdentityTile(it) }
        // Its events, one card naming them, under its identity
        app.treelune.core.events.ui.EventsCard(zone.id)

        // Display sections by group
        if (!loadedOnce) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY,
                fillMaxWidth = true,
                textAlign = TextAlign.Center
            )
        } else {
            // For each defined group, display its section (always shown, even if empty)
            zoneToolGroups.forEach { groupName ->
                GroupSection(
                    groupName = groupName,
                    zoneToolGroups = zoneToolGroups,
                    variables = variables.filter { it.group == groupName },
                    onOpenVariable = { preSelectedGroup = null; openVariable(it) },
                    onCreateVariable = {
                        preSelectedGroup = groupName
                        openVariable("")
                        showAvailableToolsForGroup = null
                    },
                    toolInstances = toolInstances,
                    automations = automations,
                    tasks = tasks.filter { it.group == groupName },
                    onCreateTask = { createTask(groupName) },
                    showAvailableToolsForGroup = showAvailableToolsForGroup,
                    onToggleToolsList = { showAvailableToolsForGroup = if (showAvailableToolsForGroup == groupName) null else groupName },
                    onSelectToolType = { toolTypeId ->
                        Navigator.push(Place.ToolConfig(zone.id, toolTypeId, null, groupName))
                        showAvailableToolsForGroup = null
                    },
                    onCreateAutomation = {
                        preSelectedGroup = groupName
                        showCreateAutomationDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onDuplicate = { type ->
                        preSelectedGroup = groupName
                        duplicating = type
                        showAvailableToolsForGroup = null
                    },
                    onToolClick = { toolId -> openTool(toolId, waiting.oldest[toolId]?.let { app.treelune.core.tools.EntryToOpen.Existing(it) }) },
                    onOpenEntry = { tool, entry -> openTool(tool.id, entry) },
                    editor = gridEditor,
                    onToolLongClick = configureTool,
                    automationActions = automationActions,
                    onAutomationLongClick = openAutomationSettings,
                    context = context
                )
            }

            // Ungrouped section: items with no group, or a group the zone does not have
            val ungroupedTools = toolInstances.filter { ToolPositions.section(it, zoneToolGroups) == null }
            val ungroupedAutomations = automations.filter { ToolPositions.section(it.group, zoneToolGroups) == null }

            // Always show ungrouped section (even if empty) to allow adding tools/automations
            UngroupedSection(
                    toolInstances = ungroupedTools,
                    variables = variables.filter { it.group == null || it.group !in zoneToolGroups },
                    onOpenVariable = { preSelectedGroup = null; openVariable(it) },
                    onCreateVariable = {
                        preSelectedGroup = null
                        openVariable("")
                        showAvailableToolsForGroup = null
                    },
                    automations = ungroupedAutomations,
                    tasks = tasks.filter { ToolPositions.section(it.group, zoneToolGroups) == null },
                    onCreateTask = { createTask(null) },
                    hasConfiguredGroups = zoneToolGroups.isNotEmpty(),
                    showAvailableToolsForGroup = showAvailableToolsForGroup,
                    onToggleToolsList = { showAvailableToolsForGroup = if (showAvailableToolsForGroup == "") null else "" },
                    onSelectToolType = { toolTypeId ->
                        Navigator.push(Place.ToolConfig(zone.id, toolTypeId, null, null))
                        showAvailableToolsForGroup = null
                    },
                    onCreateAutomation = {
                        preSelectedGroup = null
                        showCreateAutomationDialog = true
                        showAvailableToolsForGroup = null
                    },
                    onDuplicate = { type ->
                        preSelectedGroup = null
                        duplicating = type
                        showAvailableToolsForGroup = null
                    },
                    onToolClick = { toolId -> openTool(toolId, waiting.oldest[toolId]?.let { app.treelune.core.tools.EntryToOpen.Existing(it) }) },
                    onOpenEntry = { tool, entry -> openTool(tool.id, entry) },
                    editor = gridEditor,
                    onToolLongClick = configureTool,
                    automationActions = automationActions,
                    onAutomationLongClick = openAutomationSettings,
                    context = context
                )
        }
    }
    if (gridEditor.selectedId != null) app.treelune.core.ui.components.GridEditBar(gridEditor)
    }

    if (confirmLeave) {
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = { confirmLeave = false; gridEditor.cancel(); onBack() },
            onCancel = { confirmLeave = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(s.shared("grid_leave_title"), TextType.SUBTITLE)
                UI.Text(s.shared("grid_leave_message"), TextType.BODY)
            }
        }
    }

    // Create automation dialog
    if (showCreateAutomationDialog) {
        app.treelune.core.ai.ui.automation.CreateAutomationDialog(
            zoneId = zone.id,
            zoneName = zone.name,
            preSelectedGroup = preSelectedGroup,
            onDismiss = {
                showCreateAutomationDialog = false
                preSelectedGroup = null
            },
            onSuccess = { seedSessionId ->
                showCreateAutomationDialog = false
                preSelectedGroup = null
                // Reload automations to show the new one
                coroutineScope.launch { loadAutomations() }
                // Navigate to SEED editor
                Navigator.push(Place.Seed(seedSessionId, zone.id))
            }
        )
    }

    // A routine's settings, opened by a long touch on its tile; the zone reads its routines again on their change
    automationSettings?.let { automation ->
        app.treelune.core.ai.ui.automation.CreateAutomationDialog(
            zoneId = zone.id,
            zoneName = zone.name,
            automation = automation,
            onDismiss = { automationSettings = null },
            onSuccess = { automationSettings = null }
        )
    }

    // A tool, a routine or a Task copied from any zone into the section it was asked from
    duplicating?.let { type ->
        app.treelune.core.ui.selectors.DuplicateSelector(
            type = type,
            onDismiss = { duplicating = null },
            onConfirm = { _, sourceId ->
                // The operation, the source's parameter, and what is said once it is done
                val (operation, idKey, done) = when (type) {
                    app.treelune.core.ui.selectors.DuplicateType.TOOL -> Triple("tools.duplicate", "tool_instance_id", s.shared("duplicate_success_tool"))
                    app.treelune.core.ui.selectors.DuplicateType.AUTOMATION -> Triple("automations.duplicate", "automation_id", s.shared("duplicate_success_automation"))
                    app.treelune.core.ui.selectors.DuplicateType.TASK -> Triple("tasks.duplicate", "task_id", s.shared("duplicate_success_task"))
                }
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val result = coordinator.processUserAction(operation, mapOf(
                            idKey to sourceId,
                            "target_zone_id" to zone.id,
                            "target_group" to (preSelectedGroup ?: "")
                        ))
                        if (result.status == CommandStatus.SUCCESS) {
                            UI.Toast(done)
                            duplicating = null
                            // The copy among the section's tiles, which it may move
                            loadTools()
                            loadAutomations()
                            loadTasks()
                        } else {
                            errorMessage = result.error ?: s.shared("duplicate_error").format("")
                        }
                    } catch (e: Exception) {
                        errorMessage = s.shared("duplicate_error").format(e.message ?: "")
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    }

    // Error handling with Toast
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(message, Duration.LONG)
            errorMessage = null
        }
    }
}

/** A tile of the add menu: its id, its Lucide icon, its name, what touching it does. */
private class AddChoice(val id: String, val icon: String, val label: String, val onClick: () -> Unit)

/**
 * What can be added to section [groupName], titled by it: the tool types by family (ToolFamily,
 * each type saying its own, in the order the app finds them within it), then a variable and a
 * routine and a Task, then a copy of a tool, a routine or a Task. Each a tile of the settings' kind, an icon and a
 * name two cells wide, two a row, one under the other in one column.
 */
@Composable
private fun AddMenu(
    groupName: String,
    onSelectToolType: (String) -> Unit,
    onCreateVariable: () -> Unit,
    onCreateAutomation: () -> Unit,
    onCreateTask: () -> Unit,
    onDuplicate: (app.treelune.core.ui.selectors.DuplicateType) -> Unit,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }
    val types = ToolTypeManager.getAllToolTypes()
    val sections = app.treelune.core.tools.ToolFamily.entries.map { family ->
        s.shared(family.labelKey) to types.filterValues { it.getFamily() == family }.map { (id, type) ->
            AddChoice(id, type.getDefaultIconName(), type.getDisplayName(context)) { onSelectToolType(id) }
        }
    } + listOf(
        s.shared("add_section_link") to listOf(
            AddChoice("variable", "variable", s.shared("variable_display_name"), onCreateVariable),
            AddChoice("automation", "calendar-clock", s.shared("automation_display_name"), onCreateAutomation),
            AddChoice("task", app.treelune.core.tasks.TaskSettings.DEFAULT_ICON, s.shared("task_display_name"), onCreateTask)
        ),
        s.shared("add_section_copy") to listOf(
            AddChoice("copy_tool", "copy", s.shared("add_copy_tool")) { onDuplicate(app.treelune.core.ui.selectors.DuplicateType.TOOL) },
            AddChoice("copy_automation", "copy", s.shared("add_copy_automation")) { onDuplicate(app.treelune.core.ui.selectors.DuplicateType.AUTOMATION) },
            AddChoice("copy_task", "copy", s.shared("add_copy_task")) { onDuplicate(app.treelune.core.ui.selectors.DuplicateType.TASK) }
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(s.shared("add_title").format(groupName), TextType.SUBTITLE, fillMaxWidth = true)
        sections.filter { it.second.isNotEmpty() }.forEach { (title, choices) ->
            UI.Text(title, TextType.CAPTION, fillMaxWidth = true)
            // Two tiles a row, each two cells wide and one high, as the settings' tiles
            app.treelune.core.ui.components.GridLayout(
                choices,
                choices.mapIndexed { i, choice -> app.treelune.core.grid.Grid.Tile(choice.id, (i % 2) * 2, i / 2, 2, 1) },
                choices.map { false },
                edit = null
            ) { choice ->
                app.treelune.core.themes.CurrentTheme.current.ZoneCardContainer(texture = null, color = null, seed = choice.id, onClick = choice.onClick, onLongClick = {}) {
                    UI.TileHeader(choice.icon, null, choice.label, waiting = false, running = false, textType = TextType.LABEL)
                }
            }
        }
    }
}

/**
 * Display a group section with its tools and automations
 */
@Composable
private fun GroupSection(
    groupName: String,
    zoneToolGroups: List<String>,
    variables: List<app.treelune.core.ui.variables.VariableRow>,
    onOpenVariable: (String) -> Unit,
    onCreateVariable: () -> Unit,
    toolInstances: List<ToolInstance>,
    automations: List<app.treelune.core.ai.data.Automation>,
    tasks: List<app.treelune.core.tasks.Task>,
    onCreateTask: () -> Unit,
    showAvailableToolsForGroup: String?,
    onToggleToolsList: () -> Unit,
    onSelectToolType: (String) -> Unit,
    onCreateAutomation: () -> Unit,
    onDuplicate: (app.treelune.core.ui.selectors.DuplicateType) -> Unit,
    onToolClick: (String) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.EntryToOpen) -> Unit,
    editor: app.treelune.core.ui.components.GridEditor,
    onToolLongClick: (ToolInstance) -> Unit,
    automationActions: app.treelune.core.ai.ui.automation.AutomationActions,
    onAutomationLongClick: (app.treelune.core.ai.data.Automation) -> Unit,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }
    // Filter tools and automations for this group
    val groupTools = toolInstances.filter { ToolPositions.section(it, zoneToolGroups) == groupName }
    val groupAutomations = automations.filter { it.group == groupName }

    // Section header: its title over a divider (SECTION_HEADER)
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The title takes what the buttons leave, wrapping rather than squeezing them
            Box(modifier = Modifier.weight(1f)) {
                UI.Text(
                    text = groupName,
                    type = TextType.HEADING
                )
            }

            app.treelune.core.ui.components.GridSectionButtons(groupName, groupTools.isNotEmpty() || variables.isNotEmpty() || groupAutomations.isNotEmpty() || tasks.isNotEmpty(), editor, onToggleToolsList)
        }
    }

    // What can be added to the section, under its + (AddMenu)
    if (showAvailableToolsForGroup == groupName && !editor.anyEditing) {
        AddMenu(
            groupName = groupName,
            onSelectToolType = onSelectToolType,
            onCreateVariable = onCreateVariable,
            onCreateAutomation = onCreateAutomation,
            onCreateTask = onCreateTask,
            onDuplicate = onDuplicate,
            context = context
        )
    }

    // The group's tools, variables and routines on their grid
    SectionGrid(groupName, groupTools, variables, groupAutomations, tasks, editor, onToolClick, onToolLongClick, onOpenEntry, onOpenVariable, automationActions, onAutomationLongClick)


    // Empty state if no tools/automations in this group (only when list is not showing)
    if (groupTools.isEmpty() && variables.isEmpty() && groupAutomations.isEmpty() && tasks.isEmpty() && showAvailableToolsForGroup != groupName) {
        UI.Text(
            text = s.shared("message_no_items_in_group"),
            type = TextType.CAPTION,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
    }

    // Spacer after each group
    Spacer(modifier = Modifier.height(UI.Space.XL))
}

/**
 * Display the ungrouped section with tools and automations without a group
 */
@Composable
private fun UngroupedSection(
    toolInstances: List<ToolInstance>,
    variables: List<app.treelune.core.ui.variables.VariableRow>,
    onOpenVariable: (String) -> Unit,
    onCreateVariable: () -> Unit,
    automations: List<app.treelune.core.ai.data.Automation>,
    tasks: List<app.treelune.core.tasks.Task>,
    onCreateTask: () -> Unit,
    hasConfiguredGroups: Boolean,
    showAvailableToolsForGroup: String?,
    onToggleToolsList: () -> Unit,
    onSelectToolType: (String) -> Unit,
    onCreateAutomation: () -> Unit,
    onDuplicate: (app.treelune.core.ui.selectors.DuplicateType) -> Unit,
    onToolClick: (String) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.EntryToOpen) -> Unit,
    editor: app.treelune.core.ui.components.GridEditor,
    onToolLongClick: (ToolInstance) -> Unit,
    automationActions: app.treelune.core.ai.ui.automation.AutomationActions,
    onAutomationLongClick: (app.treelune.core.ai.data.Automation) -> Unit,
    context: android.content.Context
) {
    val s = remember { Strings.`for`(context = context) }

    // Determine section label
    val sectionLabel = if (hasConfiguredGroups) {
        s.shared("label_ungrouped")
    } else {
        s.shared("label_tools_and_automations")
    }

    // Section header: its title over a divider (SECTION_HEADER)
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The title takes what the buttons leave, wrapping rather than squeezing them
            Box(modifier = Modifier.weight(1f)) {
                UI.Text(
                    text = sectionLabel,
                    type = TextType.HEADING
                )
            }

            app.treelune.core.ui.components.GridSectionButtons("", toolInstances.isNotEmpty() || variables.isNotEmpty() || automations.isNotEmpty() || tasks.isNotEmpty(), editor, onToggleToolsList)
        }
    }

    // What can be added to the section, under its + (AddMenu)
    if (showAvailableToolsForGroup == "" && !editor.anyEditing) {
        AddMenu(
            groupName = sectionLabel,
            onSelectToolType = onSelectToolType,
            onCreateVariable = onCreateVariable,
            onCreateAutomation = onCreateAutomation,
            onCreateTask = onCreateTask,
            onDuplicate = onDuplicate,
            context = context
        )
    }

    // The ungrouped tools, variables and routines on their grid
    SectionGrid("", toolInstances, variables, automations, tasks, editor, onToolClick, onToolLongClick, onOpenEntry, onOpenVariable, automationActions, onAutomationLongClick)


    // Empty state (only when list is not showing)
    if (toolInstances.isEmpty() && variables.isEmpty() && automations.isEmpty() && tasks.isEmpty() && showAvailableToolsForGroup != "") {
        UI.Text(
            text = s.shared("message_no_items_in_group"),
            type = TextType.CAPTION,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
    }
}

/** A tool of a tools.list result asked with its config and its place. */
@Suppress("UNCHECKED_CAST")
private fun toolInstanceOf(map: Map<String, Any?>) = ToolInstance(
    id = map["id"] as String,
    zone_id = map["zone_id"] as String,
    tooltype = map["tooltype"] as String,
    config_json = JsonUtils.toJSONObject(map["config"] as Map<String, Any?>).toString(),
    grid_x = (map["grid_x"] as Number).toInt(),
    grid_y = (map["grid_y"] as Number).toInt(),
    created_at = (map["created_at"] as Number).toLong(),
    updated_at = (map["updated_at"] as Number).toLong()
)

/** A section's grid, its tools, variables, routines and Tasks: in edit mode when it is the section edited, faded while another is. */
@Composable
private fun SectionGrid(
    key: String,
    tools: List<ToolInstance>,
    variables: List<app.treelune.core.ui.variables.VariableRow>,
    automations: List<app.treelune.core.ai.data.Automation>,
    tasks: List<app.treelune.core.tasks.Task>,
    editor: app.treelune.core.ui.components.GridEditor,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.EntryToOpen) -> Unit,
    onOpenVariable: (String) -> Unit,
    automationActions: app.treelune.core.ai.ui.automation.AutomationActions,
    onAutomationLongClick: (app.treelune.core.ai.data.Automation) -> Unit
) {
    val tiles = tools.map { app.treelune.core.ui.components.SectionTile.Tool(it) } +
        variables.map { app.treelune.core.ui.components.SectionTile.Variable(it) } +
        automations.map { app.treelune.core.ui.components.SectionTile.Routine(it) } +
        tasks.map { app.treelune.core.ui.components.SectionTile.TaskTile(it) }
    app.treelune.core.ui.components.Faded(editor.anyEditing && !editor.isEditing(key)) {
        app.treelune.core.ui.components.SectionTiles(tiles, { onToolClick(it.id) }, onToolLongClick, onOpenEntry, onOpenVariable,
            automationActions, onAutomationLongClick, editor.gridEdit(key))
    }
}
