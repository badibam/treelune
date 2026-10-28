package app.treelune.core.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An event a step of the round sends when it ends is taken only in that step's phase: late or
 * doubled, it would start a call or a step over again. Seen on a phone: a routine that had
 * confirmed its end got a second answer during its closure, read as a first end to confirm.
 */
class AIStateMachineLateEventTest {

    private fun on(state: AIState, event: AIEvent) = AIStateMachine.transition(state, event, testLimits, T1)

    @Test
    fun anAnswerDuringTheClosureIsIgnored() {
        val closing = routineAt(Phase.AWAITING_SESSION_CLOSURE)
        assertSame(closing, on(closing, AIEvent.AIResponseParsed(aiMessage(completed = true))))
        assertSame(closing, on(closing, AIEvent.AIResponseReceived("{}")))
    }

    @Test
    fun aContinuationOutsideItsPreparationStartsNoCall() {
        val closing = routineAt(Phase.AWAITING_SESSION_CLOSURE)
        assertSame(closing, on(closing, AIEvent.ContinuationReady))
        assertEquals(Phase.CALLING_AI, on(routineAt(Phase.PREPARING_CONTINUATION), AIEvent.ContinuationReady).phase)
    }

    @Test
    fun aRetryOutsideItsWaitStartsNoCall() {
        val calling = routineAt(Phase.CALLING_AI)
        assertSame(calling, on(calling, AIEvent.RetryScheduled))
        assertSame(calling, on(calling, AIEvent.NetworkRetryScheduled))
        assertEquals(Phase.CALLING_AI, on(routineAt(Phase.WAITING_NETWORK_RETRY), AIEvent.NetworkRetryScheduled).phase)
    }

    @Test
    fun anAnswerIsTakenInItsOwnPhase() {
        assertEquals(Phase.PARSING_AI_RESPONSE, on(routineAt(Phase.CALLING_AI), AIEvent.AIResponseReceived("{}")).phase)
        assertEquals(Phase.PREPARING_CONTINUATION, on(routineAt(Phase.PARSING_AI_RESPONSE), AIEvent.AIResponseParsed(aiMessage(completed = true))).phase)
    }
}
