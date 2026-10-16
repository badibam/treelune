package app.treelune.tools.goal

import app.treelune.core.coordinator.Source
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what a goal keeps from the AI: its verdict, closed by a setting of the goal
 * (GoalToolType.AI_NO_VALIDATION) and checked by validate and reopen; an entered criterion's value,
 * closed by the field's own setting (AiReadOnly).
 */
class GoalAiValidationTest {

    private val closed = JSONObject().put(GoalToolType.AI_NO_VALIDATION, true)
    private val open = JSONObject()

    /** Closed, the AI, a routine and the connector are refused; a person never is. */
    @Test
    fun aClosedValidationRefusesTheAiOnly() {
        assertTrue(GoalToolType.closedToAi(closed, Source.AI))
        assertTrue(GoalToolType.closedToAi(closed, Source.EXTERNAL))
        assertFalse(GoalToolType.closedToAi(closed, Source.USER))
        assertFalse(GoalToolType.closedToAi(open, Source.AI))
        assertFalse(GoalToolType.closedToAi(open, Source.EXTERNAL))
    }

    /** Opening the validation again is a person's; closing it, or leaving it, is not opening. */
    @Test
    fun onlyOpeningIsAChangeToRefuse() {
        assertTrue(GoalToolType.validationOpened(closed, open))
        assertTrue(GoalToolType.validationOpened(closed, JSONObject().put(GoalToolType.AI_NO_VALIDATION, false)))
        assertFalse(GoalToolType.validationOpened(open, closed))
        assertFalse(GoalToolType.validationOpened(closed, closed))
        assertFalse(GoalToolType.validationOpened(open, open))
    }

    /** An entered criterion marked read-only for the AI gives its attempt's field the mark. */
    @Test
    fun anEnteredCriterionCarriesItsMark() {
        fun criterion(entered: String) = Criterion.fromJson(JSONObject(
            """{"key":"c_sleep","name":"Sommeil","field":$entered,"condition":{"op":">=","right":{"constant":25200000}}}"""))

        assertTrue(criterion("""{"type":"DURATION","ai_read_only":true}""").enteredField()!!.aiReadOnly)
        assertFalse(criterion("""{"type":"DURATION"}""").enteredField()!!.aiReadOnly)
    }
}
