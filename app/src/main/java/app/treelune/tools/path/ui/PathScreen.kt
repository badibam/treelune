package app.treelune.tools.path.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.pageScroll
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.tools.path.PathOrder
import app.treelune.tools.path.PathRead
import app.treelune.tools.path.PathReplay
import app.treelune.tools.path.PathRows
import app.treelune.tools.path.PathToolType
import app.treelune.tools.path.Stage
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.launch

/** What a Path's page and tile show: its current run in the plan's order, each stage's state, or why it cannot be read. */
private sealed interface PathShown {
    data class Run(val path: PathRead, val stages: List<Stage>, val outcome: PathReplay.Outcome) : PathShown
    data object NoRun : PathShown
    data class Problem(val message: String) : PathShown
}

private suspend fun shown(context: android.content.Context, toolId: String): PathShown {
    return try {
        val path = PathRead.of(context, toolId) ?: return PathShown.Problem("")
        val run = path.currentRun() ?: path.runs().firstOrNull() ?: return PathShown.NoRun
        PathShown.Run(path, PathOrder.planOrder(path.stages.filter { it.run == run.id }), path.replay(System.currentTimeMillis()))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        PathShown.Problem(e.message ?: "")
    }
}

/** One line per stage, indented by its depth: its name, then its state's name; on the page, a touch opens its sheet. */
@Composable
private fun Lines(read: PathShown.Run, limit: Int? = null, onOpen: ((String) -> Unit)? = null) {
    val byId = read.stages.associateBy { it.id }
    fun depth(stage: Stage): Int = generateSequence(stage.parent?.let { byId[it] }) { it.parent?.let { p -> byId[p] } }.count()
    val lines = read.stages.filter { !it.isRoot }
    (limit?.let { lines.take(it) } ?: lines).forEach { stage ->
        val state = read.outcome.states[stage.id]?.state
        val name = state?.let { read.path.grammar.kind(stage.kind)?.state(it)?.name } ?: "–"
        val line = "${"    ".repeat((depth(stage) - 1).coerceAtLeast(0))}${stage.name} · $name"
        if (onOpen == null) UI.Text(line, TextType.BODY)
        else androidx.compose.foundation.layout.Box(Modifier.clickable { onOpen(stage.id) }) { UI.Text(line, TextType.BODY) }
    }
}

/**
 * A Path's page, before its screens (docs/design/progression.md, « Les écrans »): the current
 * run's stages in the plan's order, each with its state, and what the replay could not do.
 */
@Composable
fun PathScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit, openAt: app.treelune.core.tools.OpenAt?) {
    val context = LocalContext.current
    val parts = app.treelune.core.tools.LocalToolParts.current
    // A stage named from outside (a notification) opens its sheet, once
    var opened by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    (openAt as? app.treelune.core.tools.OpenAt.Entry)?.let { entry ->
        if (!opened) LaunchedEffect(entry.id) { opened = true; parts.open(PathParts.stage(entry.id)) }
    }
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    var read by remember { androidx.compose.runtime.mutableStateOf<PathShown?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(toolInstanceId, version) { read = shown(context, toolInstanceId) }
    LaunchedEffect(toolInstanceId) { DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ } }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(vertical = UI.Space.L).pageScroll(scroll), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        val config = (read as? PathShown.Run)?.path?.config
        val settings = config?.let { ToolConfigSettings.read(PathToolType, it, context) }
        UI.PageHeader(
            title = settings?.string("name") ?: PathToolType.getDisplayName(context),
            subtitle = PathToolType.pageSubtitle(context, settings?.string("description")),
            icon = settings?.string("icon_name"),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )
        RunButtons(toolInstanceId, read)
        when (val shown = read) {
            null -> UI.LoadingIndicator()
            PathShown.NoRun -> UI.Text(s.tool("screen_no_run"), TextType.CAPTION)
            is PathShown.Problem -> UI.Text(shown.message, TextType.ERROR)
            is PathShown.Run -> {
                Lines(shown, onOpen = { parts.open(PathParts.stage(it)) })
                shown.outcome.problems.forEach { UI.Text(it.message, TextType.ERROR) }
            }
        }
    }
}

/**
 * What the page does with its runs: open the plan of the current one, stop it, or start one
 * when none is open (its plan copied from the latest, path.start).
 */
@Composable
private fun RunButtons(toolInstanceId: String, read: PathShown?) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val parts = app.treelune.core.tools.LocalToolParts.current
    if (read == null || read is PathShown.Problem) return
    val open = (read as? PathShown.Run)?.let { run -> !run.path.isClosed(run.stages.first { it.isRoot }) } ?: false
    fun send(action: String) = scope.launch {
        val result = coordinator.processUserAction(action, mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) UI.Toast(result.error ?: s.shared("service_error_operation_failed"))
    }
    app.treelune.core.ui.components.ButtonRow {
        if (open) UI.Button(type = app.treelune.core.ui.ButtonType.PRIMARY, onClick = { parts.open(PathParts.PLAN) }) { UI.Text(s.tool("plan_title"), TextType.LABEL) }
        if (open) UI.ActionButton(action = ButtonAction.STOP, requireConfirmation = true, confirmMessage = s.tool("screen_stop_confirm"), onClick = { send("path.stop") })
        else UI.ActionButton(action = ButtonAction.START, onClick = { send("path.start") })
    }
}

/** A Path's tile: its current run's first stages and their states. */
@Composable
fun rememberPathTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    var read by remember { androidx.compose.runtime.mutableStateOf<PathShown?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool.id, tool.config_json, version) { read = shown(context, tool.id) }
    LaunchedEffect(tool.id) { DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ } }
    return remember(tool.id) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                when (val shown = read) {
                    is PathShown.Run -> {
                        val root = shown.stages.first { it.isRoot }
                        val state = shown.outcome.states[root.id]?.state
                        UI.Text(state?.let { shown.path.grammar.kind(root.kind)?.state(it)?.name } ?: root.name, TextType.CAPTION, maxLines = 2)
                    }
                    PathShown.NoRun -> UI.Text(s.tool("screen_no_run"), TextType.CAPTION, maxLines = 2)
                    is PathShown.Problem, null -> Unit
                }
            }

            @Composable
            override fun Body(room: app.treelune.core.look.TileSize.Room) {
                (read as? PathShown.Run)?.let { Column { Lines(it, limit = room.rows?.times(2)) } }
            }
        }
    }
}
