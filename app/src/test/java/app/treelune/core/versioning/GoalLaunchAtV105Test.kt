package app.treelune.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** Covers the v105 rewrite: the choice of a recurrence gone, a one-off goal opening where it would have, or launched by hand once its attempt is there. */
class GoalLaunchAtV105Test {

    private val schedule = """{ "pattern": { "type": "WeeklySimple", "days_of_week": [1], "time": "00:00" } }"""

    @Test
    fun aRecurringGoalOnlyLosesTheChoice() {
        val next = GoalLaunchAtV105.config("goal", JSONObject("""{ "recurrence": "recurring", "duration": 604800000, "schedule": $schedule }"""), createdAt = 0, hasAttempts = true)

        assertFalse(next.has("recurrence"))
        assertEquals(604800000L, next.getLong("duration"))
        assertEquals(JSONObject(schedule).toString(), next.getJSONObject("schedule").toString())
    }

    @Test
    fun aOneOffGoalNotOpenedYet_takesItsStartAsAScheduleAndItsDeadlineAsADuration() {
        val next = GoalLaunchAtV105.config("goal", JSONObject("""{ "recurrence": "once", "start": 1000, "deadline": 5000 }"""), createdAt = 0, hasAttempts = false)

        assertFalse(next.has("recurrence") || next.has("start") || next.has("deadline"))
        val pattern = next.getJSONObject("schedule").getJSONObject("pattern")
        assertEquals("SpecificDates", pattern.getString("type"))
        assertEquals(1000L, pattern.getJSONArray("timestamps").getLong(0))
        assertEquals(4000L, next.getLong("duration"))
    }

    @Test
    fun withoutAStartNorADeadline_itOpensAtItsCreationWithoutAnEnd() {
        val next = GoalLaunchAtV105.config("goal", JSONObject("""{ "recurrence": "once" }"""), createdAt = 200, hasAttempts = false)

        assertEquals(200L, next.getJSONObject("schedule").getJSONObject("pattern").getJSONArray("timestamps").getLong(0))
        assertFalse(next.has("duration"))
    }

    @Test
    fun aOneOffGoalAlreadyOpened_isLaunchedByHandFromThenOn() {
        val next = GoalLaunchAtV105.config("goal", JSONObject("""{ "recurrence": "once", "start": 1000, "deadline": 5000 }"""), createdAt = 0, hasAttempts = true)

        assertFalse(next.has("schedule") || next.has("duration") || next.has("start") || next.has("deadline"))
    }

    @Test
    fun aConfigWithoutTheChoice_orAnotherTool_isLeftAsItIs() {
        val config = JSONObject("""{ "duration": 500 }""")
        assertSame(config, GoalLaunchAtV105.config("goal", config, createdAt = 0, hasAttempts = false))
        val other = JSONObject("""{ "recurrence": "once" }""")
        assertSame(other, GoalLaunchAtV105.config("tracking", other, createdAt = 0, hasAttempts = false))
    }
}
