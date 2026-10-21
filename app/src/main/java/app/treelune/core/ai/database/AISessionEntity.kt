package app.treelune.core.ai.database

import androidx.room.*
import app.treelune.core.ai.data.SessionType

/**
 * AI Session database entity for Event-Driven Architecture V2.
 *
 * Stores complete session state for atomic memory + DB synchronization.
 * - Searchable fields as columns
 * - Waiting context as JSON
 *
 * Architecture: Event-Driven State Machine (V2)
 * - phase: Current execution phase (replaces old 'state')
 * - Loop counter: totalRoundtrips (only limit enforced)
 * - Timestamps: lastEventTime, lastUserInteractionTime (for inactivity calculation)
 */
@Entity(
    tableName = "ai_sessions",
    indices = [
        Index(value = ["is_active"]),
        Index(value = ["type"]),
        Index(value = ["last_activity"]),
        Index(value = ["automation_id"]),
        Index(value = ["phase"]),
        Index(value = ["end_reason"])
    ]
)
data class AISessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: SessionType,
    /** What this conversation adds to the protections (SessionValidation): the home screen and the zones, the zones' content, the tools' data. */
    @ColumnInfo(name = "validate_app") val validateApp: Boolean = false,
    @ColumnInfo(name = "validate_zones") val validateZones: Boolean = false,
    @ColumnInfo(name = "validate_data") val validateData: Boolean = false,  // Session-level validation toggle
    /** The reasoning this conversation asks of its model (SessionReasoning); null thinking_off, its provider's config. */
    @ColumnInfo(name = "reasoning_thinking_off") val reasoningThinkingOff: Boolean? = null,
    @ColumnInfo(name = "reasoning_effort") val reasoningEffort: String? = null,

    // ==================== Event-Driven State (V2) ====================

    /** Current execution phase (IDLE, CALLING_AI, EXECUTING_ACTIONS, etc.) */
    val phase: String = "IDLE",


    // ==================== Loop Counters ====================

    /** AI calls made since the user last acted: see AIState.totalRoundtrips */
    @ColumnInfo(name = "total_roundtrips") val totalRoundtrips: Int = 0,

    // ==================== Timestamps ====================

    /** Timestamp of last event processed (any event) */
    @ColumnInfo(name = "last_event_time") val lastEventTime: Long = 0L,

    /** Timestamp of last user interaction (message, validation, response) */
    @ColumnInfo(name = "last_user_interaction_time") val lastUserInteractionTime: Long = 0L,

    // ==================== Session Metadata ====================

    @ColumnInfo(name = "automation_id") val automationId: String?,              // null for CHAT/SEED, automation ID for AUTOMATION
    @ColumnInfo(name = "scheduled_execution_time") val scheduledExecutionTime: Long?,      // For AUTOMATION: scheduled time (not actual execution time)
    // The provider it calls (AIProviderConfigEntity.id): a conversation takes the default at its
    // first call and keeps it unless changed; a run its routine's. Null before, and for a seed
    @ColumnInfo(name = "provider_id") val providerId: String?,
    @ColumnInfo(name = "provider_session_id") val providerSessionId: String,          // Provider API session ID
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_activity") val lastActivity: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "end_reason") val endReason: String? = null,          // SessionEndReason as string (null = crash/incomplete)

    /**
     * When the user last saw this session (docs/BRICKS.md, « Waiting »): a chat shown on
     * screen, a routine's run opened. A chat waits while an AI message is newer; a routine's run
     * waits while it has none.
     */
    @ColumnInfo(name = "seen_at") val seenAt: Long? = null,

    /** What a routine's AI said of its run, asked at its completion confirmation; null otherwise. */
    val summary: String? = null,

    /**
     * APP_STATE snapshot JSON taken at first user message
     * Used for cache stability - includes zones + tool instances (minimal, without config_json)
     * Format: {"timestamp": Long, "zones": [...], "tool_instances": [...]}
     * null = not yet captured (before first message)
     */
    @ColumnInfo(name = "app_state_snapshot") val appStateSnapshot: String? = null,

    /**
     * The identity it speaks as (docs/AI.md, « Identities »), fixed at its first message; null
     * for the app's own AI.
     */
    @ColumnInfo(name = "identity_id") val identityId: String? = null
)

/**
 * Level 4 queries (enrichments) are extracted from message history
 * Level 2 queries are generated dynamically from current user context
 */

/**
 * Type converters for Room
 */
class AITypeConverters {
    @TypeConverter
    fun fromSessionType(value: SessionType): String = value.name

    @TypeConverter
    fun toSessionType(value: String): SessionType = SessionType.valueOf(value)
}

/** Whether this is a routine's run that waits for the user: the rule of AIDao.getWaitingRuns, for one run. */
fun AISessionEntity.runWaits(): Boolean =
    type == SessionType.AUTOMATION && seenAt == null && !isActive &&
        endReason != null && app.treelune.core.ai.data.SessionEndReason.valueOf(endReason) !in app.treelune.core.ai.data.SessionEndReason.resumable

/** The reasoning this session asks of its model, or null when it follows its provider's config. */
fun AISessionEntity.reasoning(): app.treelune.core.ai.data.SessionReasoning? =
    reasoningThinkingOff?.let { app.treelune.core.ai.data.SessionReasoning(it, reasoningEffort) }
