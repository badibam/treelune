package app.treelune.core.ai.domain

import app.treelune.core.ai.data.SessionType
import org.json.JSONObject

/**
 * Configuration for AI autonomous loop limits.
 *
 * One limit per session type: how many times the AI may be called on its own. The count
 * restarts at every user intervention in a CHAT, so the limit stops an AI that keeps calling
 * itself -- each call is paid for -- without bounding a conversation. An AUTOMATION has nobody
 * to intervene and counts its whole session.
 *
 * These defaults are the only ones: a fresh install or a reset writes them to the database
 * through toSettingsJson(), and fromSettingsJson() reads them back.
 */
data class AILimitsConfig(
    /** Maximum AI calls in a row for CHAT sessions, counted from the last user intervention */
    val chatMaxAutonomousRoundtrips: Int = 10,

    /** Maximum total autonomous roundtrips for AUTOMATION sessions (safety against infinite loops) */
    val automationMaxAutonomousRoundtrips: Int = 20,

    /**
     * Size, in characters of the text the AI receives, above which data asked for in a CHAT
     * waits for the user's confirmation. 15 000 is above the chat data of the 2026-09-18 backup
     * but its largest send (median 4 400, 90th percentile 13 000, maximum 33 000).
     */
    val chatMaxDataChars: Int = 15_000,

    /**
     * Same, for an AUTOMATION, where data above it is refused outright: nobody is there to
     * confirm. 100 000 is over twice what the automations of the 2026-09-18 backup send
     * (about 43 000 on nearly every run).
     */
    val automationMaxDataChars: Int = 100_000,

    /**
     * Size, in characters of the text the AI receives, of the tools marked always_send together
     * above which a CHAT asks once per session whether to send them, and an AUTOMATION or an
     * outside AI gets their list instead (docs/AI.md, « 2 levels of context »). Distinct from the data
     * thresholds, which guard what the AI asks for: this one guards a cost the user chose and
     * that grows unseen, paid at every call.
     */
    val alwaysSendMaxChars: Int = 15_000,

    /**
     * Runs of Tasks and routines one chain holds at most (Chains, docs/CORE.md, « Chains »): what
     * runs by itself, one run starting the next, without a person or an hour between them. Counted
     * over the whole chain, not the links in a row: two runs that each start two double at every
     * turn. 50 lets a back and forth between two routines go 25 times.
     */
    val chainMaxRuns: Int = 50,

    /**
     * The day's budget of every AI call, in dollars (docs/AI.md, « Daily budget »); null for none,
     * a setting left out when stored.
     */
    val dailyBudget: Double? = null,

    /** The share of [dailyBudget], in percent, past which a notification says so; null for none. */
    val budgetAlertPercent: Int? = null
) {
    /**
     * Get limits for specific session type
     */
    fun getLimitsForSessionType(sessionType: SessionType): SessionLimits {
        return when (sessionType) {
            SessionType.CHAT -> SessionLimits(
                maxAutonomousRoundtrips = chatMaxAutonomousRoundtrips,
                maxDataChars = chatMaxDataChars
            )
            SessionType.AUTOMATION -> SessionLimits(
                maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips,
                maxDataChars = automationMaxDataChars
            )
            SessionType.SEED -> {
                // SEED sessions are never executed, use AUTOMATION limits as fallback
                SessionLimits(
                    maxAutonomousRoundtrips = automationMaxAutonomousRoundtrips,
                    maxDataChars = automationMaxDataChars
                )
            }
        }
    }

    /** The ai_limits settings as stored in the database */
    fun toSettingsJson(): String = JSONObject().apply {
        put(KEY_CHAT, chatMaxAutonomousRoundtrips)
        put(KEY_AUTOMATION, automationMaxAutonomousRoundtrips)
        put(KEY_CHAT_DATA, chatMaxDataChars)
        put(KEY_AUTOMATION_DATA, automationMaxDataChars)
        put(KEY_ALWAYS_SEND, alwaysSendMaxChars)
        put(KEY_CHAIN, chainMaxRuns)
        dailyBudget?.let { put(KEY_BUDGET, it) }
        budgetAlertPercent?.let { put(KEY_BUDGET_ALERT, it) }
    }.toString()

    companion object {
        const val KEY_CHAT = "chat_max_autonomous_roundtrips"
        const val KEY_AUTOMATION = "automation_max_autonomous_roundtrips"
        const val KEY_CHAT_DATA = "chat_max_data_chars"
        const val KEY_AUTOMATION_DATA = "automation_max_data_chars"
        const val KEY_ALWAYS_SEND = "always_send_max_chars"
        const val KEY_CHAIN = "chain_max_runs"
        const val KEY_BUDGET = "daily_budget"
        const val KEY_BUDGET_ALERT = "budget_alert_percent"

        fun default() = AILimitsConfig()

        /**
         * Read the stored ai_limits settings. Every key but the budget's is required: a missing one throws
         * rather than standing for a default, since the database is written from default()
         * and migrated to carry them all.
         */
        fun fromSettingsJson(settings: JSONObject) = AILimitsConfig(
            chatMaxAutonomousRoundtrips = settings.getInt(KEY_CHAT),
            automationMaxAutonomousRoundtrips = settings.getInt(KEY_AUTOMATION),
            chatMaxDataChars = settings.getInt(KEY_CHAT_DATA),
            automationMaxDataChars = settings.getInt(KEY_AUTOMATION_DATA),
            alwaysSendMaxChars = settings.getInt(KEY_ALWAYS_SEND),
            chainMaxRuns = settings.getInt(KEY_CHAIN),
            // The budget and its alert alone may be left out: absent, none
            dailyBudget = if (settings.has(KEY_BUDGET) && !settings.isNull(KEY_BUDGET)) settings.getDouble(KEY_BUDGET) else null,
            budgetAlertPercent = if (settings.has(KEY_BUDGET_ALERT) && !settings.isNull(KEY_BUDGET_ALERT)) settings.getInt(KEY_BUDGET_ALERT) else null
        )
    }
}

/**
 * Limits for a specific session type (helper class)
 */
data class SessionLimits(
    val maxAutonomousRoundtrips: Int,
    /** Characters of data text above which a CHAT asks for confirmation and an AUTOMATION refuses */
    val maxDataChars: Int
)
