package app.treelune.tools.path.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.LocalToolParts
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Size
import app.treelune.core.ui.StringListSaver
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.pageScroll
import app.treelune.core.ui.rememberShown
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.tools.path.PathRead
import app.treelune.tools.path.PathReplay
import app.treelune.tools.path.PathRows
import app.treelune.tools.path.Stage
import kotlinx.coroutines.launch

/** What the plan editor shows: the current run's stages and their states now, or why it cannot. */
private sealed interface PlanShown {
    data class Run(val path: PathRead, val root: Stage, val stages: List<Stage>, val outcome: PathReplay.Outcome) : PlanShown
    data object NoRun : PlanShown
    data class Problem(val message: String) : PlanShown
}

private suspend fun plan(context: android.content.Context, toolId: String): PlanShown = try {
    val path = PathRead.of(context, toolId)
    val root = path?.currentRun()
    when {
        path == null -> PlanShown.Problem("")
        root == null -> PlanShown.NoRun
        else -> PlanShown.Run(path, root, path.stages.filter { it.run == root.id }, path.replay(System.currentTimeMillis()))
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: IllegalStateException) {
    PlanShown.Problem(e.message ?: "")
}

/**
 * The plan of the current run (docs/design/progression.md, « Les écrans »), indented like a table
 * of contents: one line per stage, a group folding its content. The root is its header: its name,
 * its sheet, the edit of its plan. Each open group ends with a line « + Week » per kind it may
 * hold, where the stage will be born; a line is dragged within its group only, « Move to… » of
 * its edit taking it elsewhere. A touch on a line opens its sheet.
 */
@Composable
fun PlanEditor(toolInstanceId: String, onName: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val parts = LocalToolParts.current
    var shown by rememberShown<PlanShown?>("page:$toolInstanceId:plan") { null }
    var version by remember { mutableIntStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    var folded by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(emptyList()) }
    // The stage being added: its group and its kind, « parent|kind »
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var newName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(toolInstanceId, version) { shown = plan(context, toolInstanceId) }
    LaunchedEffect(toolInstanceId) { DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ } }
    LaunchedEffect(Unit) { onName(s.tool("plan_title")) }

    fun write(action: String, params: Map<String, Any?>) {
        if (writing) return
        writing = true
        scope.launch {
            val result = coordinator.processUserAction(action, mapOf("tool_instance_id" to toolInstanceId) + params)
            writing = false
            if (result.isSuccess) version++ else UI.Toast(result.error ?: s.shared("service_error_operation_failed"))
        }
    }

    Column(Modifier.fillMaxSize().padding(vertical = UI.Space.L).pageScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        val run = shown as? PlanShown.Run
        UI.PageHeader(
            title = run?.root?.name ?: s.tool("plan_title"),
            subtitle = run?.let { s.tool("plan_title") },
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack,
            // The root's sheet, then the edit of its plan
            beforeRight = run?.let { r -> { UI.ActionButton(action = ButtonAction.VIEW, display = ButtonDisplay.ICON, onClick = { parts.open(PathParts.stage(r.root.id)) }) } },
            rightButton = run?.let { ButtonAction.EDIT },
            onRightClick = { run?.let { parts.open(PathParts.edit(it.root.id)) } }
        )
        when (val current = shown) {
            null -> UI.LoadingIndicator()
            PlanShown.NoRun -> UI.Text(s.tool("screen_no_run"), TextType.CAPTION)
            is PlanShown.Problem -> UI.Text(current.message, TextType.ERROR)
            is PlanShown.Run -> {
                val children = current.stages.groupBy { it.parent }

                @Composable
                fun Group(parent: Stage, depth: Int) {
                    val content = children[parent.id].orEmpty().sortedBy { it.position }
                    UI.ReorderableColumn(
                        items = content,
                        key = { it.id },
                        spacing = UI.Space.XS,
                        onMove = { from, to -> write("path.move_stage", mapOf("stage" to content[from].id, "position" to to)) }
                    ) { _, stage ->
                        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                            val kind = current.path.grammar.kind(stage.kind)
                            val holds = kind?.contains.orEmpty().isNotEmpty() || children[stage.id] != null
                            val closed = stage.id in folded
                            Row(Modifier.fillMaxWidth().padding(start = UI.Space.L * depth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                                DragHandle()
                                if (holds) UI.ActionButton(action = if (closed) ButtonAction.RIGHT else ButtonAction.DOWN, display = ButtonDisplay.ICON, size = Size.S,
                                    onClick = { folded = if (closed) folded - stage.id else folded + stage.id })
                                Box(Modifier.weight(1f).clickable { parts.open(PathParts.stage(stage.id)) }) {
                                    val state = current.outcome.states[stage.id]?.state?.let { kind?.state(it)?.name }
                                    UI.Text(listOfNotNull(stage.name, state).joinToString(" · "), TextType.BODY)
                                }
                            }
                            if (holds && !closed) Group(stage, depth + 1)
                        }
                    }
                    // Where a stage of each kind it holds is born: at its end, at its content's indent
                    current.path.grammar.kind(parent.kind)?.contains.orEmpty().forEach { key ->
                        val name = current.path.grammar.kind(key)?.name ?: key
                        Box(Modifier.padding(start = UI.Space.L * depth)) {
                            UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = { newName = ""; adding = "${parent.id}|$key" }) {
                                UI.Text(s.tool("plan_add").format(name), TextType.LABEL)
                            }
                        }
                    }
                }

                Group(current.root, 0)
                current.outcome.problems.forEach { UI.Text(it.message, TextType.ERROR) }

                adding?.let { add ->
                    val (parent, kind) = add.split("|", limit = 2)
                    UI.Dialog(type = DialogType.CREATE, confirmEnabled = newName.isNotBlank() && !writing, onCancel = { adding = null }, onConfirm = {
                        write("path.write_plan", mapOf("parent" to parent, "stages" to listOf(mapOf("kind" to kind, "name" to newName.trim()))))
                        adding = null
                    }) {
                        UI.FormField(label = current.path.grammar.kind(kind)?.name ?: kind, value = newName, onChange = { newName = it }, required = true)
                    }
                }
            }
        }
    }
}
