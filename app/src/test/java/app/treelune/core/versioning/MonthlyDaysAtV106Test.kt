package app.treelune.core.versioning

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A monthly schedule's day becomes a list of days, wherever a schedule is stored. */
class MonthlyDaysAtV106Test {

    private val monthly = """{"pattern":{"type":"MonthlyRecurrent","months":[1,6],"day_of_month":15,"time":"10:00"}}"""

    @Test
    fun aMonthlySchedule_holdsItsDayAsAList() {
        val pattern = JSONObject(MonthlyDaysAtV106.rewrite(monthly)!!).getJSONObject("pattern")

        assertEquals("[15]", pattern.getJSONArray("days_of_month").toString())
        assertEquals(false, pattern.has("day_of_month"))
    }

    /** Down a config: a Messages tool's schedule, and those of an Agenda's series. */
    @Test
    fun theSchedulesOfAConfig_atAnyDepth() {
        val config = """{"name":"Agenda","series":[{"key":"a","schedule":$monthly},{"key":"b"}],"schedule":$monthly}"""
        val rewritten = JSONObject(MonthlyDaysAtV106.rewrite(config)!!)

        assertEquals("[15]", rewritten.getJSONObject("schedule").getJSONObject("pattern").getJSONArray("days_of_month").toString())
        assertEquals("[15]", rewritten.getJSONArray("series").getJSONObject(0).getJSONObject("schedule").getJSONObject("pattern").getJSONArray("days_of_month").toString())
    }

    /** Another pattern, or a config without schedule, is left as it is. */
    @Test
    fun nothingMonthly_isLeftAlone() {
        assertNull(MonthlyDaysAtV106.rewrite("""{"pattern":{"type":"WeeklySimple","days_of_week":[7],"time":"08:00"}}"""))
        assertNull(MonthlyDaysAtV106.rewrite("""{"name":"Poids"}"""))
    }

    @Test
    fun aBackup_itsToolsRoutinesAndTasks() {
        val data = JSONObject()
            .put("tool_instances", JSONArray().put(JSONObject().put("tooltype", "messages").put("config_json", """{"schedule":$monthly}""")))
            .put("automations", JSONArray().put(JSONObject().put("schedule_json", monthly)).put(JSONObject().put("schedule_json", JSONObject.NULL)))
            .put("tasks", JSONArray().put(JSONObject().put("schedule_json", monthly)))
        MonthlyDaysAtV106.backup(data)

        listOf(
            JSONObject(data.getJSONArray("tool_instances").getJSONObject(0).getString("config_json")).getJSONObject("schedule"),
            JSONObject(data.getJSONArray("automations").getJSONObject(0).getString("schedule_json")),
            JSONObject(data.getJSONArray("tasks").getJSONObject(0).getString("schedule_json"))
        ).forEach { assertEquals("[15]", it.getJSONObject("pattern").getJSONArray("days_of_month").toString()) }
        assertEquals(true, data.getJSONArray("automations").getJSONObject(1).isNull("schedule_json"))
    }

    /** The history's copy of a row, so that an undo puts back a schedule the table can read. */
    @Test
    fun aHistoryCopy() {
        val copy = MonthlyDaysAtV106.historyCopy("tasks", JSONObject().put("id", "t").put("schedule_json", monthly))

        assertEquals("[15]", JSONObject(copy.getString("schedule_json")).getJSONObject("pattern").getJSONArray("days_of_month").toString())
    }
}
