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

    /**
     * A goal config written whole by the AI without the settings keeps them: the validation closed,
     * an entered criterion's mark, in a sub-goal too. Sent open, they are left to be refused.
     */
    @Test
    fun whatIsLeftOutStaysClosed() {
        val before = JSONObject("""{"ai_no_validation":true,
            "criteria":[{"key":"c_sleep","name":"Sommeil","field":{"type":"DURATION","ai_read_only":true}}],
            "sub_goals":[{"name":"Corps","criteria":[{"key":"c_walk","name":"Marche","field":{"type":"BOOLEAN","ai_read_only":true}}]}]}""")
        val leftOut = GoalToolType.keepClosedToAi(before, JSONObject("""{
            "criteria":[{"key":"c_sleep","name":"Sommeil la nuit","field":{"type":"DURATION"}}],
            "sub_goals":[{"name":"Corps","criteria":[{"key":"c_walk","name":"Marche","field":{"type":"BOOLEAN"}}]}]}"""))
        assertTrue(leftOut.getBoolean(GoalToolType.AI_NO_VALIDATION))
        assertTrue(leftOut.getJSONArray("criteria").getJSONObject(0).getJSONObject("field").getBoolean("ai_read_only"))
        assertTrue(leftOut.getJSONArray("sub_goals").getJSONObject(0).getJSONArray("criteria").getJSONObject(0).getJSONObject("field").getBoolean("ai_read_only"))

        val sentOpen = GoalToolType.keepClosedToAi(before, JSONObject().put(GoalToolType.AI_NO_VALIDATION, false))
        assertTrue(GoalToolType.validationOpened(before, sentOpen))
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
