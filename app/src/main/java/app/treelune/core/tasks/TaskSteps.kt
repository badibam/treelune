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
 * - `run_routine`: `routine_id`, `inputs` (`data`, what its run's message is joined)
 * - `emit_event`: `event_id`, `inputs` (by the value's name the event declares, every one given)
 *
 * An input is a Term (`{"constant"}`, `{"variable"}`, `{"reading"}`), or what an earlier step gave:
 * `{"result": {"step": 0, "value": "temperature"}}` for a value of an API's answer,
 * `{"result": {"step": 1}}` for the entry a step created; or what a trigger gave the run
 * (Triggers): `{"received": {}}` for the entry written, `{"received": {"field": "data.x"}}` for
 * one of its fields, read as the entry is when the step runs, `{"received": {"value": "fiche"}}`
 * for a value the event emitted carried. An entry given as a constant is a
 * Reference (`{"kind": "ENTRY", "id"}`).
 */
object TaskSteps {

    const val CALL_API = "call_api"
    const val CREATE_ENTRY = "create_entry"
    const val UPDATE_ENTRY = "update_entry"
    const val TOOL_OPERATION = "tool_operation"
    const val RUN_ROUTINE = "run_routine"
    const val EMIT_EVENT = "emit_event"

    val KINDS = listOf(CALL_API, CREATE_ENTRY, UPDATE_ENTRY, TOOL_OPERATION, RUN_ROUTINE, EMIT_EVENT)

    const val KIND = "kind"
    const val API_ID = "api_id"
    const val INPUTS = "inputs"
    const val TOOL_INSTANCE_ID = "tool_instance_id"
    const val VALUES = "values"
    const val ENTRY = "entry"
    const val OPERATION = "operation"
    const val PARAMS = "params"
    const val ROUTINE_ID = "routine_id"
    const val EVENT_ID = "event_id"
    /** The one input of a routine run: what joins its message. */
    const val DATA = "data"

    const val RESULT = "result"
    const val STEP = "step"
    const val VALUE = "value"
    const val RECEIVED = "received"
    const val FIELD = "field"

    /** The paths of an entry a step writes, beside its fields under data and extra. */
    private val WRITTEN_TOP = setOf("name", "timestamp")

    /** Whether the path [path] of an entry is one a step may write. */
    fun writable(path: String): Boolean = path in WRITTEN_TOP || path.startsWith("data.") || path.startsWith("extra.")

    /** An input as stored: a term, or what an earlier step gave. */
    sealed interface Input {
        data class Of(val term: Term) : Input
        /** [value] of step [step]'s answer; null for the entry it created */
        data class Result(val step: Int, val value: String?) : Input
        /**
         * What a trigger gave the run: the entry written, or its [field] by path; or [value], one
         * an event carried by name
         */
        data class Received(val field: String?, val value: String? = null) : Input
    }

    /**
     * The input [json] holds; [name] says where, for the error.
     *
     * @throws IllegalArgumentException naming what does not read
     */
    fun input(json: JSONObject, name: String, text: (String) -> String): Input {
        json.optJSONObject(RECEIVED)?.let { return Input.Received(it.optString(FIELD).ifEmpty { null }, it.optString(VALUE).ifEmpty { null }) }
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

    /** [entry] as a path reads it (FieldReading.valueAt): its columns, data, extra and state as objects. */
    fun entryMap(entry: app.treelune.core.database.entities.ToolDataEntity): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "name" to entry.name,
        "timestamp" to entry.timestamp,
        "data" to app.treelune.core.utils.JsonUtils.toMap(JSONObject(entry.data)),
        "extra" to entry.extra?.let { app.treelune.core.utils.JsonUtils.toMap(JSONObject(it)) },
        "state" to entry.state?.let { app.treelune.core.utils.JsonUtils.toMap(JSONObject(it)) }
    )

    /**
     * The fields of the entry a trigger gives a run of the Task whose [triggers] they are, by
     * path: those every tool listened to has, the entry being any of theirs; null when no trigger
     * gives an entry.
     */
    suspend fun receivedFields(triggers: List<app.treelune.core.triggers.Triggers.Trigger>, context: Context, s: StringsContext): Map<String, FieldDefinition>? {
        val tools = triggers.filterIsInstance<app.treelune.core.triggers.Triggers.Trigger.EntryWritten>().map { it.toolInstanceId }.distinct()
        if (tools.isEmpty()) return null
        val each = tools.map { id ->
            try {
                ToolFields.filterable(id, context, s)
            } catch (e: IllegalStateException) {
                emptyMap()
            }
        }
        return each.first().filterKeys { path -> each.all { path in it } }
    }

    /**
     * The values a trigger gives a run of the Task whose [triggers] they are, by name, as the
     * field each is entered as: an event's, those it declares; a condition's, the sides read
     * (ConditionTriggers.values). Those every such trigger gives with one type; null when no
     * trigger gives values.
     */
    suspend fun receivedValues(triggers: List<app.treelune.core.triggers.Triggers.Trigger>, context: Context): Map<String, FieldDefinition>? {
        val dao = AppDatabase.getDatabase(context).eventDao()
        val events = triggers.filterIsInstance<app.treelune.core.triggers.Triggers.Trigger.EventEmitted>().map { it.eventId }.distinct().map { id ->
            dao.get(id)?.let { app.treelune.core.events.EventValues.read(it.valuesJson) }.orEmpty().associate { it.name to app.treelune.core.events.EventValues.field(it) }
        }
        val conditions = triggers.filterIsInstance<app.treelune.core.triggers.Triggers.Trigger.ConditionBecameTrue>().distinctBy { it.key }
            .map { app.treelune.core.triggers.ConditionTriggers.values(context, it.condition) }
        val each = events + conditions
        if (each.isEmpty()) return null
        return each.first().filter { (name, field) -> each.all { it[name] == field } }
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
     * What [step] reaches, as the operations it runs and the reads its inputs make
     * (AccessRules.reach): an entry written or an operation, its tool used; a routine run, its zone
     * used; a reading or a variable, read; an API, nothing of the app.
     */
    fun reaches(step: JSONObject, tooltypeOf: (String) -> String?, isToolType: (String) -> Boolean): List<app.treelune.core.access.Reach> {
        fun reach(action: String, params: Map<String, Any?>) = action.split('.', limit = 2).let { (resource, operation) ->
            app.treelune.core.access.AccessRules.reach(resource, operation, params, isToolType)
        }
        val tool = step.optString(TOOL_INSTANCE_ID)
        val own = when (step.optString(KIND)) {
            CREATE_ENTRY, UPDATE_ENTRY -> reach("tool_data.create", mapOf("tool_instance_id" to tool))
            TOOL_OPERATION -> reach("${tooltypeOf(tool) ?: "?"}.${step.optString(OPERATION)}", mapOf("tool_instance_id" to tool))
            RUN_ROUTINE -> reach("routines.execute_manual", mapOf("routine_id" to step.optString(ROUTINE_ID)))
            EMIT_EVENT -> reach("events.emit", mapOf("event_id" to step.optString(EVENT_ID)))
            else -> emptyList()
        }
        val reads = listOf(INPUTS, VALUES, PARAMS).mapNotNull { step.optJSONObject(it) }
            .flatMap { holder -> holder.keys().asSequence().mapNotNull { holder.optJSONObject(it) }.toList() } +
            listOfNotNull(step.optJSONObject(ENTRY))
        return own + reads.flatMap { input ->
            when {
                input.has(Term.Kind.READING.key) -> reach("readings.read", mapOf("selection" to
                    app.treelune.core.utils.JsonUtils.toMap(input.getJSONObject(Term.Kind.READING.key).optJSONObject("selection") ?: JSONObject())))
                input.has(Term.Kind.VARIABLE.key) -> reach("variables.evaluate", mapOf("variable_id" to input.getString(Term.Kind.VARIABLE.key)))
                else -> emptyList()
            }
        }
    }

    /**
     * Why [steps], written by whoever [scope] holds to a mask (an AI), reach further than it; null
     * when each of them is one it could take itself.
     */
    suspend fun reachRefusal(steps: JSONArray, scope: app.treelune.core.access.AccessScope, context: Context, s: StringsContext): String? {
        val tools = AppDatabase.getDatabase(context).toolInstanceDao()
        val lookups = app.treelune.core.access.DatabaseAccessLookups(context)
        val tooltypes = (0 until steps.length()).mapNotNull { steps.optJSONObject(it)?.optString(TOOL_INSTANCE_ID)?.ifEmpty { null } }
            .associateWith { tools.getToolInstanceById(it)?.tooltype }
        val reaches = (0 until steps.length()).mapNotNull { steps.optJSONObject(it) }.flatMap { reaches(it, { id -> tooltypes[id] }, lookups::isToolType) }
        return app.treelune.core.access.AccessRules.refusal(scope.mask, scope.holder, reaches, lookups, s::shared)
    }

    /**
     * Why [steps] cannot be stored; null when they can. Each step of a kind it knows, naming what
     * exists (an API, a tool, an operation of its type, a routine), and each input readable and
     * naming an earlier step that gives what it reads, or a field of the entry a trigger gives
     * ([received], receivedFields; null when no trigger gives one).
     */
    suspend fun refusal(steps: JSONArray, context: Context, s: StringsContext, received: Map<String, FieldDefinition>? = null, values: Map<String, FieldDefinition>? = null): String? {
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
                    if (input is Input.Received && input.value != null) {
                        values ?: return s.shared("task_error_received_untriggered").format(number)
                        if (input.value !in values) return s.shared("task_error_received_value").format(number, input.value, values.keys.joinToString(", ").ifEmpty { "-" })
                    } else if (input is Input.Received) {
                        received ?: return s.shared("task_error_received_untriggered").format(number)
                        if (input.field != null && input.field !in received) return s.shared("task_error_received_field").format(number, input.field, received.keys.joinToString(", "))
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
                RUN_ROUTINE -> {
                    database.aiDao().getRoutineById(step.optString(ROUTINE_ID))
                        ?: return s.shared("task_error_routine").format(number, step.optString(ROUTINE_ID))
                    inputs(step.optJSONObject(INPUTS), setOf(DATA))?.let { return it }
                }
                EMIT_EVENT -> {
                    val event = database.eventDao().get(step.optString(EVENT_ID))
                        ?: return s.shared("task_error_event").format(number, step.optString(EVENT_ID))
                    val declared = app.treelune.core.events.EventValues.read(event.valuesJson).map { it.name }
                    inputs(step.optJSONObject(INPUTS), declared.toSet())?.let { return it }
                    declared.firstOrNull { step.optJSONObject(INPUTS)?.has(it) != true }?.let { return s.shared("task_error_event_value").format(number, it, event.name) }
                }
                else -> return s.shared("task_error_kind").format(number, step.optString(KIND), KINDS.joinToString(", "))
            }
            given += outputs(step, context)
        }
        return null
    }
}
