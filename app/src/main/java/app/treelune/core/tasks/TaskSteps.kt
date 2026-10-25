package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.apis.ApiSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ToolFields
import app.treelune.core.strings.StringsContext
import app.treelune.core.terms.Term
import app.treelune.core.tools.ToolOperations
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Task's steps (docs/CORE.md, « Tasks »), stored as a list, each an object naming its kind:
 *
 * - `call_api`: `api_id`, `inputs` (by the API's input name, an input each)
 * - `create_entry`: `tool_instance_id`, `values` (by the field's path: `name`, `timestamp`, `data.x`, `extra.x`)
 * - `update_entry`: `tool_instance_id`, `entry` (an input giving an entry), `values` as above
 * - `tool_operation`: `tool_instance_id`, `operation` (one the tool type declares), `params` (by name)
 * - `run_routine`: `automation_id`
 *
 * An input is a Term (`{"constant"}`, `{"variable"}`, `{"reading"}`), or what an earlier step gave:
 * `{"result": {"step": 0, "value": "temperature"}}` for a value of an API's answer,
 * `{"result": {"step": 1}}` for the entry a step created. An entry given as a constant is a
 * Reference (`{"kind": "ENTRY", "id"}`).
 */
object TaskSteps {

    const val CALL_API = "call_api"
    const val CREATE_ENTRY = "create_entry"
    const val UPDATE_ENTRY = "update_entry"
    const val TOOL_OPERATION = "tool_operation"
    const val RUN_ROUTINE = "run_routine"

    val KINDS = listOf(CALL_API, CREATE_ENTRY, UPDATE_ENTRY, TOOL_OPERATION, RUN_ROUTINE)

    const val KIND = "kind"
    const val API_ID = "api_id"
    const val INPUTS = "inputs"
    const val TOOL_INSTANCE_ID = "tool_instance_id"
    const val VALUES = "values"
    const val ENTRY = "entry"
    const val OPERATION = "operation"
    const val PARAMS = "params"
    const val AUTOMATION_ID = "automation_id"

    const val RESULT = "result"
    const val STEP = "step"
    const val VALUE = "value"

    /** The paths of an entry a step writes, beside its fields under data and extra. */
    private val WRITTEN_TOP = setOf("name", "timestamp")

    /** Whether the path [path] of an entry is one a step may write. */
    fun writable(path: String): Boolean = path in WRITTEN_TOP || path.startsWith("data.") || path.startsWith("extra.")

    /** An input as stored: a term, or what an earlier step gave. */
    sealed interface Input {
        data class Of(val term: Term) : Input
        /** [value] of step [step]'s answer; null for the entry it created */
        data class Result(val step: Int, val value: String?) : Input
    }

    /**
     * The input [json] holds; [name] says where, for the error.
     *
     * @throws IllegalArgumentException naming what does not read
     */
    fun input(json: JSONObject, name: String, text: (String) -> String): Input {
        json.optJSONObject(RESULT)?.let { result ->
            if (!result.has(STEP)) throw IllegalArgumentException(text("task_error_input").format(name))
            return Input.Result(result.getInt(STEP), result.optString(VALUE).ifEmpty { null })
        }
        return Input.Of(Term.fromJson(json, name, text))
    }

    /** One value a step gives the ones after it: its name (null for an entry created), its type for the screen. */
    data class Output(val value: String?, val type: String)

    /** What step [step] gives the steps after it, read without running it. */
    suspend fun outputs(step: JSONObject, context: Context): List<Output> = when (step.optString(KIND)) {
        CALL_API -> AppDatabase.getDatabase(context).apiDao().get(step.optString(API_ID))?.let { api ->
            JSONObject(api.configJson).optJSONArray(ApiSettings.VALUES)?.let { values ->
                (0 until values.length()).map { values.getJSONObject(it) }.map { Output(it.getString(ApiSettings.NAME), it.optString(ApiSettings.TYPE, ApiSettings.TEXT)) }
            }
        }.orEmpty()
        CREATE_ENTRY -> listOf(Output(null, ENTRY))
        else -> emptyList()
    }

    /** The fields a step may write in the entries of [toolInstanceId], by path. */
    suspend fun writableFields(toolInstanceId: String, context: Context, s: StringsContext): Map<String, FieldDefinition> =
        ToolFields.filterable(toolInstanceId, context, s).filterKeys { writable(it) }

    /** The parameters of [operation] of the tool type of [toolInstanceId] a step may fill: its fields, a value each. */
    suspend fun operationParams(toolInstanceId: String, operation: String, context: Context): List<FieldDefinition>? {
        val tool = AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId) ?: return null
        val type = ToolTypeManager.getToolType(tool.tooltype) ?: return null
        return ToolOperations.find(type, operation, context)?.params?.filterIsInstance<SettingNode.Field>()?.map { it.definition }
    }

    /**
     * Why [steps] cannot be stored; null when they can. Each step of a kind it knows, naming what
     * exists (an API, a tool, an operation of its type, a routine), and each input readable and
     * naming an earlier step that gives what it reads.
     */
    suspend fun refusal(steps: JSONArray, context: Context, s: StringsContext): String? {
        val database = AppDatabase.getDatabase(context)
        val given = mutableListOf<List<Output>>()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: return s.shared("task_error_step").format(index + 1)
            val number = index + 1
            fun inputs(holder: JSONObject?, allowed: Set<String>?): String? {
                holder ?: return null
                for (key in holder.keys()) {
                    if (allowed != null && key !in allowed) return s.shared("task_error_input_unknown").format(number, key, allowed.joinToString(", ").ifEmpty { "-" })
                    val json = holder.optJSONObject(key) ?: return s.shared("task_error_input").format("$number › $key")
                    val input = try {
                        input(json, "$number › $key") { s.shared(it) }
                    } catch (e: IllegalArgumentException) {
                        return e.message
                    }
                    if (input is Input.Result) {
                        val earlier = given.getOrNull(input.step) ?: return s.shared("task_error_result_step").format(number, input.step + 1)
                        if (earlier.none { it.value == input.value }) return s.shared("task_error_result_value").format(number, input.step + 1, input.value ?: s.shared("task_output_entry"))
                    }
                }
                return null
            }
            when (step.optString(KIND)) {
                CALL_API -> {
                    val api = database.apiDao().get(step.optString(API_ID)) ?: return s.shared("task_error_api").format(number, step.optString(API_ID))
                    val declared = JSONObject(api.configJson).optJSONArray(ApiSettings.INPUTS)?.let { list -> (0 until list.length()).map { list.getJSONObject(it).getString(ApiSettings.NAME) } }.orEmpty()
                    inputs(step.optJSONObject(INPUTS), declared.toSet())?.let { return it }
                }
                CREATE_ENTRY, UPDATE_ENTRY -> {
                    val toolId = step.optString(TOOL_INSTANCE_ID)
                    database.toolInstanceDao().getToolInstanceById(toolId) ?: return s.shared("task_error_tool").format(number, toolId)
                    val fields = try {
                        writableFields(toolId, context, s)
                    } catch (e: IllegalStateException) {
                        return s.shared("task_error_tool").format(number, toolId)
                    }
                    inputs(step.optJSONObject(VALUES), fields.keys)?.let { return it }
                    if (step.optString(KIND) == UPDATE_ENTRY) {
                        val entry = step.optJSONObject(ENTRY) ?: return s.shared("task_error_entry").format(number)
                        inputs(JSONObject().put(ENTRY, entry), null)?.let { return it }
                    }
                }
                TOOL_OPERATION -> {
                    val toolId = step.optString(TOOL_INSTANCE_ID)
                    database.toolInstanceDao().getToolInstanceById(toolId) ?: return s.shared("task_error_tool").format(number, toolId)
                    val params = operationParams(toolId, step.optString(OPERATION), context)
                        ?: return s.shared("task_error_operation").format(number, step.optString(OPERATION))
                    inputs(step.optJSONObject(PARAMS), params.map { it.name }.toSet())?.let { return it }
                }
                RUN_ROUTINE -> database.aiDao().getAutomationById(step.optString(AUTOMATION_ID))
                    ?: return s.shared("task_error_routine").format(number, step.optString(AUTOMATION_ID))
                else -> return s.shared("task_error_kind").format(number, step.optString(KIND), KINDS.joinToString(", "))
            }
            given += outputs(step, context)
        }
        return null
    }
}
