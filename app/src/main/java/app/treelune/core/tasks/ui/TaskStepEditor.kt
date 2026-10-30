package app.treelune.core.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.apis.ApiSettings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.TextLength
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tasks.StepBlocks
import app.treelune.core.tasks.StepConditions
import app.treelune.core.tasks.TaskSteps
import app.treelune.core.terms.Term
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.selectors.ReadingContext
import app.treelune.core.ui.selectors.TermPicker
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/** What the steps before one give, by step index: what its inputs may take from them. */
internal typealias Earlier = List<Pair<Int, List<TaskSteps.Output>>>

/**
 * What a trigger gives the Task's runs, which an input may take: the fields of the entry written
 * by path (TaskSteps.receivedFields), the values of the event emitted by name
 * (TaskSteps.receivedValues); each null when no trigger gives it.
 */
internal data class Receivable(val entry: Map<String, FieldDefinition>?, val values: Map<String, FieldDefinition>?)

internal val LocalReceived = androidx.compose.runtime.staticCompositionLocalOf { Receivable(null, null) }

/**
 * The form of one step, [step] in its stored form (TaskSteps), its kind already chosen: what it
 * names (an API, a tool, an operation, a routine), then each of its inputs (StepInput).
 */
@Composable
internal fun StepEditor(step: JSONObject, earlier: Earlier, onChange: (JSONObject) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    fun with(change: JSONObject.() -> Unit) = onChange(JSONObject(step.toString()).apply(change))
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        when (step.optString(TaskSteps.KIND)) {
            TaskSteps.CALL_API -> CallApi(step, earlier, s, ::with)
            TaskSteps.CREATE_ENTRY, TaskSteps.UPDATE_ENTRY -> WriteEntry(step, earlier, s, ::with)
            TaskSteps.TOOL_OPERATION -> ToolOperation(step, earlier, s, ::with)
            TaskSteps.RUN_ROUTINE -> RunRoutine(step, earlier, s, ::with)
            TaskSteps.EMIT_EVENT -> EmitEvent(step, earlier, s, ::with)
        }
        if (step.optString(TaskSteps.KIND).isNotEmpty()) StepWhen(step, earlier, s, ::with)
    }
}

/** A choice among [options] (id to label), [selected] by its id. */
@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    UI.FormSelection(label = label, options = options.map { it.second }, selected = options.firstOrNull { it.first == selected }?.second ?: "",
        onSelect = { chosen -> options.firstOrNull { it.second == chosen }?.let { onSelect(it.first) } }, required = true)
}

@Composable
private fun CallApi(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val context = LocalContext.current
    var apis by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    LaunchedEffect(Unit) {
        apis = (Coordinator(context).processUserAction("apis.list").data?.get("apis") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    val apiId = step.optString(TaskSteps.API_ID).ifEmpty { null }
    Choice(s.shared("task_step_api"), apis.map { it["id"] as String to it["name"] as String }, apiId) { id ->
        with { put(TaskSteps.API_ID, id); put(TaskSteps.INPUTS, JSONObject()) }
    }
    if (apiId == null) return
    // The API's inputs, each of its type
    var inputs by remember(apiId) { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    LaunchedEffect(apiId) {
        @Suppress("UNCHECKED_CAST")
        val config = (Coordinator(context).processUserAction("apis.get", mapOf("api_id" to apiId)).data?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        inputs = config?.let { ApiSettings.inputFields(it) }.orEmpty()
    }
    Inputs(step.optJSONObject(TaskSteps.INPUTS) ?: JSONObject(), inputs, earlier, s) { with { put(TaskSteps.INPUTS, it) } }
}

/** A tool, chosen through the browser among the app's. */
@Composable
private fun ToolChoice(step: JSONObject, s: StringsContext, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    val id = step.optString(TaskSteps.TOOL_INSTANCE_ID).ifEmpty { null }
    FieldInput(
        FieldDefinition("tool", s.shared("task_step_tool"), null, FieldType.REFERENCE, false,
            mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.TOOL_INSTANCE.name)))),
        id?.let { mapOf("kind" to ReferenceKind.TOOL_INSTANCE.name, "id" to it) },
        { value -> ReferenceTarget.referenceOf(value)?.id?.let(onChoose) },
        context, required = true
    )
}

/** An entry of tool [toolId], given as a constant (the browser) or the entry an earlier step created. */
private fun entryField(toolId: String, s: StringsContext) = FieldDefinition(TaskSteps.ENTRY, s.shared("task_step_entry"), null, FieldType.REFERENCE, false,
    mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name), ReferenceTarget.TOOL_INSTANCES to listOf(toolId))))

@Composable
private fun WriteEntry(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val context = LocalContext.current
    ToolChoice(step, s) { id -> with { put(TaskSteps.TOOL_INSTANCE_ID, id); put(TaskSteps.VALUES, JSONObject()); remove(TaskSteps.ENTRY) } }
    val toolId = step.optString(TaskSteps.TOOL_INSTANCE_ID).ifEmpty { return }
    if (step.optString(TaskSteps.KIND) == TaskSteps.UPDATE_ENTRY) {
        StepInput(step.optJSONObject(TaskSteps.ENTRY), entryField(toolId, s), earlier, s, optional = false) { input ->
            with { if (input == null) remove(TaskSteps.ENTRY) else put(TaskSteps.ENTRY, input) }
        }
    }
    var fields by remember(toolId) { mutableStateOf<List<FieldDefinition>>(emptyList()) }
    LaunchedEffect(toolId) {
        fields = try {
            TaskSteps.writableFields(toolId, context, s).map { (path, field) -> field.copy(name = path) }
        } catch (e: IllegalStateException) {
            emptyList()
        }
    }
    Inputs(step.optJSONObject(TaskSteps.VALUES) ?: JSONObject(), fields, earlier, s) { with { put(TaskSteps.VALUES, it) } }
}

@Composable
private fun ToolOperation(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val context = LocalContext.current
    ToolChoice(step, s) { id -> with { put(TaskSteps.TOOL_INSTANCE_ID, id); remove(TaskSteps.OPERATION); put(TaskSteps.PARAMS, JSONObject()) } }
    val toolId = step.optString(TaskSteps.TOOL_INSTANCE_ID).ifEmpty { return }
    var tooltype by remember(toolId) { mutableStateOf<String?>(null) }
    LaunchedEffect(toolId) {
        tooltype = (Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolId)).data?.get("tool_instance") as? Map<*, *>)?.get("tooltype") as? String
    }
    val type = tooltype?.let { ToolTypeManager.getToolType(it) } ?: return
    val operations = remember(type) { type.getOperations(context) }
    if (operations.isEmpty()) {
        UI.Text(s.shared("task_step_no_operation"), TextType.CAPTION)
        return
    }
    val chosen = step.optString(TaskSteps.OPERATION).ifEmpty { null }
    Choice(s.shared("task_step_operation"), operations.map { it.name to it.description }, chosen) { name ->
        with { put(TaskSteps.OPERATION, name); put(TaskSteps.PARAMS, JSONObject()) }
    }
    val operation = operations.firstOrNull { it.name == chosen } ?: return
    // An entry's id is chosen as an entry of the tool, never typed
    val params = operation.params.filterIsInstance<app.treelune.core.fields.settings.SettingNode.Field>().map { node ->
        if (node.definition.name == "id") entryField(toolId, s).copy(name = "id", displayName = node.definition.displayName) else node.definition
    }
    Inputs(step.optJSONObject(TaskSteps.PARAMS) ?: JSONObject(), params, earlier, s) { with { put(TaskSteps.PARAMS, it) } }
}

@Composable
private fun RunRoutine(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val context = LocalContext.current
    var routines by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    LaunchedEffect(Unit) {
        routines = (Coordinator(context).processUserAction("routines.list_all").data?.get("routines") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    Choice(s.shared("task_step_routine"), routines.map { it["id"] as String to it["name"] as String }, step.optString(TaskSteps.ROUTINE_ID).ifEmpty { null }) { id ->
        with { put(TaskSteps.ROUTINE_ID, id) }
    }
    UI.Text(s.shared("task_step_blocks"), TextType.LABEL)
    UI.Text(s.shared("task_step_blocks_help"), TextType.CAPTION)
    StepBlocksEditor(step, earlier, s) { blocks -> with { put(StepBlocks.BLOCKS, StepBlocks.toJson(blocks)) } }
}

/**
 * What a `run_routine` step joins to its routine's message (StepBlocks), in the frame of a
 * routine's own blocks (BlockCard), reordered by their handle: a text, a pointer to a thing
 * chosen, an entry the run gives — joined whole or only mentioned —, a value it reads.
 */
@Composable
private fun StepBlocksEditor(step: JSONObject, earlier: Earlier, s: StringsContext, onChange: (List<StepBlocks.Block>) -> Unit) {
    // Each block with an id of the screen's alone, for the reorder to follow it
    var items by remember {
        mutableStateOf(try { StepBlocks.of(step) } catch (e: IllegalArgumentException) { emptyList() }.map { java.util.UUID.randomUUID().toString() to it })
    }
    fun update(next: List<Pair<String, StepBlocks.Block>>) {
        items = next
        onChange(next.map { it.second })
    }
    fun replace(id: String, block: StepBlocks.Block) = update(items.map { if (it.first == id) id to block else it })
    var pointing by remember { mutableStateOf(false) }
    val received = LocalReceived.current

    UI.ReorderableColumn(
        items = items,
        key = { it.first },
        spacing = UI.Space.S,
        onMove = { from, to -> update(items.toMutableList().apply { add(to, removeAt(from)) }) }
    ) { _, (id, block) ->
        app.treelune.core.ai.ui.components.BlockCard(
            isActive = false,
            onActivate = {},
            onDelete = { update(items.filter { it.first != id }) },
            bar = { end -> DragBar(end) }
        ) {
            when (block) {
                is StepBlocks.Block.Fixed -> if (block.json.optString(StepBlocks.TYPE) == StepBlocks.TEXT) {
                    UI.FormField(required = false, label = "", labelAbove = false, value = block.json.optString(StepBlocks.TEXT),
                        onChange = { text -> replace(id, StepBlocks.Block.Fixed(JSONObject().put(StepBlocks.TYPE, StepBlocks.TEXT).put(StepBlocks.TEXT, text))) },
                        fieldType = app.treelune.core.ui.FieldType.TEXT_UNLIMITED)
                } else {
                    // A pointer is chosen anew to be changed, as in a routine's message
                    app.treelune.core.ai.ui.components.EnrichmentBlockContent(
                        app.treelune.core.ai.data.MessageSegment.EnrichmentBlock(app.treelune.core.ai.data.EnrichmentType.POINTER,
                            block.json.optJSONObject(StepBlocks.POINTER)?.toString() ?: "{}"),
                        onEdit = { pointing = true }
                    )
                }
                is StepBlocks.Block.Entry -> Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    StepInput(block.input.takeIf { it.length() > 0 }, blockEntryField(s), earlier, s, optional = false) { input ->
                        replace(id, block.copy(input = input ?: JSONObject()))
                    }
                    Choice(s.shared("task_block_attach"), listOf(ATTACHED to s.shared("task_block_attached"), MENTIONED to s.shared("task_block_mentioned")),
                        if (block.attach) ATTACHED else MENTIONED) { chosen -> replace(id, block.copy(attach = chosen == ATTACHED)) }
                }
                is StepBlocks.Block.Value -> StepInput(block.input.takeIf { it.length() > 0 }, blockValueField(s), earlier, s, optional = false) { input ->
                    replace(id, StepBlocks.Block.Value(input ?: JSONObject()))
                }
            }
        }
    }

    // What a block can be, each added at the end
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Button(type = ButtonType.DEFAULT, onClick = {
            update(items + (java.util.UUID.randomUUID().toString() to StepBlocks.Block.Fixed(JSONObject().put(StepBlocks.TYPE, StepBlocks.TEXT).put(StepBlocks.TEXT, ""))))
        }) { UI.Text(s.shared("task_block_text"), TextType.LABEL) }
        UI.Button(type = ButtonType.DEFAULT, onClick = { pointing = true }) { UI.Text(s.shared("task_block_pointer"), TextType.LABEL) }
        UI.Button(type = ButtonType.DEFAULT, onClick = {
            // The entry a trigger gives, when one does
            val input = if (received.entry != null) JSONObject().put(TaskSteps.RECEIVED, JSONObject()) else JSONObject()
            update(items + (java.util.UUID.randomUUID().toString() to StepBlocks.Block.Entry(input, attach = true)))
        }) { UI.Text(s.shared("task_block_entry"), TextType.LABEL) }
        UI.Button(type = ButtonType.DEFAULT, onClick = {
            update(items + (java.util.UUID.randomUUID().toString() to StepBlocks.Block.Value(JSONObject())))
        }) { UI.Text(s.shared("task_block_value"), TextType.LABEL) }
    }

    if (pointing) app.treelune.core.ui.selectors.PointerSelector(
        reference = s.shared("task_reference_run"),
        onDismiss = { pointing = false },
        onConfirm = { config ->
            pointing = false
            update(items + (java.util.UUID.randomUUID().toString() to StepBlocks.Block.Fixed(
                JSONObject().put(StepBlocks.TYPE, StepBlocks.POINTER).put(StepBlocks.POINTER, JSONObject(config)))))
        }
    )
}

/** The field an entry block's input fills: an entry, of any tool. */
private fun blockEntryField(s: StringsContext) = FieldDefinition(StepBlocks.ENTRY, s.shared("task_block_entry"), null, FieldType.REFERENCE, false,
    mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name))))

/** The field a value block's input fills: a text, as long as it is. */
private fun blockValueField(s: StringsContext) = FieldDefinition(StepBlocks.VALUE, s.shared("task_block_value"), null, FieldType.TEXT, false,
    mapOf("length" to TextLength.UNLIMITED.name))

private const val ATTACHED = "attached"
private const val MENTIONED = "mentioned"

/**
 * The event a step emits, chosen through the app's browser, in its zone — two events of one name
 * told apart, a new one made there (« New event… ») —, then each value it carries.
 */
@Composable
private fun EmitEvent(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val context = LocalContext.current
    val chosen = step.optString(TaskSteps.EVENT_ID).ifEmpty { null }
    FieldInput(
        FieldDefinition("event", s.shared("task_step_event"), null, FieldType.REFERENCE, false,
            mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.EVENT.name)))),
        chosen?.let { mapOf("kind" to ReferenceKind.EVENT.name, "id" to it) },
        { value -> ReferenceTarget.referenceOf(value)?.id?.let { id -> with { put(TaskSteps.EVENT_ID, id); remove(TaskSteps.INPUTS) } } },
        context, required = true
    )
    // Read again as the choice changes: the event may have just been made in the browser
    var events by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    LaunchedEffect(chosen) {
        events = (Coordinator(context).processUserAction("events.list").data?.get("events") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    val event = events.firstOrNull { it["id"] == chosen } ?: return
    val values = remember(event) { app.treelune.core.events.EventValues.read(JsonUtils.toJSONArray(event["values"] as? List<*> ?: emptyList<Any>()).toString()) }
    // Every value it declares is given: none is left out
    val inputs = step.optJSONObject(TaskSteps.INPUTS) ?: JSONObject()
    values.forEach { value ->
        val field = app.treelune.core.events.EventValues.field(value)
        StepInput(inputs.optJSONObject(value.name), field, earlier, s, optional = false) { input ->
            with { put(TaskSteps.INPUTS, JSONObject(inputs.toString()).apply { if (input == null) remove(value.name) else put(value.name, input) }) }
        }
    }
}

/** The inputs of [fields], each its own input in [inputs] by name; one left empty is not given. */
@Composable
private fun Inputs(inputs: JSONObject, fields: List<FieldDefinition>, earlier: Earlier, s: StringsContext, onChange: (JSONObject) -> Unit) {
    fields.forEach { field ->
        StepInput(inputs.optJSONObject(field.name), field, earlier, s, optional = true) { input ->
            onChange(JSONObject(inputs.toString()).apply { if (input == null) remove(field.name) else put(field.name, input) })
        }
    }
}

/**
 * One input of a step (TaskSteps.Input): nothing when [optional], a term — a value of [field]'s
 * type, a reading, a variable (TermPicker) —, or what an earlier step gave. A file is never a
 * value written here: it is followed in an entry, or comes from an API.
 */
@Composable
private fun StepInput(input: JSONObject?, field: FieldDefinition, earlier: Earlier, s: StringsContext, optional: Boolean, onChange: (JSONObject?) -> Unit) {
    val results = earlier.flatMap { (index, outputs) ->
        outputs.filter { (it.value == null) == (field.type == FieldType.REFERENCE) }.map { output ->
            JSONObject().put(TaskSteps.RESULT, JSONObject().put(TaskSteps.STEP, index).apply { output.value?.let { put(TaskSteps.VALUE, it) } }) to
                s.shared("task_input_result").format(index + 1, output.value ?: s.shared("task_output_entry"))
        }
    }
    // The entry a trigger gives, for an entry; its fields of the same type, for any other value
    val given = LocalReceived.current
    val receivable = when {
        given.entry == null -> emptyList()
        field.type == FieldType.REFERENCE -> listOf(JSONObject().put(TaskSteps.RECEIVED, JSONObject()) to s.shared("task_input_received_entry"))
        else -> given.entry.filterValues { it.type == field.type }.map { (path, definition) ->
            JSONObject().put(TaskSteps.RECEIVED, JSONObject().put(TaskSteps.FIELD, path)) to s.shared("task_input_received_field").format(definition.displayName)
        }
    } + given.values.orEmpty().filterValues { it.type == field.type }.map { (name, _) ->
        JSONObject().put(TaskSteps.RECEIVED, JSONObject().put(TaskSteps.VALUE, name)) to s.shared("task_input_received_value").format(name)
    }
    val sources = listOfNotNull(
        (NONE to s.shared("task_input_none")).takeIf { optional },
        TERM to s.shared(if (field.type == FieldType.REFERENCE) "task_input_entry" else "task_input_term"),
        (RESULT to s.shared("task_input_from_step")).takeIf { results.isNotEmpty() },
        (RECEIVED to s.shared("task_input_received")).takeIf { receivable.isNotEmpty() }
    )
    val source = when {
        input == null -> if (optional) NONE else TERM
        input.has(TaskSteps.RESULT) -> RESULT
        input.has(TaskSteps.RECEIVED) -> RECEIVED
        else -> TERM
    }
    UI.Text(field.displayName, TextType.LABEL)
    if (sources.size > 1) Choice(s.shared("task_input_source"), sources, source) { chosen ->
        onChange(when (chosen) {
            NONE -> null
            RESULT -> results.first().first
            RECEIVED -> receivable.first().first
            else -> JSONObject()
        })
    }
    when (source) {
        RESULT -> Choice(s.shared("task_input_from_step"), results.map { it.first.toString() to it.second }, input.toString()) { onChange(JSONObject(it)) }
        RECEIVED -> Choice(s.shared("task_input_received"), receivable.map { it.first.toString() to it.second }, input.toString()) { onChange(JSONObject(it)) }
        TERM -> if (field.type == FieldType.REFERENCE) {
            // An entry designated: stored as a constant, read as its id when the step runs
            val context = LocalContext.current
            FieldInput(field, input?.opt(Term.Kind.CONSTANT.key)?.let { JsonUtils.toValue(it) }, { value -> onChange(value?.let { Term.Constant(it).toJson() }) }, context, required = true)
        } else TermPicker(input ?: JSONObject(), field, { onChange(it) }, s, ReadingContext(s.shared("task_reference_run")),
            kinds = if (field.type == FieldType.FILE) setOf(Term.Kind.READING) else Term.Kind.entries.toSet())
    }
}

private const val NONE = "none"
private const val TERM = "term"
private const val RESULT = "result"
private const val RECEIVED = "received"

/**
 * The conditions of [step] (StepConditions), « Only if »: each what the step reads on the left —
 * a field of the entry or a value a trigger gives, a value an earlier step gave, a variable or a
 * reading —, the operators its type takes, then the side or two on the right, an input of that
 * type (StepInput). All holding, the step runs; added by +, removed by its bin.
 */
@Composable
private fun StepWhen(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val list = step.optJSONArray(StepConditions.WHEN) ?: org.json.JSONArray()
    fun put(next: org.json.JSONArray) = with { if (next.length() == 0) remove(StepConditions.WHEN) else put(StepConditions.WHEN, next) }
    UI.Text(s.shared("task_when"), TextType.LABEL)
    UI.Text(s.shared("task_when_help"), TextType.CAPTION)
    for (index in 0 until list.length()) {
        val condition = list.optJSONObject(index) ?: JSONObject()
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    UI.Text(s.shared("task_when_line").format(index + 1), TextType.STRONG)
                }
                UI.ActionButton(action = app.treelune.core.ui.ButtonAction.DELETE, display = app.treelune.core.ui.ButtonDisplay.ICON, size = app.treelune.core.ui.Size.S, onClick = {
                    put(org.json.JSONArray().apply { for (i in 0 until list.length()) if (i != index) put(list.get(i)) })
                })
            }
            StepCondition(condition, earlier, s) { next -> put(org.json.JSONArray(list.toString()).put(index, next)) }
        }
    }
    UI.Button(type = ButtonType.DEFAULT, onClick = { put(org.json.JSONArray(list.toString()).put(JSONObject())) }) {
        UI.Text(s.shared("task_when_add"), TextType.LABEL)
    }
}

/** One condition of a step: its left side chosen among what the step reads, its operator, its right side. */
@Composable
private fun StepCondition(condition: JSONObject, earlier: Earlier, s: StringsContext, onChange: (JSONObject) -> Unit) {
    val given = LocalReceived.current
    // What the left side may be, each with the field it is a value of
    val read: List<Triple<JSONObject, String, FieldDefinition?>> =
        given.entry.orEmpty().map { (path, field) ->
            Triple(JSONObject().put(TaskSteps.RECEIVED, JSONObject().put(TaskSteps.FIELD, path)), s.shared("task_input_received_field").format(field.displayName), field)
        } + given.values.orEmpty().map { (name, field) ->
            Triple(JSONObject().put(TaskSteps.RECEIVED, JSONObject().put(TaskSteps.VALUE, name)), s.shared("task_input_received_value").format(name), field)
        } + earlier.flatMap { (index, outputs) ->
            outputs.map { output ->
                Triple(JSONObject().put(TaskSteps.RESULT, JSONObject().put(TaskSteps.STEP, index).apply { output.value?.let { put(TaskSteps.VALUE, it) } }),
                    s.shared("task_input_result").format(index + 1, output.value ?: s.shared("task_output_entry")),
                    if (output.value == null) StepConditions.entryField(s) else StepConditions.outputField(output, s))
            }
        }
    val left = condition.optJSONObject(app.treelune.core.conditions.Conditions.LEFT)
    val chosen = read.firstOrNull { left != null && it.first.toString() == left.toString() }
    val isTerm = left != null && chosen == null
    val options = read.map { it.first.toString() to it.second } + (TERM to s.shared("task_when_term"))
    // Another left side is another type: its operator and right side start over
    Choice(s.shared("task_when_left"), options, if (isTerm) TERM else chosen?.first?.toString()) { key ->
        onChange(JSONObject().put(app.treelune.core.conditions.Conditions.LEFT,
            if (key == TERM) JSONObject().put("reading", JSONObject().put("selection", JSONObject()).put("reduction", app.treelune.core.reading.Reduction.COUNT.name)) else JSONObject(key)))
    }
    if (left == null) return
    val termField = if (isTerm) {
        TermPicker(left, remember { FieldDefinition("left", s.shared("task_when_left"), null, FieldType.NUMERIC, false, null) }, { term ->
            onChange(JSONObject().put(app.treelune.core.conditions.Conditions.LEFT, term))
        }, s, ReadingContext(s.shared("task_reference_run")), kinds = setOf(Term.Kind.READING, Term.Kind.VARIABLE))
        app.treelune.core.ui.selectors.rememberTermField(left)
    } else null
    val field = chosen?.third ?: termField ?: return

    val ops = app.treelune.core.fields.EntryFilters.operatorsFor(field).toList()
    val op = app.treelune.core.fields.FilterOperator.of(condition.optString(app.treelune.core.conditions.Conditions.OP))?.takeIf { it in ops }
    fun with(change: JSONObject.() -> Unit) = onChange(JSONObject(condition.toString()).apply(change))
    Choice(s.shared("pointer_filter_condition"), ops.map { it.key to app.treelune.core.ui.selectors.PointerDescription.operator(it, s) }, op?.key) { key ->
        val next = ops.first { it.key == key }
        with {
            put(app.treelune.core.conditions.Conditions.OP, next.key)
            // The right side keeps its shape when the new operator takes the same
            when (next) {
                app.treelune.core.fields.FilterOperator.ABSENT, app.treelune.core.fields.FilterOperator.PRESENT -> remove(app.treelune.core.conditions.Conditions.RIGHT)
                app.treelune.core.fields.FilterOperator.BETWEEN -> if (opt(app.treelune.core.conditions.Conditions.RIGHT) !is org.json.JSONArray)
                    put(app.treelune.core.conditions.Conditions.RIGHT, org.json.JSONArray().put(JSONObject()).put(JSONObject()))
                else -> if (opt(app.treelune.core.conditions.Conditions.RIGHT) !is JSONObject) put(app.treelune.core.conditions.Conditions.RIGHT, JSONObject())
            }
        }
    }
    val sideField = field.copy(displayName = s.shared("variable_value"))
    when (val right = condition.opt(app.treelune.core.conditions.Conditions.RIGHT)) {
        is JSONObject -> StepInput(right.takeIf { it.length() > 0 }, sideField, earlier, s, optional = false) { input ->
            with { put(app.treelune.core.conditions.Conditions.RIGHT, input ?: JSONObject()) }
        }
        is org.json.JSONArray -> (0 until right.length()).forEach { i ->
            StepInput(right.optJSONObject(i)?.takeIf { it.length() > 0 }, field.copy(displayName = s.shared(if (i == 0) "filter_low" else "filter_high")), earlier, s, optional = false) { input ->
                with { put(app.treelune.core.conditions.Conditions.RIGHT, org.json.JSONArray(right.toString()).put(i, input ?: JSONObject())) }
            }
        }
        else -> Unit
    }
}
