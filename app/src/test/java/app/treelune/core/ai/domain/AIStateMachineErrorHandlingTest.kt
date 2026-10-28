package app.treelune.core.ai.domain

import app.treelune.core.ai.data.SessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers what happens when a call to the provider does not come back usable:
 * ProviderErrorOccurred, NetworkErrorOccurred, NetworkRetryScheduled, NetworkAvailable.
 *
 * The split runs through all of it. A CHAT has someone sitting in front of it, so it hands
 * control back and lets them decide. An AUTOMATION has nobody, so it either retries by
 * itself or gives up and closes.
 */
class AIStateMachineErrorHandlingTest {

    // ==================== The provider refused ====================

    /**
     * A provider error is one that repeating the call will not fix -- bad configuration, a
     * rate limit, exhausted credit. An AUTOMATION cannot act on any of those, so it closes.
     */
    @Test
    fun providerError_closesAnRoutine() {
        val state = AIStateMachine.transition(
            state = routineAt(Phase.CALLING_AI),
            event = AIEvent.ProviderErrorOccurred("no credit left"),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CLOSED, state.phase)
        assertEquals(SessionEndReason.ERROR, state.endReason)
    }

    /**
     * A CHAT stays open on the same error: the user can go and fix the configuration, then
     * carry on in the same conversation.
     */
    @Test
    fun providerError_leavesAChatOpen() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.CALLING_AI),
            event = AIEvent.ProviderErrorOccurred("no credit left"),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.IDLE, state.phase)
        assertNull(state.endReason)
    }

    // ==================== The network dropped ====================

    /** An AUTOMATION waits the network out rather than failing on it. */
    @Test
    fun networkError_sendsAnRoutineToWait() {
        val state = AIStateMachine.transition(
            state = routineAt(Phase.CALLING_AI),
            event = AIEvent.NetworkErrorOccurred(attempt = 0),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.WAITING_NETWORK_RETRY, state.phase)
        assertNull(state.endReason)
    }

    /** A CHAT hands control back instead: the user retries when it suits them. */
    @Test
    fun networkError_returnsControlInAChat() {
        val state = AIStateMachine.transition(
            state = chatAt(Phase.CALLING_AI),
            event = AIEvent.NetworkErrorOccurred(attempt = 0),
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.IDLE, state.phase)
        assertNull(state.endReason)
    }

    /** Waiting out the delay: the call goes out again. */
    @Test
    fun networkRetryScheduled_callsAgain() {
        val state = AIStateMachine.transition(
            state = routineAt(Phase.WAITING_NETWORK_RETRY),
            event = AIEvent.NetworkRetryScheduled,
            limits = testLimits,
            currentTime = T1
        )

        assertEquals(Phase.CALLING_AI, state.phase)
    }

    /**
     * The network coming back cuts the wait short and banks the outage it ends, so that time
     * does not count against the routine's ten minutes of working time.
     */
    @Test
    fun networkAvailable_callsAgainAndBanksTheOutage() {
        val offline = AIStateMachine.transition(
            state = routineAt(Phase.CALLING_AI),
            event = AIEvent.NetworkErrorOccurred(0),
            limits = testLimits,
            currentTime = T1
        )
        assertEquals(T1, offline.networkLostAt)

        val backOnline = AIStateMachine.transition(
            state = offline,
            event = AIEvent.NetworkAvailable,
            limits = testLimits,
            currentTime = T1 + 60_000L
        )

        assertEquals(Phase.CALLING_AI, backOnline.phase)
        assertEquals(60_000L, backOnline.networkDownTime)
        assertEquals("the outage is over", 0L, backOnline.networkLostAt)
    }

    /** A retry that fails again keeps the outage running rather than restarting its clock. */
    @Test
    fun aFailedRetry_keepsTheSameOutageRunning() {
        var state = AIStateMachine.transition(
            state = routineAt(Phase.CALLING_AI),
            event = AIEvent.NetworkErrorOccurred(0),
            limits = testLimits,
            currentTime = T1
        )
        state = AIStateMachine.transition(state, AIEvent.NetworkRetryScheduled, testLimits, T1 + 30_000L)
        state = AIStateMachine.transition(state, AIEvent.NetworkErrorOccurred(0), testLimits, T1 + 31_000L)

        assertEquals("the first thirty seconds are banked", 30_000L, state.networkDownTime)
        assertEquals("and a new outage is running", T1 + 31_000L, state.networkLostAt)
    }

    /** Retrying is not a roundtrip: the counter moves on answers, not on attempts. */
    @Test
    fun networkRetries_doNotCountRoundtrips() {
        var state = routineAt(Phase.CALLING_AI, roundtrips = 1)

        state = AIStateMachine.transition(state, AIEvent.NetworkErrorOccurred(0), testLimits, T1)
        state = AIStateMachine.transition(state, AIEvent.NetworkRetryScheduled, testLimits, T1)
        state = AIStateMachine.transition(state, AIEvent.NetworkErrorOccurred(1), testLimits, T1)
        state = AIStateMachine.transition(state, AIEvent.NetworkAvailable, testLimits, T1)

        assertEquals(1, state.totalRoundtrips)
    }

    /**
     * An AUTOMATION stops waiting once its outages add up to the limit, and closes resumable:
     * kept going, it would hold the phone awake for as long as the network stays away.
     */
    @Test
    fun anRoutineOfflineTooLong_closesResumable() {
        var state = routineAt(Phase.CALLING_AI)
        var now = T1
        // Thirty-second retries that keep failing, the way scheduleNetworkRetry drives them
        while (state.phase != Phase.CLOSED) {
            state = AIStateMachine.transition(state, AIEvent.NetworkErrorOccurred(0), testLimits, now)
            now += 30_000L
            state = AIStateMachine.transition(state, AIEvent.NetworkRetryScheduled, testLimits, now)
        }

        assertEquals(SessionEndReason.NETWORK_ERROR, state.endReason)
        assertEquals(AIStateMachine.ROUTINE_NETWORK_WAIT_LIMIT, now - T1)
    }

    /** Below the limit, the retry goes out as before. */
    @Test
    fun anRoutineOfflineBelowTheLimit_callsAgain() {
        val offline = AIStateMachine.transition(routineAt(Phase.CALLING_AI), AIEvent.NetworkErrorOccurred(0), testLimits, T1)
        val state = AIStateMachine.transition(offline, AIEvent.NetworkRetryScheduled, testLimits, T1 + AIStateMachine.ROUTINE_NETWORK_WAIT_LIMIT - 1)

        assertEquals(Phase.CALLING_AI, state.phase)
    }

    // ==================== What the timeout sees ====================

    /**
     * Waiting on the network is not idleness, and neither is working. Both report no
     * inactivity, which is how a routine that is getting on with something avoids
     * being closed by the watchdog.
     */
    @Test
    fun workingAndWaitingOnTheNetwork_reportNoInactivity() {
        val busy = listOf(
            Phase.CALLING_AI,
            Phase.EXECUTING_ENRICHMENTS,
            Phase.EXECUTING_DATA_QUERIES,
            Phase.EXECUTING_ACTIONS,
            Phase.PARSING_AI_RESPONSE,
            Phase.PREPARING_CONTINUATION,
            Phase.WAITING_NETWORK_RETRY
        )

        for (phase in busy) {
            assertEquals("at $phase", 0L, routineAt(phase).calculateInactivity(T1))
        }
    }

    /** Waiting on the user is measured from what the user last did, not from the last event. */
    @Test
    fun waitingOnTheUser_isMeasuredFromTheirLastAction() {
        val waiting = chatAt(Phase.WAITING_VALIDATION).copy(
            lastUserInteractionTime = T0,
            lastEventTime = T1
        )

        assertEquals(T1 - T0, waiting.calculateInactivity(T1))
    }

    /** Anywhere else, it is measured from the last event of any kind. */
    @Test
    fun anywhereElse_isMeasuredFromTheLastEvent() {
        val parked = chatAt(Phase.INTERRUPTED).copy(
            lastUserInteractionTime = T1,
            lastEventTime = T0
        )

        assertEquals(T1 - T0, parked.calculateInactivity(T1))
    }
}
