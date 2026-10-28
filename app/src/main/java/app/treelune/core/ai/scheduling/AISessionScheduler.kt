package app.treelune.core.ai.scheduling

import app.treelune.core.ai.data.ExecutionTrigger
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.database.AIDao

/**
 * Picks the session to run next once the execution slot frees up.
 *
 * Priority: CHAT from the queue, then a MANUAL routine from the queue, then a SCHEDULED
 * routine, which is not queued but worked out from the routines themselves.
 *
 * This needs the database, which is what separates it from SessionSlotPolicy: deciding what
 * to do with a session that asks to run, and whether the one holding the slot has been
 * there too long, depends only on the state and the clock and lives there.
 */
class AISessionScheduler(
    private val aiDao: AIDao,
    private val routineScheduler: RoutineScheduler
) {

    /**
     * Get next session to activate when slot becomes free.
     *
     * Priority: CHAT (from queue) > MANUAL (from queue) > SCHEDULED (from routines)
     *
     * @param queue Current session queue
     * @return Session to activate or null
     */
    suspend fun getNextSession(queue: List<QueuedSession>): SessionToActivate? {
        // Priority 1: CHAT from queue
        val chatSession = queue.find { it.sessionType == SessionType.CHAT }
        if (chatSession != null) {
            return SessionToActivate(
                sessionId = chatSession.sessionId,
                routineId = null,
                scheduledFor = null,
                sessionType = chatSession.sessionType,
                trigger = chatSession.trigger,
                removeFromQueue = true
            )
        }

        // Priority 2: MANUAL routine from queue
        val manualSession = queue.find {
            it.sessionType == SessionType.AUTOMATION && it.trigger == ExecutionTrigger.MANUAL
        }
        if (manualSession != null) {
            return SessionToActivate(
                sessionId = manualSession.sessionId,
                routineId = null,
                scheduledFor = null,
                sessionType = manualSession.sessionType,
                trigger = manualSession.trigger,
                removeFromQueue = true
            )
        }

        // Priority 3: SCHEDULED routine (not from queue - calculated dynamically)
        val nextSession = routineScheduler.getNextSession()
        return when (nextSession) {
            is NextSession.Resume -> {
                // Resume existing incomplete session
                SessionToActivate(
                    sessionId = nextSession.sessionId,
                    routineId = null,
                    scheduledFor = null,
                    sessionType = SessionType.AUTOMATION,
                    trigger = ExecutionTrigger.SCHEDULED, // Was scheduled, resume it
                    removeFromQueue = false // Not in queue
                )
            }
            is NextSession.Create -> {
                // Create new scheduled session from routine
                SessionToActivate(
                    sessionId = null,
                    routineId = nextSession.routineId,
                    scheduledFor = nextSession.scheduledFor,
                    sessionType = SessionType.AUTOMATION,
                    trigger = ExecutionTrigger.SCHEDULED,
                    removeFromQueue = false // Not in queue
                )
            }
            NextSession.None -> null
        }
    }
}

/**
 * Session in queue waiting for activation.
 *
 * Minimal structure - additional info (like routineId) can be loaded from DB via sessionId.
 */
data class QueuedSession(
    val sessionId: String,
    val sessionType: SessionType,
    val trigger: ExecutionTrigger,
    val queuedAt: Long
)

/**
 * Session to activate (result of getNextSession).
 *
 * Two modes:
 * 1. Resume: sessionId is set (existing session to activate)
 * 2. Create: routineId is set (create new session from routine)
 */
data class SessionToActivate(
    val sessionId: String?,              // Existing session to activate (Resume)
    val routineId: String?,           // Routine to create session from (Create)
    val scheduledFor: Long?,             // For Create only - timestamp for scheduledExecutionTime
    val sessionType: SessionType,
    val trigger: ExecutionTrigger,
    val removeFromQueue: Boolean
) {
    init {
        // Exactly one of sessionId or routineId must be set
        require((sessionId != null) xor (routineId != null)) {
            "SessionToActivate must have either sessionId or automationId set, not both or neither"
        }
    }

    /**
     * Check if this is a resume (existing session)
     */
    fun isResume(): Boolean = sessionId != null

    /**
     * Check if this is a create (new routine session)
     */
    fun isCreate(): Boolean = routineId != null
}
