package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.ai.prompts.ModelValues
import app.treelune.core.apis.ApiSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FieldValueSchema
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.terms.Term
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.JsonUtils
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the AI reads and writes of a Task (docs/AI.md): its config, read by TASK_CONFIG and written
 * by CREATE_TASK and UPDATE_TASK in the same shape — its zone, its settings (TaskSettings) and its
 * steps (TaskSteps). Its settings' instants and durations are marked and pass through ModelValues;
 * a step's constant is converted as the field it fills says (a date or a duration in ISO 8601),
 * known from the tool's fields or the API's inputs, never from the look of the value.
 */
object TaskSchemas {

    const val CONFIG_ID = "task_config"

    fun config(context: Context): Schema {
        val s = Strings.`for`(context = context)
        val content = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("zone_id", JSONObject().put("type", "string"))
                .put("settings", SettingsSchemaGenerator.generate(TaskSettings.nodes(context), s::shared))
                .put("steps", JSONObject().put("type", "array").put("description", s.shared("task_schema_steps"))
                    .put("items", JSONObject().put("type", "object").put("required", JSONArray().put(TaskSteps.KIND)))))
            .put("additionalProperties", false)
        return Schema(CONFIG_ID, s.shared("task_display_name"), "", SchemaCategory.TASK_CONFIG, content.toString())
    }

    /** [steps] as the AI wrote them, their constants in their stored form. */
    suspend fun stepsToStored(steps: JSONArray, context: Context): JSONArray = convert(steps, context) { value, field -> ModelValues.fromModel(value, field, zone()) }

    /** [steps] as stored, their constants as the AI writes them. */
    suspend fun stepsToModel(steps: JSONArray, context: Context): JSONArray = convert(steps, context) { value, field -> ModelValues.toModel(value, field, zone()) }

    /** [task], a Task as tasks.get gives it, in the form the AI writes it back. */
    @Suppress("UNCHECKED_CAST")
    suspend fun forModel(task: Map<*, *>, context: Context): Map<String, Any?> {
        val schema = JSONObject(config(context).content)
        val settings = ModelValues.toModel(task["settings"], schema.getJSONObject("properties").getJSONObject("settings"), zone())
        return mapOf(
            "id" to task["id"],
            "zone_id" to task["zone_id"],
            "settings" to settings,
            "steps" to JsonUtils.toList(stepsToModel(JsonUtils.toJSONArray(task["steps"] as List<*>), context))
        )
    }

    private fun zone() = AppConfigManager.getDateTimeConfig().getZoneId()

    /** [steps] with each constant of an input converted by [convert], given the schema of the field it fills. */
    private suspend fun convert(steps: JSONArray, context: Context, convert: (Any?, JSONObject) -> Any?): JSONArray {
        val s = Strings.`for`(context = context)
        val out = JSONArray()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index)?.let { JSONObject(it.toString()) }
            if (step == null) {
                out.put(steps.opt(index))
                continue
            }
            val fields = try {
                fieldsOf(step, context, s)
            } catch (e: IllegalStateException) {
                // A tool that does not read leaves the step as it is: the service refuses it, naming it
                emptyMap()
            }
            listOf(TaskSteps.INPUTS, TaskSteps.VALUES, TaskSteps.PARAMS).forEach { key ->
                val holder = step.optJSONObject(key) ?: return@forEach
                holder.keys().asSequence().toList().forEach { name ->
                    val input = holder.optJSONObject(name)
                    val field = fields[name]
                    if (input != null && field != null && input.has(Term.Kind.CONSTANT.key) && !input.isNull(Term.Kind.CONSTANT.key)) {
                        val converted = convert(JsonUtils.toValue(input.get(Term.Kind.CONSTANT.key)), FieldValueSchema.of(field))
                        // A list or an object comes back in Kotlin's form, put back in JSON's
                        input.put(Term.Kind.CONSTANT.key, if (converted is Map<*, *> || converted is List<*>) JsonUtils.toJSONObject(mapOf("v" to converted)).get("v") else converted)
                    }
                }
            }
            out.put(step)
        }
        return out
    }

    /** The fields a step's inputs fill, by input name: a tool's entry fields, an operation's params, an API's inputs. */
    private suspend fun fieldsOf(step: JSONObject, context: Context, s: app.treelune.core.strings.StringsContext): Map<String, FieldDefinition> = when (step.optString(TaskSteps.KIND)) {
        TaskSteps.CREATE_ENTRY, TaskSteps.UPDATE_ENTRY -> step.optString(TaskSteps.TOOL_INSTANCE_ID).ifEmpty { null }
            ?.let { TaskSteps.writableFields(it, context, s) }.orEmpty()
        TaskSteps.TOOL_OPERATION -> TaskSteps.operationParams(step.optString(TaskSteps.TOOL_INSTANCE_ID), step.optString(TaskSteps.OPERATION), context)
            ?.associateBy { it.name }.orEmpty()
        TaskSteps.CALL_API -> app.treelune.core.database.AppDatabase.getDatabase(context).apiDao().get(step.optString(TaskSteps.API_ID))?.let { api ->
            JSONObject(api.configJson).optJSONArray(ApiSettings.INPUTS)?.let { list -> (0 until list.length()).map { list.getJSONObject(it) } }.orEmpty()
                .associate { input -> input.getString(ApiSettings.NAME) to FieldDefinition(input.getString(ApiSettings.NAME), input.getString(ApiSettings.NAME), null,
                    if (input.optString(ApiSettings.TYPE) == ApiSettings.NUMBER) FieldType.NUMERIC else FieldType.TEXT, false, null) }
        }.orEmpty()
        else -> emptyMap()
    }
}
