package app.treelune.core.ai.prompts

import app.treelune.core.files.fileFormat
import app.treelune.core.utils.JsonUtils
import android.content.Context
import app.treelune.core.ai.data.*
import app.treelune.core.ai.processing.UserCommandProcessor
import app.treelune.core.ai.providers.toPromptText
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * Singleton PromptManager implementing 3-level prompt system
 * Level 1: System documentation (very stable, includes AI limits)
 * Level 2: User data (stable, always_send tools)
 * Level 3: Application state snapshot (frozen at first message for cache stability)
 *
 * Level 4 has been REMOVED - enrichments are now stored as separate SystemMessages
 * and fused by the provider during prompt construction.
 */
object PromptManager {

    /**
     * Build prompt data for AI session
     * Generates L1-L2 (always fresh) + L3 (snapshot, cached after first message)
     *
     * @param sessionId The session ID to load messages from
     * @return PromptData ready for provider transformation
     */
    suspend fun buildPromptData(sessionId: String, context: Context): PromptData {
        LogManager.aiPrompt("Building prompt data for session $sessionId", "INFO")

        val coordinator = Coordinator(context)

        // 1. Load session to determine type and get snapshot
        val sessionResult = coordinator.processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId))
        if (!sessionResult.isSuccess) {
            LogManager.aiPrompt("Failed to load session: ${sessionResult.error}", "ERROR")
            throw IllegalStateException("Failed to load session $sessionId: ${sessionResult.error}")
        }

        val sessionData = sessionResult.data?.get("session") as? Map<*, *>
            ?: throw IllegalStateException("No session data found")

        val sessionTypeStr = sessionData["type"] as? String ?: "CHAT"
        val sessionType = SessionType.valueOf(sessionTypeStr)
        val existingSnapshot = sessionData["app_state_snapshot"] as? String

        // 2. Build Level 1 (DOC + limits) using PromptChunks
        LogManager.aiPrompt("Building Level 1 (DOC)", "DEBUG")

        // The identity it speaks as, as it is now: one changed or reimported speaks from this turn.
        // One deleted leaves the session unable to go on, said rather than answered without it
        val identity = (sessionData["identity_id"] as? String)?.let { id ->
            app.treelune.core.database.AppDatabase.getDatabase(context).identityDao().getById(id)?.toIdentity()
                ?: throw IllegalStateException(Strings.`for`(context = context).shared("identity_session_gone"))
        }

        // Generate complete documentation with PromptChunks (schemas included via placeholders)
        val level1Content = PromptChunks.buildLevel1StaticDoc(
            context = context,
            sessionType = sessionType,
            config = PromptChunks.ChunkConfig(
                includeDegree1 = true,
                includeDegree2 = true,
                includeDegree3 = false
            ),
            identity = identity
        )

        // 3. Build Level 2 (USER DATA - always_send tools), with what an automation's AI may
        //    reach, so that it does not try what would be refused
        val access = if (sessionType == SessionType.AUTOMATION) automationAccessText(context, sessionData["automation_id"] as? String) else null
        val level2Content = buildLevel2Content(context, sessionId, sessionType) + (access?.let { "\n\n$it" } ?: "")

        // 4. Build/Load Level 3 (APP_STATE snapshot)
        val level3Content = if (existingSnapshot != null) {
            // Use existing snapshot (cached for this session)
            LogManager.aiPrompt("Using existing APP_STATE snapshot (${existingSnapshot.length} chars)", "DEBUG")
            formatSnapshotContent(existingSnapshot, context)
        } else {
            // First message: generate and store snapshot
            LogManager.aiPrompt("Generating APP_STATE snapshot (first message)", "INFO")
            val snapshot = generateAppStateSnapshot(context)

            // Store in DB via AIStateRepository
            val database = app.treelune.core.database.AppDatabase.getDatabase(context)
            val aiDao = database.aiDao()
            aiDao.updateAppStateSnapshot(sessionId, snapshot)

            LogManager.aiPrompt("APP_STATE snapshot generated and stored (${snapshot.length} chars)", "INFO")
            formatSnapshotContent(snapshot, context)
        }

        // 5. Load session messages (raw, provider will transform them)
        val messagesData = sessionResult.data?.get("messages") as? List<*> ?: emptyList<Any>()
        val allMessages = parseSessionMessages(messagesData)

        // 6-7. The messages as the AI reads them
        val promptMessages = promptMessages(allMessages, context)

        LogManager.aiPrompt("Prompt data built: L1=${estimateTokens(level1Content)} tokens, L2=${estimateTokens(level2Content)} tokens, L3=${estimateTokens(level3Content)} tokens, ${promptMessages.size} messages", "INFO")

        return PromptData(
            level1Content = level1Content,
            level2Content = level2Content,
            level3Content = level3Content,
            sessionMessages = promptMessages,
            scheduledExecutionTime = if (sessionType == SessionType.AUTOMATION) {
                sessionData["scheduled_execution_time"] as? Long
            } else {
                null
            }
        )
    }

    /**
     * A routine's run [sessionId] as its AI received it (AUTOMATION_RUN), one message after the
     * other, each under who sent it: the routine's message as the prompt writes it, the AI's
     * answers in the JSON it wrote, the app's results. The data the starting message's pointers
     * attached is not repeated: they name what they designate, which may be read again.
     *
     * @param onlyFailures Only the app's messages telling a failure, each after the answer that
     *   led to it
     * @throws IllegalArgumentException when [sessionId] is not a routine's run
     */
    suspend fun runHistory(context: Context, sessionId: String, onlyFailures: Boolean): String {
        val s = Strings.`for`(context = context)
        val result = Coordinator(context).processUserAction("ai_sessions.get_session", mapOf("session_id" to sessionId))
        val session = result.data?.get("session") as? Map<*, *>
            ?: throw IllegalArgumentException(result.error ?: s.shared("ai_error_session_not_found").format(sessionId))
        if (session["type"] != SessionType.AUTOMATION.name) throw IllegalArgumentException(s.shared("ai_error_not_a_run").format(sessionId))
        val all = parseSessionMessages(result.data?.get("messages") as? List<*> ?: emptyList<Any>())

        // What a user message's pointers attached: the data that comes right after it
        val attached = all.indices.filter { i ->
            i > 0 && all[i].systemMessage?.type == SystemMessageType.DATA_ADDED && all[i - 1].sender == MessageSender.USER &&
                all[i - 1].richContent?.segments?.any { it is MessageSegment.EnrichmentBlock } == true
        }.map { all[it].id }.toSet()
        val shown = promptMessages(all, context).map { message ->
            if (message.id !in attached) message
            else message.copy(systemMessage = message.systemMessage!!.copy(summary = s.shared("ai_run_pointer_data"), commandResults = emptyList(), formattedData = null))
        }

        val kept = if (!onlyFailures) shown else shown.flatMapIndexed { i, message ->
            if (!tellsAFailure(message)) emptyList()
            else listOfNotNull(shown.getOrNull(i - 1)?.takeIf { it.sender == MessageSender.AI }, message)
        }.distinct()
        if (kept.isEmpty()) return s.shared(if (onlyFailures) "ai_run_no_failure" else "ai_run_no_message")

        return kept.joinToString("\n\n") { message ->
            val text = when {
                message.systemMessage != null -> message.systemMessage.toPromptText()
                message.sender == MessageSender.AI -> message.aiMessageJson ?: message.textContent ?: ""
                else -> message.promptParts?.filterIsInstance<PromptPart.Text>()?.joinToString("\n") { it.text } ?: message.textContent ?: ""
            }
            s.shared("ai_run_sender_${message.sender.name.lowercase()}") + "\n" + text.trim()
        }
    }

    /** Whether [message] is the app telling a failure: a command failed, an answer did not read, data was refused, the limit reached. */
    private fun tellsAFailure(message: SessionMessage): Boolean {
        val system = message.systemMessage ?: return false
        return system.type in setOf(SystemMessageType.FORMAT_ERROR, SystemMessageType.DATA_REFUSED, SystemMessageType.LIMIT_REACHED) ||
            system.commandResults.any { it.status == CommandStatus.FAILED }
    }

    /**
     * The messages of a session as its AI reads them, the provider turning them into its own
     * request: those kept out of the prompt left out, a user message in text, its pointers naming
     * their targets as they are now.
     */
    private suspend fun promptMessages(allMessages: List<SessionMessage>, context: Context): List<SessionMessage> {
        // Out of the prompt: NETWORK_ERROR, SESSION_TIMEOUT, INTERRUPTED and PROVIDER_ERROR (audit
        // only), and what is shown alone (excludeFromPrompt, a postText)
        val sessionMessages = allMessages.filter { message ->
            val type = message.systemMessage?.type
            val isSystemError = type == SystemMessageType.NETWORK_ERROR ||
                               type == SystemMessageType.SESSION_TIMEOUT ||
                               type == SystemMessageType.INTERRUPTED ||
                               type == SystemMessageType.PROVIDER_ERROR
            !isSystemError && !message.excludeFromPrompt
        }

        val filtered = allMessages.size - sessionMessages.size
        if (filtered > 0) {
            LogManager.aiPrompt("Filtered $filtered messages from prompt", "DEBUG")
        }

        // A user message reaches the AI as text, its pointers naming their targets as they are
        // now, with their ids: a message only stores what the user chose (EnrichmentText). One
        // with images reaches it in parts, each image where the user put it
        val enrichmentText = app.treelune.core.ai.enrichments.EnrichmentText.load(context,
            app.treelune.core.ai.enrichments.EnrichmentText.blocksOf(sessionMessages.mapNotNull { it.richContent }))
        // The row of each file the messages hold, which says how it is read: the images the user
        // joined and those of entries IMAGES brought in, the sounds and videos joined
        val fileIds = sessionMessages.flatMap { it.richContent?.imageIds.orEmpty() + it.richContent?.mediaIds.orEmpty() + shownImages(it) }
        val files = if (fileIds.isEmpty()) emptyMap()
            else app.treelune.core.database.AppDatabase.getDatabase(context).fileDao().getByIds(fileIds).associateBy { it.id }
        return sessionMessages.map { message ->
            message.richContent?.let { rich ->
                val parts = enrichmentText.promptParts(rich, files)
                if (parts.none { it is app.treelune.core.ai.data.PromptPart.Image })
                    message.copy(richContent = null, textContent = (parts.singleOrNull() as? app.treelune.core.ai.data.PromptPart.Text)?.text ?: "")
                else message.copy(richContent = null, textContent = null, promptParts = parts)
            }
                // Text the AI wrote around its JSON reaches it only as the notice that it was set
                // aside: quoted back, it would read as part of what it said
                ?: message.systemMessage?.takeIf { it.type == SystemMessageType.TEXT_OUTSIDE_JSON }?.let { system ->
                    message.copy(systemMessage = system.copy(summary = system.formattedData ?: "", formattedData = null))
                }
                // Images of entries IMAGES brought in follow the data that names them, as if the
                // user had joined them
                ?: message.systemMessage?.let { system ->
                    shownImages(message).takeIf { it.isNotEmpty() }?.let { ids ->
                        message.copy(promptParts = listOf(PromptPart.Text(system.toPromptText())) + ids.flatMap { id ->
                            val format = checkNotNull(files[id]) { "Image $id: its row is missing" }.fileFormat
                            listOf(PromptPart.Text(app.treelune.core.ai.enrichments.EnrichmentText.imageLabel(id)), PromptPart.Image(id, format))
                        })
                    }
                }
                ?: message
        }
    }

    /** The ids of the images IMAGES brought into the conversation with [message], in order. */
    private fun shownImages(message: SessionMessage): List<String> =
        message.systemMessage?.commandResults.orEmpty()
            .filter { it.command == "files.show_images" && it.status == CommandStatus.SUCCESS }
            .flatMap { (it.data?.get("image_ids") as? List<*>).orEmpty().map { id -> id as String } }

    /**
     * Parse raw message data from DB into SessionMessage objects
     */
    private fun parseSessionMessages(messagesData: List<*>): List<SessionMessage> {
        return messagesData.mapNotNull { msg ->
            if (msg !is Map<*, *>) return@mapNotNull null

            val id = msg["id"] as? String ?: return@mapNotNull null
            val timestamp = (msg["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
            val senderStr = msg["sender"] as? String ?: return@mapNotNull null
            val sender = try {
                MessageSender.valueOf(senderStr)
            } catch (e: Exception) {
                LogManager.aiPrompt("Invalid sender: $senderStr", "WARN")
                return@mapNotNull null
            }

            // Parse richContent if present
            val richContent = (msg["rich_content_json"] as? String)?.let { json ->
                try {
                    parseRichMessage(json)
                } catch (e: Exception) {
                    LogManager.aiPrompt("Failed to parse richContentJson: ${e.message}", "WARN")
                    null
                }
            }

            // Parse aiMessage if present
            val aiMessage = (msg["ai_message_json"] as? String)?.let { json ->
                try {
                    parseAIMessage(json)
                } catch (e: Exception) {
                    LogManager.aiPrompt("Failed to parse aiMessageJson: ${e.message}", "WARN")
                    null
                }
            }

            // Parse systemMessage if present
            val systemMessage = (msg["system_message_json"] as? String)?.let { json ->
                SystemMessage.fromJson(json) ?: run {
                    LogManager.aiPrompt("Failed to parse systemMessageJson of message $id", "WARN")
                    null
                }
            }

            SessionMessage(
                id = id,
                timestamp = timestamp,
                sender = sender,
                richContent = richContent,
                textContent = msg["text_content"] as? String,
                aiMessage = aiMessage,
                aiMessageJson = msg["ai_message_json"] as? String,
                systemMessage = systemMessage,
                executionMetadata = null, // TODO: Parse when implementing automation
                excludeFromPrompt = msg["exclude_from_prompt"] as? Boolean ?: false
            )
        }
    }

    /** A user message's segments, which step 7 turns into the text the AI reads. */
    private fun parseRichMessage(json: String): RichMessage =
        RichMessage.fromJson(json) ?: throw IllegalArgumentException("rich content does not read")

    /**
     * Parse AIMessage from JSON
     * TODO: Implement full deserialization when needed
     */
    private fun parseAIMessage(json: String): AIMessage {
        // Stub for now - provider only needs JSON string
        return AIMessage(
            preText = "",
            validationRequest = null,
            dataCommands = null,
            actionCommands = null,
            postText = null,
            keepControl = null,
            communicationModule = null,
            completed = null
        )
    }

    /**
     * Format level content with title, static doc, and command results
     */
    private fun formatLevel(title: String, staticContent: String, results: List<PromptCommandResult>): String {
        val sb = StringBuilder()
        sb.appendLine("## $title")
        sb.appendLine()

        if (staticContent.isNotEmpty()) {
            sb.appendLine(staticContent)
            sb.appendLine()
        }

        for (result in results) {
            if (result.dataTitle.isNotEmpty()) {
                sb.appendLine("### ${result.dataTitle}")
                sb.appendLine()
            }
            if (result.formattedData.isNotEmpty()) {
                sb.appendLine(result.formattedData)
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    // === Level Command Builders ===
    // Note: Level 1 now uses PromptChunks - no commands needed

    /** A tool marked always_send, the size of what the AI reads of it. */
    data class AlwaysSentTool(val id: String, val name: String, val chars: Int)

    /** The tools marked always_send and what the AI reads of them: their data, and their schema outside a session. */
    data class AlwaysSent(val tools: List<AlwaysSentTool>, val results: List<PromptCommandResult>) {
        /** The size of it all, as the AI receives it. */
        val chars: Int get() = tools.sumOf { it.chars }

        /** The tools as the confirmation and the prompt list them: name (id, size). */
        fun listed(): String = tools.joinToString(", ") { "${it.name} (${it.id}, ${it.chars})" }
    }

    /** The user's choice on the tools sent always, for a CHAT session above the threshold. */
    enum class AlwaysSendChoice { ACCEPTED, REFUSED }

    /** What becomes of the tools sent always at one call. */
    enum class AlwaysSendOutcome { SEND, ASK, LIST }

    /**
     * The tools sent always, [chars] of them against [threshold]: sent within it; above it, sent
     * in a CHAT whose user accepted, asked in a CHAT not asked yet, listed otherwise (refused, an
     * AUTOMATION, an outside AI with no [sessionType]).
     */
    fun alwaysSendOutcome(chars: Int, threshold: Int, sessionType: SessionType?, choice: AlwaysSendChoice?): AlwaysSendOutcome = when {
        chars <= threshold -> AlwaysSendOutcome.SEND
        sessionType != SessionType.CHAT -> AlwaysSendOutcome.LIST
        choice == AlwaysSendChoice.ACCEPTED -> AlwaysSendOutcome.SEND
        choice == AlwaysSendChoice.REFUSED -> AlwaysSendOutcome.LIST
        else -> AlwaysSendOutcome.ASK
    }

    /**
     * The tools marked always_send, read now. The tools' config is asked for only to find the
     * marked ones (tools.list_all leaves it out otherwise): it does not reach the AI.
     *
     * @param withSchemas Whether their schemas come with their data: outside a session (an
     *   outside AI's context), where there is no history to hold them. In a session they are
     *   stored in it once, as any schema received (AIEventProcessor.holdForAlwaysSend)
     * @throws IllegalStateException when the tools or a tool's config cannot be read: a tool
     *   skipped in silence is how its data went missing for a year
     */
    suspend fun readAlwaysSent(context: Context, withSchemas: Boolean): AlwaysSent {
        val result = Coordinator(context).processUserAction("tools.list_all", mapOf("include_config" to true))
        if (!result.isSuccess) throw IllegalStateException("Cannot list the tools for the ones sent always: ${result.error}")
        val toolInstances = result.data?.get("tool_instances") as? List<*>
            ?: throw IllegalStateException("Cannot list the tools for the ones sent always: no tool_instances")

        val tools = mutableListOf<AlwaysSentTool>()
        val results = mutableListOf<PromptCommandResult>()
        for (toolInstance in toolInstances) {
            val map = toolInstance as Map<*, *>
            val id = map["id"] as String
            @Suppress("UNCHECKED_CAST")
            val config = (map["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
                ?: throw IllegalStateException("Tool $id has no config to tell whether it is sent always")
            val marked = try {
                app.treelune.core.tools.ToolConfigSettings.read(map["tooltype"] as String, config, context).boolean("always_send")
            } catch (e: Exception) {
                throw IllegalStateException("Cannot read whether tool $id is sent always: ${e.message}", e)
            }
            if (!marked) continue

            val commands = listOfNotNull(
                if (withSchemas) DataCommand(id = "always_send_schema_$id", type = "SCHEMA", params = mapOf("tool_instance_id" to id), isRelative = false) else null,
                DataCommand(id = "always_send_data_$id", type = "TOOL_DATA", params = mapOf("id" to id), isRelative = false)
            )
            // They name a tool and carry no period, so the reference never applies
            val executable = UserCommandProcessor(context).processCommands(commands, System.currentTimeMillis())
            val executed = CommandExecutor(context).executeCommands(
                commands = executable,
                messageType = SystemMessageType.DATA_ADDED,
                origin = app.treelune.core.coordinator.Source.SYSTEM,
                level = "L2",
                // No session: its schemas are in it already (holdForAlwaysSend), not counted here
                sessionId = null
            )
            tools += AlwaysSentTool(id, map["name"] as String, executed.promptResults.sumOf { it.dataTitle.length + it.formattedData.length })
            results += executed.promptResults
        }
        LogManager.aiPrompt("Level 2: ${tools.size} tool(s) sent always, ${tools.sumOf { it.chars }} characters", "DEBUG")
        return AlwaysSent(tools, results)
    }

    /** The choice made in [sessionId] on the tools sent always, the last one, or null when none was asked. */
    suspend fun alwaysSendChoice(context: Context, sessionId: String): AlwaysSendChoice? {
        val messages = app.treelune.core.ai.state.AIMessageRepository(
            app.treelune.core.database.AppDatabase.getDatabase(context).aiDao()
        ).loadMessages(sessionId)
        return messages.asReversed().firstNotNullOfOrNull { message ->
            when (message.systemMessage?.type) {
                SystemMessageType.ALWAYS_SEND_ACCEPTED -> AlwaysSendChoice.ACCEPTED
                SystemMessageType.ALWAYS_SEND_REFUSED -> AlwaysSendChoice.REFUSED
                else -> null
            }
        }
    }

    /**
     * Level 2: the data of the tools marked always_send, read now (docs/AI.md, « 2 levels of context »).
     * Above their threshold they go only in a CHAT whose user accepted them; otherwise (refused,
     * an AUTOMATION, an outside AI, or not asked yet) the AI is given their list to read them.
     *
     * @param sessionId The session, null outside one (an outside AI's context, given the schemas too)
     * @param sessionType The session's type, null outside a session
     */
    suspend fun buildLevel2Content(context: Context, sessionId: String?, sessionType: SessionType?): String {
        LogManager.aiPrompt("Building Level 2 (USER DATA)", "DEBUG")
        val alwaysSent = readAlwaysSent(context, withSchemas = sessionId == null)
        val s = Strings.`for`(context = context)
        if (alwaysSent.tools.isEmpty()) return formatLevel("Level 2: User Data", "", emptyList())

        val threshold = AppConfigManager.getAILimits().alwaysSendMaxChars
        val choice = if (sessionType == SessionType.CHAT && sessionId != null) alwaysSendChoice(context, sessionId) else null
        // ASK here means the data grew above the threshold since holdForAlwaysSend looked: listed, said so
        if (alwaysSendOutcome(alwaysSent.chars, threshold, sessionType, choice) != AlwaysSendOutcome.SEND) {
            LogManager.aiPrompt("Level 2: ${alwaysSent.chars} characters above $threshold, the list sent instead", "INFO")
            return formatLevel("Level 2: User Data", s.shared("ai_prompt_level2_not_sent").format(alwaysSent.listed()), emptyList())
        }
        return formatLevel("Level 2: User Data", s.shared("ai_prompt_level2_intro"), alwaysSent.results)
    }

    /**
     * What the AI of automation [automationId] may reach (accessText); null when it reaches
     * everything. An automation that cannot be read reaches nothing, and is said so.
     */
    private suspend fun automationAccessText(context: Context, automationId: String?): String? {
        val automation = automationId?.let { app.treelune.core.database.AppDatabase.getDatabase(context).aiDao().getAutomationById(it) }
        val mask = automation?.let {
            try {
                app.treelune.core.access.AccessMask.fromJson(it.accessJson)
            } catch (e: Exception) {
                null
            }
        } ?: app.treelune.core.access.AccessMask.NOTHING
        return accessText(context, mask)
    }

    /**
     * What an AI acting without the user may reach under [mask] (an automation's, a connector
     * client's), each zone and tool by its name, id and level; null when it reaches everything.
     */
    suspend fun accessText(context: Context, mask: app.treelune.core.access.AccessMask): String? {
        val s = Strings.`for`(context = context)
        if (mask.open) return null
        if (mask.grants.isEmpty()) return s.shared("ai_prompt_access_nothing")
        val database = app.treelune.core.database.AppDatabase.getDatabase(context)
        val lines = mask.grants.map { grant ->
            val id = grant.target.id!!
            val level = s.shared("ai_prompt_access_level_${grant.level.key}")
            // A zone or a tool deleted since stays listed (nothing removes it): it reaches nothing,
            // and the list holding it is not empty, so it does not open everything either
            if (grant.target.kind == app.treelune.core.selection.ReferenceKind.ZONE)
                database.zoneDao().getZoneById(id)?.let { s.shared("ai_prompt_access_zone").format(it.name, id, level) }
                    ?: s.shared("ai_prompt_access_zone_deleted").format(id)
            else
                database.toolInstanceDao().getToolInstanceById(id)?.let { s.shared("ai_prompt_access_tool").format(org.json.JSONObject(it.config_json).optString("name"), id, level) }
                    ?: s.shared("ai_prompt_access_tool_deleted").format(id)
        }
        return s.shared("ai_prompt_access_intro") + "\n" + lines.joinToString("\n") + "\n\n" + s.shared("ai_prompt_access_always_sent")
    }

    /** Level 3 as it stands now: the zones and the tools, for an outside AI's context. */
    suspend fun buildAppStateContent(context: Context): String =
        formatSnapshotContent(generateAppStateSnapshot(context), context)

    /**
     * Generate APP_STATE snapshot (zones + tool instances + routines)
     * Called once on first message to capture initial state
     *
     * @return JSON string containing snapshot with timestamp
     */
    private suspend fun generateAppStateSnapshot(context: Context): String {
        val coordinator = Coordinator(context)
        val timestamp = System.currentTimeMillis()

        // Execute APP_STATE command (uses include_config: false by default)
        val appStateCommand = DataCommand(
            id = "app_state_snapshot",
            type = "APP_STATE",
            params = mapOf("include_config" to false),
            isRelative = false
        )

        val userCommandProcessor = UserCommandProcessor(context)
        // APP_STATE carries no period, so the reference never applies
        val executable = userCommandProcessor.processCommands(listOf(appStateCommand), System.currentTimeMillis())

        // Execute commands to get zones + tool instances
        val result = coordinator.processUserAction(executable[0].resource + "." + executable[0].operation, executable[0].params)
        val zonesData = result.data?.get("zones") as? List<*> ?: emptyList<Any>()

        val result2 = coordinator.processUserAction(executable[1].resource + "." + executable[1].operation, executable[1].params)
        val toolInstancesData = result2.data?.get("tool_instances") as? List<*> ?: emptyList<Any>()

        // Build snapshot JSON - convert Kotlin collections to JSONArray manually
        val zonesArray = org.json.JSONArray()
        zonesData.forEach { zone ->
            if (zone is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                zonesArray.put(app.treelune.core.utils.JsonUtils.toJSONObject(zone as Map<String, Any?>))
            }
        }

        val toolInstancesArray = org.json.JSONArray()
        toolInstancesData.forEach { toolInstance ->
            if (toolInstance is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                toolInstancesArray.put(app.treelune.core.utils.JsonUtils.toJSONObject(toolInstance as Map<String, Any?>))
            }
        }

        // The routines, each in a line (AutomationSchemas.listed)
        val s = Strings.`for`(context = context)
        val result3 = coordinator.processUserAction(executable[3].resource + "." + executable[3].operation, executable[3].params)
        val automationsArray = org.json.JSONArray()
        (result3.data?.get("automations") as? List<*> ?: emptyList<Any>()).forEach { automation ->
            automationsArray.put(JSONObject(AutomationSchemas.listed(automation as Map<*, *>, s)))
        }

        // The groups a zone may hold, in their order on the home screen
        val zoneGroups = org.json.JSONArray(app.treelune.core.services.AppConfigService(context).getZoneGroups())

        // The identities, each by its id, name and the language it speaks, never their texts
        val identities = org.json.JSONArray().apply {
            app.treelune.core.database.AppDatabase.getDatabase(context).identityDao().getAll().forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("language", it.language))
            }
        }

        // The AI providers, each by its id and name, a routine's provider_id being one of them;
        // never their config, which holds a key
        val providers = org.json.JSONArray().apply {
            app.treelune.core.database.AppDatabase.getDatabase(context).aiDao().getAllProviderConfigs().forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind).put("is_default", it.isDefault))
            }
        }

        val snapshot = org.json.JSONObject().apply {
            put("timestamp", timestamp)
            put("zone_groups", zoneGroups)
            put("zones", zonesArray)
            put("tool_instances", toolInstancesArray)
            put("automations", automationsArray)
            put("identities", identities)
            put("ai_providers", providers)
        }

        return snapshot.toString()
    }

    /**
     * Format snapshot JSON into readable prompt content with timestamp header
     *
     * @param snapshot JSON string containing snapshot data
     * @return Formatted content for prompt
     */
    private fun formatSnapshotContent(snapshot: String, context: Context): String {
        val s = Strings.`for`(context = context)
        val snapshotObj = org.json.JSONObject(snapshot)
        val timestamp = snapshotObj.getLong("timestamp")
        val zones = snapshotObj.getJSONArray("zones")
        val toolInstances = snapshotObj.getJSONArray("tool_instances")

        // ISO 8601 with its offset, in the app's timezone, like every date the AI reads
        val dateStr = app.treelune.core.utils.DateTimeConverter.timestampToISO(
            timestamp, app.treelune.core.utils.AppConfigManager.getDateTimeConfig().getZoneId()
        )

        val sb = StringBuilder()
        sb.appendLine("## ${s.shared("ai_prompt_level3_title")}")
        sb.appendLine()
        sb.appendLine(s.shared("ai_prompt_level3_snapshot_time").format(dateStr))
        sb.appendLine(s.shared("ai_prompt_level3_snapshot_note"))
        sb.appendLine()
        // A snapshot stored before the zone groups were part of it has none to show
        snapshotObj.optJSONArray("zone_groups")?.let { groups ->
            sb.appendLine("### ${s.shared("ai_prompt_level3_zone_groups_title")}")
            sb.appendLine()
            sb.appendLine("```json")
            sb.appendLine(groups.toString())
            sb.appendLine("```")
            sb.appendLine()
        }
        sb.appendLine("### ${s.shared("ai_prompt_level3_zones_title")}")
        sb.appendLine()
        sb.appendLine("```json")
        sb.appendLine(zones.toString(2))
        sb.appendLine("```")
        sb.appendLine()
        sb.appendLine("### ${s.shared("ai_prompt_level3_tools_title")}")
        sb.appendLine()
        sb.appendLine("```json")
        sb.appendLine(toolInstances.toString(2))
        sb.appendLine("```")
        // A snapshot stored before the routines were part of it has none to show
        snapshotObj.optJSONArray("automations")?.let { automations ->
            sb.appendLine()
            sb.appendLine("### ${s.shared("ai_prompt_level3_automations_title")}")
            sb.appendLine()
            sb.appendLine("```json")
            sb.appendLine(automations.toString(2))
            sb.appendLine("```")
        }
        // A snapshot stored before the identities were part of it has none to show
        snapshotObj.optJSONArray("identities")?.let { identities ->
            sb.appendLine()
            sb.appendLine("### ${s.shared("ai_prompt_level3_identities_title")}")
            sb.appendLine()
            sb.appendLine("```json")
            sb.appendLine(identities.toString(2))
            sb.appendLine("```")
        }
        // A snapshot stored before the providers were part of it has none to show
        snapshotObj.optJSONArray("ai_providers")?.let { providers ->
            sb.appendLine()
            sb.appendLine("### ${s.shared("ai_prompt_level3_providers_title")}")
            sb.appendLine()
            sb.appendLine("```json")
            sb.appendLine(providers.toString(2))
            sb.appendLine("```")
        }

        return sb.toString()
    }

    // === Utilities ===

    /**
     * Estimate token count from text
     * Rough estimation: 1 token ≈ 4 characters for most languages
     */
    fun estimateTokens(text: String): Int {
        return text.length / 4
    }
}
