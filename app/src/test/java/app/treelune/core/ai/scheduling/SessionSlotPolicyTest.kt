package app.treelune.core.ai.scheduling

import app.treelune.core.ai.data.ExecutionTrigger
import app.treelune.core.ai.data.SessionEndReason
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.domain.AIState
import app.treelune.core.ai.domain.Phase
import app.treelune.core.ai.domain.T0
import app.treelune.core.ai.domain.routineAt
import app.treelune.core.ai.domain.chatAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers who gets the one execution slot, and when its occupant has to give it up.
 *
 * Every case here fixes the clock rather than waiting on it: putting a session six minutes
 * into the past is what makes an eviction or a timeout observable at all, and it is the
 * reason these decisions were worth pulling out of AISessionScheduler, whose constructor
 * opens a database.
 */
class SessionSlotPolicyTest {

    /** A session that last did anything this long ago. */
    private fun chatIdleFor(millis: Long) = chatAt(Phase.IDLE).copy(lastEventTime = T0 - millis)

    private fun request(
        sessionType: SessionType,
        trigger: ExecutionTrigger,
        currentState: AIState,
        currentTime: Long = T0
    ) = SessionSlotPolicy.requestSession(
        sessionId = "asking-session",
        sessionType = sessionType,
        trigger = trigger,
        currentState = currentState,
        currentTime = currentTime
    )

    // ==================== A free slot ====================

    /** Nothing is running: whoever asks, starts. */
    @Test
    fun anyRequest_startsWhenTheSlotIsFree() {
        for (type in listOf(SessionType.CHAT, SessionType.ROUTINE_RUN)) {
            val result = request(type, ExecutionTrigger.MANUAL, AIState.idle())

            assertEquals(
                "for $type",
                ActivationResult.ActivateImmediate("asking-session"),
                result
            )
        }
    }

    // ==================== A CHAT asks ====================

    /** The user opening a new conversation replaces the old one, whatever it was doing. */
    @Test
    fun chat_replacesAnotherChatAtOnce() {
        val result = request(SessionType.CHAT, ExecutionTrigger.MANUAL, chatAt(Phase.CALLING_AI))

        val evict = result as ActivationResult.EvictAndActivate
        assertEquals("chat-session", evict.sessionToEvict)
        assertEquals("asking-session", evict.sessionToActivate)
        assertEquals(SessionEndReason.CANCELLED, evict.evictionReason)
    }

    /**
     * A CHAT does not cut into a running routine. It queues, and the interface asks the
     * user whether to interrupt -- that question is not settled here.
     */
    @Test
    fun chat_queuesBehindARunningRoutine() {
        val result = request(SessionType.CHAT, ExecutionTrigger.MANUAL, routineAt(Phase.CALLING_AI))

        assertEquals(ActivationResult.Enqueue("asking-session", priority = 1), result)
    }

    // ==================== A routine asks, a CHAT holds the slot ====================

    /** A conversation nobody has touched for over five minutes gives way, and is suspended. */
    @Test
    fun routine_evictsALongIdleChat() {
        for (trigger in listOf(ExecutionTrigger.MANUAL, ExecutionTrigger.SCHEDULED)) {
            val result = request(
                SessionType.ROUTINE_RUN,
                trigger,
                chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT + 1_000L)
            )

            val evict = result as ActivationResult.EvictAndActivate
            assertEquals("for $trigger", SessionEndReason.SUSPENDED, evict.evictionReason)
        }
    }

    /**
     * A conversation still in use does not. What happens then depends on who asked: someone
     * pressing the button waits in the queue, where a scheduled run simply gives up and
     * comes back at the next tick.
     */
    @Test
    fun routine_waitsOrGivesUpOnAChatStillInUse() {
        val busyChat = chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT - 1_000L)

        assertEquals(
            ActivationResult.Enqueue("asking-session", priority = 2),
            request(SessionType.ROUTINE_RUN, ExecutionTrigger.MANUAL, busyChat)
        )
        assertTrue(
            request(SessionType.ROUTINE_RUN, ExecutionTrigger.SCHEDULED, busyChat)
                is ActivationResult.Skip
        )
    }

    /**
     * A CHAT that is mid-call reports no inactivity at all, however long the call has been
     * out, so it cannot be evicted. Waiting on the provider is not the user being away.
     */
    @Test
    fun routine_cannotEvictAChatThatIsMidCall() {
        val callingForAges = chatAt(Phase.CALLING_AI)
            .copy(lastEventTime = T0 - 10 * SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT)

        val result = request(SessionType.ROUTINE_RUN, ExecutionTrigger.MANUAL, callingForAges)

        assertEquals(ActivationResult.Enqueue("asking-session", priority = 2), result)
    }

    // ==================== A routine asks, a routine holds the slot ====================

    /**
     * One routine never evicts another, however long it has been running: the watchdog
     * is what ends it. The asker queues or gives up depending on where it came from.
     */
    @Test
    fun routine_neverEvictsAnotherRoutine() {
        val running = routineAt(Phase.EXECUTING_ACTIONS)

        assertEquals(
            ActivationResult.Enqueue("asking-session", priority = 2),
            request(SessionType.ROUTINE_RUN, ExecutionTrigger.MANUAL, running)
        )
        assertTrue(
            request(SessionType.ROUTINE_RUN, ExecutionTrigger.SCHEDULED, running)
                is ActivationResult.Skip
        )
    }

    // ==================== When the occupant's time is up ====================

    /** Nothing is running, so nothing can run out of time. */
    @Test
    fun noSession_neverTimesOut() {
        assertFalse(SessionSlotPolicy.shouldTimeout(AIState.idle(), hasWaitingRoutines = true, currentTime = T0))
    }

    /**
     * A conversation left open costs nothing while nothing else wants the slot, so it is
     * only timed out when a routine is actually waiting for it.
     */
    @Test
    fun chat_onlyTimesOutWhenSomethingIsWaiting() {
        val longIdle = chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT + 1_000L)

        assertFalse(SessionSlotPolicy.shouldTimeout(longIdle, hasWaitingRoutines = false, currentTime = T0))
        assertTrue(SessionSlotPolicy.shouldTimeout(longIdle, hasWaitingRoutines = true, currentTime = T0))
    }

    /** Under five minutes, a waiting routine is not reason enough. */
    @Test
    fun chat_survivesAShortSilence() {
        val shortIdle = chatIdleFor(SessionSlotPolicy.CHAT_INACTIVITY_TIMEOUT - 1_000L)

        assertFalse(SessionSlotPolicy.shouldTimeout(shortIdle, hasWaitingRoutines = true, currentTime = T0))
    }

    /** A routine that has been going for over ten minutes is stopped. */
    @Test
    fun routine_timesOutOnTheGlobalBudget() {
        val started = routineAt(Phase.CALLING_AI)
        val tenMinutesLater = started.sessionCreatedAt + SessionSlotPolicy.ROUTINE_GLOBAL_TIMEOUT + 1_000L

        assertTrue(
            SessionSlotPolicy.shouldTimeout(started, hasWaitingRoutines = false, currentTime = tenMinutesLater)
        )
    }

    /** A routine that has stopped doing anything for two minutes is stopped too. */
    @Test
    fun routine_timesOutOnGoingQuiet() {
        // PREPARING_CONTINUATION would report no inactivity; INTERRUPTED measures from the last event.
        val quiet = routineAt(Phase.INTERRUPTED)
        val twoMinutesLater = quiet.lastEventTime + SessionSlotPolicy.AUTO_INACTIVITY_TIMEOUT + 1_000L

        assertTrue(
            SessionSlotPolicy.shouldTimeout(quiet, hasWaitingRoutines = false, currentTime = twoMinutesLater)
        )
    }

    /** A routine getting on with the work is left alone until the global budget runs out. */
    @Test
    fun routine_isLeftAloneWhileWorking() {
        val working = routineAt(Phase.EXECUTING_ACTIONS)
        val fiveMinutesLater = working.sessionCreatedAt + 5 * 60_000L

        assertFalse(
            SessionSlotPolicy.shouldTimeout(working, hasWaitingRoutines = false, currentTime = fiveMinutesLater)
        )
    }

    /** A routine waiting out a network outage is never timed out, whatever the clock says. */
    @Test
    fun routine_isNeverTimedOutWhileWaitingOnTheNetwork() {
        val offline = routineAt(Phase.WAITING_NETWORK_RETRY)
        val hoursLater = offline.sessionCreatedAt + 100 * SessionSlotPolicy.ROUTINE_GLOBAL_TIMEOUT

        assertFalse(
            SessionSlotPolicy.shouldTimeout(offline, hasWaitingRoutines = false, currentTime = hoursLater)
        )
    }

    /**
     * An outage that is over is not charged to the routine.
     *
     * The ten-minute budget is working time, not wall clock: a routine that spent eight of
     * its first nine minutes offline comes back with its budget nearly intact. Reading the
     * downtime off the current phase used to make the subtraction unreachable, since the only
     * phase carrying one is the phase shouldTimeout returns on.
     */
    @Test
    fun routine_isNotChargedForAnOutageItHasComeBackFrom() {
        val start = T0
        val outage = 8 * 60_000L
        val justPastTheWallClockBudget = start + SessionSlotPolicy.ROUTINE_GLOBAL_TIMEOUT + 1_000L

        val resumed = routineAt(Phase.CALLING_AI).copy(
            sessionCreatedAt = start,
            networkDownTime = outage,
            lastEventTime = justPastTheWallClockBudget
        )

        assertFalse(
            SessionSlotPolicy.shouldTimeout(resumed, hasWaitingRoutines = false, currentTime = justPastTheWallClockBudget)
        )
    }

    /** Once it has actually worked for ten minutes, outages aside, it is stopped. */
    @Test
    fun routine_isStoppedAfterTenMinutesOfWorkingTime() {
        val start = T0
        val outage = 8 * 60_000L
        val past = start + outage + SessionSlotPolicy.ROUTINE_GLOBAL_TIMEOUT + 1_000L

        val resumed = routineAt(Phase.CALLING_AI).copy(
            sessionCreatedAt = start,
            networkDownTime = outage,
            lastEventTime = past
        )

        assertTrue(
            SessionSlotPolicy.shouldTimeout(resumed, hasWaitingRoutines = false, currentTime = past)
        )
    }

    /** Several outages add up, rather than only the last one counting. */
    @Test
    fun routine_addsUpTheOutagesItHasSatOut() {
        val start = T0
        val outages = 3 * 60_000L + 4 * 60_000L
        val currentTime = start + outages + 9 * 60_000L

        val resumed = routineAt(Phase.CALLING_AI).copy(
            sessionCreatedAt = start,
            networkDownTime = outages,
            lastEventTime = currentTime
        )

        assertFalse(
            SessionSlotPolicy.shouldTimeout(resumed, hasWaitingRoutines = false, currentTime = currentTime)
        )
    }

    // ==================== Resuming a routine ====================

    /** One closed for the network waits out its pause, so it does not wait and close again in a loop. */
    @Test
    fun closedForTheNetwork_resumesAfterItsPause() {
        val closedAt = T0
        assertFalse(SessionSlotPolicy.resumableNow(SessionEndReason.NETWORK_ERROR, closedAt, closedAt + SessionSlotPolicy.NETWORK_RESUME_PAUSE - 1))
        assertTrue(SessionSlotPolicy.resumableNow(SessionEndReason.NETWORK_ERROR, closedAt, closedAt + SessionSlotPolicy.NETWORK_RESUME_PAUSE))
    }

    /** One cut without a reason, or suspended for a conversation, is taken up at once. */
    @Test
    fun cutOrSuspended_resumesAtOnce() {
        assertTrue(SessionSlotPolicy.resumableNow(null, T0, T0))
        assertTrue(SessionSlotPolicy.resumableNow(SessionEndReason.SUSPENDED, T0, T0))
    }
}
