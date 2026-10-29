package app.treelune.core.services

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.tools.BaseSchemas
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.validation.SchemaCategory
import app.treelune.core.validation.Schema
import app.treelune.core.schemas.ZoneSettings
import app.treelune.core.fields.settings.FieldTypeSchemas
import app.treelune.core.ai.data.AIMessageSchemas
import app.treelune.core.ai.data.CommunicationModules
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * Schema Service - Centralized access to all schemas in the system
 *
 * Provides unified access to schemas from:
 * - System schemas (zones, app_config, etc.) - hardcoded providers
 * - Tooltype schemas (tracking, journal, etc.) - via ToolTypeManager discovery
 *
 * Used by AI system to retrieve schema definitions for prompt generation.
 */
class SchemaService(private val context: Context) : ExecutableService {

    // Access ToolTypeManager object directly
    private val s = Strings.`for`(context = context)

    /**
     * Execute schema operation with cancellation support
     */
    override suspend fun execute(
        operation: String,
        params: JSONObject,
        token: CancellationToken
    ): OperationResult {
        return try {
            when (operation) {
                "get" -> handleGetSchema(params, token)
                "list" -> handleListSchemas(params, token)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: Exception) {
            LogManager.service("SchemaService operation failed: ${e.message}", "ERROR", e)
            OperationResult.error("Schema service error: ${e.message}")
        }
    }

    /**
     * The schema of what the params name: "tooltype", the config of a tool of that type;
     * "tool_instance_id", the entries of that tool; "id", any other schema by its name.
     *
     * A tool's schemas are generated from its type's declarations. Their name is computed from
     * what they describe ("tracking_config", "tracking_data") and handed back as "schema_id" so a
     * caller can tell two schemas apart; it is stored nowhere.
     *
     * Read by the AI alone: written in English, its labels with the app's beside (AiLanguage).
     */
    private suspend fun handleGetSchema(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val tooltype = params.optString("tooltype")
        val toolInstanceId = params.optString("tool_instance_id")
        val schemaId = params.optString("id")
        LogManager.service("SchemaService.get() called with tooltype='$tooltype', toolInstanceId='$toolInstanceId', id='$schemaId'")

        suspend fun resolve(language: Context): Schema? = when {
            toolInstanceId.isNotEmpty() -> entrySchema(toolInstanceId, language)
            tooltype.isNotEmpty() -> configSchema(tooltype, language)
            else -> getSystemSchema(schemaId, language)
        }
        if (toolInstanceId.isEmpty() && tooltype.isEmpty() && schemaId.isEmpty()) {
            return OperationResult.error(s.shared("service_error_schema_target_missing"))
        }
        val schema = resolve(context)
            // A tool that does not exist is said so, rather than taken for a schema missing
            ?: return OperationResult.error(if (toolInstanceId.isNotEmpty()) s.shared("service_error_schema_tool_not_found").format(toolInstanceId)
                else s.shared("service_error_schema_not_found").format(listOf(tooltype, schemaId).first { it.isNotEmpty() }))

        val resultData = mutableMapOf<String, Any>(
            "schema_id" to schema.id,
            "content" to app.treelune.core.ai.prompts.AiLanguage.schema(context) { language -> resolve(language)!!.content }
        )
        // Entries schemas are one per tool: the tool is part of what tells two of them apart.
        // Its type comes with it, whose operations are read beside the entries (ToolOperation).
        if (toolInstanceId.isNotEmpty()) {
            resultData["tool_instance_id"] = toolInstanceId
            toolTypeOf(toolInstanceId)?.let { resultData["tooltype"] = it }
        }

        return OperationResult.success(resultData)
    }

    /** The config schema of a tool of [tooltype], or null when there is no such type. */
    private fun configSchema(tooltype: String, language: Context): Schema? {
        val toolType = ToolTypeManager.getToolType(tooltype) ?: return null
        return ToolConfigSettings.schema(toolType, "${tooltype}_config", language)
    }

    /** The tool type of the tool [toolInstanceId], or null when there is no such tool. */
    private suspend fun toolTypeOf(toolInstanceId: String): String? =
        app.treelune.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)?.tooltype

    /**
     * The schema of the entries of the tool [toolInstanceId], from its current config; null when
     * there is no such tool.
     *
     * @throws IllegalStateException when the tool's config or fields cannot be read: an error
     *   for the caller, not a schema that does not exist
     */
    private suspend fun entrySchema(toolInstanceId: String, language: Context): Schema? {
        val tool = app.treelune.core.database.AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(toolInstanceId)
            ?: return null
        val toolType = ToolTypeManager.getToolType(tool.tooltype) ?: return null
        return Schema(
            id = "${tool.tooltype}_data",
            displayName = toolType.getDisplayName(language),
            description = toolType.getUsage(language),
            category = SchemaCategory.TOOL_DATA,
            content = BaseSchemas.getEntrySchemaOrThrow(toolType, JSONObject(tool.config_json), toolInstanceId, language)
        )
    }

    /**
     * List all available schema IDs
     */
    private suspend fun handleListSchemas(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.service("SchemaService.list() called")

        val schemaIds = getAllSchemaIds()
        return OperationResult.success(mapOf(
            "schema_ids" to schemaIds
        ))
    }

    // ========================================================================================
    // Schema Resolution Methods
    // ========================================================================================

    /**
     * Get system schemas (hardcoded providers)
     */
    private fun getSystemSchema(schemaId: String, context: Context): Schema? {
        return when {
            schemaId == ZoneSettings.SCHEMA_ID -> ZoneSettings.schema(context)
            schemaId.startsWith("field_type_") -> FieldTypeSchemas.getSchema(schemaId, context)
            schemaId.startsWith("app_config_") -> app.treelune.core.config.AppSettings.CATEGORIES
                .find { schemaId == "app_config_$it" }?.let { app.treelune.core.config.AppSettings.schema(it, context) }
            schemaId == "ai_message_response" -> {
                // AI message response schema
                LogManager.service("AI schema requested: $schemaId - using AIMessageSchemas")
                AIMessageSchemas.getAIMessageResponseSchema(context)
            }
            schemaId == CommunicationModules.SCHEMA_ID -> CommunicationModules.schema(context)
            schemaId == app.treelune.core.ai.data.RoutineSchemas.CONFIG_ID -> app.treelune.core.ai.data.RoutineSchemas.config(context)
            schemaId == app.treelune.core.ai.data.RoutineSchemas.RUNS_ID -> app.treelune.core.ai.data.RoutineSchemas.runs(context)
            schemaId == app.treelune.core.tasks.TaskSchemas.CONFIG_ID -> app.treelune.core.tasks.TaskSchemas.config(context)
            schemaId == app.treelune.core.apis.ApiSettings.SCHEMA_ID -> app.treelune.core.apis.ApiSettings.schema(context)
            else -> null
        }
    }

    /**
     * Get all available schema IDs in the system
     */
    private fun getAllSchemaIds(): List<String> {
        val schemaIds = mutableListOf<String>()

        // Add system schema IDs (hardcoded)
        schemaIds.addAll(getSystemSchemaIds())

        // A tool type's config and entries, by their computed names
        ToolTypeManager.getAllToolTypes().keys.forEach { schemaIds.add("${it}_config"); schemaIds.add("${it}_data") }

        LogManager.service("Found ${schemaIds.size} total schema IDs")
        return schemaIds.sorted()
    }

    /**
     * Get system schema IDs (hardcoded list)
     */
    private fun getSystemSchemaIds(): List<String> {
        val schemaIds = mutableListOf(
            ZoneSettings.SCHEMA_ID,
            // AI schemas
            "ai_message_response",
            CommunicationModules.SCHEMA_ID,
            // A routine's config and its runs
            app.treelune.core.ai.data.RoutineSchemas.CONFIG_ID,
            app.treelune.core.ai.data.RoutineSchemas.RUNS_ID,
            // A Task's config
            app.treelune.core.tasks.TaskSchemas.CONFIG_ID,
            // An API's config
            app.treelune.core.apis.ApiSettings.SCHEMA_ID
        )
        // One schema per category of the app's settings, by the name getSystemSchema serves
        schemaIds.addAll(app.treelune.core.config.AppSettings.CATEGORIES.map { "app_config_$it" })

        schemaIds.addAll(FieldTypeSchemas.getAllSchemaIds())

        return schemaIds
    }

    /**
     * Verbalize schema operation
     */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)

        return when (operation) {
            "get" -> {
                // Named as the user knows it: a tool by its name, a tool type by the name it shows;
                // a schema of the app itself has no other name than its id
                val toolId = params.optString("tool_instance_id")
                val tooltype = params.optString("tooltype")
                val target = when {
                    toolId.isNotEmpty() -> {
                        val tool = app.treelune.core.coordinator.Coordinator(context).processUserAction("tools.get", mapOf("tool_instance_id" to toolId))
                        (tool.data?.get("tool_instance") as? Map<*, *>)?.get("name") as? String ?: s.shared("content_unnamed")
                    }
                    tooltype.isNotEmpty() -> app.treelune.core.tools.ToolTypeManager.getToolType(tooltype)?.getDisplayName(context) ?: tooltype
                    else -> params.optString("id")
                }
                s.shared("action_verbalize_schema_get").format(target)
            }
            "list" -> s.shared("action_verbalize_schema_list")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}