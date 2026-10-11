package app.treelune.core.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An event a step of the round sends when it ends is taken only in that step's phase: late or
 * doubled, it would start a call or a step over again. Seen on a phone: an automation that had
 * confirmed its end got a second answer during its closure, read as a first end to confirm.
 */
class AIStateMachineLateEventTest {

    private fun on(state: AIState, event: AIEvent) = AIStateMachine.transition(state, event, testLimits, T1)

    @Test
    fun anAnswerDuringTheClosureIsIgnored() {
        val closing = automationAt(Phase.AWAITING_SESSION_CLOSURE)
        assertSame(closing, on(closing, AIEvent.AIResponseParsed(aiMessage(completed = true))))
        assertSame(closing, on(closing, AIEvent.AIResponseReceived("{}")))
    }

    @Test
    fun aContinuationOutsideItsPreparationStartsNoCall() {
        val closing = automationAt(Phase.AWAITING_SESSION_CLOSURE)
        assertSame(closing, on(closing, AIEvent.ContinuationReady))
        assertEquals(Phase.CALLING_AI, on(automationAt(Phase.PREPARING_CONTINUATION), AIEvent.ContinuationReady).phase)
    }

    @Test
    fun aRetryOutsideItsWaitStartsNoCall() {
        val calling = automationAt(Phase.CALLING_AI)
        assertSame(calling, on(calling, AIEvent.RetryScheduled))
        assertSame(calling, on(calling, AIEvent.NetworkRetryScheduled))
        assertEquals(Phase.CALLING_AI, on(automationAt(Phase.WAITING_NETWORK_RETRY), AIEvent.NetworkRetryScheduled).phase)
    }

    @Test
    fun anAnswerIsTakenInItsOwnPhase() {
        assertEquals(Phase.PARSING_AI_RESPONSE, on(automationAt(Phase.CALLING_AI), AIEvent.AIResponseReceived("{}")).phase)
        assertEquals(Phase.PREPARING_CONTINUATION, on(automationAt(Phase.PARSING_AI_RESPONSE), AIEvent.AIResponseParsed(aiMessage(completed = true))).phase)
    }
}
