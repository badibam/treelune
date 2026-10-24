package app.treelune.core.ai.prompts

import app.treelune.core.identities.IdentityPrompt
import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.ai.data.*
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.ServiceRegistry
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ReferenceName
import app.treelune.core.fields.loadReferenceNames
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Result of executing a single command for prompt formatting
 */
data class PromptCommandResult(
    val dataTitle: String,         // Title/header for data section in prompt
    val formattedData: String,     // JSON formatted data for prompt
    /**
     * Whether it is left out of the session's data threshold: an identity read, whose size is
     * known and shown on the identity's screen (docs/AI.md, « Identities »)
     */
    val sizeExempt: Boolean = false
)

/** The characters of [toPromptSection] that come from results left out of the data threshold. */
fun List<PromptCommandResult>.exemptChars(): Int =
    filter { it.sizeExempt }.sumOf { "# ${it.dataTitle}\n${it.formattedData}".length + 2 }

/**
 * The results of one batch as the section the model reads: a heading and the data under it.
 *
 * A command that failed, or a schema already sent, carries neither title nor data. Left in,
 * it printed an empty "# " heading among the real ones.
 */
fun List<PromptCommandResult>.toPromptSection(): String =
    filter { it.dataTitle.isNotEmpty() || it.formattedData.isNotEmpty() }
        .joinToString("\n\n") { "# ${it.dataTitle}\n${it.formattedData}" }

/**
 * Complete execution result including prompt data and system message
 */
data class CommandExecutionResult(
    val promptResults: List<PromptCommandResult>,  // For prompt inclusion
    val systemMessage: SystemMessage                // For conversation history
)

/**
 * Executes ExecutableCommands and formats results for prompt inclusion and conversation history
 *
 * Unified command executor used by:
 * - PromptManager (user enrichments Level 2/4)
 * - AICommandProcessor (AI data/action commands)
 *
 * Core responsibilities:
 * - Execute ExecutableCommand configurations via coordinator
 * - Format results as JSON with metadata first for prompt inclusion
 * - Generate SystemMessage with aggregate results for conversation history
 * - Track success/failure status for each command
 * - Send a read once in a session (SessionReads): a schema, an identity, a resource of its
 *   bagage, its text not sent again while unchanged, sent again whole when it changed
 * - Put before a schema of a tool type, its config's or a tool's entries', the first level of
 *   that type's entry (EntryDescription), itself a read sent once
 */
class CommandExecutor(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val s = Strings.`for`(context = context)

    /** What [command] reads, when it is sent once in a session (SessionReads). */
    private fun readTarget(command: ExecutableCommand): String? =
        SessionReads.target(command.resource, command.operation, command.params)

    /**
     * Execute commands and return complete result with prompt data + SystemMessage
     *
     * @param commands The commands to execute
     * @param messageType Type of SystemMessage (DATA_ADDED or ACTIONS_EXECUTED)
     * @param level The level name for logging purposes
     * @param sessionId Session ID whose reads are sent once (null = every read sent)
     * @param origin Who the commands come from: the AI's, the user's pointers, the app's prompt
     * @return CommandExecutionResult with prompt data and system message
     */
    suspend fun executeCommands(
        commands: List<ExecutableCommand>,
        messageType: SystemMessageType,
        origin: app.treelune.core.coordinator.Source,
        level: String = "unknown",
        sessionId: String? = null
    ): CommandExecutionResult {
        LogManager.aiPrompt("CommandExecutor executing ${commands.size} commands for $level (sessionId=$sessionId)", "DEBUG")

        // The texts the model holds, by target: those of the session, then those of this batch
        val sent = if (sessionId != null) loadSentReads(sessionId) else emptyMap()
        val sentInBatch = mutableMapOf<String, String>()

        // A query waits on the entries schema of the tool it reads, as the model last received
        // it, unless a SCHEMA command of the same batch asks for it
        if (sessionId != null) {
            val askedInBatch = commands
                .filter { it.resource == "schemas" && it.operation == "get" }
                .mapNotNull { readTarget(it) }
                .toSet()
            val queriedTools = commands
                .filter { it.resource == "tool_data" && it.operation == "get" }
                .mapNotNull { it.params["tool_instance_id"] as? String ?: it.params["id"] as? String }
                .filter { SessionReads.entriesSchemaTarget(it) !in askedInBatch }
            val missingSchemas = missingEntrySchemas(queriedTools, sent)

            if (missingSchemas.isNotEmpty()) {
                LogManager.aiPrompt("Schema verification failed: ${missingSchemas.size} schemas missing", "WARN")
                return CommandExecutionResult(
                    promptResults = emptyList(),
                    systemMessage = schemaRequiredMessage(missingSchemas, sent, "ai_schema_required_shown", "ai_schema_required_summary")
                )
            }
        }

        if (commands.isEmpty()) {
            LogManager.aiPrompt("No commands to execute, returning empty result", "DEBUG")
            return CommandExecutionResult(
                promptResults = emptyList(),
                systemMessage = SystemMessage(
                    type = messageType,
                    commandResults = emptyList(),
                    summary = s.shared("ai_system_no_commands")
                )
            )
        }

        val promptResults = mutableListOf<PromptCommandResult>()
        val commandResults = mutableListOf<app.treelune.core.ai.data.CommandResult>()
        var successCount = 0
        var failedCount = 0

        for ((index, command) in commands.withIndex()) {
            LogManager.aiPrompt("Executing command ${index + 1}/${commands.size}: ${command.resource}.${command.operation}", "DEBUG")

            // A read sent once is run all the same: only its text tells whether it changed
            val target = sessionId?.let { readTarget(command) }
            // Images shown to the model enter its conversation, which only the executor knows
            val run = if (command.resource == "files" && command.operation == "show_images" && sessionId != null)
                command.copy(params = command.params + ("session_id" to sessionId)) else command
            // A step of the history keeps the conversation whose command it is
            var result = if (sessionId != null) {
                kotlinx.coroutines.withContext(app.treelune.core.history.StepSession(sessionId)) { executeCommand(run, origin) }
            } else executeCommand(run, origin)

            // A schema of a tool type comes after the first level of its entry, unless the model
            // holds it already: what the parts of an entry mean in the type, which neither the
            // config's schema nor the entries' says
            if (command.resource == "schemas" && command.operation == "get" && result?.commandResult?.status == CommandStatus.SUCCESS) {
                val tooltype = command.params["tooltype"] as? String ?: result.commandResult.data?.get("tooltype") as? String
                tooltype?.let { entryDescriptionRead(it, sent + sentInBatch) }?.let { read ->
                    sentInBatch[read.target] = read.fingerprint
                    promptResults.add(read.result.promptResult)
                    commandResults.add(read.result.commandResult)
                }
            }

            val fingerprint = result?.commandResult?.takeIf { it.status == CommandStatus.SUCCESS }
                ?.data?.get(SessionReads.FINGERPRINT) as? String
            if (target != null && fingerprint != null) {
                val outcome = SessionReads.outcome(sentInBatch[target] ?: sent[target], fingerprint)
                LogManager.aiPrompt("Read $target: $outcome", "DEBUG")
                sentInBatch[target] = fingerprint
                when (outcome) {
                    SessionReads.Outcome.SAME -> {
                        promptResults.add(PromptCommandResult("", ""))
                        commandResults.add(app.treelune.core.ai.data.CommandResult(
                            command = "${command.resource}.${command.operation}",
                            status = CommandStatus.CACHED,
                            details = if (command.resource == "schemas") s.shared("ai_schema_already_included").format(target)
                                else s.shared("ai_identity_already_read"),
                            data = null,
                            error = null,
                            isActionCommand = false
                        ))
                        successCount++
                        continue
                    }
                    SessionReads.Outcome.CHANGED -> result = result!!.copy(promptResult = result.promptResult.copy(
                        formattedData = s.shared("ai_read_replaces") + "\n\n" + result.promptResult.formattedData
                    ))
                    SessionReads.Outcome.NEW -> {}
                }
            }

            if (result != null) {
                promptResults.add(result.promptResult)
                commandResults.add(result.commandResult)
                if (result.commandResult.status == CommandStatus.SUCCESS) {
                    successCount++
                    LogManager.aiPrompt("Command ${index + 1} succeeded", "DEBUG")
                } else {
                    failedCount++
                    LogManager.aiPrompt("Command ${index + 1} failed: ${result.commandResult.details}", "WARN")
                }
            } else {
                failedCount++
                commandResults.add(
                    app.treelune.core.ai.data.CommandResult(
                        command = "${command.resource}.${command.operation}",
                        status = CommandStatus.FAILED,
                        details = s.shared("ai_command_not_run"),
                        data = null,
                        error = s.shared("ai_command_app_error"),
                        isActionCommand = command.isActionCommand
                    )
                )
                LogManager.aiPrompt("Command ${index + 1} failed - continuing with remaining commands", "WARN")
            }
        }

        // Generate summary based on message type
        val summary = generateSummary(messageType, successCount, failedCount)

        LogManager.aiPrompt("CommandExecutor completed for $level: $successCount succeeded, $failedCount failed", "INFO")

        return CommandExecutionResult(
            promptResults = promptResults,
            systemMessage = SystemMessage(
                type = messageType,
                commandResults = commandResults,
                summary = summary
            )
        )
    }

    /**
     * Internal result combining prompt data and command status
     */
    private data class InternalCommandResult(
        val promptResult: PromptCommandResult,
        val commandResult: app.treelune.core.ai.data.CommandResult
    )

    /**
     * Execute a single ExecutableCommand through coordinator
     *
     * Routes to coordinator using resource.operation pattern and returns
     * InternalCommandResult with prompt data and command status.
     *
     * The services check what they are given, whoever the caller.
     */
    private suspend fun executeCommand(command: ExecutableCommand, origin: app.treelune.core.coordinator.Source): InternalCommandResult? {
        LogManager.aiPrompt("Executing ExecutableCommand: resource=${command.resource}, operation=${command.operation}, isActionCommand=${command.isActionCommand}", "DEBUG")

        return withContext(Dispatchers.IO) {
            try {
                val commandString = "${command.resource}.${command.operation}"

                val paramsMap = command.params

                LogManager.aiPrompt("Calling coordinator: $commandString with ${paramsMap.size} params", "VERBOSE")

                // Note: Coordinator.executeServiceOperation() handles recursive Map→JSON conversion
                val result = coordinator.process(origin, commandString, paramsMap)

                if (result.isSuccess) {
                    val data = result.data ?: emptyMap()

                    // For actions, data may be empty or minimal (just success/ID)
                    // For queries, empty data is unusual
                    if (data.isEmpty() && !command.isActionCommand) {
                        LogManager.aiPrompt("Query succeeded but returned empty data", "WARN")

                        // Generate description for empty query result
                        val description = userDetails(command, data).ifEmpty { s.shared("ai_system_query_no_data") }

                        return@withContext InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),
                            commandResult = app.treelune.core.ai.data.CommandResult(
                                command = commandString,
                                status = CommandStatus.SUCCESS,
                                details = description,
                                data = null,
                                error = null
                            )
                        )
                    }

                    val dataTitle = generateDataTitle(command, data)
                    // A result that cannot be put in the model's form fails the command out loud:
                    // handed raw, its dates would reach the model as milliseconds, unannounced
                    val formattedData = if (command.isActionCommand) "" else try {
                        formatResultData(command, data)
                    } catch (e: Exception) {
                        LogManager.aiPrompt("Result of $commandString could not be formatted: ${e.message}", "ERROR", e)
                        return@withContext InternalCommandResult(
                            promptResult = PromptCommandResult("", ""),
                            commandResult = app.treelune.core.ai.data.CommandResult(
                                command = commandString,
                                status = CommandStatus.FAILED,
                                details = dataTitle.ifEmpty { null },
                                data = null,
                                error = s.shared("ai_error_result_unformattable").format(e.message ?: ""),
                                isActionCommand = false
                            )
                        )
                    }

                    // Get verbalized description for all commands
                    // - Action commands: use service verbalization (e.g., "Création de la zone « Santé »")
                    // - Query commands: a line for the user (userDetails), the data's header being the AI's
                    val verbalizedDescription = if (command.isActionCommand) {
                        // Enrich params with name from result for delete operations
                        // This allows verbalize() to access the name even after the entity is deleted
                        val enrichedCommand = if (command.operation == "delete" && data.containsKey("name")) {
                            command.copy(params = command.params + ("name" to data["name"]!!))
                        } else {
                            command
                        }
                        getVerbalizedDescription(enrichedCommand)
                    } else {
                        userDetails(command, data).ifEmpty { null }
                    }

                    // Store data appropriately based on command type:
                    // - Actions: filter to minimal data (ID, name)
                    // - Queries: filter to essential metadata (exclude large content fields like schema.content)
                    // - A read sent once: its target and its text's fingerprint besides, for the
                    //   session to know what the model holds; an identity read nothing else
                    val target = readTarget(command)
                    val storedData = if (command.isActionCommand) {
                        filterActionResultData(command.operation, data)
                    } else if (target != null) {
                        val kept = if (command.resource == "identities") emptyMap() else filterQueryResultData(command.resource, data) ?: emptyMap()
                        kept + mapOf(SessionReads.TARGET to target, SessionReads.FINGERPRINT to SessionReads.fingerprint(formattedData))
                    } else {
                        filterQueryResultData(command.resource, data)
                    }

                    LogManager.aiPrompt("Command succeeded", "DEBUG")
                    val cmdResult = app.treelune.core.ai.data.CommandResult(
                        command = commandString,
                        status = CommandStatus.SUCCESS,
                        details = verbalizedDescription,
                        data = storedData,
                        error = null,
                        isActionCommand = command.isActionCommand
                    )
                    LogManager.aiPrompt("Created CommandResult: command=$commandString, isActionCommand=${cmdResult.isActionCommand}, data=${cmdResult.data}", "DEBUG")
                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult(dataTitle, formattedData, sizeExempt = command.resource == "identities" && target != null),
                        commandResult = cmdResult
                    )
                } else {
                    LogManager.aiPrompt("Command failed: ${result.error}", "WARN")

                    // Get verbalized description (even for failures)
                    val verbalizedDescription = getVerbalizedDescription(command)

                    return@withContext InternalCommandResult(
                        promptResult = PromptCommandResult("", ""),
                        commandResult = app.treelune.core.ai.data.CommandResult(
                            command = commandString,
                            status = CommandStatus.FAILED,
                            details = verbalizedDescription,
                            data = null,
                            error = result.error,
                            isActionCommand = command.isActionCommand
                        )
                    )
                }

            } catch (e: Exception) {
                LogManager.aiPrompt("Command execution failed: ${e.message}", "ERROR", e)
                return@withContext null
            }
        }
    }

    /**
     * Get verbalized description from service for action command
     * Returns substantive form description (e.g., "Création de la zone « Santé »")
     */
    private suspend fun getVerbalizedDescription(command: ExecutableCommand): String {
        return try {
            val serviceRegistry = ServiceRegistry(context)
            val service = serviceRegistry.getService(command.resource)

            if (service is ExecutableService) {
                val paramsJson = org.json.JSONObject(command.params)
                service.verbalize(command.operation, paramsJson, context)
            } else {
                // Fallback if service doesn't implement ExecutableService
                "${command.resource}.${command.operation}"
            }
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to verbalize command ${command.resource}.${command.operation}: ${e.message}", "WARN", e)
            "${command.resource}.${command.operation}"
        }
    }

    /**
     * Filter action result data according to rules:
     * - create/update: keep only name + id fields
     * - delete: return null (no data needed)
     * - batch_*: keep only count fields (*_count)
     * - Remove all timestamp fields (created_at, updated_at, deleted_at, createdAt, updatedAt)
     */
    private fun filterActionResultData(operation: String, data: Map<String, Any>): Map<String, Any>? {
        return when (operation) {
            "delete" -> {
                // Delete operations: no data needed
                null
            }
            "create", "update" -> {
                // Create/Update: keep only name + id fields (zone_id, tool_instance_id, id, name, tooltype)
                val filtered = mutableMapOf<String, Any>()

                // ID fields (various naming patterns)
                data["zone_id"]?.let { filtered["zone_id"] = it }
                data["tool_instance_id"]?.let { filtered["tool_instance_id"] = it }
                data["id"]?.let { filtered["id"] = it }

                // Name/type fields
                data["name"]?.let { filtered["name"] = it }
                data["tooltype"]?.let { filtered["tooltype"] = it }

                // A group left behind by a change of zone (Groups)
                data["group_emptied"]?.let { filtered["group_emptied"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            "batch_create", "batch_update", "batch_delete" -> {
                // Batch operations: their counts, and the ids of the entries created, for the AI
                // to act on what it just wrote without reading it back
                val filtered = mutableMapOf<String, Any>()

                data["created_count"]?.let { filtered["created_count"] = it }
                if (operation == "batch_create") data["ids"]?.let { filtered["ids"] = it }
                // The user's fields the entries created leave empty, each with how many
                data["extra_left_empty"]?.let { filtered["extra_left_empty"] = it }
                data["failed_count"]?.let { filtered["failed_count"] = it }
                data["updated_count"]?.let { filtered["updated_count"] = it }
                data["deleted_count"]?.let { filtered["deleted_count"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            else -> {
                // Unknown operation: pass through without filtering
                data
            }
        }
    }

    /**
     * Filter query result data to essential metadata (avoid DB bloat)
     *
     * Removes large content fields while keeping identifiers needed for deduplication:
     * - schemas: keep schema_id + tool_instance_id (for data/execution schema deduplication)
     * - tools/zones: keep id, name, count fields
     * - Remove large nested objects and arrays
     */
    private fun filterQueryResultData(resource: String, data: Map<String, Any>): Map<String, Any>? {
        return when (resource) {
            "schemas" -> {
                // Schemas: keep schema_id + tool_instance_id for deduplication, remove large 'content'
                // tool_instance_id is needed for data/execution schemas (custom_fields enrichment);
                // the tooltype of a tool's entries, whose entry's first level comes before them
                val filtered = mutableMapOf<String, Any>()
                data["schema_id"]?.let { filtered["schema_id"] = it }
                data["tool_instance_id"]?.let { filtered["tool_instance_id"] = it }
                data["tooltype"]?.let { filtered["tooltype"] = it }
                if (filtered.isEmpty()) null else filtered
            }
            // Images shown: their ids in the conversation, which PromptManager sends after the message
            "files" -> data["image_ids"]?.let { mapOf("image_ids" to it) }
            "zones", "tools", "tool_data" -> {
                // Tools/Zones/Data: keep IDs, names, counts - remove large objects/arrays
                val filtered = mutableMapOf<String, Any>()

                // ID fields
                data["id"]?.let { filtered["id"] = it }
                data["zone_id"]?.let { filtered["zone_id"] = it }
                data["tool_instance_id"]?.let { filtered["tool_instance_id"] = it }

                // Name/type fields
                data["name"]?.let { filtered["name"] = it }
                data["tooltype"]?.let { filtered["tooltype"] = it }
                data["tooltype"]?.let { filtered["tooltype"] = it }

                // Count fields
                data["count"]?.let { filtered["count"] = it }
                data["total_count"]?.let { filtered["total_count"] = it }

                if (filtered.isEmpty()) null else filtered
            }
            else -> {
                // Unknown resource: filter conservatively (keep scalar values only)
                val filtered = mutableMapOf<String, Any>()
                data.forEach { (key, value) ->
                    // Keep only scalar values (not maps or lists)
                    if (value !is Map<*, *> && value !is List<*> && value !is Array<*>) {
                        filtered[key] = value
                    }
                }
                if (filtered.isEmpty()) null else filtered
            }
        }
    }

    /**
     * The summary of a batch of commands, read by the user and the AI alike: how many went
     * through. Only queries (DATA_ADDED) and actions (ACTIONS_EXECUTED) are batches; every other
     * system message is written with its own summary where it happens.
     */
    private fun generateSummary(type: SystemMessageType, successCount: Int, failedCount: Int): String {
        val kind = when (type) {
            SystemMessageType.DATA_ADDED -> "queries"
            SystemMessageType.ACTIONS_EXECUTED -> "actions"
            else -> throw IllegalStateException("$type is written where it happens, not generated by CommandExecutor")
        }
        return when {
            failedCount == 0 && successCount == 1 -> s.shared("ai_system_${kind}_success_one")
            failedCount == 0 -> s.shared("ai_system_${kind}_success").format(successCount)
            successCount == 0 -> s.shared("ai_system_${kind}_all_failed").format(failedCount)
            else -> s.shared("ai_system_${kind}_partial").format(successCount, failedCount)
        }
    }

    /**
     * What the user reads of a query in the chat, in the app's language: what was read, named —
     * for entries, the tool and how many. The data's header is written for the AI, in English
     * (filters, fields, ids), and stays in the prompt.
     */
    private suspend fun userDetails(command: ExecutableCommand, data: Map<String, Any>): String {
        if (command.resource != "tool_data") return userReadLine(command, data)
        val toolInstanceId = command.params["tool_instance_id"] as? String ?: command.params["id"] as? String
            ?: return userReadLine(command, data)
        val count = data["count"] as? Int ?: (data["entries"] as? List<*>)?.size ?: 0
        return s.shared("ai_data_user_summary").format(resolveToolInstanceName(toolInstanceId), count)
    }

    /**
     * The line of a read other than entries, one per read the AI can ask for (AICommands): what
     * was read, by its name when it has one. Empty for a read without its line, which then shows
     * its command alone.
     */
    private suspend fun userReadLine(command: ExecutableCommand, data: Map<String, Any>): String {
        fun line(key: String, vararg args: Any) = s.shared("ai_read_user_$key").format(*args)
        val params = command.params
        val tool = (params["tool_instance_id"] as? String)?.let { resolveToolInstanceName(it) }
        val zone = (params["zone_id"] as? String)?.let { resolveZoneName(it) }
        return when ("${command.resource}.${command.operation}") {
            "schemas.get" -> tool?.let { line("schema_of", it) } ?: line("schema")
            "tools.get" -> line("tool_config", tool ?: throw IllegalStateException("tools.get without a tool_instance_id"))
            "tools.list" -> zone?.let { line("tools_of", it) } ?: line("tools")
            "tools.list_all" -> line("tools")
            "zones.get" -> line("zone_config", zone ?: throw IllegalStateException("zones.get without a zone_id"))
            "zones.list" -> line("zones")
            "variables.list" -> line("variables_of", zone ?: "")
            "variables.list_all" -> line("variables")
            "readings.read" -> line("reading", params["variable"] as? String ?: "")
            "automations.config" -> line("routine", resolveAutomationName(params["automation_id"] as? String))
            "automations.list" -> line("routines_of", zone ?: "")
            "automations.list_all" -> line("routines")
            "ai_sessions.list_sessions_for_automation" -> line("runs_of", resolveAutomationName(params["automation_id"] as? String))
            "ai_sessions.run_history" -> line("run")
            "files.read" -> line("file")
            "files.show_images" -> line("images", (data["images"] as? List<*>)?.size ?: 0)
            "imports.detect" -> line("import_plan")
            "app_config.get_current_datetime" -> line("datetime")
            "app_config.get" -> line("settings")
            "icons.overview", "icons.search" -> line("icons")
            "identities.list" -> line("identities")
            "identities.read" -> line("identity", (data["identity"] as? Map<*, *>)?.get("name") as? String ?: "")
            "identities.read_resource" -> line("identity_resource", params["key"] as? String ?: "")
            "history.list" -> (tool ?: zone)?.let { line("history_of", it) } ?: line("history")
            else -> ""
        }
    }

    /**
     * Generate title/header for data section in prompt
     * Creates descriptive title with context (tool name, filters, period, etc.)
     * Returns empty string for action commands
     *
     * Always states the result count (even if 0) and the exact query (filters, fields): the AI
     * knows what the data covers, and has no reason to ask for it again
     *
     * A name missing from a successful result throws: executeCommand then fails the command
     */
    private suspend fun generateDataTitle(command: ExecutableCommand, data: Map<String, Any>): String {
        // No title needed for action commands
        if (command.operation in listOf("create", "update", "delete", "batch_create", "batch_update", "batch_delete")) {
            return ""
        }

        return when (command.resource) {
            "tool_data" -> {
                // Resolve tool instance name from ID in command params
                // Note: UserCommandProcessor transforms "id" → "tool_instance_id"
                LogManager.aiPrompt("tool_data command params keys: ${command.params.keys}", "VERBOSE")
                LogManager.aiPrompt("tool_instance_id=${command.params["tool_instance_id"]}, id=${command.params["id"]}", "VERBOSE")

                val toolInstanceId = command.params["tool_instance_id"] as? String
                    ?: command.params["id"] as? String

                LogManager.aiPrompt("Resolved toolInstanceId: $toolInstanceId", "VERBOSE")

                val toolName = resolveToolInstanceName(toolInstanceId
                    ?: throw IllegalStateException("tool_data.${command.operation} succeeded without a tool_instance_id in its params"))
                LogManager.aiPrompt("Resolved tool name: $toolName", "VERBOSE")

                val count = data["count"] as? Int
                    ?: (data["entries"] as? List<*>)?.size
                    ?: 0

                // Build comprehensive header with ALL query details
                val headerParts = mutableListOf<String>()

                // Main title with result count (ALWAYS shown, even if 0)
                headerParts.add("=== ${s.shared("ai_data_header_tool").format(toolName)} ===")
                headerParts.add(s.shared("ai_data_result_count").format(count))

                // The filters, a period included, written as the model writes them
                val filters = command.params["filters"] as? List<*>
                if (filters.isNullOrEmpty()) {
                    headerParts.add(s.shared("ai_data_filters_none"))
                } else {
                    headerParts.add(s.shared("ai_data_filters").format(describeFilters(command, filters)))
                }

                val limit = command.params["limit"] as? Int
                if (limit != null) {
                    headerParts.add(s.shared("ai_data_limit").format(limit))
                }

                // Fields included (dynamic from command params)
                val requestedFields = command.params["fields"] as? List<*>
                val fieldsDisplay = if (requestedFields != null && requestedFields.isNotEmpty()) {
                    requestedFields.joinToString(", ")
                } else {
                    // Fallback for backward compatibility (no fields param = all fields)
                    s.shared("ai_data_fields_all")
                }
                headerParts.add(s.shared("ai_data_fields").format(fieldsDisplay))

                // An empty result says so, so that it does not read as a failure
                if (count == 0) headerParts.add(s.shared("ai_data_no_results_warning"))

                headerParts.joinToString("\n")
            }
            "schemas" -> {
                val schemaId = data["schema_id"] as? String
                    ?: throw IllegalStateException("schemas.${command.operation} result without schema_id")
                "Schema: $schemaId"
            }
            "identities" -> IdentityPrompt.title(command.operation, data)
            "tools" -> {
                when (command.operation) {
                    "get" -> {
                        // ToolInstanceService.handleGetById returns data["tool_instance"]["name"]
                        val toolInstance = data["tool_instance"] as? Map<*, *>
                        val toolName = toolInstance?.get("name") as? String
                            ?: throw IllegalStateException("tools.get result without tool_instance.name")
                        "Configuration for tool '$toolName'"
                    }
                    "list" -> {
                        val zoneId = command.params["zone_id"] as? String
                        if (zoneId != null) {
                            val zoneName = resolveZoneName(zoneId)
                            "Tool instances in zone '$zoneName'"
                        } else {
                            "All tool instances"
                        }
                    }
                    "list_all" -> "All tool instances"
                    else -> ""
                }
            }
            "zones" -> {
                when (command.operation) {
                    "get" -> {
                        // ZoneService.handleGet returns data["zone"]["name"]
                        val zone = data["zone"] as? Map<*, *>
                        val zoneName = zone?.get("name") as? String
                            ?: throw IllegalStateException("zones.get result without zone.name")
                        "Configuration for zone '$zoneName'"
                    }
                    "list" -> "All zones"
                    else -> ""
                }
            }
            "automations" -> when (command.operation) {
                "config" -> "Configuration for routine '${resolveAutomationName(command.params["automation_id"] as? String)}'"
                "list" -> "Routines in zone '${resolveZoneName(command.params["zone_id"] as String)}'"
                else -> "All routines"
            }
            "ai_sessions" -> when (command.operation) {
                "list_sessions_for_automation" -> "Runs of routine '${resolveAutomationName(command.params["automation_id"] as? String)}'"
                else -> "Run ${command.params["session_id"]}, its messages as its AI received them"
            }
            "files" -> when (command.operation) {
                "show_images" -> "Images of entries, shown below, each under its id in this conversation: ${(data["images"] as? List<*>)?.size ?: 0}"
                else -> "Data from ${command.resource}.${command.operation}"
            }
            "history" -> {
                val tool = (command.params["tool_instance_id"] as? String)?.let { " of tool '${resolveToolInstanceName(it)}'" }
                val zone = (command.params["zone_id"] as? String)?.let { " of zone '${resolveZoneName(it)}'" }
                "Steps of the history${tool ?: zone ?: ""}, the most recent first"
            }
            "icons" -> when (command.operation) {
                "overview" -> "Icons: overview"
                else -> {
                    val query = (command.params["query"] as? List<*>)?.joinToString(", ")
                    val categories = (command.params["categories"] as? List<*>)?.joinToString(", ")
                    listOfNotNull(
                        query?.let { "matching $it" },
                        categories?.let { "in $it" }
                    ).joinToString(" ", prefix = "Icons ")
                }
            }
            else -> {
                "Data from ${command.resource}.${command.operation}"
            }
        }
    }

    // The names below are read for a command that has just succeeded on the same id: one that
    // cannot be read is a bug, and throws

    /** The name of tool [toolInstanceId], read by tools.get. */
    private suspend fun resolveToolInstanceName(toolInstanceId: String): String {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        check(result.isSuccess) { "Cannot read the name of tool $toolInstanceId: ${result.error}" }
        return (result.data?.get("tool_instance") as? Map<*, *>)?.get("name") as? String
            ?: throw IllegalStateException("tools.get result for $toolInstanceId without tool_instance.name")
    }

    /** The name of routine [automationId], read by automations.get. */
    private suspend fun resolveAutomationName(automationId: String?): String {
        automationId ?: throw IllegalStateException("A routine command succeeded without an automation_id")
        val result = coordinator.processUserAction("automations.get", mapOf("automation_id" to automationId))
        check(result.isSuccess) { "Cannot read the name of routine $automationId: ${result.error}" }
        return (result.data?.get("automation") as? Map<*, *>)?.get("name") as? String
            ?: throw IllegalStateException("automations.get result for $automationId without automation.name")
    }

    /** The name of zone [zoneId], read by zones.get. */
    private suspend fun resolveZoneName(zoneId: String): String {
        val result = coordinator.processUserAction("zones.get", mapOf("zone_id" to zoneId))
        check(result.isSuccess) { "Cannot read the name of zone $zoneId: ${result.error}" }
        return (result.data?.get("zone") as? Map<*, *>)?.get("name") as? String
            ?: throw IllegalStateException("zones.get result for $zoneId without zone.name")
    }

    /**
     * Format result data as JSON with metadata first
     * Reorganizes data to put important metadata before bulk data
     */
    private suspend fun formatResultData(command: ExecutableCommand, data: Map<String, Any>): String {
        val reordered = mutableMapOf<String, Any>()
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()

        // An identity is read as its texts are written, never as escaped JSON
        if (command.resource == "identities") IdentityPrompt.forModel(command.operation, data)?.let { return it }

        // A schema is read in its notation, not as the JSON the app validates against
        (data["content"] as? String)?.takeIf { command.resource == "schemas" }?.let { content ->
            return schemaText(data["schema_id"], content, data["tooltype"] as? String)
        }

        // Extract metadata keys first based on command type
        when (command.resource) {
            "tool_data" -> {
                // Metadata: toolInstanceName, count
                // Bulk data: entries (with parsed data JSON)
                data["tool_instance_name"]?.let { reordered["tool_instance_name"] = it }
                data["count"]?.let { reordered["count"] = it }

                // The entries' dates and durations in ISO 8601, found by the tool's entry
                // schema: its fixed fields, the user's and the core's alike; each reference with
                // the current name of what it designates
                data["entries"]?.let { entries ->
                    val schema = runBlocking { loadEntrySchema(command) }
                    val converted = (entries as List<*>).map { ModelValues.toModel(it, schema, timezone)!! }
                    val references = converted.flatMap { ModelValues.references(it, schema) }.distinct()
                    val names = runBlocking { loadReferenceNames(references, context) }
                        .mapValues { (_, name) -> (name as? ReferenceName.Named)?.name }
                    // Each image by its id and its size, never the image (IMAGES shows it)
                    val imageIds = converted.flatMap { ModelValues.imageIds(it, schema) }.distinct()
                    val images = if (imageIds.isEmpty()) emptyMap() else runBlocking {
                        app.treelune.core.database.AppDatabase.getDatabase(context).fileDao().getByIds(imageIds)
                    }.associateBy { it.id }
                    reordered["entries"] = converted.map { ModelValues.withImageSizes(ModelValues.withReferenceNames(it, schema, names), schema, images)!! }
                }

                // Add pagination if present
                data["pagination"]?.let { reordered["pagination"] = it }
            }
            "tools" -> {
                // Config or list
                data["id"]?.let { reordered["id"] = it }
                data["name"]?.let { reordered["name"] = it }
                data["tooltype"]?.let { reordered["tooltype"] = it }

                // A config's dates and durations in ISO 8601, found by its tooltype's config schema
                data["tool_instance"]?.let { reordered["tool_instance"] = configForModel(it, timezone)!! }
                (data["tool_instances"] as? List<*>)?.let { list -> reordered["tool_instances"] = list.map { configForModel(it, timezone) } }

                // Add remaining fields (except tool_instance if already processed)
                data.forEach { (key, value) ->
                    if (key !in reordered) reordered[key] = value
                }
            }
            // A variable's values: each instant in ISO 8601, a duration too
            "readings", "variables" -> {
                val duration = (data["field"] as? Map<*, *>)?.get("type") == "DURATION"
                (data["values"] as? List<*>)?.let { values ->
                    reordered["values"] = values.map { row ->
                        (row as Map<*, *>).entries.associate { (key, value) ->
                            key.toString() to when {
                                key == "at" && value is Number -> DateTimeConverter.timestampToISO(value.toLong(), timezone)
                                key == "value" && duration && value is Number -> java.time.Duration.ofMillis(value.toLong()).toString()
                                else -> value
                            }
                        }
                    }
                }
                // A variable's terms: the fixed bounds of their periods in ISO 8601
                (data["variables"] as? List<*>)?.let { list -> reordered["variables"] = list.map { variableForModel(it, timezone) } }
                data["variable"]?.let { reordered["variable"] = variableForModel(it, timezone)!! }
            }
            // A routine's config, in the form the model writes it back (AutomationSchemas); the
            // routines of a list each in a line: what they are, never their message
            "automations" -> {
                if (command.operation == "config") {
                    val schema = JSONObject(AutomationSchemas.config(context).content)
                    return JSONObject(ModelValues.toModel(data, schema, timezone) as Map<*, *>).toString(2)
                }
                (data["automations"] as? List<*>)?.let { list -> reordered["automations"] = list.map { AutomationSchemas.listed(it as Map<*, *>, s) } }
                data["zone_id"]?.let { reordered["zone_id"] = it }
                return JSONObject(reordered as Map<*, *>).toString(2)
            }
            // A routine's runs, by their declared schema; one run, the text its AI read
            "ai_sessions" -> {
                if (command.operation == "run_history") return data["messages"] as String
                val runs = (data["sessions"] as List<*>).map { run -> (run as Map<*, *>).filterKeys { it in AutomationSchemas.RUN_KEYS } }
                val schema = JSONObject(AutomationSchemas.runs(context).content)
                reordered["runs"] = (ModelValues.toModel(mapOf("runs" to runs), schema, timezone) as Map<*, *>)["runs"]!!
                data["pagination"]?.let { reordered["pagination"] = it }
                return JSONObject(reordered as Map<*, *>).toString(2)
            }
            "zones" -> {
                // List or single zone
                data["id"]?.let { reordered["id"] = it }
                data["name"]?.let { reordered["name"] = it }
                // Add remaining fields
                data.forEach { (key, value) ->
                    if (key !in reordered) reordered[key] = value
                }
            }
            else -> {
                // Default: keep original order
                reordered.putAll(data)
            }
        }

        // Add any remaining keys not yet added
        data.forEach { (key, value) ->
            if (key !in reordered) reordered[key] = value
        }

        // This is where a result becomes the text the model reads, so this is where the
        // milliseconds everything else speaks turn into the ISO 8601 the model does
        // (docs/reference.md, the rule of dates and durations). Entries were converted above by their schema;
        // any other result carries its dates under the few names DateTimeConverter knows.
        val json = org.json.JSONObject(reordered as Map<*, *>)
        return if (command.resource == "tool_data") json.toString(2)
        else DateTimeConverter.timestampsToISO(json, timezone).toString(2)
    }

    /** A variable as the model reads it: the fixed bounds of its terms' periods in ISO 8601. */
    private fun variableForModel(variable: Any?, timezone: java.time.ZoneId): Any? {
        val row = variable as? Map<*, *> ?: return variable
        val definition = row["definition"] as? Map<*, *> ?: return variable
        val terms = definition["terms"] as? Map<*, *> ?: return variable
        fun iso(bound: Any?) = if (bound is Number) DateTimeConverter.timestampToISO(bound.toLong(), timezone) else bound
        val converted = terms.entries.associate { (name, term) ->
            val reading = (term as? Map<*, *>)?.get("reading") as? Map<*, *>
            val selection = reading?.get("selection") as? Map<*, *>
            val period = selection?.get("period") as? Map<*, *>
            name to if (period == null) term else mapOf("reading" to reading + ("selection" to selection + ("period" to period.mapValues { iso(it.value) })))
        }
        return row + ("definition" to definition + ("terms" to converted))
    }

    /**
     * A schema as the model reads it: its dates and durations in ISO 8601 (SchemaModelView),
     * written in the notation (SchemaNotation).
     */
    /**
     * The operations the tool type [tooltype] declares (ToolOperation), as the model reads them
     * after a tool's entries schema: each one's name, its sentence and its parameters' schema.
     * Empty when there is no tool type or it declares none.
     */
    private fun operationsForModel(tooltype: String?): String {
        val toolType = tooltype?.let { app.treelune.core.tools.ToolTypeManager.getToolType(it) } ?: return ""
        // In English, their parameters' labels with the app's beside (AiLanguage)
        val operations = toolType.getOperations(AiLanguage.english(context))
        if (operations.isEmpty()) return ""
        return buildString {
            append("\n\n## ").append(s.shared("ai_chunk_tool_operations_header"))
            for (operation in operations) {
                append("\n\n### ").append(operation.name).append("\n").append(operation.description)
                if (operation.params.isNotEmpty()) {
                    val content = AiLanguage.schema(context) { language ->
                        app.treelune.core.tools.ToolOperations.schema(tooltype, toolType.getOperations(language).first { it.name == operation.name }, language).content
                    }
                    append("\n").append(schemaForModel(content))
                }
            }
        }
    }

    private fun schemaForModel(content: String): String =
        SchemaNotation.render(SchemaModelView.forModel(JSONObject(content), AppConfigManager.getDateTimeConfig().getZoneId()))

    /** A tool instance of a result, its config in the form the model reads, its conditions' values included (ConditionValues). */
    private suspend fun configForModel(instance: Any?, timezone: java.time.ZoneId): Any? {
        val map = instance as? Map<*, *> ?: return instance
        val config = map["config"] ?: return instance
        val tooltype = map["tooltype"] as? String ?: return instance
        val toolType = app.treelune.core.tools.ToolTypeManager.getToolType(tooltype)
            ?: throw IllegalStateException("Unknown tooltype $tooltype")
        val schema = JSONObject(app.treelune.core.tools.ToolConfigSettings.schema(toolType, "${tooltype}_config", context).content)
        val values = app.treelune.core.ai.processing.ConditionValues(app.treelune.core.conditions.AppConditionSources(context, toolType.getRowFields()), timezone) { s.shared(it) }
            .toModel(app.treelune.core.tools.ToolConfigSettings.nodes(toolType, context), JsonUtils.toJSONObject(JsonUtils.toMap(config)))
        return map.toMutableMap().apply { put("config", ModelValues.toModel(JsonUtils.toMap(values), schema, timezone)) }
    }

    /**
     * The entry schema of the tool a tool_data command reads, generated from its current config:
     * what the result's dates and durations are converted by on the way to the model.
     *
     * @throws IllegalStateException when the tool or its fields cannot be read
     */
    /**
     * The filters of a TOOL_DATA query as the model writes them, their instants and durations in
     * ISO 8601 by the tool's entry schema: "timestamp >= 2026-09-19T00:00:00+02:00".
     */
    private suspend fun describeFilters(command: ExecutableCommand, filters: List<*>): String {
        val schema = loadEntrySchema(command)
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        return filters.joinToString(", ") { raw ->
            val filter = (raw as? Map<*, *>)?.let { map -> JSONObject(map.entries.associate { it.key.toString() to it.value }) } ?: return@joinToString raw.toString()
            val path = app.treelune.core.conditions.Conditions.fieldOf(filter) ?: ""
            val leaf = schema.optJSONObject("properties")?.let { properties ->
                if ('.' in path) properties.optJSONObject(path.substringBefore('.'))
                    ?.optJSONObject("properties")?.optJSONObject(path.substringAfter('.'))
                else properties.optJSONObject(path)
            }
            fun model(value: Any?) = if (leaf != null) ModelValues.toModel(value, leaf, zone) else value
            val shown = when (val right = app.treelune.core.conditions.Conditions.right(filter)) {
                app.treelune.core.conditions.Conditions.Right.None -> ""
                is app.treelune.core.conditions.Conditions.Right.Written -> when (val value = right.value) {
                    is org.json.JSONArray -> (0 until value.length()).map { model(JsonUtils.toValue(value.get(it))) }.toString()
                    else -> model(value).toString()
                }
                is app.treelune.core.conditions.Conditions.Right.NotWritten -> right.raw.toString()
            }
            "$path ${filter.optString("op")} $shown".trim()
        }
    }

    private suspend fun loadEntrySchema(command: ExecutableCommand): JSONObject {
        val toolInstanceId = command.params["tool_instance_id"] as? String
            ?: throw IllegalStateException("No tool_instance_id in ${command.resource}.${command.operation}")
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        val toolInstance = result.data?.get("tool_instance") as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val config = (toolInstance?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        val toolType = (toolInstance?.get("tooltype") as? String)?.let { app.treelune.core.tools.ToolTypeManager.getToolType(it) }
        if (!result.isSuccess || config == null || toolType == null) {
            throw IllegalStateException("Cannot read the tool $toolInstanceId: ${result.error}")
        }
        return JSONObject(app.treelune.core.tools.BaseSchemas.getEntrySchemaOrThrow(toolType, config, toolInstanceId, context))
    }

    /**
     * Data class for missing schema information with full schema content
     */
    private data class MissingSchemaInfo(
        val schemaId: String,
        val target: String,             // Its read target (SessionReads)
        val params: Map<String, String>, // What it is asked by, as a SCHEMA command: a tool's id, or a tool type
        val schemaContent: String,      // Full JSON schema content
        val tooltype: String?,          // Whose entry's first level comes before it
        val withOperations: Boolean,    // A tool's entries schema, which its type's operations follow
        val fingerprint: String,        // Of its text, as a SCHEMA command's result reads (schemaText)
        val replacesEarlier: Boolean    // The model received another version of it in the session
    )

    /** A schema as the model reads it: its id, its notation, its tooltype's operations. */
    private fun schemaText(schemaId: Any?, content: String, tooltype: String?): String =
        "schema_id: $schemaId\n" + schemaForModel(content) + operationsForModel(tooltype)

    /**
     * The SCHEMA_REQUIRED message for the AI's actions: the entries schemas of the tools among
     * [toolInstanceIds] they write, and the config schemas of the tool types among [tooltypes]
     * they create a tool of, those the model has not received in [sessionId]; null when it holds
     * them all.
     *
     * A write waits on the schema as a query does. A value that fits the schema can still be
     * wrong in meaning — a unit, the ends of a scale, what an option stands for — and only the
     * schema tells it; a tool's config as much, which the schema of its type and the first level
     * of its entry explain. The caller checks before the user is asked to validate the actions.
     */
    suspend fun schemaRequiredForActions(toolInstanceIds: Collection<String>, tooltypes: Collection<String>, sessionId: String): SystemMessage? {
        val sent = loadSentReads(sessionId)
        val missingSchemas = missingEntrySchemas(toolInstanceIds, sent) + missingConfigSchemas(tooltypes, sent)
        if (missingSchemas.isEmpty()) return null
        LogManager.aiPrompt("Actions held back: ${missingSchemas.size} schemas missing", "WARN")
        return schemaRequiredMessage(missingSchemas, sent, "ai_schema_required_for_actions_shown", "ai_schema_required_for_actions_summary")
    }

    /**
     * The message bringing the entries schemas of the tools sent always [toolInstanceIds] the
     * model has not received in [sessionId], or null when it holds them all. Stored once in the
     * session before the call, like any schema received: the Level 2 then carries only their
     * data, and the model's queries and writes on them find their schema in the session.
     */
    suspend fun schemasForAlwaysSent(toolInstanceIds: Collection<String>, sessionId: String): SystemMessage? {
        val sent = loadSentReads(sessionId)
        val missingSchemas = missingEntrySchemas(toolInstanceIds, sent)
        if (missingSchemas.isEmpty()) return null
        LogManager.aiPrompt("Tools sent always: ${missingSchemas.size} entries schemas brought into the session", "INFO")
        return schemaRequiredMessage(missingSchemas, sent, "ai_schema_always_send_shown", "ai_schema_always_send_summary")
    }

    /**
     * The SCHEMA_REQUIRED message holding [missingSchemas]: the chat shows [shownKey]'s sentence,
     * the AI reads [forAIKey]'s, naming the schemas, ahead of the schemas themselves, and ahead of
     * those, the first level of each of their types' entry the model does not hold ([sent]).
     * Each is recorded as a schemas.get result, so the session counts it as sent.
     */
    private suspend fun schemaRequiredMessage(missingSchemas: List<MissingSchemaInfo>, sent: Map<String, String>, shownKey: String, forAIKey: String): SystemMessage {
        val schemaSummary = missingSchemas.joinToString("\n") { schema ->
            "- ${schema.schemaId} (" + schema.params.entries.joinToString { (key, value) -> "$key: $value" } + ")"
        }

        val schemasText = StringBuilder()
        schemasText.appendLine(s.shared(forAIKey).format(missingSchemas.size, schemaSummary))
        schemasText.appendLine()
        val entryReads = missingSchemas.mapNotNull { it.tooltype }.distinct().mapNotNull { entryDescriptionRead(it, sent) }
        for (read in entryReads) {
            schemasText.appendLine("## ${read.result.promptResult.dataTitle}")
            schemasText.appendLine()
            schemasText.appendLine(read.result.promptResult.formattedData)
            schemasText.appendLine()
        }
        for (schema in missingSchemas) {
            schemasText.appendLine("## Schema: ${schema.schemaId}")
            schema.params.forEach { (key, value) -> schemasText.appendLine("$key: $value") }
            if (schema.replacesEarlier) schemasText.appendLine(s.shared("ai_read_replaces"))
            schemasText.appendLine()
            schemasText.appendLine(schemaForModel(schema.schemaContent) + operationsForModel(schema.tooltype.takeIf { schema.withOperations }))
            schemasText.appendLine()
        }
        LogManager.aiPrompt("SCHEMA_REQUIRED formattedData length: ${schemasText.length}", "DEBUG")

        // Verbalized by SchemaService, as a SCHEMA command's result is
        val schemaService = app.treelune.core.services.SchemaService(context)
        val schemaCommandResults = missingSchemas.map { schema ->
            app.treelune.core.ai.data.CommandResult(
                command = "schemas.get",
                status = CommandStatus.SUCCESS,
                details = schemaService.verbalize("get", JSONObject(schema.params), context),
                data = mapOf<String, Any>(
                    "schema_id" to schema.schemaId,
                    SessionReads.TARGET to schema.target,
                    SessionReads.FINGERPRINT to schema.fingerprint
                ) + schema.params,
                error = null,
                isActionCommand = false
            )
        }

        return SystemMessage(
            type = SystemMessageType.SCHEMA_REQUIRED,
            commandResults = entryReads.map { it.result.commandResult } + schemaCommandResults,
            summary = s.shared(shownKey),
            formattedData = schemasText.toString()
        )
    }

    /**
     * The entries schemas of the tools among [toolInstanceIds] the model does not hold as they
     * are now: never received in the session, or received in another version ([sent], the
     * fingerprints it last received by target). Each is read, for its text to be compared.
     *
     * @throws UnreadableSchema when one of them cannot be read: the commands waiting on it go no
     *   further, and the reason reaches the model (a command error for writes, which it answers
     *   by correcting them; a system error for queries)
     */
    private suspend fun missingEntrySchemas(
        toolInstanceIds: Collection<String>,
        sent: Map<String, String>
    ): List<MissingSchemaInfo> {
        val missingSchemas = mutableListOf<MissingSchemaInfo>()

        for (toolInstanceId in toolInstanceIds.distinct()) {
            // The entries schema of this tool, its user's fields included
            val target = SessionReads.entriesSchemaTarget(toolInstanceId)
            val schemaResult = coordinator.processUserAction("schemas.get", mapOf("tool_instance_id" to toolInstanceId))
            val schemaContent = (schemaResult.data?.get("content") as? String)?.takeIf { it.isNotEmpty() }
            if (!schemaResult.isSuccess || schemaContent == null) {
                throw UnreadableSchema(toolInstanceId, schemaResult.error ?: "no content")
            }
            val schemaId = schemaResult.data?.get("schema_id") as? String ?: target
            val tooltype = schemaResult.data?.get("tooltype") as? String
            val fingerprint = SessionReads.fingerprint(schemaText(schemaId, schemaContent, tooltype))
            val outcome = SessionReads.outcome(sent[target], fingerprint)
            LogManager.aiPrompt("Schema $target held by the model: $outcome", "DEBUG")
            if (outcome == SessionReads.Outcome.SAME) continue

            missingSchemas.add(MissingSchemaInfo(
                schemaId = schemaId,
                target = target,
                params = mapOf("tool_instance_id" to toolInstanceId),
                schemaContent = schemaContent,
                tooltype = tooltype,
                withOperations = true,
                fingerprint = fingerprint,
                replacesEarlier = outcome == SessionReads.Outcome.CHANGED
            ))
        }

        return missingSchemas
    }

    /**
     * The config schemas of the tool types among [tooltypes] the model does not hold as they are
     * now ([sent]), as missingEntrySchemas does for a tool's entries.
     *
     * @throws UnreadableSchema when one of them cannot be read (a tool type that does not exist)
     */
    private suspend fun missingConfigSchemas(tooltypes: Collection<String>, sent: Map<String, String>): List<MissingSchemaInfo> =
        tooltypes.distinct().mapNotNull { tooltype ->
            val params = mapOf("tooltype" to tooltype)
            val target = SessionReads.target("schemas", "get", params)!!
            val schemaResult = coordinator.processUserAction("schemas.get", params)
            val schemaContent = (schemaResult.data?.get("content") as? String)?.takeIf { it.isNotEmpty() }
            if (!schemaResult.isSuccess || schemaContent == null) throw UnreadableSchema(tooltype, schemaResult.error ?: "no content")
            val schemaId = schemaResult.data?.get("schema_id") as? String ?: target
            // As a SCHEMA command's result reads it: a config's schema carries no operations
            val fingerprint = SessionReads.fingerprint(schemaText(schemaId, schemaContent, null))
            val outcome = SessionReads.outcome(sent[target], fingerprint)
            LogManager.aiPrompt("Schema $target held by the model: $outcome", "DEBUG")
            if (outcome == SessionReads.Outcome.SAME) null
            else MissingSchemaInfo(schemaId, target, params, schemaContent, tooltype, withOperations = false, fingerprint, outcome == SessionReads.Outcome.CHANGED)
        }

    /** The first level of a tool type's entry, read for the model, with its read target and its text's fingerprint. */
    private class EntryRead(val result: InternalCommandResult, val target: String, val fingerprint: String)

    /**
     * The first level of [tooltype]'s entry (EntryDescription), to go before a schema of that
     * type, or null when the model holds it as it is now ([held], the fingerprints it holds by
     * target) or the type keeps no entries. Recorded as a schemas.get result, so that the session
     * counts it as sent; a text changed since says it replaces the earlier one.
     */
    private fun entryDescriptionRead(tooltype: String, held: Map<String, String>): EntryRead? {
        val toolType = app.treelune.core.tools.ToolTypeManager.getToolType(tooltype) ?: return null
        // In English, as everything made for the AI (AiLanguage)
        val english = AiLanguage.english(context)
        val text = app.treelune.core.fields.EntryDescription.text(toolType, english) ?: return null
        val target = app.treelune.core.fields.EntryDescription.target(tooltype)
        val fingerprint = SessionReads.fingerprint(text)
        val outcome = SessionReads.outcome(held[target], fingerprint)
        LogManager.aiPrompt("Entry of $tooltype held by the model: $outcome", "DEBUG")
        if (outcome == SessionReads.Outcome.SAME) return null
        val title = Strings.`for`(context = english).shared("ai_entry_description_title").format(toolType.getDisplayName(english))
        val shown = if (outcome == SessionReads.Outcome.CHANGED) s.shared("ai_read_replaces") + "\n\n" + text else text
        return EntryRead(
            result = InternalCommandResult(
                promptResult = PromptCommandResult(title, shown),
                commandResult = app.treelune.core.ai.data.CommandResult(
                    command = "schemas.get",
                    status = CommandStatus.SUCCESS,
                    // The user's line, in the app's language
                    details = s.shared("ai_entry_description_title").format(toolType.getDisplayName(context)),
                    data = mapOf("schema_id" to target, SessionReads.TARGET to target, SessionReads.FINGERPRINT to fingerprint),
                    error = null,
                    isActionCommand = false
                )
            ),
            target = target,
            fingerprint = fingerprint
        )
    }

    /** The schema of [what], a tool or a tool type, could not be read, for [reason] (the service's words). */
    class UnreadableSchema(val what: String, val reason: String) :
        IllegalStateException("Cannot read the schema of $what: $reason")

    /** The fingerprints the model last received in [sessionId], by target (SessionReads.sent). */
    private suspend fun loadSentReads(sessionId: String): Map<String, String> {
        return try {
            // Create repository locally to access message history
            val appDatabase = app.treelune.core.database.AppDatabase.getDatabase(context)
            val messageRepository = app.treelune.core.ai.state.AIMessageRepository(appDatabase.aiDao())
            SessionReads.sent(messageRepository.loadMessages(sessionId))
                .also { LogManager.aiPrompt("Loaded ${it.size} reads sent in session $sessionId", "DEBUG") }
        } catch (e: Exception) {
            LogManager.aiPrompt("Failed to load the reads sent in session $sessionId: ${e.message}", "WARN", e)
            emptyMap()
        }
    }
}
