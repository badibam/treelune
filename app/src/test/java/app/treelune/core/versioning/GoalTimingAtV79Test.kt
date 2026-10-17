package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/** Covers the v79 rewrite: a goal chooses once or recurring, and keeps only what its scheduler reads, its attempts ending where they did. */
class GoalTimingAtV79Test {

    private val schedule = """{ "pattern": { "type": "WeeklySimple", "days_of_week": [1], "time": "00:00" } }"""

    @Test
    fun aGoalWithAScheduleIsRecurringWithoutStartNorDeadline() {
        val config = JSONObject("""{ "start": 1000, "deadline": 5000, "duration": 604800000, "schedule": $schedule }""")

        val next = GoalTimingAtV79.config("goal", config, createdAt = 0)

        assertEquals(GoalTimingAtV79.RECURRING, next.getString(GoalTimingAtV79.RECURRENCE))
        assertFalse(next.has("start"))
        assertFalse(next.has("deadline"))
        assertEquals(604800000L, next.getLong("duration"))
    }

    @Test
    fun aOneOffGoalsDurationBecomesItsDeadline() {
        val next = GoalTimingAtV79.config("goal", JSONObject("""{ "start": 1000, "duration": 500 }"""), createdAt = 0)

        assertEquals(GoalTimingAtV79.ONCE, next.getString(GoalTimingAtV79.RECURRENCE))
        assertEquals(1500L, next.getLong("deadline"))
        assertFalse(next.has("duration"))
    }

    @Test
    fun withoutAStartTheDurationRunsFromTheCreation() {
        val next = GoalTimingAtV79.config("goal", JSONObject("""{ "duration": 500 }"""), createdAt = 200)

        assertEquals(700L, next.getLong("deadline"))
        assertFalse(next.has("start"))
    }

    @Test
    fun aDeadlineGivenWinsOverTheDuration() {
        val next = GoalTimingAtV79.config("goal", JSONObject("""{ "start": 1000, "deadline": 9000, "duration": 500 }"""), createdAt = 0)

        assertEquals(9000L, next.getLong("deadline"))
        assertFalse(next.has("duration"))
    }

    @Test
    fun aGoalAlreadyChosenAndAnotherToolAreLeftAsTheyAre() {
        val chosen = JSONObject("""{ "recurrence": "once", "duration": 500 }""")
        assertSame(chosen, GoalTimingAtV79.config("goal", chosen, createdAt = 0))
        val notes = JSONObject("""{ "duration": 500 }""")
        assertSame(notes, GoalTimingAtV79.config("notes", notes, createdAt = 0))
    }

    @Test
    fun theBackupsGoalsAreRewritten() {
        val backup = JSONObject().put("tool_instances", JSONArray().put(JSONObject()
            .put("id", "g").put("tooltype", "goal").put("created_at", 100L).put("config_json", """{ "duration": 50 }""")))

        GoalTimingAtV79.backup(backup)

        val config = JSONObject(backup.getJSONArray("tool_instances").getJSONObject(0).getString("config_json"))
        assertEquals(GoalTimingAtV79.ONCE, config.getString(GoalTimingAtV79.RECURRENCE))
        assertEquals(150L, config.getLong("deadline"))
    }
}
