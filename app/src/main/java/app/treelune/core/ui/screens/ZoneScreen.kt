package app.treelune.core.ui.screens

import app.treelune.core.utils.JsonUtils
import app.treelune.core.ui.pageScroll
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
fun ZoneScreen(zone: Zone) = app.treelune.core.readouts.ui.ProvideReadoutColumns(zone.readout_columns) { ZoneScreenBody(zone) }

/** The zone's screen, its gathered readouts in the columns it sets (ProvideReadoutColumns). */
@Composable
private fun ZoneScreenBody(zone: Zone) {
    val onBack = { Navigator.pop() }
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Load tool instances via command pattern
    var toolInstances by app.treelune.core.ui.rememberShown<List<ToolInstance>>("zone:${zone.id}:tools") { emptyList() }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load routines for this zone
    var routines by app.treelune.core.ui.rememberShown<List<app.treelune.core.ai.data.Routine>>("zone:${zone.id}:routines") { emptyList() }
    var isLoadingRoutines by remember { mutableStateOf(true) }
    // The zone's Tasks, among its tools as its routines are
    var tasks by app.treelune.core.ui.rememberShown<List<app.treelune.core.tasks.Task>>("zone:${zone.id}:tasks") { emptyList() }
    // Its readouts, their own tiles among its tools, and the lines they raise to its head and its groups'
    var readouts by app.treelune.core.ui.rememberShown<List<app.treelune.core.readouts.ui.ReadoutRow>>("zone:${zone.id}:readouts") { emptyList() }

    // The zone shows "loading" until its tools and routines are first read. A later reload
    // (a routine switched on, a tool changed or duplicated, coming back to it) keeps the content
    // on screen and updates it when the new data arrives: replaced by "loading", the whole screen
    // would blink, and lose its scroll position (Shown)
    var loadedOnce by app.treelune.core.ui.rememberShown("zone:${zone.id}:loaded") { false }
    LaunchedEffect(isLoading, isLoadingRoutines) {
        if (!isLoading && !isLoadingRoutines) loadedOnce = true
    }
    
    // The page's add menu open, under its « + »: what is made there chooses its group in its own form
    var adding by rememberSaveable { mutableStateOf(false) }

    // A tool opened on top of the zone: on its page from its tile, where what waits shows; on an entry from a tile that names one
    val openTool = { toolId: String, entry: app.treelune.core.tools.OpenAt? -> Navigator.push(Place.Tool(toolId, zone.id), entry) }
    val configureTool = { tool: ToolInstance -> Navigator.push(Place.ToolConfig(zone.id, tool.tooltype, tool.id)) }
    // What a routine's tile does (RoutineActions), its long touch opening its config
    val routineActions = app.treelune.core.ai.ui.routine.rememberRoutineActions { errorMessage = it }
    // A routine's config opened for a new one: nothing is made before it is saved
    val createRoutine = { Navigator.push(Place.RoutineConfig(zone.id, null)) }

    // What is being copied into the zone, its picker open
    var duplicating by rememberSaveable { mutableStateOf<app.treelune.core.ui.selectors.DuplicateType?>(null) }

    // The zone's tools, variables and routines, read through the dispatcher
    var variables by app.treelune.core.ui.rememberShown<List<app.treelune.core.ui.variables.VariableRow>>("zone:${zone.id}:variables") { emptyList() }
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
            toolInstances = result.mapData("tool_instances") { app.treelune.core.ui.components.toolInstanceOf(it) }
        }
    }
    val loadVariables: suspend () -> Unit = {
        val result = coordinator.processUserAction("variables.list", mapOf("zone_id" to zone.id))
        if (result.status == CommandStatus.SUCCESS) {
            variables = (result.data?.get("variables") as? List<*> ?: emptyList<Any>())
                .filterIsInstance<Map<*, *>>().map { app.treelune.core.ui.variables.VariableRow.of(it) }
        } else errorMessage = result.error
    }
    val loadRoutines: suspend () -> Unit = {
        coordinator.executeWithLoading(
            operation = "routines.list",
            params = mapOf("zone_id" to zone.id),
            onLoading = { isLoadingRoutines = it },
            onError = { error -> errorMessage = error }
        )?.let { result ->
            val routinesList = result.data?.get("routines") as? List<*> ?: emptyList<Any>()
            routines = routinesList.map { app.treelune.core.ai.data.Routine.fromResult(it as Map<*, *>) }
        }
    }

    val loadTasks: suspend () -> Unit = {
        val result = coordinator.processUserAction("tasks.list", mapOf("zone_id" to zone.id))
        if (result.status == CommandStatus.SUCCESS) tasks = (result.data?.get("tasks") as List<*>).map { app.treelune.core.tasks.Task.fromResult(it as Map<*, *>) }
        else errorMessage = result.error
    }
    // A Task's config opened for a new one: nothing is made before it is saved
    val createTask = { Navigator.push(Place.TaskConfig(zone.id, null)) }
    val loadReadouts: suspend () -> Unit = {
        val result = coordinator.processUserAction("readouts.list", mapOf("zone_id" to zone.id))
        if (result.status == CommandStatus.SUCCESS) readouts = (result.data?.get("readouts") as List<*>).map { app.treelune.core.readouts.ui.ReadoutRow.of(it as Map<*, *>) }
        else errorMessage = result.error
    }

    // Tools, variables and routines share their section's grid (SectionGrid): a write to one
    // can move the others' tiles there (a size changed, a tile placed, one leaving), so a change to
    // any of them rereads all three. Variables change anywhere (one may read another); a zone
    // change includes a routine switched on, updated or deleted.
    LaunchedEffect(zone.id) {
        launch { loadTools() }
        launch { loadVariables() }
        launch { loadRoutines() }
        launch { loadTasks() }
        launch { loadReadouts() }
        DataChangeNotifier.changes.collect { event ->
            val tiles = (event is DataChangeEvent.ToolsChanged && event.zoneId == zone.id) ||
                event is DataChangeEvent.ZonesChanged || event is DataChangeEvent.VariablesChanged
            if (tiles) {
                loadTools()
                loadVariables()
                loadRoutines()
                loadTasks()
                loadReadouts()
            }
        }
    }
    // A variable opened on top of the zone: its id, "" for a new one
    val openVariable = { id: String -> Navigator.push(Place.Variable(zone.id, id.ifEmpty { null })) }

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

    // The edit mode of the sections' grids, a section named by its key (Section.key): a group's,
    // "" for the ungrouped one, or the hidden part just after it
    val gridEditor = app.treelune.core.ui.components.rememberGridEditor(
        placeOperation = "zones.place_section",
        placeParams = mapOf("zone_id" to zone.id),
        // The section's tools and variables, laid on the same grid
        sectionTiles = { key ->
            val section = app.treelune.core.grid.Section.ofKey(key)
            toolInstances.filter { ToolPositions.part(it, zoneToolGroups) == section }.map { app.treelune.core.grid.SectionItem.of(it).tile } +
                variables.filter { ToolPositions.part(it.group, it.hidden, zoneToolGroups) == section }.map { it.tile } +
                routines.filter { ToolPositions.part(it.group, it.hidden, zoneToolGroups) == section }.map { it.tile } +
                tasks.filter { ToolPositions.part(it.group, it.hidden, zoneToolGroups) == section }.map { it.tile } +
                readouts.filter { ToolPositions.part(it.group, it.hidden, zoneToolGroups) == section }.map { it.tile }
        },
        // Its groups, then the ungrouped section, each followed by its hidden part: what an arrow crosses into
        sectionOrder = { app.treelune.core.grid.Section.ordered(zoneToolGroups).map { it.key } },
        onError = { errorMessage = it }
    )

    // Whether the hidden tiles show, never remembered from a visit to the next
    var showHidden by remember { mutableStateOf(false) }

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
            .pageScroll()
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
            onRightClick = { Navigator.push(Place.ZoneConfig(zone.id)) },
            // Organising and adding, once for the whole page, before its gear
            beforeRight = {
                // A hidden tile waiting for something or running marks the button that shows them
                val waiting = app.treelune.core.ui.LocalWaiting.current
                val running = app.treelune.core.ui.LocalRunning.current
                val hiddenTools = toolInstances.filter { it.hidden }
                val hiddenRoutines = routines.filter { it.hidden }
                val anyHidden = hiddenTools.isNotEmpty() || hiddenRoutines.isNotEmpty() || variables.any { it.hidden } || tasks.any { it.hidden } || readouts.any { it.hidden }
                app.treelune.core.ui.components.GridHeaderButtons(
                    toolInstances.isNotEmpty() || variables.isNotEmpty() || routines.isNotEmpty() || tasks.isNotEmpty() || readouts.isNotEmpty(), gridEditor,
                    if (!anyHidden) null else app.treelune.core.ui.components.HiddenTiles(showHidden,
                        hiddenTools.any { waiting.tool(it.id) || running.tool(it.id) } || hiddenRoutines.any { waiting.routine(it.id) || running.routine(it.id) }
                    ) { showHidden = !showHidden }
                ) { adding = !adding }
            }
        )

        // The readouts raised to its head, in the order of its groups
        if (loadedOnce) app.treelune.core.readouts.ui.ReadoutLines(
            app.treelune.core.readouts.ui.risingTo(readouts, app.treelune.core.readouts.ReadoutSettings.Place.ZONE_HEAD, listOf(zone.id)) { zoneToolGroups })

        // Display sections by group
        if (!loadedOnce) {
            UI.Text(
                text = s.shared("message_loading"),
                type = TextType.BODY,
                fillMaxWidth = true,
                textAlign = TextAlign.Center
            )
        } else {
            // The add menu, under the header's +
            if (adding && !gridEditor.anyEditing) {
                AddMenu(
                    zoneName = zone.name,
                    onSelectToolType = { toolTypeId -> adding = false; Navigator.push(Place.ToolConfig(zone.id, toolTypeId, null)) },
                    onCreateVariable = { adding = false; openVariable("") },
                    onCreateRoutine = { adding = false; createRoutine() },
                    onCreateTask = { adding = false; createTask() },
                    onCreateReadout = { adding = false; Navigator.push(Place.ReadoutConfig(zone.id, null)) },
                    onDuplicate = { type -> adding = false; duplicating = type },
                    context = context
                )
            }

            // The section a tile stands in (Section.key): its group's or the ungrouped one, shown or
            // hidden; while a move crosses sections, where it puts it (GridEditor.sectionOf)
            fun keyOf(id: String, group: String?, hidden: Boolean): String =
                gridEditor.sectionOf(id) ?: ToolPositions.part(group, hidden, zoneToolGroups).key
            val editing = gridEditor.anyEditing

            // Each group, then the ungrouped section, each followed by its hidden part
            (zoneToolGroups.map { it as String? } + null).forEach { group ->
                val shownKey = app.treelune.core.grid.Section(group).key
                val hiddenKey = app.treelune.core.grid.Section(group, hidden = true).key
                fun <T> held(items: List<T>, key: String, id: (T) -> String, group: (T) -> String?, hidden: (T) -> Boolean) =
                    items.filter { keyOf(id(it), group(it), hidden(it)) == key }
                fun part(key: String) = SectionParts(
                    held(toolInstances, key, { it.id }, { ToolPositions.section(it, zoneToolGroups) }, { it.hidden }),
                    held(variables, key, { it.id }, { it.group }, { it.hidden }),
                    held(routines, key, { it.id }, { it.group }, { it.hidden }),
                    held(tasks, key, { it.id }, { it.group }, { it.hidden }),
                    held(readouts, key, { it.id }, { it.group }, { it.hidden })
                )
                val shown = part(shownKey)
                val hidden = part(hiddenKey)
                // A group whose tiles are all hidden goes, title included, while they are; an empty
                // group stays. Beside groups, the ungrouped section shows only when it holds
                // something; without groups it is the page's one section, kept unless all of it is
                // hidden. In edit mode every section and every hidden part is there: a tile crosses in
                val shownThere = when {
                    editing -> true
                    !shown.isEmpty -> true
                    !hidden.isEmpty -> showHidden
                    else -> group != null || zoneToolGroups.isEmpty()
                }
                if (shownThere) {
                    val title = group ?: s.shared(if (zoneToolGroups.isNotEmpty()) "label_ungrouped" else "label_tools_and_routines")
                    // The readouts of the group raised to its head, hidden ones too: hiding a tile leaves its place at the head
                    Section(shownKey, title, shown, shown.readouts + hidden.readouts, s.shared("message_no_items_in_group"), gridEditor,
                        { toolId -> openTool(toolId, null) }, configureTool, { tool, entry -> openTool(tool.id, entry) }, openVariable, routineActions, context)
                }
                if (editing || (showHidden && !hidden.isEmpty)) {
                    Section(hiddenKey, s.shared("label_hidden_part"), hidden, emptyList(), s.shared("message_no_hidden_tiles"), gridEditor,
                        { toolId -> openTool(toolId, null) }, configureTool, { tool, entry -> openTool(tool.id, entry) }, openVariable, routineActions, context)
                }
            }
        }
    }
    if (gridEditor.anyEditing) app.treelune.core.ui.components.GridEditBar(gridEditor)
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


    // A tool, a routine or a Task copied from any zone, without a group
    duplicating?.let { type ->
        app.treelune.core.ui.selectors.DuplicateSelector(
            type = type,
            onDismiss = { duplicating = null },
            onConfirm = { _, sourceId ->
                // The operation, the source's parameter, and what is said once it is done
                val (operation, idKey, done) = when (type) {
                    app.treelune.core.ui.selectors.DuplicateType.TOOL -> Triple("tools.duplicate", "tool_instance_id", s.shared("duplicate_success_tool"))
                    app.treelune.core.ui.selectors.DuplicateType.ROUTINE -> Triple("routines.duplicate", "routine_id", s.shared("duplicate_success_routine"))
                    app.treelune.core.ui.selectors.DuplicateType.TASK -> Triple("tasks.duplicate", "task_id", s.shared("duplicate_success_task"))
                }
                coroutineScope.launch {
                    isLoading = true
                    try {
                        val result = coordinator.processUserAction(operation, mapOf(
                            idKey to sourceId,
                            "target_zone_id" to zone.id,
                            "target_group" to ""
                        ))
                        if (result.status == CommandStatus.SUCCESS) {
                            UI.Toast(done)
                            duplicating = null
                            // The copy among the section's tiles, which it may move
                            loadTools()
                            loadRoutines()
                            loadTasks()
                        } else {
                            errorMessage = result.error ?: s.shared("duplicate_error").format("")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
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
 * What can be added to the zone [zoneName], titled by it: the tool types by family (ToolFamily,
 * each type saying its own, in the order the app finds them within it), then a variable, a
 * routine, a Task and a readout, then a copy of a tool, a routine or a Task. Each a tile of the
 * settings' kind, an icon and a name two cells wide, two a row, one under the other in one column.
 */
@Composable
private fun AddMenu(
    zoneName: String,
    onSelectToolType: (String) -> Unit,
    onCreateVariable: () -> Unit,
    onCreateRoutine: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateReadout: () -> Unit,
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
            AddChoice("routine", "calendar-clock", s.shared("routine_display_name"), onCreateRoutine),
            AddChoice("task", app.treelune.core.tasks.TaskSettings.DEFAULT_ICON, s.shared("task_display_name"), onCreateTask),
            AddChoice("readout", app.treelune.core.readouts.ReadoutSettings.DEFAULT_ICON, s.shared("readout_display_name"), onCreateReadout)
        ),
        s.shared("add_section_copy") to listOf(
            AddChoice("copy_tool", "copy", s.shared("add_copy_tool")) { onDuplicate(app.treelune.core.ui.selectors.DuplicateType.TOOL) },
            AddChoice("copy_routine", "copy", s.shared("add_copy_routine")) { onDuplicate(app.treelune.core.ui.selectors.DuplicateType.ROUTINE) },
            AddChoice("copy_task", "copy", s.shared("add_copy_task")) { onDuplicate(app.treelune.core.ui.selectors.DuplicateType.TASK) }
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(s.shared("add_title").format(zoneName), TextType.SUBTITLE, fillMaxWidth = true)
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

/** What one part of a section holds, its shown or its hidden tiles, each kind apart. */
private class SectionParts(
    val tools: List<ToolInstance>,
    val variables: List<app.treelune.core.ui.variables.VariableRow>,
    val routines: List<app.treelune.core.ai.data.Routine>,
    val tasks: List<app.treelune.core.tasks.Task>,
    val readouts: List<app.treelune.core.readouts.ui.ReadoutRow>
) {
    val isEmpty: Boolean get() = tools.isEmpty() && variables.isEmpty() && routines.isEmpty() && tasks.isEmpty() && readouts.isEmpty()
}

/**
 * A section [key] titled [title] over a divider: a group's, the ungrouped one, or a hidden part;
 * the readouts of [head] raised to its head, its grid, or [empty] when it holds nothing.
 */
@Composable
private fun Section(
    key: String,
    title: String,
    parts: SectionParts,
    head: List<app.treelune.core.readouts.ui.ReadoutRow>,
    empty: String,
    editor: app.treelune.core.ui.components.GridEditor,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.OpenAt) -> Unit,
    onOpenVariable: (String) -> Unit,
    routineActions: app.treelune.core.ai.ui.routine.RoutineActions,
    context: android.content.Context
) {
    // One block: its title, its readouts, its grid, close together; the page puts one space
    // between two sections, never its own between each of their parts
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
    UI.Card(type = CardType.SECTION_HEADER) {
        Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.M)) {
            UI.Text(text = title, type = TextType.HEADING)
        }
    }
    // The readouts raised to the head of their group, over its grid
    app.treelune.core.readouts.ui.ReadoutLines(head.filter { app.treelune.core.readouts.ReadoutSettings.Place.GROUP_HEAD in it.places }.sortedWith(compareBy({ it.hidden }, { it.gridY }, { it.gridX })))
    SectionGrid(key, parts.tools, parts.variables, parts.routines, parts.tasks, parts.readouts, editor, onToolClick, onToolLongClick, onOpenEntry, onOpenVariable, routineActions)
    if (parts.isEmpty) {
        UI.Text(
            text = empty,
            type = TextType.CAPTION,
            fillMaxWidth = true,
            textAlign = TextAlign.Center
        )
    }
    }
}

/** A section's grid, its tools, variables, routines and Tasks: in edit mode while the page is. */
@Composable
private fun SectionGrid(
    key: String,
    tools: List<ToolInstance>,
    variables: List<app.treelune.core.ui.variables.VariableRow>,
    routines: List<app.treelune.core.ai.data.Routine>,
    tasks: List<app.treelune.core.tasks.Task>,
    readouts: List<app.treelune.core.readouts.ui.ReadoutRow>,
    editor: app.treelune.core.ui.components.GridEditor,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.OpenAt) -> Unit,
    onOpenVariable: (String) -> Unit,
    routineActions: app.treelune.core.ai.ui.routine.RoutineActions
) {
    val tiles = tools.map { app.treelune.core.ui.components.SectionTile.Tool(it) } +
        variables.map { app.treelune.core.ui.components.SectionTile.Variable(it) } +
        routines.map { app.treelune.core.ui.components.SectionTile.Routine(it) } +
        tasks.map { app.treelune.core.ui.components.SectionTile.TaskTile(it) } +
        readouts.map { app.treelune.core.ui.components.SectionTile.Readout(it) }
    app.treelune.core.ui.components.SectionTiles(tiles, { onToolClick(it.id) }, onToolLongClick, onOpenEntry, onOpenVariable,
        routineActions, editor.gridEdit(key))
}
