package app.treelune.core.ai.database

import androidx.room.*
import app.treelune.core.ai.data.SessionEndReason

/**
 * DAO for AI sessions and messages
 */
@Dao
interface AIDao {

    // === Sessions ===

    @Query("SELECT * FROM ai_sessions ORDER BY last_activity DESC")
    suspend fun getAllSessions(): List<AISessionEntity>

    @Query("SELECT * FROM ai_sessions WHERE is_active = 1 ORDER BY last_activity DESC LIMIT 1")
    suspend fun getActiveSession(): AISessionEntity?

    @Query("SELECT * FROM ai_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AISessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AISessionEntity)

    @Update
    suspend fun updateSession(session: AISessionEntity)

    @Query("UPDATE ai_sessions SET is_active = 0")
    suspend fun deactivateAllSessions()

    @Query("UPDATE ai_sessions SET is_active = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: String)

    @Query("UPDATE ai_sessions SET is_active = 1 WHERE id = :sessionId")
    suspend fun activateSession(sessionId: String)

    @Query("UPDATE ai_sessions SET last_activity = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionActivity(sessionId: String, timestamp: Long)

    @Query("UPDATE ai_sessions SET end_reason = :endReason WHERE id = :sessionId")
    suspend fun updateSessionEndReason(sessionId: String, endReason: String?)

    @Query("UPDATE ai_sessions SET app_state_snapshot = :snapshot WHERE id = :sessionId")
    suspend fun updateAppStateSnapshot(sessionId: String, snapshot: String)

    /**
     * The state AIStateRepository keeps of a session, written alone: a whole row written back
     * would undo what was written beside it meanwhile (seen_at, the chat shown).
     */
    @Query("""
        UPDATE ai_sessions SET phase = :phase, total_roundtrips = :totalRoundtrips, last_event_time = :lastEventTime,
            last_user_interaction_time = :lastUserInteractionTime, last_activity = :lastActivity, is_active = :isActive,
            end_reason = :endReason
        WHERE id = :sessionId
    """)
    suspend fun updateSessionState(sessionId: String, phase: String, totalRoundtrips: Int, lastEventTime: Long,
        lastUserInteractionTime: Long, lastActivity: Long, isActive: Boolean, endReason: String?)

    /** The session seen by the user at [at] (docs/BRICKS.md, « Waiting »). */
    @Query("UPDATE ai_sessions SET seen_at = :at WHERE id = :sessionId")
    suspend fun updateSeenAt(sessionId: String, at: Long?)

    /**
     * A routine's runs that wait for the user (docs/AI.md, « Summary of the run »): over for good,
     * not yet seen. AISessionEntity.runWaits says the same of one run.
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE type = 'AUTOMATION' AND seen_at IS NULL
          AND end_reason IS NOT NULL AND end_reason NOT IN ${SessionEndReason.RESUMABLE_SQL}
    """)
    suspend fun getWaitingRuns(): List<AISessionEntity>

    /** Whether the active chat has an AI message newer than when it was last seen. */
    @Query("""
        SELECT EXISTS(SELECT 1 FROM ai_sessions s JOIN session_messages m ON m.session_id = s.id
            WHERE s.is_active = 1 AND s.type = 'CHAT' AND m.sender = 'AI' AND m.timestamp > COALESCE(s.seen_at, 0))
    """)
    suspend fun chatWaits(): Boolean

    @Delete
    suspend fun deleteSession(session: AISessionEntity)

    // === Messages ===

    /** What the messages of these sessions say about their AI calls' cost, oldest first: see SessionCost */
    @Query("""
        SELECT session_id, input_tokens, cache_write_tokens, cache_read_tokens, output_tokens,
               model_id, input_price, cache_write_price, cache_read_price, output_price, usage_unknown
        FROM session_messages
        WHERE session_id IN (:sessionIds) AND (sender = 'AI' OR usage_unknown = 1)
        ORDER BY timestamp ASC
    """)
    suspend fun getCallUsages(sessionIds: List<String>): List<CallUsageRow>

    @Query("SELECT * FROM session_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<SessionMessageEntity>

    @Query("SELECT * FROM session_messages WHERE id = :messageId")
    suspend fun getMessage(messageId: String): SessionMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SessionMessageEntity)

    @Update
    suspend fun updateMessage(message: SessionMessageEntity)

    @Delete
    suspend fun deleteMessage(message: SessionMessageEntity)

    @Query("DELETE FROM session_messages WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    // === Utility queries ===

    @Query("SELECT COUNT(*) FROM ai_sessions WHERE type = :type")
    suspend fun getSessionCountByType(type: String): Int

    @Query("SELECT COUNT(*) FROM session_messages WHERE session_id = :sessionId")
    suspend fun getMessageCountForSession(sessionId: String): Int

    // === Provider Configurations ===

    @Query("SELECT * FROM ai_provider_configs ORDER BY created_at ASC")
    suspend fun getAllProviderConfigs(): List<AIProviderConfigEntity>

    @Query("SELECT * FROM ai_provider_configs WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultProviderConfig(): AIProviderConfigEntity?

    @Query("SELECT * FROM ai_provider_configs WHERE id = :id")
    suspend fun getProviderConfig(id: String): AIProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProviderConfig(config: AIProviderConfigEntity)

    @Update
    suspend fun updateProviderConfig(config: AIProviderConfigEntity)

    /** [id] the default, and no other, in one go. */
    @Query("UPDATE ai_provider_configs SET is_default = (id = :id)")
    suspend fun setDefaultProvider(id: String)

    @Query("DELETE FROM ai_provider_configs WHERE id = :id")
    suspend fun deleteProviderConfigById(id: String)

    // === Automations ===

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getAutomationById(id: String): AutomationEntity?

    @Query("SELECT * FROM automations WHERE zone_id = :zoneId ORDER BY created_at DESC")
    suspend fun getAutomationsByZone(zoneId: String): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE seed_session_id = :seedSessionId")
    suspend fun getAutomationBySeedSession(seedSessionId: String): AutomationEntity?

    @Query("SELECT * FROM automations ORDER BY created_at DESC")
    suspend fun getAllAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE is_enabled = 1")
    suspend fun getAllEnabledAutomations(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    @Update
    suspend fun updateAutomation(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomationById(id: String)

    @Query("UPDATE automations SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setAutomationEnabled(id: String, enabled: Boolean, updatedAt: Long)


    // === History Queries ===

    /**
     * List CHAT sessions with search and pagination
     * Search in session name and message content (LIKE NOCASE)
     * Filter by period (optional) and only completed sessions (endReason IS NOT NULL)
     */
    @Query("""
        SELECT DISTINCT s.* FROM ai_sessions s
        LEFT JOIN session_messages m ON m.session_id = s.id
        WHERE s.type = 'CHAT'
          AND s.end_reason IS NOT NULL
          AND (:search IS NULL OR :search = '' OR
               s.name LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.rich_content_json LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.text_content LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.ai_message_json LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:startTime IS NULL OR s.created_at >= :startTime)
          AND (:endTime IS NULL OR s.created_at <= :endTime)
        ORDER BY s.created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getChatSessionsWithSearch(
        search: String?,
        startTime: Long?,
        endTime: Long?,
        limit: Int,
        offset: Int
    ): List<AISessionEntity>

    /**
     * Count CHAT sessions matching search and period criteria
     * Used for pagination calculation
     */
    @Query("""
        SELECT COUNT(DISTINCT s.id) FROM ai_sessions s
        LEFT JOIN session_messages m ON m.session_id = s.id
        WHERE s.type = 'CHAT'
          AND s.end_reason IS NOT NULL
          AND (:search IS NULL OR :search = '' OR
               s.name LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.rich_content_json LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.text_content LIKE '%' || :search || '%' COLLATE NOCASE OR
               m.ai_message_json LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:startTime IS NULL OR s.created_at >= :startTime)
          AND (:endTime IS NULL OR s.created_at <= :endTime)
    """)
    suspend fun countChatSessionsWithSearch(
        search: String?,
        startTime: Long?,
        endTime: Long?
    ): Int

    /**
     * Get first user message for a session (for preview)
     * Returns the first message with sender = 'USER'
     */
    @Query("""
        SELECT * FROM session_messages
        WHERE session_id = :sessionId AND sender = 'USER'
        ORDER BY timestamp ASC
        LIMIT 1
    """)
    suspend fun getFirstUserMessage(sessionId: String): SessionMessageEntity?

    /**
     * Update session name (for rename operation)
     */
    @Query("UPDATE ai_sessions SET name = :name WHERE id = :sessionId")
    suspend fun updateSessionName(sessionId: String, name: String)

    /**
     * Delete session by ID
     * Messages are deleted automatically via ON DELETE CASCADE FK constraint
     */
    @Query("DELETE FROM ai_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    // === Automation Scheduling Queries ===

    /**
     * Get ALL incomplete automation sessions (for any automation, enabled or disabled)
     * Returns the sessions not active, with no end reason or a resumable one (SessionEndReason.resumable)
     * Used by scheduler to detect all sessions to resume (crash, network timeout, suspended by CHAT)
     * Ordered by scheduledExecutionTime ASC (oldest first)
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE type = 'AUTOMATION'
          AND (end_reason IS NULL OR end_reason IN ${SessionEndReason.RESUMABLE_SQL})
          AND is_active = 0
        ORDER BY scheduled_execution_time ASC
    """)
    suspend fun getAllIncompleteAutomationSessions(): List<AISessionEntity>

    /**
     * Get incomplete automation session for a specific automation
     * Returns the sessions not active, with no end reason or a resumable one (SessionEndReason.resumable)
     * Used by scheduler to detect sessions to resume (crash, network timeout)
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND (end_reason IS NULL OR end_reason IN ${SessionEndReason.RESUMABLE_SQL})
          AND is_active = 0
        ORDER BY scheduled_execution_time DESC
        LIMIT 1
    """)
    suspend fun getIncompleteAutomationSession(automationId: String): AISessionEntity?

    /**
     * Get last completed automation session for a specific automation: one with an end reason
     * that is not resumable, the exact opposite of the incomplete ones, so that no reason falls
     * between the two (an occurrence run again, or never taken up)
     * Used by scheduler to calculate next expected execution time
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND end_reason IS NOT NULL AND end_reason NOT IN ${SessionEndReason.RESUMABLE_SQL}
        ORDER BY scheduled_execution_time DESC
        LIMIT 1
    """)
    suspend fun getLastCompletedAutomationSession(automationId: String): AISessionEntity?

    /**
     * Get sessions for automation with pagination and optional time filtering
     * Returns sessions ordered by scheduledExecutionTime DESC (most recent first)
     * Used by AutomationScreen to display execution history
     */
    @Query("""
        SELECT * FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND (:startTime IS NULL OR created_at >= :startTime)
          AND (:endTime IS NULL OR created_at <= :endTime)
        ORDER BY scheduled_execution_time DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getSessionsForAutomationPaginated(
        automationId: String,
        limit: Int,
        offset: Int,
        startTime: Long?,
        endTime: Long?
    ): List<AISessionEntity>

    /**
     * Count sessions for automation with optional time filtering
     * Used by AutomationScreen for pagination calculation
     */
    @Query("""
        SELECT COUNT(*) FROM ai_sessions
        WHERE automation_id = :automationId
          AND type = 'AUTOMATION'
          AND (:startTime IS NULL OR created_at >= :startTime)
          AND (:endTime IS NULL OR created_at <= :endTime)
    """)
    suspend fun countSessionsForAutomation(
        automationId: String,
        startTime: Long?,
        endTime: Long?
    ): Int
}

/** One message's share of its session's cost, as getCallUsages reads it */
data class CallUsageRow(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "input_tokens") val inputTokens: Int,
    @ColumnInfo(name = "cache_write_tokens") val cacheWriteTokens: Int,
    @ColumnInfo(name = "cache_read_tokens") val cacheReadTokens: Int,
    @ColumnInfo(name = "output_tokens") val outputTokens: Int,
    @ColumnInfo(name = "model_id") val modelId: String?,
    @ColumnInfo(name = "input_price") val inputPrice: Double?,
    @ColumnInfo(name = "cache_write_price") val cacheWritePrice: Double?,
    @ColumnInfo(name = "cache_read_price") val cacheReadPrice: Double?,
    @ColumnInfo(name = "output_price") val outputPrice: Double?,
    @ColumnInfo(name = "usage_unknown") val usageUnknown: Boolean
) {
    fun toCallUsage() = app.treelune.core.ai.data.CallUsage(
        inputTokens = inputTokens,
        cacheWriteTokens = cacheWriteTokens,
        cacheReadTokens = cacheReadTokens,
        outputTokens = outputTokens,
        pricing = modelId?.let {
            app.treelune.core.ai.data.CallPricing(it, inputPrice, cacheWritePrice, cacheReadPrice, outputPrice)
        },
        usageUnknown = usageUnknown
    )
}
