package app.treelune.tools.path

import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A Path through the dispatcher, as a screen or the AI calls it (docs/design/progression.md): its
 * grammar's rules checked when written, `self` typed by their kind and the Path's own views read
 * through "self"; its stages written by its operations alone, an ordinary write refused; a level
 * passed by the judge once another tool's entries reach its threshold, after their write; a
 * manual state noted and removed, `note_if` holding; a run stopped, its states frozen, then a new
 * one opened with the plan copied; the view `stages` read by the core's reading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PathServiceTest {

    private val context = RuntimeEnvironment.getApplication()
    private val coordinator = Coordinator(context)

    private suspend fun ok(action: String, params: Map<String, Any?>): Map<String, Any?> {
        val result = coordinator.processUserAction(action, params)
        assertTrue("$action: ${result.error}", result.isSuccess)
        @Suppress("UNCHECKED_CAST")
        return result.data as Map<String, Any?>
    }

    private fun json(text: String): Map<String, Any?> = app.treelune.core.utils.JsonUtils.toMap(JSONObject(text))

    @Test
    fun `a sequence of levels, each passed by the judge at its threshold of practice, its runs closed and copied`() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Konnakol"))["zone_id"] as String
        val practice = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to "tracking",
            "config" to mapOf("name" to "Practice", "type" to "numeric", "value" to mapOf("decimals" to 0), "units" to emptyList<String>())))["tool_instance_id"] as String
        val previous = """{"left": {"field": "id"}, "op": "in", "right": {"self": "previous"}}"""
        val passed = """{"left": {"field": "data.state"}, "op": "in", "right": {"constant": ["passed"]}}"""
        fun stages(filters: String) = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [$filters]}, "reduction": "COUNT"}}"""
        val config = json("""{"name": "Konnakol", "root_kind": "course", "kinds": [
            {"name": "Course", "key": "course", "contains": ["level"], "states": [{"key": "ongoing", "name": "Ongoing"}]},
            {"name": "Level", "key": "level", "plan_fields": [{"name": "threshold", "display_name": "Threshold", "type": "NUMERIC", "config": {"decimals": 0}}], "states": [
                {"key": "mastered", "name": "Mastered", "manual": true, "note_if": {"left": {"self": "state"}, "op": "in", "right": {"constant": ["passed"]}}},
                {"key": "passed", "name": "Passed", "record": true, "when": {"all": [
                    {"left": {"self": "state"}, "op": "in", "right": {"constant": ["current"]}},
                    {"left": {"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "$practice"}}, "reduction": "COUNT"}}, "op": ">=", "right": {"self": "threshold"}}]}},
                {"key": "current", "name": "Current", "when": {"left": ${stages("$previous, $passed")}, "op": "=", "right": ${stages(previous)}}},
                {"key": "locked", "name": "Locked"}]}]}""")
        val path = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config))["tool_instance_id"] as String

        // A rule reading `self` where the kind has no such field is refused when written
        val wrong = JSONObject(JSONObject(config).toString())
        wrong.getJSONArray("kinds").getJSONObject(1).getJSONArray("states").getJSONObject(1).getJSONObject("when").getJSONArray("all")
            .getJSONObject(1).put("right", JSONObject().put("self", "limit"))
        assertFalse(coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to path, "config" to app.treelune.core.utils.JsonUtils.toMap(wrong))).isSuccess)

        val run = ok("path.start", mapOf("tool_instance_id" to path, "name" to "Spring"))["run"] as String
        val ids = ok("path.write_plan", mapOf("tool_instance_id" to path, "stages" to listOf(
            mapOf("key" to "n1", "kind" to "level", "name" to "Level 1", "fields" to mapOf("threshold" to 2)),
            mapOf("key" to "n2", "kind" to "level", "name" to "Level 2", "fields" to mapOf("threshold" to 4))
        )))["ids"] as Map<*, *>
        val n1 = ids["n1"] as String
        val n2 = ids["n2"] as String

        suspend fun states(): Map<String, Any?> = (ok("path.state", mapOf("tool_instance_id" to path))["stages"] as List<*>)
            .map { it as Map<*, *> }.associate { it["id"] as String to (it["data"] as Map<*, *>)["state"] }
        assertEquals("current", states()[n1])
        assertEquals("locked", states()[n2])

        // An ordinary write of a stage is refused, saying what to do
        val written = coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to path, "name" to "Level 3"))
        assertFalse(written.isSuccess)
        assertTrue(written.error, written.error!!.contains("write_plan"))

        // Two sessions of practice, written by the user: the judge writes the first level passed, the second opens
        repeat(2) { ok("tool_data.create", mapOf("tool_instance_id" to practice, "name" to "Session", "data" to mapOf("value" to 1))) }
        assertEquals("passed", states()[n1])
        assertEquals("current", states()[n2])

        // A manual state its note_if allows, noted then removed; refused where it does not hold
        assertFalse(coordinator.processUserAction("path.note", mapOf("tool_instance_id" to path, "stage" to n2, "state" to "mastered")).isSuccess)
        ok("path.note", mapOf("tool_instance_id" to path, "stage" to n1, "state" to "mastered"))
        assertEquals("mastered", states()[n1])
        ok("path.unnote", mapOf("tool_instance_id" to path, "stage" to n1))
        assertEquals("passed", states()[n1])

        // The view read by the core: one level passed
        val count = ok("readings.read", mapOf("selection" to json("""{"target": {"kind": "TOOL_INSTANCE", "id": "$path"}, "view": "stages",
            "filters": [$passed], "sort": [{"field": "data.position", "direction": "asc"}]}"""), "reduction" to "COUNT", "reference" to System.currentTimeMillis()))
        assertEquals(1, (count["value"] as Number).toInt())

        // Stopped: its states frozen; a new run copies the plan, its thresholds, without the moves
        ok("path.stop", mapOf("tool_instance_id" to path))
        val next = ok("path.start", mapOf("tool_instance_id" to path))["run"] as String
        val read = PathRead.of(context, path)!!
        assertEquals("passed", read.stage(n1)!!.finalState)
        val copied = read.stages.filter { it.run == next && !it.isRoot }.sortedBy { it.position }
        assertEquals(listOf("Level 1", "Level 2"), copied.map { it.name })
        assertEquals(listOf(2, 4), copied.map { (it.fields["threshold"] as Number).toInt() })
        assertTrue(read.isClosed(read.stage(run)!!))
        // No move copied; the practice already counting 2, the judge wrote the copy's first level passed at once
        assertEquals(listOf("passed"), copied[0].moves.map { it.state })
        assertTrue(copied[1].moves.isEmpty())
        assertEquals("passed", states()[copied[0].id])
        assertEquals("current", states()[copied[1].id])
    }

    @Test
    fun `a run opened on its schedule by the judge, once, ending at the next start or its length, a chained run opened as soon as the one before closes, its states told once`() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Habits"))["zone_id"] as String
        val done = """{"left": {"field": "data.state"}, "op": "in", "right": {"constant": ["done"]}}"""
        val children = """{"left": {"field": "data.parent"}, "op": "in", "right": {"self": "id"}}"""
        fun count(filters: String) = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [$filters]}, "reduction": "COUNT"}}"""
        fun config(opens: String, lasts: Long? = null) = json("""{"name": "Day", "root_kind": "day", "run_opens": "$opens", ${lasts?.let { "\"run_lasts\": $it," } ?: ""}
            "schedule": {"pattern": {"type": "DailyMultiple", "times": ["06:00"]}}, "kinds": [
            {"name": "Day", "key": "day", "contains": ["task"], "states": [
                {"key": "finished", "name": "Finished", "closes_run": true, "when": {"all": [
                    {"left": ${count("$children, $done")}, "op": "=", "right": ${count(children)}},
                    {"left": ${count(children)}, "op": ">", "right": {"constant": 0}}]}},
                {"key": "ongoing", "name": "Ongoing"}]},
            {"name": "Task", "key": "task", "states": [{"key": "done", "name": "Done", "manual": true, "notify": true}, {"key": "todo", "name": "To do"}]}]}""")

        // Each day at 06:00: the judge opens the run of the last start come since the Path was
        // made (two days ago here), ending at the next start, and opens no second one
        val now = System.currentTimeMillis()
        val pattern = app.treelune.core.utils.SchedulePattern.DailyMultiple(listOf("06:00"))
        val lastStart = app.treelune.core.utils.ScheduleCalculator.calculateNextExecution(pattern, now - 24 * 3600_000L)!!
        val nextStart = app.treelune.core.utils.ScheduleCalculator.calculateNextExecution(pattern, lastStart)!!
        suspend fun scheduledPath(lasts: Long?): String {
            val id = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config("scheduled", lasts)))["tool_instance_id"] as String
            val dao = app.treelune.core.database.AppDatabase.getDatabase(context).toolInstanceDao()
            dao.updateToolInstance(dao.getToolInstanceById(id)!!.copy(created_at = now - 2 * 24 * 3600_000L))
            ok("path.judge", mapOf("tool_instance_id" to id))
            ok("path.judge", mapOf("tool_instance_id" to id))
            return id
        }
        val daily = PathRead.of(context, scheduledPath(null))!!.runs()
        assertEquals(1, daily.size)
        assertEquals(lastStart, daily.single().moves.first().at)
        assertEquals(nextStart, daily.single().plannedEnd)
        // A length beyond the next start: the next start closes the run all the same
        assertEquals(nextStart, PathRead.of(context, scheduledPath(3 * 24 * 3600_000L))!!.runs().single().plannedEnd)
        // A length short of it: the run closes at its end
        assertEquals(lastStart + 3600_000L, PathRead.of(context, scheduledPath(3600_000L))!!.runs().single().plannedEnd)

        // A run opened on a schedule without one: refused
        val refused = coordinator.processUserAction("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE,
            "config" to config("scheduled") - "schedule"))
        assertFalse(refused.isSuccess)

        // Chained: the root finished closes the run, and the next opens at once with the plan copied
        val chained = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config("chained")))["tool_instance_id"] as String
        val first = ok("path.start", mapOf("tool_instance_id" to chained, "name" to "Lap"))["run"] as String
        val task = (ok("path.write_plan", mapOf("tool_instance_id" to chained, "stages" to listOf(mapOf("key" to "t", "kind" to "task", "name" to "Stretch"))))["ids"] as Map<*, *>)["t"] as String
        // A stage seen for the first time tells the state it is in, once
        assertEquals("todo", PathRead.of(context, chained)!!.entity(task)!!.state!!.let { JSONObject(it).optString(PathStages.TOLD) })
        ok("path.note", mapOf("tool_instance_id" to chained, "stage" to task, "state" to "done"))
        val read = PathRead.of(context, chained)!!
        assertEquals("done", read.entity(task)!!.state!!.let { JSONObject(it).optString(PathStages.TOLD) })
        assertTrue(read.isClosed(read.stage(first)!!))
        assertEquals("finished", read.stage(first)!!.finalState)
        val next = read.currentRun()!!
        assertTrue(next.id != first)
        assertEquals(listOf("Stretch"), read.stages.filter { it.run == next.id && !it.isRoot }.map { it.name })
    }

    @Test
    fun `links looping freely while no rule reads their type, a config coming to read it refused, the loop named`() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Guitar"))["zone_id"] as String
        fun config(rule: String?) = json("""{"name": "Guitar", "root_kind": "tree", "link_types": [{"key": "see", "name": "See also"}], "kinds": [
            {"name": "Tree", "key": "tree", "contains": ["skill"], "states": [{"key": "open", "name": "Open"}]},
            {"name": "Skill", "key": "skill", "states": [{"key": "done", "name": "Done", "manual": true}, ${rule?.let { "{\"key\": \"ready\", \"name\": \"Ready\", \"when\": $it}," } ?: ""} {"key": "todo", "name": "To do"}]}]}""")
        val path = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config(null)))["tool_instance_id"] as String
        ok("path.start", mapOf("tool_instance_id" to path, "name" to "Spring"))
        // Each waits for the other through « See also », which no rule reads: drawn, never computed
        ok("path.write_plan", mapOf("tool_instance_id" to path, "stages" to listOf(
            mapOf("key" to "a", "kind" to "skill", "name" to "Chords", "links" to listOf(mapOf("from" to "b", "type" to "see"))),
            mapOf("key" to "b", "kind" to "skill", "name" to "Scales", "links" to listOf(mapOf("from" to "a", "type" to "see")))
        )))
        val linked = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [
            {"left": {"field": "id"}, "op": "in", "right": {"self": "links", "type": "see"}},
            {"left": {"field": "data.state"}, "op": "in", "right": {"constant": ["done"]}}]}, "reduction": "COUNT"}}"""
        val refused = coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to path,
            "config" to config("""{"left": $linked, "op": ">", "right": {"constant": 0}}""")))
        assertFalse(refused.isSuccess)
        assertTrue(refused.error, refused.error!!.contains("Chords") && refused.error!!.contains("Scales"))
    }

    @Test
    fun `a run reaching its end with confirm awaits its validation, the tile marked, until validated`() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Running"))["zone_id"] as String
        val config = json("""{"name": "Week", "root_kind": "week", "confirm": true, "notify_validation": true, "run_lasts": 1, "kinds": [
            {"name": "Week", "key": "week", "contains": ["run"], "states": [{"key": "ongoing", "name": "Ongoing"}]},
            {"name": "Run", "key": "run", "states": [{"key": "done", "name": "Done", "manual": true}, {"key": "todo", "name": "To do"}]}]}""")
        val path = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config))["tool_instance_id"] as String
        val run = ok("path.start", mapOf("tool_instance_id" to path, "name" to "Week 1"))["run"] as String
        Thread.sleep(5)
        // Its end past: the judge leaves it open, awaiting, rather than freezing it
        ok("path.judge", mapOf("tool_instance_id" to path))
        val read = PathRead.of(context, path)!!
        assertFalse(read.isClosed(read.stage(run)!!))
        assertEquals(listOf(run), read.awaiting().map { it.id })
        val waiting = coordinator.processUserAction("tools.waiting", emptyMap())
        assertEquals("the tile's dot", 1, (waiting.data!!["tools"] as Map<*, *>)[path])

        ok("path.validate", mapOf("tool_instance_id" to path, "run" to run))
        val validated = PathRead.of(context, path)!!
        assertTrue(validated.isClosed(validated.stage(run)!!))
        assertTrue(validated.awaiting().isEmpty())
        assertEquals("ongoing", validated.stage(run)!!.finalState)
        val after = coordinator.processUserAction("tools.waiting", emptyMap())
        assertEquals("no dot once validated", 0, (after.data!!["tools"] as Map<*, *>)[path])
    }

    @Test
    fun `a kind's readout computed on each stage with self, its short form and its gauge, a name no value has refused`() = runBlocking {
        val zone = ok("zones.create", mapOf("name" to "Drawing"))["zone_id"] as String
        fun count(filters: String) = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [$filters]}, "reduction": "COUNT"}}"""
        val content = """{"left": {"field": "data.parent"}, "op": "in", "right": {"self": "id"}}"""
        val done = """{"left": {"field": "data.state"}, "op": "in", "right": {"constant": ["done"]}}"""
        fun config(core: String) = json("""{"name": "Cycle", "root_kind": "cycle", "kinds": [
            {"name": "Cycle", "key": "cycle", "contains": ["week"], "states": [{"key": "ongoing", "name": "Ongoing"}]},
            {"name": "Week", "key": "week", "contains": ["session"], "states": [{"key": "open", "name": "Open"}],
             "readout": {"values": [{"name": "done", "value": ${count("$content, $done")}}, {"name": "total", "value": ${count(content)}}],
                         "core": "$core", "gauge": "done / total"}},
            {"name": "Session", "key": "session", "states": [{"key": "done", "name": "Done", "manual": true}, {"key": "todo", "name": "To do"}]}]}""")
        // A short form naming no value of the readout is refused when written
        assertFalse(coordinator.processUserAction("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config("{done} / {all}"))).isSuccess)
        val path = ok("tools.create", mapOf("zone_id" to zone, "tooltype" to PathRows.TOOLTYPE, "config" to config("{done} / {total}")))["tool_instance_id"] as String
        ok("path.start", mapOf("tool_instance_id" to path, "name" to "July"))
        val ids = ok("path.write_plan", mapOf("tool_instance_id" to path, "stages" to listOf(mapOf("key" to "w", "kind" to "week", "name" to "Week 1", "content" to listOf(
            mapOf("key" to "a", "kind" to "session", "name" to "Session 1"), mapOf("key" to "b", "kind" to "session", "name" to "Session 2"))))))["ids"] as Map<*, *>
        ok("path.note", mapOf("tool_instance_id" to path, "stage" to ids["a"], "state" to "done"))
        val read = PathRead.of(context, path)!!
        val now = System.currentTimeMillis()
        val outcome = read.replay(now)
        val readouts = read.readouts(read.stages, outcome, now)
        val week = readouts.getValue(ids["w"] as String)
        assertEquals("1 / 2", week.core)
        assertEquals(0.5, week.gauge!!, 1e-9)
        // A kind without a readout has none
        assertFalse(readouts.containsKey(ids["a"]))
    }
}
