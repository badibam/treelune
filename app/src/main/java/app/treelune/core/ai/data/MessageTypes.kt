package app.treelune.core.ai.data

/**
 * Types and enums shared across AI message system
 */

enum class MessageSender {
    USER,
    AI,
    SYSTEM
}

enum class SessionType {
    CHAT,       // Interactive conversation with user
    AUTOMATION, // Automated execution session
    SEED        // Template session for automation (not executed)
}

/**
 * Session state for tracking execution progress and distinguishing
 * real inactivity from network issues
 */
enum class SessionState {
    IDLE,                   // Waiting / inactive
    PROCESSING,             // Currently processing
    WAITING_NETWORK,        // Blocked on network availability
    WAITING_USER_RESPONSE,  // Waiting for communication module response
    WAITING_VALIDATION      // Waiting for user validation
}

/**
 * Reason why a session ended (for audit, debugging, and scheduler logic)
 *
 * Sessions to resume: null (crash), NETWORK_ERROR, SUSPENDED
 * Sessions completed: COMPLETED, CANCELLED, TIMEOUT, ERROR, LIMIT_REACHED
 */
enum class SessionEndReason {
    COMPLETED,       // AI indicated completion with completed flag
    LIMIT_REACHED,   // Autonomous loop limit reached (maxAutonomousRoundtrips for AUTOMATION)
    TIMEOUT,         // Watchdog timeout (inactivity without network issue)
    ERROR,           // Fatal technical error (format errors maxed out, provider error, etc.)
    CANCELLED,       // User clicked stop (or CHAT evicted by AUTOMATION)
    INTERRUPTED,     // Legacy/alias for null (crash detected as orphan)
    NETWORK_ERROR,   // Timeout with network flag active (to resume)
    SUSPENDED;       // To resume later

    companion object {
        /**
         * The reasons an automation session is taken up again for (AutomationScheduler); every
         * other reason ends its occurrence, which the scheduler then counts as done. The SQL list
         * is what the queries read (AIDao), a constant so that an annotation can carry it.
         */
        val resumable = setOf(NETWORK_ERROR, SUSPENDED)
        const val RESUMABLE_SQL = "('NETWORK_ERROR', 'SUSPENDED')"
    }
}

/**
 * Trigger type for automation executions
 * Used to distinguish manual vs scheduled vs event-triggered automations
 */
enum class ExecutionTrigger {
    SCHEDULED,  // Created by AutomationScheduler (never goes through queue)
    MANUAL,     // User clicked execute (goes to queue if slot occupied)
    ENTRY,      // An entry written that one of its triggers listens to (Triggers): goes through the queue as MANUAL
    EVENT       // An event emitted that one of its triggers listens to (docs/CORE.md, « Events »): the same
}

/**
 * The kinds of enrichment a message carries. [iconName] is the Lucide icon that shows it.
 */
enum class EnrichmentType(val iconName: String) {
    POINTER("search"),          // read-only data references
    FILE("file-text")           // a text file joined to the message, whole or previewed
}