package app.treelune.core.ai.services

import android.content.Context
import app.treelune.core.ai.data.*
import app.treelune.core.ai.database.AIDao
import app.treelune.core.ai.prompts.PromptManager
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.reasoning
import app.treelune.core.ai.database.runWaits
import app.treelune.core.ai.database.toDomain
import app.treelune.core.ai.database.SessionMessageEntity
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Pure AI session database service (ExecutableService)
 *
 * Responsibilities:
 * - Session CRUD operations via CommandDispatcher
 * - Message CRUD operations
 * - Database-only operations following CORE.md patterns
 *
 * Available operations:
 * - ai_sessions.create, .get, .list, .update, .delete, .set_active
 * - ai_messages.create, .get, .list, .update, .delete
 */
class AISessionService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)
    private val aiDao: AIDao get() = AppDatabase.getDatabase(context).aiDao()

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("AISessionService.execute - operation: $operation", "DEBUG")

        return try {
            when (operation) {
                // Session operations
                "create_session" -> createSession(params, token)
                "get_session" -> getSession(params, token)
                "get_active_session" -> getActiveSession(params, token)
                "stop_active_session" -> stopActiveSession(params, token)
                "list_sessions" -> listSessions(params, token)
                "list_sessions_for_routine" -> listSessionsForRoutine(params, token)
                "run_history" -> runHistory(params, token)
                "update_session" -> updateSession(params, token)
                "delete_session" -> deleteSession(params, token)
                "set_active_session" -> setActiveSession(params, token)

                // History operations
                "list" -> listChatSessions(params, token)
                "rename" -> renameSession(params, token)
                "delete" -> deleteChatSession(params, token)

                // Message operations
                "create_message" -> createMessage(params, token)
                "get_message" -> getMessage(params, token)
                "list_messages" -> listMessages(params, token)
                "update_message" -> updateMessage(params, token)
                "delete_message" -> deleteMessage(params, token)

                // What a conversation cost, and what it did
                "get_stats" -> getSessionStats(params, token)

                // What the session adds to the protections, written whole
                "update_validation" -> updateValidation(params, token)
                // The reasoning it asks of its model, or its provider's config again
                "update_reasoning" -> updateReasoning(params, token)
                // The provider a conversation calls, chosen in its parameters or taken at its first call
                "set_provider" -> setProvider(params, token)
                // Who it speaks as, until its first message fixes it
                "set_identity" -> setIdentity(params)

                // Session state updates (V2 - managed by AIStateRepository)
                "set_end_reason" -> setEndReason(params, token)

                // What waits for the user (docs/BRICKS.md, « Waiting »)
                "seen" -> markSeen(params, token)
                "end_run" -> endRun(params, token)
                "chat_waiting" -> OperationResult.success(mapOf("waits" to aiDao.chatWaits()))

                // SEED pre-fill management

                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("AISessionService.execute - Error: ${e.message}", "ERROR", e)
            OperationResult.error(s.shared("service_error_ai_session").format(e.message ?: ""))
        }
    }

    /**
     * The identity a CHAT session speaks as: `session_id`, `identity_id` (null for the app's own
     * AI). Refused once the user has sent a message: from there it is fixed, another conversation
     * changing who speaks (docs/AI.md, « Identities »).
     */
    private suspend fun setIdentity(params: JSONObject): OperationResult {
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val session = aiDao.getSession(sessionId) ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
        if (session.type != SessionType.CHAT) return OperationResult.error(s.shared("identity_session_not_chat"))
        if (aiDao.getMessagesForSession(sessionId).any { it.sender == app.treelune.core.ai.data.MessageSender.USER })
            return OperationResult.error(s.shared("identity_session_fixed"))
        val identityId = params.optString("identity_id").takeIf { it.isNotEmpty() && !params.isNull("identity_id") }
        if (identityId != null && AppDatabase.getDatabase(context).identityDao().getById(identityId) == null)
            return OperationResult.error(s.shared("identity_error_not_found").format(identityId))
        aiDao.updateSession(session.copy(identityId = identityId))
        return OperationResult.success(mapOf("session_id" to sessionId, "identity_id" to (identityId ?: JSONObject.NULL)))
    }

    private suspend fun createSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val name = params.optString("name").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_name_required"))
        val type = params.optString("type").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_type_required"))
        // None: a conversation takes the default at its first call (ProviderVerifier)
        val providerId = params.optString("provider_id").takeIf { it.isNotEmpty() && !params.isNull("provider_id") }
        if (providerId != null && AppDatabase.getDatabase(context).aiDao().getProviderConfig(providerId) == null)
            return OperationResult.error(s.shared("ai_error_provider_not_found").format(providerId))

        // A seed session is made with its routine and its message, by routines.create
        if (type == SessionType.SEED.name) return OperationResult.error(s.shared("ai_error_seed_with_routine"))

        LogManager.aiSession("Creating AI session: name=$name, type=$type, providerId=$providerId", "DEBUG")

        // The app's own demo gives its ids; every other caller gets one made here
        val sessionId = when (val given = app.treelune.core.coordinator.GivenId.read(params)) {
            app.treelune.core.coordinator.GivenId.Read.None -> UUID.randomUUID().toString()
            is app.treelune.core.coordinator.GivenId.Read.Accepted -> given.id
            is app.treelune.core.coordinator.GivenId.Read.Refused -> return OperationResult.error(s.shared("service_error_id_not_given").format(given.id))
        }
        val now = System.currentTimeMillis()

        val identityId = params.optString("identity_id").takeIf { it.isNotEmpty() && !params.isNull("identity_id") }
        if (identityId != null && AppDatabase.getDatabase(context).identityDao().getById(identityId) == null)
            return OperationResult.error(s.shared("identity_error_not_found").format(identityId))

        val sessionEntity = AISessionEntity(
            id = sessionId,
            name = name,
            identityId = identityId,
            type = SessionType.valueOf(type),
            routineId = params.optString("routine_id").takeIf { it.isNotEmpty() }, // null for CHAT
            scheduledExecutionTime = if (params.has("scheduled_execution_time")) params.getLong("scheduled_execution_time") else null,
            providerId = providerId,
            providerSessionId = "",
            createdAt = now,
            lastActivity = now,
            isActive = false // Will be activated separately
        )

        // Insert into database
        try {
            val database = AppDatabase.getDatabase(context)
            database.aiDao().insertSession(sessionEntity)

            LogManager.aiSession("Successfully created session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "name" to name,
                "type" to type,
                "created_at" to now
            ) + (providerId?.let { mapOf("provider_id" to it) } ?: emptyMap()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to create session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_create_session").format(e.message ?: ""))
        }
    }

    private suspend fun getSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Getting AI session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntity = database.aiDao().getSession(sessionId)

            if (sessionEntity == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Get messages for this session
            val messageEntities = database.aiDao().getMessagesForSession(sessionId)

            LogManager.aiSession("Found session $sessionId with ${messageEntities.size} messages", "DEBUG")

            return OperationResult.success(mapOf(
                "session" to mapOf(
                    "id" to sessionEntity.id,
                    "name" to sessionEntity.name,
                    "type" to sessionEntity.type.name, // Convert enum to string
                    SessionValidation.KEY_APP to sessionEntity.validateApp,
                    SessionValidation.KEY_ZONES to sessionEntity.validateZones,
                    SessionValidation.KEY_DATA to sessionEntity.validateData,
                    SessionReasoning.KEY to sessionEntity.reasoning()?.toMap(),
                    "routine_id" to sessionEntity.routineId,
                    "scheduled_execution_time" to sessionEntity.scheduledExecutionTime,
                    "triggered_by" to sessionEntity.triggeredBy,
                    "provider_id" to sessionEntity.providerId,
                    "provider_session_id" to sessionEntity.providerSessionId,
                    "created_at" to sessionEntity.createdAt,
                    "last_activity" to sessionEntity.lastActivity,
                    "is_active" to sessionEntity.isActive,
                    "end_reason" to sessionEntity.endReason,
                    "summary" to sessionEntity.summary,
                    "identity_id" to sessionEntity.identityId,
                    // Frozen at the first call: PromptManager builds L3 from it, and a snapshot
                    // rebuilt on each call changes the prompt right after L1, where the
                    // provider's cache then stops
                    "app_state_snapshot" to sessionEntity.appStateSnapshot
                ),
                "messages" to messageEntities.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "timestamp" to msg.timestamp,
                        "sender" to msg.sender.name, // Convert enum to string
                        "rich_content_json" to msg.richContentJson,
                        "text_content" to msg.textContent,
                        "ai_message_json" to msg.aiMessageJson,
                        "system_message_json" to msg.systemMessageJson,
                        "exclude_from_prompt" to msg.excludeFromPrompt
                    )
                }
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_load_session").format(e.message ?: ""))
        }
    }

    private suspend fun listSessions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("Listing all sessions", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntities = database.aiDao().getAllSessions()

            LogManager.aiSession("Found ${sessionEntities.size} sessions", "DEBUG")

            val sessions = sessionEntities.map { session ->
                mapOf(
                    "id" to session.id,
                    "name" to session.name,
                    "type" to session.type.name,
                    "provider_id" to session.providerId,
                    "created_at" to session.createdAt,
                    "last_activity" to session.lastActivity,
                    "is_active" to session.isActive
                )
            }

            return OperationResult.success(mapOf(
                "sessions" to sessions,
                "count" to sessions.size
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list sessions: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_sessions").format(e.message ?: ""))
        }
    }

    /**
     * A routine's run as its AI received it (PromptManager.runHistory): "session_id", and
     * "messages", "all" or "errors" for the failures alone.
     */
    private suspend fun runHistory(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val onlyFailures = when (val messages = params.optString("messages", "all")) {
            "all" -> false
            "errors" -> true
            else -> return OperationResult.error(s.shared("ai_error_run_messages").format(messages))
        }
        return try {
            OperationResult.success(mapOf("session_id" to sessionId, "messages" to PromptManager.runHistory(context, sessionId, onlyFailures)))
        } catch (e: IllegalArgumentException) {
            OperationResult.error(e.message ?: sessionId)
        }
    }

    private suspend fun listSessionsForRoutine(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val routineId = params.optString("routine_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_routine_id_required"))
        val limit = params.optInt("limit", 10)
        val page = params.optInt("page", 1)
        val startTime = if (params.has("start_time")) params.getLong("start_time") else null
        val endTime = if (params.has("end_time")) params.getLong("end_time") else null

        LogManager.aiSession("Listing sessions for routine: routineId=$routineId, limit=$limit, page=$page, startTime=$startTime, endTime=$endTime", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Calculate offset for pagination (page 1 = offset 0)
            val offset = (page - 1) * limit

            // Get paginated sessions
            val sessionEntities = dao.getSessionsForRoutinePaginated(
                routineId = routineId,
                limit = limit,
                offset = offset,
                startTime = startTime,
                endTime = endTime
            )

            // Get total count for pagination
            val totalEntries = dao.countSessionsForRoutine(
                routineId = routineId,
                startTime = startTime,
                endTime = endTime
            )

            val totalPages = if (totalEntries == 0) 0 else ((totalEntries - 1) / limit) + 1

            LogManager.aiSession("Found ${sessionEntities.size} sessions for routine $routineId (total: $totalEntries, page: $page/$totalPages)", "DEBUG")

            // Each session's cost, summed from its messages in one query for the page
            val costs = dao.getCallUsages(sessionEntities.map { it.id })
                .groupBy { it.sessionId }
                .mapValues { (_, rows) -> SessionCost.of(rows.map { it.toCallUsage() }) }

            // Map sessions to result format including all necessary fields for ExecutionCard
            val sessions = sessionEntities.map { session ->
                val cost = costs[session.id] ?: SessionCost.of(emptyList())
                mapOf(
                    "id" to session.id,
                    "name" to session.name,
                    "type" to session.type.name,
                    "routine_id" to session.routineId,
                    "scheduled_execution_time" to session.scheduledExecutionTime,
                    "triggered_by" to session.triggeredBy,
                    "provider_id" to session.providerId,
                    "provider_session_id" to session.providerSessionId,
                    "created_at" to session.createdAt,
                    "last_activity" to session.lastActivity,
                    "is_active" to session.isActive,
                    "phase" to session.phase,
                    "end_reason" to session.endReason,
                    "summary" to session.summary,
                    "waits" to session.runWaits(),
                    "total_roundtrips" to session.totalRoundtrips,
                    "total_tokens" to cost.totalTokens,
                    "total_cost" to cost.totalCost,
                    "cost_is_lower_bound" to cost.isLowerBound
                )
            }

            return OperationResult.success(mapOf(
                "sessions" to sessions,
                "pagination" to mapOf(
                    "current_page" to page,
                    "total_pages" to totalPages,
                    "total_entries" to totalEntries
                )
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list sessions for routine: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_sessions").format(e.message ?: ""))
        }
    }

    private suspend fun updateSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Updating session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntity = database.aiDao().getSession(sessionId)

            if (sessionEntity == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Extract fields to update (name is the main updatable field)
            val name = params.optString("name")
            val now = System.currentTimeMillis()

            val updatedEntity = sessionEntity.copy(
                name = if (name.isNotEmpty()) name else sessionEntity.name,
                lastActivity = now
            )

            database.aiDao().updateSession(updatedEntity)

            LogManager.aiSession("Successfully updated session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "name" to updatedEntity.name,
                "updated_at" to now
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_session").format(e.message ?: ""))
        }
    }

    private suspend fun deleteSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Deleting session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val sessionEntity = database.aiDao().getSession(sessionId)

            if (sessionEntity == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Its files, images and texts, whose rows go with it by cascade and whose files go after them
            val files = database.fileDao().getForSession(sessionId)

            // Delete all messages for this session first
            database.aiDao().deleteMessagesForSession(sessionId)

            // Delete the session
            database.aiDao().deleteSession(sessionEntity)
            files.forEach { app.treelune.core.files.Files.delete(context, it) }

            LogManager.aiSession("Successfully deleted session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "deleted" to true
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_session").format(e.message ?: ""))
        }
    }

    private suspend fun setActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Setting active session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Check if session exists
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Deactivate all sessions first
            dao.deactivateAllSessions()

            // Activate the target session
            dao.activateSession(sessionId)

            LogManager.aiSession("Successfully set active session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "is_active" to true
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to set active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_set_active_session").format(e.message ?: ""))
        }
    }

    private suspend fun getActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("Getting active session", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val activeSessionEntity = database.aiDao().getActiveSession()

            if (activeSessionEntity == null) {
                LogManager.aiSession("No active session found", "DEBUG")
                return OperationResult.success(mapOf(
                    "has_active_session" to false
                ))
            }

            // Get messages for the active session
            val messageEntities = database.aiDao().getMessagesForSession(activeSessionEntity.id)

            LogManager.aiSession("Found active session: ${activeSessionEntity.id} with ${messageEntities.size} messages", "DEBUG")

            return OperationResult.success(mapOf(
                "has_active_session" to true,
                "session_id" to activeSessionEntity.id,
                "session" to mapOf(
                    "id" to activeSessionEntity.id,
                    "name" to activeSessionEntity.name,
                    "type" to activeSessionEntity.type.name, // Convert enum to string
                    SessionValidation.KEY_APP to activeSessionEntity.validateApp,
                    SessionValidation.KEY_ZONES to activeSessionEntity.validateZones,
                    SessionValidation.KEY_DATA to activeSessionEntity.validateData,
                    SessionReasoning.KEY to activeSessionEntity.reasoning()?.toMap(),
                    "provider_id" to activeSessionEntity.providerId,
                    "provider_session_id" to activeSessionEntity.providerSessionId,
                    "created_at" to activeSessionEntity.createdAt,
                    "last_activity" to activeSessionEntity.lastActivity,
                    "is_active" to activeSessionEntity.isActive
                ),
                "messages" to messageEntities.map { msg ->
                    mapOf(
                        "id" to msg.id,
                        "timestamp" to msg.timestamp,
                        "sender" to msg.sender.name, // Convert enum to string
                        "rich_content_json" to msg.richContentJson,
                        "text_content" to msg.textContent,
                        "ai_message_json" to msg.aiMessageJson,
                        "system_message_json" to msg.systemMessageJson,
                        "exclude_from_prompt" to msg.excludeFromPrompt
                    )
                }
            ))

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_get_active_session").format(e.message ?: ""))
        }
    }

    private suspend fun stopActiveSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        LogManager.aiSession("Stopping active session (deactivating all sessions)", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Deactivate all sessions
            dao.deactivateAllSessions()

            LogManager.aiSession("Successfully stopped active session", "INFO")
            return OperationResult.success(mapOf(
                "sessions_deactivated" to true
            ))

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to stop active session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_stop_session"))
        }
    }

    private suspend fun createMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        // Extract required parameters
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val senderString = params.optString("sender").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_sender_required"))
        val timestamp = params.optLong("timestamp", System.currentTimeMillis())

        // Parse sender enum
        val sender = try {
            MessageSender.valueOf(senderString)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OperationResult.error(s.shared("ai_error_invalid_sender").format(senderString))
        }

        LogManager.aiSession("Creating message: sessionId=$sessionId, sender=$sender, timestamp=$timestamp", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageId = UUID.randomUUID().toString()

            // Extract content based on message type
            // richContent is already serialized JSON from RichMessage.toJson()
            val richContentJson = params.optString("rich_content")?.takeIf { it.isNotEmpty() }
            val textContent = params.optString("text_content")?.takeIf { it.isNotEmpty() }
            val aiMessageJson = params.optString("ai_message_json")?.takeIf { it.isNotEmpty() }

            // Handle SystemMessage if provided
            val systemMessageJson = if (params.has("system_message")) {
                val systemMessage = params.get("system_message") as? SystemMessage
                systemMessage?.toJson()
            } else {
                null
            }

            // Get excludeFromPrompt flag
            val excludeFromPrompt = params.optBoolean("exclude_from_prompt", false)

            // Extract token usage metrics (for AI messages only, 0 for USER/SYSTEM)
            val inputTokens = params.optInt("input_tokens", 0)
            val cacheWriteTokens = params.optInt("cache_write_tokens", 0)
            val cacheReadTokens = params.optInt("cache_read_tokens", 0)
            val outputTokens = params.optInt("output_tokens", 0)

            // Create message entity
            val messageEntity = SessionMessageEntity(
                id = messageId,
                sessionId = sessionId,
                timestamp = timestamp,
                sender = sender,
                richContentJson = richContentJson, // Store RichMessage JSON as-is (already serialized)
                textContent = textContent,
                aiMessageJson = aiMessageJson,
                aiMessageParsedJson = null, // TODO: Parse AIMessage when implementing
                systemMessageJson = systemMessageJson,
                executionMetadataJson = null, // TODO: Implement routine metadata
                excludeFromPrompt = excludeFromPrompt,
                // Token usage metrics for cost calculation
                inputTokens = inputTokens,
                cacheWriteTokens = cacheWriteTokens,
                cacheReadTokens = cacheReadTokens,
                outputTokens = outputTokens
            )

            // Insert message
            database.aiDao().insertMessage(messageEntity)

            // Update session last activity
            database.aiDao().updateSessionActivity(sessionId, timestamp)

            LogManager.aiSession("Successfully created message: $messageId for session $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "message_id" to messageId,
                "session_id" to sessionId,
                "timestamp" to timestamp,
                "sender" to sender.name
            ))

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to create message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_create_message").format(e.message ?: ""))
        }
    }

    private suspend fun getMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_message_id_required"))

        LogManager.aiSession("Getting message: $messageId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntity = database.aiDao().getMessage(messageId)

            if (messageEntity == null) {
                LogManager.aiSession("Message not found: $messageId", "WARN")
                return OperationResult.error(s.shared("ai_error_message_not_found").format(messageId))
            }

            return OperationResult.success(mapOf(
                "message" to mapOf(
                    "id" to messageEntity.id,
                    "session_id" to messageEntity.sessionId,
                    "timestamp" to messageEntity.timestamp,
                    "sender" to messageEntity.sender.name,
                    "rich_content_json" to messageEntity.richContentJson,
                    "text_content" to messageEntity.textContent,
                    "ai_message_json" to messageEntity.aiMessageJson,
                    "system_message_json" to messageEntity.systemMessageJson
                )
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_get_message").format(e.message ?: ""))
        }
    }

    private suspend fun listMessages(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Listing messages for session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntities = database.aiDao().getMessagesForSession(sessionId)

            LogManager.aiSession("Found ${messageEntities.size} messages for session $sessionId", "DEBUG")

            val messages = messageEntities.map { msg ->
                mapOf(
                    "id" to msg.id,
                    "timestamp" to msg.timestamp,
                    "sender" to msg.sender.name,
                    "rich_content_json" to msg.richContentJson,
                    "text_content" to msg.textContent,
                    "ai_message_json" to msg.aiMessageJson,
                    "system_message_json" to msg.systemMessageJson
                )
            }

            return OperationResult.success(mapOf(
                "messages" to messages,
                "count" to messages.size,
                "session_id" to sessionId
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list messages: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_messages").format(e.message ?: ""))
        }
    }

    private suspend fun updateMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_message_id_required"))

        LogManager.aiSession("Updating message: $messageId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntity = database.aiDao().getMessage(messageId)

            if (messageEntity == null) {
                LogManager.aiSession("Message not found: $messageId", "WARN")
                return OperationResult.error(s.shared("ai_error_message_not_found").format(messageId))
            }

            // Extract fields to update
            val richContentJson = params.optString("rich_content_json")
            val textContent = params.optString("text_content")
            val aiMessageJson = params.optString("ai_message_json")

            val updatedEntity = messageEntity.copy(
                richContentJson = if (richContentJson.isNotEmpty()) richContentJson else messageEntity.richContentJson,
                textContent = if (textContent.isNotEmpty()) textContent else messageEntity.textContent,
                aiMessageJson = if (aiMessageJson.isNotEmpty()) aiMessageJson else messageEntity.aiMessageJson
            )

            database.aiDao().updateMessage(updatedEntity)

            LogManager.aiSession("Successfully updated message: $messageId", "INFO")

            return OperationResult.success(mapOf(
                "message_id" to messageId,
                "updated" to true
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_message").format(e.message ?: ""))
        }
    }

    private suspend fun deleteMessage(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val messageId = params.optString("message_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_message_id_required"))

        LogManager.aiSession("Deleting message: $messageId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val messageEntity = database.aiDao().getMessage(messageId)

            if (messageEntity == null) {
                LogManager.aiSession("Message not found: $messageId", "WARN")
                return OperationResult.error(s.shared("ai_error_message_not_found").format(messageId))
            }

            database.aiDao().deleteMessage(messageEntity)

            LogManager.aiSession("Successfully deleted message: $messageId", "INFO")

            return OperationResult.success(mapOf(
                "message_id" to messageId,
                "deleted" to true
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete message: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_message").format(e.message ?: ""))
        }
    }

    /**
     * What the session [session_id] cost and did, read from its messages: its tokens and cost
     * (SessionCost, a lower bound with calls of unknown cost), its calls to the AI, the size of the
     * last request sent (uncached input and cache, what each new message adds to), the cost of each
     * model it called, and its writes done and refused or failed (RunEnd.facts).
     */
    private suspend fun getSessionStats(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("error_session_id_required"))

        LogManager.aiSession("Getting stats for session: $sessionId", "DEBUG")

        try {
            val dao = AppDatabase.getDatabase(context).aiDao()
            dao.getSession(sessionId) ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))

            val calls = dao.getCallUsages(listOf(sessionId)).map { it.toCallUsage() }
            val cost = SessionCost.of(calls)
            val lastSent = calls.lastOrNull { !it.usageUnknown }?.let { it.inputTokens + it.cacheWriteTokens + it.cacheReadTokens }
            // A call whose usage never came back has no model: it counts in the total alone
            val byModel = calls.mapNotNull { call -> call.pricing?.let { it.modelId to call } }
                .groupBy({ it.first }, { it.second })
                .map { (model, its) ->
                    val modelCost = SessionCost.of(its)
                    mapOf("model_id" to model, "calls" to its.size, "total_cost" to modelCost.totalCost,
                        "calls_with_unknown_cost" to modelCost.callsWithUnknownCost)
                }
            val writes = app.treelune.core.ai.orchestration.RunEnd.facts(
                dao.getMessagesForSession(sessionId).map { it.toDomain() }, null)

            LogManager.aiSession("Session stats: sessionId=$sessionId, total=\$${String.format("%.3f", cost.totalCost)}, calls=${calls.size}, calls with unknown cost=${cost.callsWithUnknownCost}", "DEBUG")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "total_uncached_input_tokens" to cost.uncachedInputTokens,
                "total_cache_write_tokens" to cost.cacheWriteTokens,
                "total_cache_read_tokens" to cost.cacheReadTokens,
                "total_output_tokens" to cost.outputTokens,
                "input_cost" to cost.inputCost,
                "cache_write_cost" to cost.cacheWriteCost,
                "cache_read_cost" to cost.cacheReadCost,
                "output_cost" to cost.outputCost,
                "total_cost" to cost.totalCost,
                "calls_with_unknown_cost" to cost.callsWithUnknownCost,
                "currency" to "USD",
                "calls" to calls.size,
                "by_model" to byModel,
                "writes_done" to writes.written,
                "writes_failed" to writes.failed
            // Absent before a call's usage came back
            ) + (lastSent?.let { mapOf("last_sent_tokens" to it) } ?: emptyMap()))

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to get session stats: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("error_cost_calculation_failed"))
        }
    }

    /**
     * What the session [session_id] adds to the protections: the three given (SessionValidation),
     * written whole.
     */
    private suspend fun updateValidation(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val validation = try {
            SessionValidation(params.getBoolean(SessionValidation.KEY_APP), params.getBoolean(SessionValidation.KEY_ZONES), params.getBoolean(SessionValidation.KEY_DATA))
        } catch (e: org.json.JSONException) {
            return OperationResult.error(s.shared("ai_error_toggle_validation"))
        }

        LogManager.aiSession("Validation of session $sessionId: $validation", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            dao.updateSession(session.copy(validateApp = validation.app, validateZones = validation.zones, validateData = validation.data))

            LogManager.aiSession("Validation of session $sessionId now $validation", "INFO")

            return OperationResult.success(mapOf("session_id" to sessionId) + validation.toMap())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to toggle validation for session $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_toggle_validation"))
        }
    }

    /**
     * Sets the reasoning a conversation asks of its model (SessionReasoning): `reasoning` with
     * `thinking_off` and `effort` (null, the model's default), or null to follow its provider's
     * config again. Refused, in the provider's words, when the model of the provider it calls
     * (the default for a conversation that has none yet) does not take it.
     */
    private suspend fun updateReasoning(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val reasoning = params.optJSONObject(SessionReasoning.KEY)?.let {
            if (!it.has(SessionReasoning.KEY_THINKING_OFF)) {
                return OperationResult.error(s.shared("ai_error_param_missing").format(SessionReasoning.KEY_THINKING_OFF))
            }
            SessionReasoning(
                it.getBoolean(SessionReasoning.KEY_THINKING_OFF),
                if (it.isNull(SessionReasoning.KEY_EFFORT)) null else it.getString(SessionReasoning.KEY_EFFORT)
            )
        }

        val dao = AppDatabase.getDatabase(context).aiDao()
        val session = dao.getSession(sessionId)
            ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
        val providerId = session.providerId ?: dao.getDefaultProviderConfig()?.id
        if (reasoning != null && providerId != null) {
            app.treelune.core.ai.providers.AIClient(context).reasoningRefusal(reasoning, providerId)?.let { return OperationResult.error(it) }
        }

        dao.updateSession(session.copy(reasoningThinkingOff = reasoning?.thinkingOff, reasoningEffort = reasoning?.effort))
        LogManager.aiSession("Reasoning of session $sessionId now ${reasoning ?: "its provider's"}", "INFO")
        return OperationResult.success(buildMap { put("session_id", sessionId); reasoning?.let { put(SessionReasoning.KEY, it.toMap()) } })
    }

    /**
     * Sets the provider conversation [params] calls (docs/AI.md, « Provider abstraction »), from its next
     * message on: one that exists, never on a routine's run nor a seed, which take their routine's.
     * Its own reasoning goes: what one model offers another may refuse; it follows its new
     * provider's config until set again.
     */
    private suspend fun setProvider(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val providerId = params.optString("provider_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_provider_id_required"))
        val dao = AppDatabase.getDatabase(context).aiDao()
        val session = dao.getSession(sessionId)
            ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
        if (session.type != SessionType.CHAT) return OperationResult.error(s.shared("ai_error_session_provider_chat_only"))
        dao.getProviderConfig(providerId) ?: return OperationResult.error(s.shared("ai_error_provider_not_found").format(providerId))
        val changed = session.providerId != null && session.providerId != providerId
        dao.updateSession(session.copy(providerId = providerId,
            reasoningThinkingOff = if (changed) null else session.reasoningThinkingOff,
            reasoningEffort = if (changed) null else session.reasoningEffort))
        LogManager.aiSession("Session $sessionId now calls provider $providerId${if (changed) ", its own reasoning dropped" else ""}", "INFO")
        return OperationResult.success(mapOf("session_id" to sessionId, "provider_id" to providerId, "reasoning_dropped" to changed))
    }

    /**
     * The session [params] name seen by the user now: a chat shown on screen, a routine's run
     * opened. A routine's run no longer waits, and its zone and routine are told.
     */
    private suspend fun markSeen(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val session = aiDao.getSession(sessionId)
            ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
        aiDao.updateSeenAt(sessionId, System.currentTimeMillis())
        app.treelune.core.utils.DataChangeNotifier.notifyAISessionsChanged(session.routineId)
        return OperationResult.success(mapOf("session_id" to sessionId))
    }

    /**
     * A routine's run over (RunEnd): its summary kept, and seen at once when its routine does not
     * signal it ([params] `problem`, as RunEnd judged it; RoutineSettings.NOTIFY_*). Gives
     * whether it waits, with the routine and zone a notification opens it in.
     */
    private suspend fun endRun(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val session = aiDao.getSession(sessionId)
            ?: return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
        val routine = session.routineId?.let { aiDao.getRoutineById(it) }
            ?: return OperationResult.error(s.shared("error_routine_not_found"))
        if (!params.has("problem")) return OperationResult.error("Parameter 'problem' is required")
        val waits = app.treelune.core.ai.orchestration.RunEnd.waits(routine.notifyEnd, params.getBoolean("problem"))
        val summary = params.optString("summary").takeIf { it.isNotEmpty() }
        aiDao.updateSession(session.copy(summary = summary, seenAt = if (waits) null else System.currentTimeMillis()))
        app.treelune.core.utils.DataChangeNotifier.notifyAISessionsChanged(routine.id)
        LogManager.aiSession("Run $sessionId of ${routine.id} over, ${if (waits) "waiting" else "seen"}, ${if (summary == null) "no summary" else "its summary kept"}", "INFO")
        return OperationResult.success(mapOf(
            "waits" to waits,
            "routine_id" to routine.id,
            "routine_name" to routine.name,
            "zone_id" to routine.zoneId
        ))
    }

    /**
     * Set session end reason (COMPLETED, TIMEOUT, ERROR, CANCELLED, INTERRUPTED, NETWORK_ERROR, SUSPENDED)
     * Note: V2 architecture manages state transitions via AIStateRepository, this is kept for backward compatibility
     */
    private suspend fun setEndReason(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val endReasonString = params.optString("end_reason").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error("Parameter 'endReason' is required")

        LogManager.aiSession("Updating session end reason for $sessionId: $endReasonString", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Get session
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update endReason field
            val updatedSession = session.copy(endReason = endReasonString)
            dao.updateSession(updatedSession)

            LogManager.aiSession("Successfully updated session end reason for $sessionId: $endReasonString", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "end_reason" to endReasonString
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to update session end reason for $sessionId: ${e.message}", "ERROR", e)
            return OperationResult.error("Failed to update session end reason")
        }
    }

    /**
     * List CHAT sessions with search and pagination (for History feature)
     * Same pattern as list_sessions_for_routine
     */
    private suspend fun listChatSessions(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val search = params.optString("search").takeIf { it.isNotEmpty() }
        val limit = params.optInt("limit", 20)
        val page = params.optInt("page", 1)
        val startTime = if (params.has("start_time")) params.getLong("start_time") else null
        val endTime = if (params.has("end_time")) params.getLong("end_time") else null

        LogManager.aiSession("Listing CHAT sessions: search=$search, page=$page, limit=$limit, startTime=$startTime, endTime=$endTime", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Calculate offset for pagination (page 1 = offset 0)
            val offset = (page - 1) * limit

            // Get sessions and count
            val sessionEntities = dao.getChatSessionsWithSearch(
                search = search,
                startTime = startTime,
                endTime = endTime,
                limit = limit,
                offset = offset
            )

            val total = dao.countChatSessionsWithSearch(
                search = search,
                startTime = startTime,
                endTime = endTime
            )

            val totalPages = if (total == 0) 0 else ((total - 1) / limit) + 1

            LogManager.aiSession("Found ${sessionEntities.size} CHAT sessions (total: $total, page: $page/$totalPages)", "DEBUG")

            // Build session list with preview and message count; the previews' pointers are named
            // as their targets are now, read once for the whole page
            val firstMessages = sessionEntities.associate { it.id to dao.getFirstUserMessage(it.id) }
            val enrichmentText = app.treelune.core.ai.enrichments.EnrichmentText.load(context,
                app.treelune.core.ai.enrichments.EnrichmentText.blocksOf(firstMessages.values.mapNotNull { m -> m?.richContentJson?.let { RichMessage.fromJson(it) } }))
            val sessions = sessionEntities.map { session ->
                // Get message count
                val messageCount = dao.getMessageCountForSession(session.id)

                // Get first user message for preview
                val firstMessage = firstMessages[session.id]
                val preview = if (firstMessage?.richContentJson != null) {
                    try {
                        val text = RichMessage.fromJson(firstMessage.richContentJson)?.let { enrichmentText.display(it) } ?: ""
                        // Truncate to 60 chars
                        if (text.length > 60) {
                            text.substring(0, 60) + "..."
                        } else {
                            text
                        }
                    } catch (e: Exception) {
                        LogManager.aiSession("Failed to parse richContentJson for preview: ${e.message}", "WARN")
                        ""
                    }
                } else {
                    ""
                }

                mapOf(
                    "id" to session.id,
                    "name" to session.name,
                    "created_at" to session.createdAt,
                    "last_activity" to session.lastActivity,
                    "message_count" to messageCount,
                    "first_user_message" to preview
                )
            }

            return OperationResult.success(mapOf(
                "sessions" to sessions,
                "pagination" to mapOf(
                    "current_page" to page,
                    "total_pages" to totalPages,
                    "total_entries" to total
                )
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to list CHAT sessions: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_list_sessions").format(e.message ?: ""))
        }
    }

    /**
     * Rename a session
     */
    private suspend fun renameSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))
        val name = params.optString("name").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_name_required"))

        // A name is as long as any other name of the app
        if (name.length > app.treelune.core.validation.FieldLimits.SHORT_LENGTH) {
            return OperationResult.error(s.shared("error_validation_maxlength").format(app.treelune.core.validation.FieldLimits.SHORT_LENGTH.toString()))
        }

        LogManager.aiSession("Renaming session $sessionId to: $name", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Check session exists
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            // Update name
            dao.updateSessionName(sessionId, name)

            LogManager.aiSession("Successfully renamed session $sessionId to: $name", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "name" to name
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to rename session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_update_session").format(e.message ?: ""))
        }
    }

    /**
     * Delete a CHAT session (for History feature)
     * Uses CASCADE DELETE via FK constraints to automatically delete associated messages
     */
    private suspend fun deleteChatSession(params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()

        val sessionId = params.optString("session_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("ai_error_param_session_id_required"))

        LogManager.aiSession("Deleting CHAT session: $sessionId", "DEBUG")

        try {
            val database = AppDatabase.getDatabase(context)
            val dao = database.aiDao()

            // Check session exists and is CHAT type
            val session = dao.getSession(sessionId)
            if (session == null) {
                LogManager.aiSession("Session not found: $sessionId", "WARN")
                return OperationResult.error(s.shared("ai_error_session_not_found").format(sessionId))
            }

            if (session.type != SessionType.CHAT) {
                LogManager.aiSession("Cannot delete non-CHAT session: $sessionId (type: ${session.type})", "WARN")
                return OperationResult.error("Cannot delete non-CHAT session")
            }

            // Its files, images and texts, whose rows go with it by cascade and whose files go after them
            val files = database.fileDao().getForSession(sessionId)

            // Delete session (CASCADE DELETE will handle messages automatically)
            dao.deleteSessionById(sessionId)
            files.forEach { app.treelune.core.files.Files.delete(context, it) }

            LogManager.aiSession("Successfully deleted CHAT session: $sessionId", "INFO")

            return OperationResult.success(mapOf(
                "session_id" to sessionId,
                "deleted" to true
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogManager.aiSession("Failed to delete CHAT session: ${e.message}", "ERROR", e)
            return OperationResult.error(s.shared("ai_error_delete_session").format(e.message ?: ""))
        }
    }

    /**
     * Get AI database DAO
     */
    private fun getAIDao(): AIDao {
        return AppDatabase.getDatabase(context).aiDao()
    }

    /**
     * Verbalize AI session operation
     * AI session management is typically not exposed to AI actions
     */
    /** A routine's runs and one of them read, by the routine's name; nothing else is the AI's. */
    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        val dao = AppDatabase.getDatabase(context).aiDao()
        return when (operation) {
            "list_sessions_for_routine" -> s.shared("action_verbalize_runs_list")
                .format(params.optString("routine_id").let { dao.getRoutineById(it)?.name ?: it })
            "run_history" -> s.shared("action_verbalize_run_history").format(params.optString("session_id").let { id ->
                dao.getSession(id)?.routineId?.let { dao.getRoutineById(it)?.name } ?: id
            })
            else -> s.shared("action_verbalize_unknown")
        }
    }
}