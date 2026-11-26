package app.treelune.core.ui.screens

import kotlinx.coroutines.launch
import app.treelune.core.ui.pageScroll
import app.treelune.core.utils.JsonUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.ItemTexture
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.ui.UI
import app.treelune.core.ui.*
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.mapData
import app.treelune.core.coordinator.executeWithLoading
import app.treelune.core.strings.Strings
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.database.entities.Zone
import app.treelune.core.navigation.LocalBreadcrumb
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.navigation.PlaceStack
import app.treelune.core.tools.OpenAt
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.screens.settings.*
import app.treelune.core.ai.ui.screens.AIProvidersScreen
import app.treelune.core.ai.ui.chat.AIFloatingChat
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.LogManager

/**
 * The app's screens: the place at the top of the stack (Navigator), the chat laid over the one
 * under it. What opens a place from outside the screen on display arrives here: a notification's
 * tool ([openToolId]), a tool asked for from any screen (ToolRequests), a chat asked for with its
 * content (ChatRequests).
 *
 * The stack is saved with the activity's state, as addresses, and read back after the process's
 * death. Each place keeps what its screen saved (rememberSaveable) while it stays in the stack,
 * and drops it once it leaves.
 *
 * @param openToolId A tool to open on its page, where what waits shows and which one to answer is
 *   chosen: the one a touched notification is about; [onToolOpened] once it is opened
 * @param openEntryId The entry of that tool the notification is about, opened on it (an event's
 *   reminder, a Session running)
 * @param openPlace The address of a place to open, the one a touched notification is about (a
 *   routine's run); [onPlaceOpened] once it is opened
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(openToolId: String? = null, openEntryId: String? = null, onToolOpened: () -> Unit = {}, openPlace: String? = null, onPlaceOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // The stack across the process's death: read back once, when nothing else is open
    val saved = rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    remember { saved.value?.let { if (Navigator.stack.size == 1) Navigator.restore(it) }; true }
    LaunchedEffect(Unit) { snapshotFlow { Navigator.addresses() }.collect { saved.value = it } }

    val zones = rememberZones { errorMessage = it }
    // The demo is there while one of its zones is
    val demoInstalled = zones.list.any { it.id.startsWith(app.treelune.core.guide.DEMO_PREFIX) }
    LaunchedEffect(Unit) { app.treelune.core.guide.Guide.start(context) }

    // Whether the chat waits, read at start and whenever a session changes (ChatWaiting)
    LaunchedEffect(Unit) {
        app.treelune.core.ai.orchestration.ChatWaiting.refresh(context)
        app.treelune.core.utils.DataChangeNotifier.changes.collect {
            if (it is app.treelune.core.utils.DataChangeEvent.AISessionsChanged) app.treelune.core.ai.orchestration.ChatWaiting.refresh(context)
        }
    }

    // A chat asked for from any screen, with its content, laid over that screen
    LaunchedEffect(Unit) {
        app.treelune.core.ai.orchestration.ChatRequests.requests.collect { request ->
            app.treelune.core.ai.orchestration.AIOrchestrator.startNewChatSession(request.prefill, request.identityId, request.providerId)
            if (Navigator.top != Place.Chat) Navigator.push(Place.Chat)
        }
    }
    // The tool a notification asked for, on its page or on the entry the notification names
    LaunchedEffect(openToolId) {
        val toolId = openToolId ?: return@LaunchedEffect
        val zoneId = zoneOfTool(coordinator, toolId)
        if (zoneId == null) {
            // Deleted since the notification: the app opens as it is
            LogManager.ui("MainScreen: tool $toolId of a notification not found", "WARN")
            onToolOpened()
            return@LaunchedEffect
        }
        Navigator.open(Place.Tool(toolId, zoneId), openEntryId?.let { OpenAt.Entry(it) })
        onToolOpened()
    }
    // The place a notification asked for
    LaunchedEffect(openPlace) {
        val address = openPlace ?: return@LaunchedEffect
        Navigator.open(Place.of(address))
        onPlaceOpened()
    }
    // A tool asked for from any screen, where its tool type opens
    LaunchedEffect(Unit) {
        app.treelune.core.tools.ToolRequests.requests.collect { (toolId, at) ->
            val zoneId = zoneOfTool(coordinator, toolId)
            if (zoneId == null) {
                LogManager.ui("MainScreen: tool $toolId asked for not found", "WARN")
                errorMessage = s.shared("service_error_tool_instance_not_found")
                return@collect
            }
            Navigator.open(Place.Tool(toolId, zoneId), at)
        }
    }

    // The back key: the home screen asks before leaving the app, any other place goes back.
    // Composed before the places, whose own handlers (a form with changes) answer first.
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = Navigator.top == Place.Home) { showExitConfirm = true }
    BackHandler(enabled = Navigator.top != Place.Home) { Navigator.pop() }

    // The place drawn: the top, or the one under the chat
    val stack = Navigator.stack
    val baseIndex = PlaceStack.baseIndex(stack)
    val base = stack[baseIndex]
    val breadcrumb = if (baseIndex == 0) null else PlaceStack.breadcrumb(stack, baseIndex) { Navigator.names[it.address] }

    // What each place saved, kept while it is in the stack
    val holder = rememberSaveableStateHolder()
    val kept = remember { mutableSetOf<String>() }
    LaunchedEffect(stack.toList()) {
        val now = stack.map { it.address }.toSet()
        (kept - now).forEach { holder.removeState(it) }
        kept.clear(); kept.addAll(now)
    }

    // The first-launch screen, once, before any place; then the place, the tutorial's band under it
    val guide = app.treelune.core.guide.Guide.progress
    if (guide != null && !guide.welcomeSeen && zones.loaded) {
        app.treelune.core.guide.ui.WelcomeScreen(demoInstalled)
        return
    }
    // The way back to the top runs here, not under its button: the button leaves once the top is
    // less than a screen away, and the scroll would stop with it
    val toTopScope = androidx.compose.runtime.rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(base.address) {
                holder.SaveableStateProvider(base.address) {
                    CompositionLocalProvider(LocalBreadcrumb provides breadcrumb) {
                        PlaceScreen(base, zones, demoInstalled)
                    }
                }
            }
            // Back to the top of the page, facing the chat's button: once the top is a screen away
            if (base.toTopButton && Navigator.top != Place.Chat && !ChatButton.hidden && !app.treelune.core.ui.keyboardOpen() && app.treelune.core.ui.ScrollTop.shown) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(UI.Space.L)) {
                    UI.FloatingButton(action = ButtonAction.TO_TOP, onClick = { toTopScope.launch { app.treelune.core.ui.ScrollTop.toTop() } })
                }
            }
            // The chat's button, floating over the places one reads, measured for the room they leave
            // it; gone while the keyboard is open, over which it would float
            if (base.chatButton && Navigator.top != Place.Chat && !ChatButton.hidden && !app.treelune.core.ui.keyboardOpen()) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(UI.Space.L)
                    .onSizeChanged { with(density) { ChatButton.height = it.height.toDp(); ChatButton.width = it.width.toDp() } }) {
                    UI.FloatingButton(action = ButtonAction.AI_CHAT, onClick = { Navigator.push(Place.Chat) })
                    // Marked while the chat has an AI message not shown yet
                    if (app.treelune.core.ui.waiting(app.treelune.core.ui.WaitingSource.CHAT)) Box(modifier = Modifier.align(Alignment.TopEnd)) { UI.WaitingMark() }
                }
            }
        }
        app.treelune.core.guide.ui.GuideBand()
    }
    app.treelune.core.guide.Guide.ended?.let { app.treelune.core.guide.ui.GuideEndDialog(it, demoInstalled) }
    app.treelune.core.guide.Guide.demoMissingFor?.let { app.treelune.core.guide.ui.GuideDemoMissingDialog(it) }
    app.treelune.core.guide.Guide.introducing?.let { app.treelune.core.guide.ui.GuideStartDialog(it) }

    AIFloatingChat(isVisible = Navigator.top == Place.Chat, onDismiss = { Navigator.pop() })

    if (showExitConfirm) {
        UI.ConfirmDialog(
            title = s.shared("action_quit"),
            message = s.shared("exit_confirm_message"),
            confirmText = s.shared("action_quit"),
            cancelText = s.shared("action_cancel"),
            onConfirm = { (context as? android.app.Activity)?.finish() },
            onDismiss = { showExitConfirm = false }
        )
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(message, Duration.LONG)
            errorMessage = null
        }
    }
}

/** The zone a tool belongs to, null when there is no such tool. */
private suspend fun zoneOfTool(coordinator: Coordinator, toolId: String): String? {
    val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
    return (tool.data?.get("tool_instance") as? Map<*, *>)?.get("zone_id") as? String
}

/**
 * The screen of [place], which names it for the breadcrumb. A place whose zone or tool is gone
 * (deleted from another screen, by the AI) leaves the stack.
 */
@Composable
private fun PlaceScreen(place: Place, zones: ZonesState, demoInstalled: Boolean) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val back = { Navigator.pop() }
    val zone = place.zoneId?.let { id -> zones.list.find { it.id == id } }
    // A place of a zone that is gone leaves, once the zones are read
    if (place.zoneId != null && zone == null) {
        if (zones.loaded) LaunchedEffect(place) { Navigator.drop(place) }
        return
    }
    @Composable
    fun name(text: String) = SideEffect { Navigator.name(place, text) }

    // A tool's page lays its ground once its tool is read; a zone's page on its texture, any other on none
    if (place is Place.Tool) return ToolPlace(place.id, place.zoneId, place, zone!!.name)
    if (place is Place.ToolPart) return ToolPlace(place.id, place.zoneId, place, zone!!.name)
    val texture = if (place is Place.Zone) ItemTexture.of(zone!!.texture, zone.icon_name, IconColor.of(zone.icon_color)) else null
    Page(texture) {
        when (place) {
            Place.Home -> {
                name(s.shared("nav_home"))
                HomeScreen(zones)
            }
            Place.HomeConfig -> {
                name(s.shared("nav_home_config"))
                AppSettingsScreen(category = AppSettingCategories.MAIN_SCREEN, onBack = back)
            }
            Place.Settings -> {
                name(s.shared("settings_title"))
                SettingsScreen(onBack = back, onOpen = { Navigator.push(Place.SettingsPage(it)) })
            }
            is Place.SettingsPage -> {
                name(s.shared("settings_${place.id}"))
                when (place.id) {
                    "ai_providers" -> AIProvidersScreen(onBack = back)
                    "apis" -> app.treelune.core.apis.ui.ApisScreen(onBack = back)
                    "identities" -> app.treelune.core.identities.ui.IdentitiesScreen(onBack = back)
                    "format" -> AppSettingsScreen(category = AppSettingCategories.FORMAT, onBack = back)
                    // Under its limits, what the day spent and the providers the budget cannot count
                    "ai_limits" -> AppSettingsScreen(category = AppSettingCategories.AI_LIMITS, onBack = back,
                        below = { app.treelune.core.ai.budget.ui.BudgetCard() })
                    "validation" -> AppSettingsScreen(category = AppSettingCategories.VALIDATION_CONFIG, onBack = back)
                    "ui" -> AppSettingsScreen(category = AppSettingCategories.UI, onBack = back)
                    "github" -> AppSettingsScreen(category = AppSettingCategories.GITHUB, onBack = back)
                    "demo" -> DemoSettingsScreen(onBack = back)
                    "external_access" -> app.treelune.core.mcp.ui.ExternalAccessSettingsScreen(onBack = back)
                    "data" -> DataSettingsScreen(onBack = back)
                    "write_history" -> app.treelune.core.history.ui.WriteHistoryScreen(onBack = back)
                    "logs" -> LogsScreen(onBack = back)
                    "bug_report" -> BugReportScreen(onBack = back)
                    "about" -> app.treelune.core.ui.screens.settings.AboutScreen(onBack = back)
                    else -> throw IllegalArgumentException("No settings page '${place.id}'")
                }
            }
            Place.Export -> {
                name(s.shared("share_export_title"))
                app.treelune.core.ui.exports.ExportScreen(onBack = back)
            }
            is Place.Import -> {
                name(s.shared("share_import_title"))
                app.treelune.core.ui.exports.ImportScreen(place.uri, onBack = back)
            }
            Place.AIProviderKind -> {
                name(s.shared("ai_provider_choose_kind"))
                app.treelune.core.ai.ui.screens.AIProviderKindScreen(onBack = back)
            }
            is Place.AIProvider -> app.treelune.core.ai.ui.screens.AIProviderScreen(place, onName = { name(it) }, onBack = back)
            is Place.Api -> app.treelune.core.apis.ui.ApiScreen(place, onName = { name(it) }, onBack = back)
            is Place.Identity -> app.treelune.core.identities.ui.IdentityScreen(
                place.id,
                onName = { name(it) },
                onBack = back,
                onGone = { Navigator.drop(place) }
            )
            is Place.IdentityText -> app.treelune.core.identities.ui.IdentityTextScreen(place, onName = { name(it) }, onBack = back, onGone = { Navigator.drop(place) })
            is Place.CreateZone -> {
                name(s.shared("nav_new_zone"))
                CreateZoneScreen(onCancel = back, onCreate = back)
            }
            is Place.Zone -> {
                name(zone!!.name)
                app.treelune.core.ui.components.ShownScreen { ZoneScreen(zone = zone) }
            }
            is Place.ZoneConfig -> {
                name(s.shared("nav_configuration"))
                CreateZoneScreen(
                    existingZone = zone,
                    onCancel = back,
                    onUpdate = back,
                    onDelete = { Navigator.dropZone(place.id) }
                )
            }
            is Place.Tool, is Place.ToolPart -> throw IllegalStateException("A tool's place lays its own page (ToolPlace)")
            is Place.ToolConfig -> {
                name(s.shared(if (place.toolId == null) "nav_new_tool" else "nav_configuration"))
                val toolType = requireNotNull(ToolTypeManager.getToolType(place.tooltype)) { "No tool type '${place.tooltype}'" }
                app.treelune.core.tools.ui.ToolConfigScreen(
                    toolType = toolType,
                    tooltype = place.tooltype,
                    zoneId = place.zoneId,
                    existingToolId = place.toolId,
                    onDone = back,
                    onCancel = back
                )
            }
            is Place.Variable -> {
                name(s.shared(if (place.variableId == null) "nav_new_variable" else "nav_variable"))
                app.treelune.core.ui.variables.VariableScreen(zoneId = place.zoneId, variableId = place.variableId, onDone = back)
            }
            is Place.Routine -> {
                app.treelune.core.ai.ui.routine.RoutineScreen(
                    routineId = place.id,
                    onName = { Navigator.name(place, it) },
                    onNavigateBack = back,
                    onNavigateToExecution = { sessionId -> Navigator.push(Place.Execution(sessionId, place.id, place.zoneId)) }
                )
            }
            is Place.Execution -> {
                name(s.shared("nav_execution"))
                // Its routine named too: opened from a notification, the run is stacked alone and the
                // breadcrumb names the routine it belongs to
                app.treelune.core.ai.ui.routine.ExecutionDetailScreen(sessionId = place.sessionId, onRoutineName = { Navigator.name(place.parent(), it) }, onNavigateBack = back)
            }
            is Place.Task -> app.treelune.core.tasks.ui.TaskScreen(place, onName = { Navigator.name(place, it) }, onBack = back)
            is Place.TaskConfig -> {
                name(s.shared(if (place.taskId == null) "task_new_name" else "nav_configuration"))
                // Deleted, its page under the config goes with it
                app.treelune.core.tasks.ui.TaskConfigScreen(place, onDone = back, onDeleted = {
                    place.taskId?.let { Navigator.drop(Place.Task(it, place.zoneId)) }
                    back()
                })
            }
            is Place.ReadoutConfig -> {
                name(s.shared(if (place.readoutId == null) "readout_new_name" else "nav_configuration"))
                app.treelune.core.readouts.ui.ReadoutConfigScreen(place, onDone = back)
            }
            is Place.TaskRun -> {
                name(s.shared("nav_execution"))
                app.treelune.core.tasks.ui.TaskRunScreen(place, onTaskName = { Navigator.name(place.parent(), it) }, onBack = back)
            }
            is Place.RoutineConfig -> {
                name(s.shared(if (place.routineId == null) "routine_new_name" else "nav_configuration"))
                // Deleted, its page under the config goes with it
                app.treelune.core.ai.ui.routine.RoutineConfigScreen(place, onDone = back, onDeleted = {
                    place.routineId?.let { Navigator.drop(Place.Routine(it, place.zoneId)) }
                    back()
                })
            }
            Place.Guide -> {
                name(s.shared("guide_title"))
                app.treelune.core.guide.ui.GuideScreen()
            }
            is Place.Chapter -> app.treelune.core.guide.ui.ChapterScreen(place.id, demoInstalled)
            Place.Chat -> throw IllegalStateException("The chat is laid over a place, never drawn as one")
        }
    }
}

/**
 * A place's page: its ground, textured for a zone's or a tool's page (ThemeContract.PageGround,
 * docs/THEMES.md, « What a theme is in the app »), across the whole width; then every place the same margin from
 * the sides of the screen, given here once: a place's screen sets none of its own.
 *
 * The page is moved whole from one theme's ground to another's when the theme changes, as the
 * screens are from one frame to another (MainActivity): each theme draws PageGround its own way,
 * and without moving it the place's screen would start over, the interface settings being edited
 * among them, which would take the choice for one left unsaved and put the stored theme back.
 */
@Composable
private fun Page(texture: ItemTexture?, content: @Composable () -> Unit) {
    val page = remember { movableContentOf { shown: @Composable () -> Unit ->
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = UI.Space.EDGE)) { shown() }
    } }
    CurrentTheme.current.PageGround(texture) { page(content) }
}

/**
 * A tool's screen, or a page inside it ([place] a Place.ToolPart), the tool read by its id and read
 * again when its zone's tools change (its config saved). A tool that is not found any more
 * (deleted from its config screen) leaves, and its pages with it.
 */
@Composable
private fun ToolPlace(toolId: String, zoneId: String, place: Place, zoneName: String) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    // The entry to open, taken from the stack once and kept by the place across recreation
    val opening by rememberSaveable(stateSaver = OpenAt.Saver) { mutableStateOf(if (place is Place.Tool) Navigator.take(place) else null) }
    var tool by remember { mutableStateOf<Map<*, *>?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(toolId, version) {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
        if (result.status == CommandStatus.SUCCESS) {
            tool = result.data?.get("tool_instance") as Map<*, *>
        } else {
            LogManager.ui("ToolPlace: tool $toolId not read, leaving its place: ${result.error}", "WARN")
            Navigator.drop(place)
        }
    }
    LaunchedEffect(toolId) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolsChanged && event.zoneId == zoneId) version++
        }
    }
    val current = tool ?: return
    val tooltype = current["tooltype"] as String
    if (place is Place.Tool) SideEffect { Navigator.name(place, current["name"] as String) }
    // A page stacked alone names its tool in the breadcrumb
    else SideEffect { Navigator.name(Place.Tool(toolId, zoneId), current["name"] as String) }
    val config = JsonUtils.toJSONObject(current["config"] as Map<String, Any?>)
    // The texture as its tile draws it (UI.ToolCard), its type's icon standing for none
    val settings = app.treelune.core.tools.ToolConfigSettings.read(tooltype, config, context)
    val texture = ItemTexture.of(settings.string(app.treelune.core.themes.Texture.KEY), settings.string("icon_name"), IconColor.of(settings.string(IconColor.KEY)))
    val toolType = ToolTypeManager.getToolType(tooltype) ?: return
    val parts = remember(toolId, zoneId) { app.treelune.core.tools.ToolParts { Navigator.push(Place.ToolPart(toolId, zoneId, it)) } }
    Page(texture) {
        CompositionLocalProvider(app.treelune.core.tools.LocalToolParts provides parts) {
            app.treelune.core.ui.components.ShownScreen {
                when (place) {
                    is Place.ToolPart -> toolType.PartScreen(
                        toolInstanceId = toolId,
                        configJson = config.toString(),
                        part = place.part,
                        onName = { Navigator.name(place, it) },
                        onBack = { Navigator.pop() },
                        onGone = { Navigator.drop(place) }
                    )
                    else -> toolType.getUsageScreen(
                        toolInstanceId = toolId,
                        configJson = config.toString(),
                        zoneName = zoneName,
                        onNavigateBack = { Navigator.pop() },
                        onLongClick = { Navigator.push(Place.ToolConfig(zoneId, tooltype, toolId)) },
                        openAt = opening
                    )
                }
            }
        }
    }
}

/** The zones and the home screen's groups, read once and again whenever either changes. */
class ZonesState {
    var list by mutableStateOf<List<Zone>>(emptyList())
    var groups by mutableStateOf<List<String>>(emptyList())
    /** Both read at least once: a zone missing from [list] is then gone. */
    var loaded by mutableStateOf(false)
}

@Composable
private fun rememberZones(onError: (String) -> Unit): ZonesState {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val state = remember { ZonesState() }
    /**
     * The zones and the groups, both read before either is shown: zones shown against groups not
     * read yet (at the start, or a group just added) would each hold a group the home screen does
     * not have, logged as a bug at every drawing.
     */
    suspend fun read() {
        val zones = coordinator.executeWithLoading(
            operation = "zones.list",
            params = mapOf("include_position" to true),
            onLoading = {},
            onError = onError
        )?.mapData("zones") { zoneFrom(it) } ?: return
        val groups = coordinator.executeWithLoading(
            operation = "app_config.get",
            params = mapOf("category" to AppSettingCategories.MAIN_SCREEN),
            onLoading = {},
            onError = onError
        )?.let { result -> ((result.data?.get("settings") as Map<*, *>)["zone_groups"] as List<*>).map { it as String } } ?: return
        state.groups = groups
        state.list = zones
        // Every zone named for the breadcrumb, where a tool stacked alone names its zone
        zones.forEach { Navigator.name(Place.Zone(it.id), it.name) }
    }
    LaunchedEffect(Unit) {
        read()
        state.loaded = true
    }
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ZonesChanged, is DataChangeEvent.AppConfigChanged -> read()
                else -> {}
            }
        }
    }
    return state
}

/**
 * The home screen: the zones by group, each opened on top of it; its header opens the settings
 * and its own configuration, a zone's long touch the zone's configuration.
 */
@Composable
private fun HomeScreen(zones: ZonesState) = app.treelune.core.readouts.ui.ProvideReadoutColumns(null) { HomeScreenBody(zones) }

/** The home screen, its gathered readouts in the columns it sets (ProvideReadoutColumns). */
@Composable
private fun HomeScreenBody(zones: ZonesState) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // Whether the settings button is marked, read each time the home screen shows
    LaunchedEffect(Unit) { app.treelune.core.backup.AutoBackup.refresh(context) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val zoneGroups = zones.groups

    // Every readout, for those raised to the home screen: its head, a group's head, a zone's tile
    val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }
    var readouts by remember { mutableStateOf<List<app.treelune.core.readouts.ui.ReadoutRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        suspend fun read() = app.treelune.core.readouts.ui.ReadoutRow.all(coordinator).onSuccess { readouts = it }.onFailure { errorMessage = it.message }
        read()
        DataChangeNotifier.changes.collect { if (it is DataChangeEvent.ZonesChanged) read() }
    }
    // The pieces of the zones whose tile lists some (docs/design/icon-tiles.md), read again when
    // the zones' tools, routines, Tasks, variables or readouts change
    var pieceTiles by remember { mutableStateOf<Map<String, List<app.treelune.core.ui.components.SectionTile>>>(emptyMap()) }
    val listing = zones.list.filter { it.tile_pieces != null }.map { it.id }
    LaunchedEffect(listing) {
        suspend fun read() {
            pieceTiles = listing.associateWith { id ->
                app.treelune.core.ui.components.zonePieceTiles(coordinator, id).getOrElse { errorMessage = it.message; emptyList() }
            }
        }
        read()
        DataChangeNotifier.changes.collect {
            if (it is DataChangeEvent.ToolsChanged || it is DataChangeEvent.ZonesChanged || it is DataChangeEvent.VariablesChanged) read()
        }
    }
    val piecesOf = { zone: Zone -> app.treelune.core.ui.components.listedPieces(zone.tile_pieces, pieceTiles[zone.id].orEmpty()) }

    // The order of the tree: the zones as the home screen shows them, each section's shown ones then its hidden ones, each zone's groups as its screen does
    val zoneOrder = app.treelune.core.grid.Section.ordered(zoneGroups).flatMap { section ->
        zones.list.filter { app.treelune.core.grid.ZonePositions.part(it, zoneGroups) == section }.sortedWith(compareBy({ it.grid_y }, { it.grid_x })).map { it.id }
    }
    val groupsOf = { zoneId: String -> app.treelune.core.grid.ToolPositions.zoneGroups(zones.list.firstOrNull { it.id == zoneId }?.tool_groups) }
    fun rising(place: app.treelune.core.readouts.ReadoutSettings.Place) = app.treelune.core.readouts.ui.risingTo(readouts, place, zoneOrder, groupsOf)

    // The edit mode of the zone groups' grids, a section named by its key (Section.key): a group's,
    // "" for the ungrouped one, or the hidden part just after it
    val gridEditor = app.treelune.core.ui.components.rememberGridEditor(
        placeOperation = "zones.place",
        placeParams = emptyMap(),
        sectionTiles = { key -> app.treelune.core.grid.ZonePositions.tiles(zones.list, zoneGroups, app.treelune.core.grid.Section.ofKey(key)) },
        // Its groups, then the ungrouped section, each followed by its hidden part: what an arrow crosses into
        sectionOrder = { app.treelune.core.grid.Section.ordered(zoneGroups).map { it.key } },
        onError = { errorMessage = it }
    )

    // Whether the hidden zones show, never remembered from a visit to the next
    var showHidden by remember { mutableStateOf(false) }
    app.treelune.core.ui.components.CloseEditOnLeave(gridEditor)

    Box(modifier = Modifier.fillMaxSize()) {
      // The bar of a zone being moved goes under the scrolling content
      Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pageScroll()
                .padding(vertical = UI.Space.L),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            UI.PageHeader(
                title = s.shared("app_name"),
                subtitle = null,
                icon = null,
                appMark = true,
                titleShown = app.treelune.core.themes.CurrentTheme.homeTitle,
                leftButton = ButtonAction.SETTINGS,
                // Marked while Settings › Data has something to look at (the automatic backup)
                leftMarked = app.treelune.core.ui.waiting(app.treelune.core.ui.WaitingSource.BACKUP),
                rightButton = ButtonAction.CONFIGURE,
                onLeftClick = { Navigator.push(Place.Settings) },
                onRightClick = { Navigator.push(Place.HomeConfig) },
                // Organising and adding, once for the whole page, before its gear: a zone created chooses its group in its form
                beforeRight = {
                    // A hidden zone with something waiting or running marks the button that shows them
                    val waiting = app.treelune.core.ui.LocalWaiting.current
                    val running = app.treelune.core.ui.LocalRunning.current
                    val hiddenZones = zones.list.filter { it.hidden }
                    app.treelune.core.ui.components.GridHeaderButtons(zones.list.isNotEmpty(), gridEditor,
                        if (hiddenZones.isEmpty()) null else app.treelune.core.ui.components.HiddenTiles(showHidden,
                            hiddenZones.any { waiting.zone(it.id) || running.zone(it.id) }) { showHidden = !showHidden }
                    ) { Navigator.push(Place.CreateZone) }
                },
                // The Guide's book after the settings, marked while a tutorial waits
                afterLeft = {
                    Box {
                        UI.ActionButton(action = ButtonAction.GUIDE, display = ButtonDisplay.ICON) { Navigator.push(Place.Guide) }
                        if (app.treelune.core.ui.waiting(app.treelune.core.ui.WaitingSource.GUIDE)) Box(modifier = Modifier.align(Alignment.TopEnd)) { UI.WaitingMark() }
                    }
                }
            )

            if (!zones.loaded) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            } else {
                // The readouts raised to the head of the home screen
                app.treelune.core.readouts.ui.ReadoutLines(rising(app.treelune.core.readouts.ReadoutSettings.Place.HOME_HEAD))
                // The section a zone stands in (Section.key); while a move crosses sections, where it puts it
                val keyOf = { zone: Zone -> gridEditor.sectionOf(zone.id) ?: app.treelune.core.grid.ZonePositions.part(zone, zoneGroups).key }
                val editing = gridEditor.anyEditing
                val groupHead = rising(app.treelune.core.readouts.ReadoutSettings.Place.ZONE_GROUP_HEAD)
                // Each group, then the ungrouped section, each followed by its hidden part
                (zoneGroups.map { it as String? } + null).forEach { group ->
                    val shownKey = app.treelune.core.grid.Section(group).key
                    val hiddenKey = app.treelune.core.grid.Section(group, hidden = true).key
                    val shown = zones.list.filter { keyOf(it) == shownKey }
                    val hidden = zones.list.filter { keyOf(it) == hiddenKey }
                    // A group whose zones are all hidden goes, title included, while they are; an
                    // empty group stays. Beside groups, the ungrouped section shows only when it
                    // holds a zone (one is added from the page's header); without groups it is the
                    // page's one section, kept unless all of it is hidden. In edit mode every
                    // section and every hidden part is there: a zone crosses in
                    val shownThere = when {
                        editing -> true
                        shown.isNotEmpty() -> true
                        hidden.isNotEmpty() -> showHidden
                        else -> group != null || zoneGroups.isEmpty()
                    }
                    val title = group ?: s.shared(if (zoneGroups.isNotEmpty()) "label_ungrouped" else "label_zones")
                    if (shownThere) ZoneGroupSection(
                        key = shownKey,
                        title = title,
                        zones = shown,
                        // The readouts of its zones raised to its head, a hidden zone's too: hiding leaves its place at the head
                        groupHead = groupHead.filter { readout -> (shown + hidden).any { it.id == readout.zoneId } },
                        empty = s.shared(if (group != null) "message_no_zones_in_group" else "message_no_ungrouped_zones"),
                        piecesOf = piecesOf,
                        editor = gridEditor,
                        onZoneClick = { zone -> Navigator.push(Place.Zone(zone.id)) },
                        onZoneLongClick = { zone -> Navigator.push(Place.ZoneConfig(zone.id)) }
                    )
                    if (editing || (showHidden && hidden.isNotEmpty())) ZoneGroupSection(
                        key = hiddenKey,
                        title = s.shared("label_hidden_part"),
                        zones = hidden,
                        groupHead = emptyList(),
                        empty = s.shared("message_no_hidden_tiles"),
                        piecesOf = piecesOf,
                        editor = gridEditor,
                        onZoneClick = { zone -> Navigator.push(Place.Zone(zone.id)) },
                        onZoneLongClick = { zone -> Navigator.push(Place.ZoneConfig(zone.id)) }
                    )
                }
            }
        }
        if (gridEditor.anyEditing) app.treelune.core.ui.components.GridEditBar(gridEditor)
      }

    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(message, Duration.LONG)
            errorMessage = null
        }
    }
}

/**
 * A section of the home screen, [key] (Section.key) titled [title]: a zone group's, the ungrouped
 * one, or a hidden part; its [zones] on its grid, in edit mode while the page is, or [empty] when
 * it holds none, the readouts of [groupHead] raised to its head. Adding and organising are the
 * page header's (GridHeaderButtons).
 */
@Composable
private fun ZoneGroupSection(
    key: String,
    title: String,
    zones: List<Zone>,
    groupHead: List<app.treelune.core.readouts.ui.ReadoutRow>,
    empty: String,
    /** The pieces a zone's tile lists, found among its own (ListedPiece). */
    piecesOf: (Zone) -> List<app.treelune.core.ui.components.ListedPiece>,
    editor: app.treelune.core.ui.components.GridEditor,
    onZoneClick: (Zone) -> Unit,
    onZoneLongClick: (Zone) -> Unit
) {
    // One block: its title, its readouts, its grid, close together; the page puts one space
    // between two sections, never its own between each of their parts
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
    // Section header: its title over a divider (SECTION_HEADER)
    UI.Card(type = CardType.SECTION_HEADER) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UI.Text(
                text = title,
                type = TextType.HEADING
            )
        }
    }

    app.treelune.core.readouts.ui.ReadoutLines(groupHead)

    if (zones.isEmpty()) {
        UI.Card(type = CardType.DEFAULT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                contentAlignment = Alignment.Center
            ) {
                UI.Text(
                    text = empty,
                    type = TextType.CAPTION,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // The zones on their grid, in edit mode while the page is
        app.treelune.core.ui.components.GridLayout(zones, zones.map { app.treelune.core.ui.components.zoneGridTile(it, piecesOf(it)) }, zones.map { app.treelune.core.look.TileSize.ofStored(it.tile_size).followsContent }, editor.gridEdit(key)) { zone ->
            UI.ZoneCard(
                zone = zone,
                pieces = piecesOf(zone),
                onClick = { onZoneClick(zone) },
                onLongClick = { onZoneLongClick(zone) }
            )
        }
    }
    }
}

/**
 * A zone from one entry of zones.list. Every field the list returns is read here, and only
 * here: three copies of this used to drop whatever they did not name, the icon among them.
 */
private fun zoneFrom(map: Map<String, Any?>): Zone = Zone(
    id = map["id"] as String,
    name = map["name"] as String,
    description = map["description"] as? String,
    icon_name = map["icon_name"] as? String,
    icon_color = map["icon_color"] as? String,
    texture = map[app.treelune.core.themes.Texture.KEY] as? String,
    tile_size = map["tile_size"] as String,
    grid_x = (map["grid_x"] as Number).toInt(),
    grid_y = (map["grid_y"] as Number).toInt(),
    created_at = (map["created_at"] as Number).toLong(),
    updated_at = (map["updated_at"] as Number).toLong(),
    tool_groups = (map["tool_groups"] as? List<*>)?.let { JsonUtils.toJSONArray(it).toString() },
    group = map["group"] as? String,
    validate = map["validate"] as Boolean,
    readout_columns = app.treelune.core.readouts.ReadoutColumns.stored(map[app.treelune.core.readouts.ReadoutColumns.KEY] as? String),
    tile_pieces = (map[app.treelune.core.look.TilePieces.KEY] as? List<*>)?.let { app.treelune.core.look.TilePieces.write(app.treelune.core.look.TilePieces.of(JsonUtils.toJSONArray(it))) },
    tile_pieces_icons = map[app.treelune.core.look.TilePieces.ICONS] as Boolean,
    hidden = map["hidden"] as Boolean
)
