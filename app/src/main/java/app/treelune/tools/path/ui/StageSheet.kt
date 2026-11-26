package app.treelune.tools.path.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.fields.FieldValue
import app.treelune.core.strings.Strings
import app.treelune.core.themes.TagColor
import app.treelune.core.tools.LocalToolParts
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.FieldValuesSaver
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.pageScroll
import app.treelune.core.ui.rememberShown
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateUtils
import app.treelune.tools.path.Kind
import app.treelune.tools.path.Move
import app.treelune.tools.path.PathRead
import app.treelune.tools.path.PathReplay
import app.treelune.tools.path.PathRows
import app.treelune.tools.path.PathService
import app.treelune.tools.path.Stage
import app.treelune.tools.path.StateRule
import kotlinx.coroutines.launch

/** What a stage's sheet shows: the stage read with its Path and its replay now, or why it cannot be. */
private sealed interface SheetShown {
    /**
     * @property refusals Why each manual state of its kind may not be noted now (its `note_if`), by key; absent when it may
     */
    data class Found(val path: PathRead, val stage: Stage, val kind: Kind, val outcome: PathReplay.Outcome, val closed: Boolean, val refusals: Map<String, String>) : SheetShown
    data object Gone : SheetShown
    data class Problem(val message: String) : SheetShown
}

private suspend fun sheet(context: android.content.Context, toolId: String, stageId: String): SheetShown = try {
    val path = PathRead.of(context, toolId)
    val stage = path?.stage(stageId)
    val kind = stage?.let { path.grammar.kind(it.kind) }
    when {
        path == null || stage == null -> SheetShown.Gone
        kind == null -> SheetShown.Problem(Strings.`for`(tool = PathRows.TOOLTYPE, context = context).tool("path_problem_kind").format(stage.kind))
        else -> {
            val now = System.currentTimeMillis()
            val closed = path.stage(stage.run)?.let { path.isClosed(it) } ?: false
            val service = PathService(context)
            val refusals = if (closed) emptyMap() else kind.states.filter { it.manual }
                .mapNotNull { state -> service.refuseNote(path, stage, state, now)?.let { state.key to it } }.toMap()
            SheetShown.Found(path, stage, kind, path.replay(now), closed, refusals)
        }
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: IllegalStateException) {
    SheetShown.Problem(e.message ?: "")
}

/**
 * A stage's sheet (docs/design/progression.md, « Les écrans »), the one way to read a stage, from
 * the Path's page and from its plan: its name and icon, its kind from two kinds of stage on; its
 * state now and since when; a button per manual state, refused ones greyed and saying why when
 * touched, a lower state of the ladder a step back; its plan fields; its fields to fill, written
 * from here; its links by type, each opening the sheet of the stage it comes from; its states
 * in the run. A closed run's stage is read only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageSheet(toolInstanceId: String, stageId: String, onName: (String) -> Unit, onBack: () -> Unit, onGone: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val parts = LocalToolParts.current
    var shown by rememberShown<SheetShown?>("page:$toolInstanceId:stage:$stageId") { null }
    var version by remember { mutableIntStateOf(0) }
    var writing by remember { mutableStateOf(false) }
    LaunchedEffect(toolInstanceId, stageId, version) { shown = sheet(context, toolInstanceId, stageId) }
    LaunchedEffect(toolInstanceId) { DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ } }
    // The fields to fill as edited here, taken from the stage once
    var filled by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(emptyMap()) }
    var filledTaken by rememberSaveable { mutableStateOf(false) }

    /** Sends [action] on this stage, then reads the sheet again; a refusal said. */
    fun write(action: String, params: Map<String, Any?>, after: () -> Unit = {}) {
        if (writing) return
        writing = true
        scope.launch {
            val result = coordinator.processUserAction(action, mapOf("tool_instance_id" to toolInstanceId, "stage" to stageId) + params)
            writing = false
            if (result.isSuccess) { after(); version++ } else UI.Toast(result.error ?: s.shared("service_error_operation_failed"))
        }
    }

    val found = shown as? SheetShown.Found
    if (shown == SheetShown.Gone) LaunchedEffect(Unit) { onGone() }
    found?.let { f -> LaunchedEffect(f.stage.name) { onName(f.stage.name) } }
    if (found != null && !filledTaken) LaunchedEffect(Unit) {
        filled = found.kind.fillFields.associate { it.name to found.stage.fields[it.name] }.filterValues { it != null }
        filledTaken = true
    }

    Column(Modifier.fillMaxSize().padding(vertical = UI.Space.L).pageScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
        val grammar = found?.path?.grammar
        UI.PageHeader(
            title = found?.stage?.name ?: "",
            // Its kind named from two kinds of stage on, the root's aside
            subtitle = found?.takeIf { grammar!!.kinds.count { k -> k.key != grammar.rootKind } >= 2 && !it.stage.isRoot }?.kind?.name,
            icon = found?.kind?.icon,
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )
        when (val current = shown) {
            null -> UI.LoadingIndicator()
            SheetShown.Gone -> Unit
            is SheetShown.Problem -> UI.Text(current.message, TextType.ERROR)
            is SheetShown.Found -> {
                val stage = current.stage
                val kind = current.kind
                val state = current.outcome.states[stage.id]
                val rule = (if (current.closed) stage.finalState else state?.state)?.let { kind.state(it) }

                // Its state now, painted, and since when
                Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                    StateName(rule)
                    if (!current.closed) state?.since?.let { UI.Text(s.tool("sheet_since").format(DateUtils.formatFullDateTime(it)), TextType.CAPTION) }
                    if (current.closed) UI.Text(s.tool("sheet_closed"), TextType.CAPTION)
                }

                if (!current.closed) StateButtons(kind, rule, stage, current.refusals, writing, { write("path.note", mapOf("state" to it)) }, { write("path.unnote", emptyMap()) })

                // The plan's fields, written by « Modifier »
                val planShown = kind.planFields.filter { stage.fields[it.name] != null }
                if (planShown.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                    planShown.forEach { field ->
                        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                            UI.Text(field.displayName, TextType.SUBTITLE)
                            FieldValue(field, stage.fields[field.name], context)
                        }
                    }
                }

                // The fields to fill, written here: a closed run's are read
                if (kind.fillFields.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                    UI.Text(s.tool("sheet_fill"), TextType.HEADING)
                    if (current.closed) kind.fillFields.forEach { field ->
                        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                            UI.Text(field.displayName, TextType.SUBTITLE)
                            FieldValue(field, stage.fields[field.name], context)
                        }
                    } else {
                        val values = filled
                        CustomFieldsInput(customFieldsMetadata = kind.fillFields, values = values, onValuesChange = { filled = it }, context = context)
                        val stored = kind.fillFields.associate { it.name to stage.fields[it.name] }.filterValues { it != null }
                        if (values != stored) UI.FormActions {
                            UI.ActionButton(action = ButtonAction.CANCEL, onClick = { filled = stored })
                            UI.ActionButton(action = ButtonAction.SAVE, enabled = !writing, onClick = {
                                // A field emptied is removed, as update_stage merges
                                write("path.update_stage", mapOf("fields" to kind.fillFields.associate { it.name to values[it.name] }))
                            })
                        }
                    }
                }

                // What it waits for, by link type, each opening its sheet
                val byType = stage.links.groupBy { it.type }
                current.path.grammar.linkTypes.filter { it.key in byType }.forEach { type ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                        UI.Text(s.tool("sheet_links_of").format(type.name), TextType.SUBTITLE)
                        byType.getValue(type.key).mapNotNull { current.path.stage(it.from) }.forEach { from ->
                            Box(Modifier.clickable { parts.open(PathParts.stage(from.id)) }) { UI.Text(from.name, TextType.STRONG) }
                        }
                    }
                }

                History(current, stage, kind)
            }
        }
    }
}

/** The state's name, in its colour when its paint gives one; « – » when it has none. */
@Composable
private fun StateName(rule: StateRule?) {
    val color = rule?.paint?.color?.let { name -> TagColor.entries.firstOrNull { it.name == name } }
    when {
        rule == null -> UI.Text("–", TextType.SUBTITLE)
        color != null -> UI.Tag(rule.name, color, maxLines = 2)
        else -> UI.Text(rule.name, TextType.SUBTITLE)
    }
}

/**
 * A button per manual state of [kind] but the current one, in the list's order, which is the
 * ladder: one above the current state notes it; one below is a step back (« Back to … », or its
 * `back_label`), set apart. Refused by its `note_if`, a button is greyed and says why when
 * touched. The last move, when a manual state's, may be undone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateButtons(kind: Kind, current: StateRule?, stage: Stage, refusals: Map<String, String>, writing: Boolean, onNote: (String) -> Unit, onUnnote: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val at = current?.let { kind.states.indexOf(it) } ?: -1
    val manual = kind.states.withIndex().filter { (_, it) -> it.manual && it.key != current?.key }
    val (back, ahead) = manual.partition { (index, _) -> at >= 0 && index > at }
    val last = stage.moves.lastOrNull { it.state != Move.START && it.state != Move.END }?.let { kind.state(it.state) }?.takeIf { it.manual }
    if (ahead.isEmpty() && back.isEmpty() && last == null) return

    @Composable
    fun StateButton(state: StateRule, label: String, type: ButtonType) {
        val refusal = refusals[state.key]
        UI.Button(type = type, state = if (refusal != null || writing) ComponentState.DISABLED else ComponentState.NORMAL, refusal = refusal, onClick = { onNote(state.key) }) {
            UI.Text(label, TextType.LABEL)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        if (ahead.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            ahead.forEach { (_, state) -> StateButton(state, state.name, ButtonType.PRIMARY) }
        }
        if (back.isNotEmpty() || last != null) FlowRow(Modifier.padding(start = UI.Space.L), horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            back.forEach { (_, state) -> StateButton(state, state.backLabel ?: s.tool("sheet_back_to").format(state.name), ButtonType.DEFAULT) }
            last?.let { noted ->
                UI.Button(type = ButtonType.DEFAULT, state = if (writing) ComponentState.DISABLED else ComponentState.NORMAL, onClick = onUnnote) {
                    UI.Text(s.tool("sheet_unnote").format(noted.name), TextType.LABEL)
                }
            }
        }
    }
}

/** Its states in its run, the latest first: its real moves, and those its kind's effects gave it, said so. */
@Composable
private fun History(shown: SheetShown.Found, stage: Stage, kind: Kind) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val virtual = shown.outcome.virtual[stage.id].orEmpty()
    val moves = (stage.moves.map { it to false } + virtual.map { it to true }).sortedByDescending { it.first.at }
    if (moves.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        UI.Text(s.tool("sheet_history"), TextType.HEADING)
        moves.forEach { (move, byEffect) ->
            val name = when (move.state) {
                Move.START -> s.tool("sheet_move_start")
                Move.END -> s.tool("sheet_move_end")
                else -> kind.state(move.state)?.name ?: move.state
            }
            Column(Modifier.fillMaxWidth()) {
                UI.Text("${DateUtils.formatFullDateTime(move.at)} · $name${if (byEffect) " · ${s.tool("sheet_by_effect")}" else ""}", TextType.BODY)
                move.note?.let { UI.Text(it, TextType.CAPTION) }
            }
        }
    }
}
