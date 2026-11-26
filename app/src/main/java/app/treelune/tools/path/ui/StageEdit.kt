package app.treelune.tools.path.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.FieldValuesSaver
import app.treelune.core.ui.Size
import app.treelune.core.ui.StringListSaver
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.rememberLoadOnce
import app.treelune.core.ui.LoadState
import app.treelune.tools.path.Link
import app.treelune.tools.path.PathRead
import app.treelune.tools.path.PathRows
import app.treelune.tools.path.Stage
import kotlinx.coroutines.launch

/**
 * The edit of a stage's plan (« Modifier », docs/design/progression.md, « Les écrans »): its
 * name, its plan fields and its links, saved together; then « Move to… », the run's plan to
 * choose its new group, and its deletion, confirmed with what goes with it. Opened from its
 * sheet and from the plan, so that using the Path never touches the plan by mistake. A closed
 * run's stage is not edited here: its run is computed no more.
 */
@Composable
fun StageEdit(toolInstanceId: String, stageId: String, onName: (String) -> Unit, onBack: () -> Unit, onGone: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = PathRows.TOOLTYPE, context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf<PathRead?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var fields by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(emptyMap()) }
    // Its links as « type|from », in the order written
    var links by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(emptyList()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }

    LaunchedEffect(toolInstanceId, stageId) {
        try {
            path = PathRead.of(context, toolInstanceId)
        } catch (e: IllegalStateException) {
            problem = e.message
        }
    }
    val load = rememberLoadOnce(toolInstanceId, stageId, path != null) {
        val stage = path?.stage(stageId) ?: return@rememberLoadOnce false
        name = stage.name
        fields = path!!.grammar.kind(stage.kind)?.planFields.orEmpty().associate { it.name to stage.fields[it.name] }.filterValues { it != null }
        links = stage.links.map { "${it.type}|${it.from}" }
        true
    }
    val read = path
    val stage = read?.stage(stageId)
    if (read != null && stage == null) LaunchedEffect(Unit) { onGone() }
    stage?.let { LaunchedEffect(Unit) { onName(s.shared("action_edit")) } }

    /** Sends [action] on this stage; done, [after]; a refusal said. */
    fun write(action: String, params: Map<String, Any?>, after: () -> Unit) {
        if (writing) return
        writing = true
        scope.launch {
            val result = coordinator.processUserAction(action, mapOf("tool_instance_id" to toolInstanceId, "stage" to stageId) + params)
            writing = false
            if (result.isSuccess) after() else UI.Toast(result.error ?: s.shared("service_error_operation_failed"))
        }
    }

    // A form, its buttons at its end: no chat's button over it
    app.treelune.core.ui.HideChatButton()
    Column(Modifier.fillMaxSize().padding(vertical = UI.Space.L).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
        UI.PageHeader(title = stage?.name ?: "", subtitle = s.shared("action_edit"), leftButton = ButtonAction.BACK, onLeftClick = onBack)
        problem?.let { UI.Text(it, TextType.ERROR) }
        if (read == null || stage == null || load != LoadState.LOADED) {
            if (problem == null) UI.LoadingIndicator()
            return@Column
        }
        val kind = read.grammar.kind(stage.kind)
        val run = read.stages.filter { it.run == stage.run }
        if (read.stage(stage.run)?.let { read.isClosed(it) } == true) {
            UI.Text(s.tool("path_error_closed"), TextType.CAPTION)
            return@Column
        }

        UI.FormField(label = s.shared("label_name"), value = name, onChange = { name = it }, required = true)
        kind?.planFields?.takeIf { it.isNotEmpty() }?.let { planFields ->
            CustomFieldsInput(customFieldsMetadata = planFields, values = fields, onValuesChange = { fields = it }, context = context)
        }

        // Its links by type: each removable, one added by choosing its source in the plan
        read.grammar.linkTypes.forEach { type ->
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                UI.Text(type.name, TextType.SUBTITLE)
                links.filter { it.substringBefore("|") == type.key }.forEach { link ->
                    val from = read.stage(link.substringAfter("|"))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { UI.Text(from?.name ?: link.substringAfter("|"), TextType.BODY) }
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { links = links - link })
                    }
                }
                UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = { picking = type.key }) { UI.Text(s.tool("edit_add_link").format(type.name), TextType.LABEL) }
            }
        }

        UI.FormActions {
            UI.ActionButton(action = ButtonAction.CANCEL, onClick = onBack)
            UI.ActionButton(action = ButtonAction.SAVE, enabled = !writing && name.isNotBlank(), onClick = {
                write("path.update_stage", mapOf(
                    "name" to name.trim(),
                    // A field emptied is removed, as update_stage merges
                    "fields" to kind?.planFields.orEmpty().associate { it.name to fields[it.name] },
                    "links" to links.map { mapOf("type" to it.substringBefore("|"), "from" to it.substringAfter("|")) }
                ), onBack)
            })
        }

        // Its place and its deletion; the root, which is its run, has neither
        if (!stage.isRoot) {
            UI.Divider()
            UI.Button(type = ButtonType.DEFAULT, onClick = { moving = true }) { UI.Text(s.tool("edit_move"), TextType.LABEL) }
            UI.ActionButton(action = ButtonAction.DELETE, enabled = !writing, onClick = { deleting = true })
        }

        picking?.let { type ->
            StagePickerDialog(
                title = s.tool("pick_link").format(read.grammar.linkTypes.firstOrNull { it.key == type }?.name ?: type),
                stages = run,
                refusal = { from ->
                    if ("$type|${from.id}" in links) s.tool("pick_linked")
                    else linkRefusal(read.grammar, run.map { if (it.id == stage.id) it.copy(links = links.map { l -> Link(l.substringAfter("|"), l.substringBefore("|")) }) else it }, stage, from, type) { s.tool(it) }
                },
                onPick = { from -> links = links + "$type|${from.id}"; picking = null },
                onCancel = { picking = null }
            )
        }
        if (moving) StagePickerDialog(
            title = s.tool("pick_group"),
            stages = run,
            refusal = { group -> moveRefusal(read, stage, group) { s.tool(it) } },
            onPick = { group -> moving = false; write("path.move_stage", mapOf("parent" to group.id), onBack) },
            onCancel = { moving = false }
        )
        if (deleting) UI.ConfirmDialog(
            title = s.shared("action_delete"),
            message = deletion(read, stage) { s.tool(it) },
            confirmText = s.shared("action_delete"),
            cancelText = s.shared("action_cancel"),
            onConfirm = { deleting = false; write("path.delete_stage", emptyMap(), onGone) },
            onDismiss = { deleting = false }
        )
    }
}

/** Why [stage] may not go into [group]: itself or its own content, or a group whose kind does not contain its kind. */
private fun moveRefusal(path: PathRead, stage: Stage, group: Stage, text: (String) -> String): String? {
    var up: Stage? = group
    while (up != null) {
        if (up.id == stage.id) return text("pick_inside")
        up = up.parent?.let { path.stage(it) }
    }
    val kind = path.grammar.kind(stage.kind)
    val groupKind = path.grammar.kind(group.kind)
    if (stage.kind !in groupKind?.contains.orEmpty()) return text("pick_contains").format(groupKind?.name ?: group.kind, kind?.name ?: stage.kind)
    return null
}

/** What deleting [stage] takes with it, in words: its content by kind, its moves, the stages that waited for what goes. */
private fun deletion(path: PathRead, stage: Stage, text: (String) -> String): String {
    val gone = mutableListOf<Stage>()
    fun collect(of: Stage) { gone += of; path.stages.filter { it.parent == of.id }.forEach { collect(it) } }
    collect(stage)
    val content = gone.drop(1).groupBy { it.kind }.map { (kind, stages) -> text("delete_count").format(path.grammar.kind(kind)?.name ?: kind, stages.size) }
    val ids = gone.map { it.id }.toSet()
    val waiting = path.stages.count { it.id !in ids && it.links.any { l -> l.from in ids } }
    val moves = gone.sumOf { it.moves.size }
    return listOfNotNull(
        text("delete_what").format(stage.name),
        content.takeIf { it.isNotEmpty() }?.joinToString(", "),
        text("delete_moves").format(moves),
        waiting.takeIf { it > 0 }?.let { text("delete_waiting").format(it) }
    ).joinToString("\n")
}
