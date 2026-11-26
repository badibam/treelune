package app.treelune.tools.path.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.treelune.core.drawing.diagram.DiagramReading
import app.treelune.core.drawing.diagram.DiagramTouch
import app.treelune.core.drawing.diagram.DiagramView
import app.treelune.tools.path.PathDiagram
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

/** What a Path's page and tile show: one of its runs, its root and its stages, every run's root, and the replay now; or why it cannot be read. */
private sealed interface PathShown {
    data class Run(val path: PathRead, val root: Stage, val stages: List<Stage>, val runs: List<Stage>, val outcome: PathReplay.Outcome,
                   val readouts: Map<String, app.treelune.core.readouts.ReadoutComputed>) : PathShown {
        /** The run drawn as the shared diagram, each stage in its state now, a closed run's frozen. */
        val diagram: DiagramReading.Drawn by lazy {
            DiagramReading.Drawn(PathDiagram.of(path.grammar, stages, { outcome.states[it.id]?.state }, { outcome.states[it.id]?.marks.orEmpty() }, { readouts[it.id] }), emptyMap())
        }

        /** The name of the state [stage] is in now; null when it has none. */
        fun stateName(stage: Stage): String? = outcome.states[stage.id]?.state?.let { path.grammar.kind(stage.kind)?.state(it)?.name }
    }
    data object NoRun : PathShown
    data class Problem(val message: String) : PathShown
}

/** The run [runId] of the Path [toolId], or by default its current one, else its latest. */
private suspend fun shown(context: android.content.Context, toolId: String, runId: String? = null): PathShown {
    return try {
        val path = PathRead.of(context, toolId) ?: return PathShown.Problem("")
        val runs = path.runs()
        val run = runs.firstOrNull { it.id == runId } ?: path.currentRun() ?: runs.firstOrNull() ?: return PathShown.NoRun
        val now = System.currentTimeMillis()
        val outcome = path.replay(now)
        val stages = path.stages.filter { it.run == run.id }
        PathShown.Run(path, run, stages, runs, outcome, path.readouts(stages, outcome, now))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        PathShown.Problem(e.message ?: "")
    }
}

/**
 * A Path's page (docs/design/progression.md, « Les écrans »): what to do with its runs, then the
 * run shown drawn whole, each stage a box painted by its state, a touch on it or on a group's
 * title opening its sheet; under it, every run with the state of its root, one touched shown in
 * its place. By default the current run, else the latest.
 */
@Composable
fun PathScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit, openAt: app.treelune.core.tools.OpenAt?) {
    val context = LocalContext.current
    val parts = app.treelune.core.tools.LocalToolParts.current
    // A stage named from outside (a notification) opens its sheet, once
    var opened by rememberSaveable { mutableStateOf(false) }
    (openAt as? app.treelune.core.tools.OpenAt.Entry)?.let { entry ->
        if (!opened) LaunchedEffect(entry.id) { opened = true; parts.open(PathParts.stage(entry.id)) }
    }
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    var runId by rememberSaveable { mutableStateOf<String?>(null) }
    var read by app.treelune.core.ui.rememberShown<PathShown?>("page:$toolInstanceId:run:$runId") { null }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(toolInstanceId, runId, version) { read = shown(context, toolInstanceId, runId) }
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
                shown.path.awaiting().forEach { root -> Awaiting(toolInstanceId, shown, root, onView = { runId = root.id }) }
                // The run shown, named with its root's state when it is not the current one
                if (shown.root.id != shown.path.currentRun()?.id) UI.Text(listOfNotNull(shown.root.name, shown.stateName(shown.root)).joinToString(" · "), TextType.SUBTITLE)
                if (shown.diagram.spec.nodes.isEmpty()) UI.Text(s.tool("screen_plan_empty"), TextType.CAPTION)
                else DiagramView(shown.diagram, folded = emptySet(), pageScroll = scroll, onTouch = { touch ->
                    when (touch) {
                        is DiagramTouch.Box -> parts.open(PathParts.stage(touch.node.id))
                        is DiagramTouch.Group -> parts.open(PathParts.stage(touch.id))
                        // One end of a cut arrow: the view scrolls to the other
                        is DiagramTouch.Ref -> Unit
                    }
                })
                shown.outcome.problems.forEach { UI.Text(it.message, TextType.ERROR) }
                if (shown.runs.size > 1) Runs(shown, onShow = { runId = it })
            }
        }
    }
}

/** A run awaiting its validation: its root's state for now, « View » showing it, « Validate » freezing it as it stands. */
@Composable
private fun Awaiting(toolInstanceId: String, shown: PathShown.Run, root: Stage, onView: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val validate = rememberValidate(toolInstanceId)
    UI.Card(type = app.treelune.core.ui.CardType.DEFAULT) {
        Column(Modifier.padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(s.tool("screen_awaiting").format(root.name, shown.stateName(root) ?: "–"), TextType.BODY)
            app.treelune.core.ui.components.ButtonRow {
                UI.Button(type = app.treelune.core.ui.ButtonType.PRIMARY, onClick = { validate(root.id) }) { UI.Text(s.tool("screen_validate"), TextType.LABEL) }
                if (root.id != shown.root.id) UI.Button(type = app.treelune.core.ui.ButtonType.DEFAULT, onClick = onView) { UI.Text(s.tool("screen_view_run"), TextType.LABEL) }
            }
        }
    }
}

/** Validates the run its root names: its state frozen as it stands (path.validate); a refusal said. */
@Composable
internal fun rememberValidate(toolInstanceId: String): (String) -> Unit {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return { run ->
        scope.launch {
            val result = coordinator.processUserAction("path.validate", mapOf("tool_instance_id" to toolInstanceId, "run" to run))
            if (!result.isSuccess) UI.Toast(result.error ?: s.shared("service_error_operation_failed"))
        }
    }
}

/** Every run, the latest opened first, each with its root's state (final once closed); the one shown marked, another touched shown. */
@Composable
private fun Runs(shown: PathShown.Run, onShow: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        UI.Text(s.tool("screen_runs"), TextType.HEADING)
        shown.runs.forEach { root ->
            val line = listOfNotNull(root.name, shown.stateName(root)).joinToString(" · ")
            Box(Modifier.fillMaxWidth().clickable { onShow(root.id) }) {
                UI.Text(line, if (root.id == shown.root.id) TextType.STRONG else TextType.BODY)
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
    val open = (read as? PathShown.Run)?.path?.currentRun() != null
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

/**
 * Where the tile is too small even for the drawing's dots: the short form of the root's readout
 * and its gauge (« 4 / 9 »), in the root's state's colour; the root's state without a readout.
 */
@Composable
private fun Glance(run: PathShown.Run) {
    val readout = run.readouts[run.root.id]
    val state = run.outcome.states[run.root.id]?.state?.let { run.path.grammar.kind(run.root.kind)?.state(it) }
    val color = state?.paint?.color?.let { name -> app.treelune.core.themes.TagColor.entries.firstOrNull { it.name == name } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(UI.Space.XS, androidx.compose.ui.Alignment.CenterVertically)) {
        UI.Text(readout?.core?.takeIf { it.isNotBlank() } ?: state?.name ?: run.root.name, TextType.BODY, maxLines = 1)
        readout?.gauge?.let { UI.Gauge(it.toFloat(), color) }
    }
}

/** A Path's tile: its root's state as its summary, its current run drawn as its body. */
@Composable
fun rememberPathTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    var read by app.treelune.core.ui.rememberShown<PathShown?>("tile:${tool.id}:run") { null }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool.id, tool.config_json, version) { read = shown(context, tool.id) }
    LaunchedEffect(tool.id) { DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ } }
    return remember(tool.id) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                when (val shown = read) {
                    is PathShown.Run -> {
                        UI.Text(shown.stateName(shown.root) ?: shown.root.name, TextType.CAPTION, maxLines = 2)
                    }
                    PathShown.NoRun -> UI.Text(s.tool("screen_no_run"), TextType.CAPTION, maxLines = 2)
                    is PathShown.Problem, null -> Unit
                }
            }

            /** The current run's drawing, scaled down to the body (DiagramView), sideways on a body wider than tall, a root in parts aside. */
            @Composable
            override fun Body(room: app.treelune.core.look.TileSize.Room) {
                val run = read as? PathShown.Run ?: return
                if (run.diagram.spec.nodes.isEmpty()) return
                if (room.rows == null) DiagramView(run.diagram, emptySet(), onTouch = null)
                else DiagramView(run.diagram, emptySet(), onTouch = null, modifier = Modifier.fillMaxSize(), reduced = true,
                    across = room.width > room.rows && !run.diagram.spec.parts, none = { Glance(run) })
            }
        }
    }
}
