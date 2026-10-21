package app.treelune.core.ai.orchestration

import app.treelune.core.ai.data.AIMessage
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.ai.data.CommandResult
import app.treelune.core.ai.data.CommandStatus
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.ai.data.SessionEndReason
import app.treelune.core.ai.data.SessionMessage
import app.treelune.core.ai.data.SystemMessage
import app.treelune.core.ai.data.SystemMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The end of a routine's run (docs/AI.md, « Summary of the run »): which summary is kept, what the
 * app counts beside it, and which runs wait for the user.
 */
class RunEndTest {

    private fun ai(summary: String?, completed: Boolean = true) = SessionMessage(
        id = "ai-$summary", timestamp = 0, sender = MessageSender.AI, richContent = null, textContent = null,
        aiMessage = AIMessage("done", null, null, null, null, null, null, completed, summary),
        aiMessageJson = null, systemMessage = null
    )

    private fun result(status: CommandStatus, action: Boolean = true) =
        CommandResult("tool_data.create", status, null, null, null, isActionCommand = action)

    private fun executed(vararg results: CommandResult, type: SystemMessageType = SystemMessageType.ACTIONS_EXECUTED) = SessionMessage(
        id = "system", timestamp = 0, sender = MessageSender.SYSTEM, richContent = null, textContent = null,
        aiMessage = null, aiMessageJson = null, systemMessage = SystemMessage(type, results.toList(), "summary")
    )

    @Test
    fun theSummaryKeptIsTheOneOfTheAnswerThatConfirmed() {
        val messages = listOf(ai("first claim"), executed(), ai("confirmed"))
        assertEquals("confirmed", RunEnd.summary(messages, SessionEndReason.COMPLETED))
    }

    @Test
    fun aConfirmationWithoutSummaryKeepsNone() {
        assertNull(RunEnd.summary(listOf(ai("first claim"), ai(null)), SessionEndReason.COMPLETED))
    }

    @Test
    fun aRunNotCompletedHasNoSummary() {
        assertNull(RunEnd.summary(listOf(ai("written before the error")), SessionEndReason.ERROR))
    }

    @Test
    fun theFactsCountTheWritesOnly() {
        val facts = RunEnd.facts(listOf(
            executed(result(CommandStatus.SUCCESS), result(CommandStatus.SUCCESS), result(CommandStatus.FAILED), result(CommandStatus.CANCELLED)),
            executed(result(CommandStatus.SUCCESS, action = false), result(CommandStatus.FAILED, action = false)),
            executed(result(CommandStatus.SUCCESS), type = SystemMessageType.DATA_ADDED)
        ), SessionEndReason.COMPLETED)
        assertEquals(2, facts.written)
        assertEquals(2, facts.failed)
    }

    @Test
    fun aProblemIsAnEndOtherThanCompletedNoSummaryOrAWriteNotDone() {
        val clean = RunEnd.Facts(3, 0, SessionEndReason.COMPLETED)
        assertFalse(RunEnd.problem(clean, "all good"))
        assertTrue(RunEnd.problem(clean, null))
        assertTrue(RunEnd.problem(clean.copy(failed = 1), "all good"))
        assertTrue(RunEnd.problem(clean.copy(endReason = SessionEndReason.TIMEOUT), "all good"))
    }

    @Test
    fun aRunWaitsAsItsRoutineSays() {
        assertTrue(RunEnd.waits(AutomationSettings.NOTIFY_ALWAYS, problem = false))
        assertTrue(RunEnd.waits(AutomationSettings.NOTIFY_ON_PROBLEM, problem = true))
        assertFalse(RunEnd.waits(AutomationSettings.NOTIFY_ON_PROBLEM, problem = false))
        assertFalse(RunEnd.waits(AutomationSettings.NOTIFY_NEVER, problem = true))
    }
}
