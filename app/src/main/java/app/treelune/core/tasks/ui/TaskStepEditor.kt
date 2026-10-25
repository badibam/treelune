package app.treelune.core.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.treelune.core.tasks.TaskSteps
import app.treelune.core.terms.Term
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.selectors.ReadingContext
import app.treelune.core.ui.selectors.TermPicker
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/** What the steps before one give, by step index: what its inputs may take from them. */
internal typealias Earlier = List<Pair<Int, List<TaskSteps.Output>>>

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
            TaskSteps.RUN_ROUTINE -> RunRoutine(step, s, ::with)
        }
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
        inputs = config?.optJSONArray(ApiSettings.INPUTS)?.let { list -> (0 until list.length()).map { list.getJSONObject(it) } }.orEmpty().map { input ->
            val name = input.getString(ApiSettings.NAME)
            FieldDefinition(name, name, null, when (input.optString(ApiSettings.TYPE)) {
                ApiSettings.NUMBER -> FieldType.NUMERIC
                ApiSettings.BOOLEAN -> FieldType.BOOLEAN
                else -> FieldType.TEXT
            }, false, if (input.optString(ApiSettings.TYPE) == ApiSettings.NUMBER) null else mapOf("length" to TextLength.UNLIMITED.name).takeIf { input.optString(ApiSettings.TYPE) != ApiSettings.BOOLEAN })
        }
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
private fun RunRoutine(step: JSONObject, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    val context = LocalContext.current
    var routines by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    LaunchedEffect(Unit) {
        routines = (Coordinator(context).processUserAction("automations.list_all").data?.get("automations") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    Choice(s.shared("task_step_routine"), routines.map { it["id"] as String to it["name"] as String }, step.optString(TaskSteps.AUTOMATION_ID).ifEmpty { null }) { id ->
        with { put(TaskSteps.AUTOMATION_ID, id) }
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
    val sources = listOfNotNull(
        (NONE to s.shared("task_input_none")).takeIf { optional },
        TERM to s.shared(if (field.type == FieldType.REFERENCE) "task_input_entry" else "task_input_term"),
        (RESULT to s.shared("task_input_from_step")).takeIf { results.isNotEmpty() }
    )
    val source = when {
        input == null -> if (optional) NONE else TERM
        input.has(TaskSteps.RESULT) -> RESULT
        else -> TERM
    }
    UI.Text(field.displayName, TextType.LABEL)
    if (sources.size > 1) Choice(s.shared("task_input_source"), sources, source) { chosen ->
        onChange(when (chosen) {
            NONE -> null
            RESULT -> results.first().first
            else -> JSONObject()
        })
    }
    when (source) {
        RESULT -> Choice(s.shared("task_input_from_step"), results.map { it.first.toString() to it.second }, input.toString()) { onChange(JSONObject(it)) }
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
