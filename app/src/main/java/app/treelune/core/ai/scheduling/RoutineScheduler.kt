package app.treelune.core.ai.scheduling

import android.content.Context
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.ai.database.RoutineEntity
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.database.AppDatabase
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.ScheduleConfig
import kotlinx.serialization.json.Json

/**
 * Routine Scheduler - Pure calculation helper
 *
 * Responsibilities:
 * - Calculate which routine should execute next (if any)
 * - Detect incomplete sessions to resume (crash, network error, suspended)
 * - Calculate next scheduled execution time from schedule configuration
 *
 * NO notion of slot/queue - just pure calculation
 * Called by AISessionScheduler when slot is free and queue is empty
 */
class RoutineScheduler(private val context: Context) {

    private val coordinator = Coordinator(context)
    private val database = AppDatabase.getDatabase(context)
    private val aiDao = database.aiDao()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Format timestamp to human-readable date for logs (HH:mm:ss dd/MM/yyyy)
     */
    private fun formatTimestamp(timestamp: Long): String {
        return app.treelune.core.utils.DateUtils.format(timestamp, "HH:mm:ss dd/MM/yyyy")
    }

    /**
     * CatchUpPolicy.searchStart, plus the log line the skipping deserves.
     *
     * Skipped occurrences leave this line and nothing else: the history is made of sessions, and
     * an empty "skipped" one would be a shape to handle in every screen that reads it.
     */
    private fun catchUpSearchStart(
        routine: RoutineEntity,
        schedule: ScheduleConfig,
        lastCompletedSession: AISessionEntity?,
        now: Long
    ): Long {
        val lastExecutionTime = lastCompletedSession?.scheduledExecutionTime ?: 0L
        val start = CatchUpPolicy.searchStart(
            lastExecutionTime = lastExecutionTime,
            routineUpdatedAt = routine.updatedAt,
            catchUpWindow = routine.catchUpWindow,
            now = now
        )

        if (CatchUpPolicy.windowSkippedOccurrences(
                lastExecutionTime = lastExecutionTime,
                routineUpdatedAt = routine.updatedAt,
                catchUpWindow = routine.catchUpWindow,
                now = now
            )
        ) {
            LogManager.aiSession(
                "RoutineScheduler: Routine ${routine.id} skips what is older than its catch-up window " +
                "of ${routine.catchUpWindow} ms (search starts at ${formatTimestamp(start)})",
                "INFO"
            )
        }

        return start
    }

    /**
     * Get next routine session to execute
     * Returns Resume/Create/None based on enabled routines and their states
     *
     * Logic:
     * 1. Check ALL incomplete sessions (any routine, even disabled) → RESUME, unless closed
     *    for the network less than SessionSlotPolicy.NETWORK_RESUME_PAUSE ago
     * 2. For each enabled routine WITH schedule (without incomplete session):
     *    - Calculate next execution time from last completed → CREATE if time passed
     * 3. Sort all candidates by scheduledTime ASC (oldest first)
     * 4. Return first candidate or None
     */
    suspend fun getNextSession(): NextSession {
        try {
            LogManager.aiSession("RoutineScheduler: Calculating next session to execute", "DEBUG")

            // Build list of candidates (resume or create)
            val candidates = mutableListOf<ScheduleCandidate>()

            // Step 1: Check ALL incomplete sessions (any routine, even if disabled)
            // This ensures SUSPENDED sessions (from CHAT eviction) are always resumed
            val incompleteSessions = aiDao.getAllIncompleteRoutineSessions()

            LogManager.aiSession(
                "RoutineScheduler: Found ${incompleteSessions.size} incomplete sessions to check",
                "DEBUG"
            )

            // Routines whose incomplete session is closed for the network and still in its
            // pause: neither resumed nor run anew meanwhile
            val pausedRoutines = mutableSetOf<String>()

            for (incompleteSession in incompleteSessions) {
                // scheduledExecutionTime must not be null for ROUTINE_RUN sessions
                if (incompleteSession.scheduledExecutionTime == null) {
                    LogManager.aiSession(
                        "RoutineScheduler: Skipping incomplete session ${incompleteSession.id} - scheduledExecutionTime is null (data error)",
                        "ERROR"
                    )
                    continue
                }

                val routineId = incompleteSession.routineId
                if (routineId == null) {
                    LogManager.aiSession(
                        "RoutineScheduler: Skipping incomplete session ${incompleteSession.id} - routineId is null (data error)",
                        "ERROR"
                    )
                    continue
                }

                val endReason = incompleteSession.endReason?.let { app.treelune.core.ai.data.SessionEndReason.valueOf(it) }
                if (!SessionSlotPolicy.resumableNow(endReason, incompleteSession.lastEventTime, System.currentTimeMillis())) {
                    LogManager.aiSession(
                        "RoutineScheduler: Incomplete session ${incompleteSession.id} closed for the network, resumed after its pause",
                        "DEBUG"
                    )
                    pausedRoutines.add(routineId)
                    continue
                }

                LogManager.aiSession(
                    "RoutineScheduler: Found incomplete session ${incompleteSession.id} for routine $routineId " +
                    "(endReason=${incompleteSession.endReason}, scheduled=${formatTimestamp(incompleteSession.scheduledExecutionTime)})",
                    "INFO"
                )
                candidates.add(
                    ScheduleCandidate(
                        routineId = routineId,
                        scheduledTime = incompleteSession.scheduledExecutionTime,
                        action = CandidateAction.RESUME,
                        sessionId = incompleteSession.id
                    )
                )
            }

            // Step 2: For enabled routines WITH schedule, calculate next scheduled execution
            // Only if no incomplete session was found above
            val allEnabledRoutines = aiDao.getAllEnabledRoutines()

            if (allEnabledRoutines.isEmpty()) {
                LogManager.aiSession("RoutineScheduler: No enabled routines", "DEBUG")
            }

            val routinesWithIncomplete = candidates.map { it.routineId }.toSet() + pausedRoutines
            val routinesWithSchedule = allEnabledRoutines
                .filter { !it.scheduleJson.isNullOrEmpty() && !routinesWithIncomplete.contains(it.id) }

            LogManager.aiSession(
                "RoutineScheduler: Found ${candidates.size} sessions to resume, checking ${routinesWithSchedule.size} scheduled routines",
                "DEBUG"
            )

            for (routine in routinesWithSchedule) {
                // Parse schedule
                val schedule = try {
                    json.decodeFromString<ScheduleConfig>(routine.scheduleJson!!)
                } catch (e: Exception) {
                    LogManager.aiSession("RoutineScheduler: Failed to parse schedule for routine ${routine.id}: ${e.message}", "ERROR", e)
                    continue
                }

                // Get last completed session to calculate next execution time
                val lastCompletedSession = aiDao.getLastCompletedRoutineSession(routine.id)

                val now = System.currentTimeMillis()
                val fromTimestamp = catchUpSearchStart(routine, schedule, lastCompletedSession, now)

                LogManager.aiSession(
                    "RoutineScheduler: Calculating next execution for routine ${routine.id} " +
                    "(lastCompleted=${lastCompletedSession?.scheduledExecutionTime?.let { formatTimestamp(it) }}, " +
                    "updatedAt=${formatTimestamp(routine.updatedAt)}, " +
                    "catchUpWindow=${routine.catchUpWindow ?: "unlimited"}, " +
                    "fromTimestamp=${formatTimestamp(fromTimestamp)})",
                    "DEBUG"
                )

                val nextExecutionTime = ScheduleCalculator.calculateNextExecution(
                    pattern = schedule.pattern,
                    fromTimestamp = fromTimestamp
                )

                if (nextExecutionTime == null) {
                    LogManager.aiSession("RoutineScheduler: No more executions for routine ${routine.id} (schedule ended or invalid)", "DEBUG")
                    continue
                }

                // Check if execution time has passed
                if (nextExecutionTime <= now) {
                    // Among the occurrences that are due, run the most recent one or the oldest
                    val dueTime = if (routine.dismissOlderInstances) {
                        CatchUpPolicy.lastDueOccurrence(schedule, fromTimestamp, now, nextExecutionTime)
                    } else {
                        nextExecutionTime
                    }

                    if (dueTime != nextExecutionTime) {
                        LogManager.aiSession(
                            "RoutineScheduler: Routine ${routine.id} set to the most recent due occurrence " +
                            "(${formatTimestamp(dueTime)} instead of ${formatTimestamp(nextExecutionTime)}); the ones in between are dropped",
                            "INFO"
                        )
                    }

                    LogManager.aiSession(
                        "RoutineScheduler: Routine ${routine.id} is due (scheduled=${formatTimestamp(dueTime)}, now=${formatTimestamp(now)})",
                        "INFO"
                    )
                    candidates.add(
                        ScheduleCandidate(
                            routineId = routine.id,
                            scheduledTime = dueTime,
                            action = CandidateAction.CREATE,
                            sessionId = null
                        )
                    )
                } else {
                    LogManager.aiSession(
                        "RoutineScheduler: Routine ${routine.id} not yet due (next=${formatTimestamp(nextExecutionTime)}, now=${formatTimestamp(now)})",
                        "DEBUG"
                    )
                }
            }

            // Sort candidates by scheduled time (oldest first)
            val sortedCandidates = candidates.sortedBy { it.scheduledTime }

            // Return first candidate or None
            return when {
                sortedCandidates.isEmpty() -> {
                    LogManager.aiSession("RoutineScheduler: No candidates to execute", "DEBUG")
                    NextSession.None
                }
                else -> {
                    val first = sortedCandidates.first()
                    LogManager.aiSession(
                        "RoutineScheduler: Selected ${first.action} for routine ${first.routineId} " +
                        "(scheduled=${formatTimestamp(first.scheduledTime)}${if (first.sessionId != null) ", sessionId=${first.sessionId}" else ""})",
                        "INFO"
                    )
                    when (first.action) {
                        CandidateAction.RESUME -> NextSession.Resume(first.sessionId!!)
                        CandidateAction.CREATE -> NextSession.Create(first.routineId, first.scheduledTime)
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.aiSession("RoutineScheduler: Error calculating next session: ${e.message}", "ERROR", e)
            return NextSession.None
        }
    }

    /**
     * Where the search for the next occurrence starts.
     *
     * The base is the last completed run, or the routine's last modification, whichever is
     * later: a config change must not drag it back through occurrences that predate the change.
     * With neither, the schedule's start date, or now.
     *
     * The catch-up window then pulls that start forward. An occurrence older than the window is
     * skipped, so starting before it would mean walking to each one only to drop it -- which is
     * how 47 days of missed runs became 47 sessions. Skipped occurrences leave this log line and
     * nothing else: the history is made of sessions, and an empty "skipped" one would be a shape
     * to handle in every screen that reads it.
     */

    /**
     * Internal candidate for scheduling decision
     */
    private data class ScheduleCandidate(
        val routineId: String,
        val scheduledTime: Long,
        val action: CandidateAction,
        val sessionId: String?  // For RESUME, null for CREATE
    )

    /**
     * Internal action type
     */
    private enum class CandidateAction {
        RESUME,  // Resume existing incomplete session
        CREATE   // Create new scheduled session
    }

    /**
     * When the routine [routineId] runs next, as getNextSession will start it; null when it
     * will not run (disabled, without a schedule, its schedule ended). Read by routines.next_execution.
     */
    suspend fun nextExecutionAt(routineId: String): Long? {
        val routine = aiDao.getRoutineById(routineId)
            ?: throw IllegalArgumentException("Routine not found: $routineId")
        if (!routine.isEnabled) return null
        val scheduleJson = routine.scheduleJson?.takeIf { it.isNotEmpty() } ?: return null
        // Checked when written (RoutineService.withSettings): a schedule unreadable here is corrupt
        val schedule = json.decodeFromString<ScheduleConfig>(scheduleJson)

        // Same start of search as getNextSession, so the screen announces the run that will
        // actually happen and not one the window has already ruled out
        val lastCompletedSession = aiDao.getLastCompletedRoutineSession(routineId)
        val fromTimestamp = catchUpSearchStart(routine, schedule, lastCompletedSession, System.currentTimeMillis())
        return ScheduleCalculator.calculateNextExecution(pattern = schedule.pattern, fromTimestamp = fromTimestamp)
            .also { LogManager.aiSession("RoutineScheduler: next execution of $routineId from ${formatTimestamp(fromTimestamp)}: ${it?.let(::formatTimestamp)}", "DEBUG") }
    }
}

/**
 * Result of getNextSession() calculation
 * Tells AISessionScheduler what to do (if anything)
 */
sealed class NextSession {
    /**
     * Resume existing incomplete session (crash, network error)
     * NO system message should be added - transparent resume
     */
    data class Resume(val sessionId: String) : NextSession()

    /**
     * Create new scheduled session for this routine
     */
    data class Create(
        val routineId: String,
        val scheduledFor: Long  // Timestamp for scheduledExecutionTime
    ) : NextSession()

    /**
     * Nothing to execute right now
     */
    object None : NextSession()
}
