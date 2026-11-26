package app.treelune.tools.path

import app.treelune.core.reading.FieldReading
import app.treelune.core.selection.TimeResolver
import app.treelune.core.terms.Term
import app.treelune.core.terms.TermReader
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The replay of a Path on the setups of its design (docs/design/progression.md, « Les montages »):
 * each state computed from the moves, in the order of computation; the judge writing a `record`
 * once; effects going off on a move, their loop stopped by the ceiling; marks read top-down; a
 * closed run computing nothing. The setups stand for the checks on the phone the engine alone
 * could not have.
 */
class PathReplayTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val day = 86_400_000L
    private val t0 = 1_767_225_600_000L // 2026-01-01 01:00 in Paris

    /** What the core's terms read: a count, by the level their filter names (`data.level`), the stage judged put in its place. */
    private val counts = mutableMapOf<String, Int>()

    private val rules = PathRules(
        pathId = PATH,
        external = { term, _ ->
            val filters = (term as Term.Reading).selection.filters
            val level = (0 until filters.length()).map { filters.getJSONObject(it) }
                .first { it.getJSONObject("left").getString("field") == "data.level" }.getJSONObject("right").getString("constant")
            TermReader.Read.Value(counts[level] ?: 0, FieldReading.COUNT_FIELD)
        },
        calendar = { TimeResolver(it, zone, 4, "MONDAY") },
        text = { it },
        own = { it }
    )

    private fun replay(config: String, stages: List<Stage>, now: Long, judge: Boolean = false) = runBlocking {
        PathReplay(PathGrammar.fromJson(JSONObject(config)), PATH, rules, { TimeResolver(it, zone, 4, "MONDAY") }, { it }, { it }).replay(stages, now, judge)
    }

    private fun stage(id: String, kind: String, parent: String?, position: Int = 0, moves: List<Move> = emptyList(), links: List<Link> = emptyList(), run: String = "root", plannedEnd: Long? = null, finalState: String? = null) =
        Stage(id, run, kind, parent, position, id, links, emptyMap(), moves, finalState, plannedEnd)

    private fun root(kind: String = "cycle", moves: List<Move> = emptyList(), id: String = "root", plannedEnd: Long? = null, finalState: String? = null) =
        stage(id, kind, null, 0, listOf(Move(t0, Move.START)) + moves, run = id, plannedEnd = plannedEnd, finalState = finalState)

    private fun Map<String, PathReplay.StageState>.of(id: String) = getValue(id).state

    @Test
    fun `a plan noted by hand, a group done when its whole content is`() {
        val stages = listOf(
            root(),
            stage("july", "month", "root", 0),
            stage("s1", "session", "july", 0, listOf(Move(t0 + day, "done"))),
            stage("s2", "session", "july", 1)
        )
        val before = replay(PLAN, stages, t0 + 2 * day)
        assertEquals("done", before.states.of("s1"))
        assertEquals("todo", before.states.of("s2"))
        assertEquals("todo", before.states.of("july"))
        // The second session noted: the month follows; its move removed, it falls back
        val noted = stages.map { if (it.id == "s2") it.copy(moves = listOf(Move(t0 + 2 * day, "done"))) else it }
        val after = replay(PLAN, noted, t0 + 3 * day)
        assertEquals("done", after.states.of("july"))
        assertEquals(t0 + 2 * day, after.states.getValue("july").since)
        assertTrue(after.problems.isEmpty())
    }

    @Test
    fun `a sequence, each level opens when the one before has passed, the first at once, a pass written once by the judge`() {
        val stages = listOf(root(kind = "suite"), stage("n1", "level", "root", 0), stage("n2", "level", "root", 1), stage("n3", "level", "root", 2))
        val start = replay(LEVELS, stages, t0 + day)
        assertEquals(listOf("current", "locked", "locked"), listOf("n1", "n2", "n3").map { start.states.of(it) })

        // Five sessions of the practice name the first level: the judge writes it passed, the second opens in the same pass
        counts["n1"] = 5
        val judged = replay(LEVELS, stages, t0 + 2 * day, judge = true)
        assertEquals(listOf("n1" to Move(t0 + 2 * day, "passed")), judged.records.filter { it.first == "n1" })
        assertEquals("passed", judged.states.of("n1"))
        assertEquals("current", judged.states.of("n2"))

        // Written, the pass stays: the count falling back changes nothing, and nothing is written twice
        counts["n1"] = 0
        val written = stages.map { stage -> judged.records.firstOrNull { it.first == stage.id }?.let { stage.copy(moves = stage.moves + it.second) } ?: stage }
        val again = replay(LEVELS, written, t0 + 3 * day, judge = true)
        assertEquals("passed", again.states.of("n1"))
        assertTrue(again.records.isEmpty())
    }

    @Test
    fun `a tree, available when every stage required is done, none required being available at once`() {
        val stages = listOf(
            root(kind = "tree"),
            stage("scales", "skill", "root", 0, listOf(Move(t0 + day, "done"))),
            stage("chords", "skill", "root", 1),
            stage("songs", "skill", "root", 2, links = listOf(Link("scales", "required"), Link("chords", "required")))
        )
        val one = replay(TREE, stages, t0 + 2 * day)
        assertEquals("available", one.states.of("chords"))
        assertEquals("locked", one.states.of("songs"))
        val both = replay(TREE, stages.map { if (it.id == "chords") it.copy(moves = listOf(Move(t0 + 2 * day, "done"))) else it }, t0 + 3 * day)
        assertEquals("available", both.states.of("songs"))
    }

    @Test
    fun `links of a type a rule reads that loop leave no state, and say which stages wait for one another`() {
        val stages = listOf(
            root(kind = "tree"),
            stage("a", "skill", "root", 0, links = listOf(Link("b", "required"))),
            stage("b", "skill", "root", 1, links = listOf(Link("a", "required")))
        )
        val outcome = replay(TREE, stages, t0 + day)
        assertEquals(1, outcome.problems.size)
        assertTrue(outcome.problems.single().message.startsWith("path_problem_loop"))
        // A type no rule reads loops freely
        val seeAlso = stages.map { it.copy(links = it.links.map { l -> l.copy(type = "see_also") }) }
        assertTrue(replay(TREE, seeAlso, t0 + day).problems.isEmpty())
    }

    @Test
    fun `an effect gives the next stage its state when a stage passes, and removing the move removes what it gave`() {
        val stages = listOf(
            root(kind = "rehab"),
            stage("p1", "step", "root", 0, listOf(Move(t0 + day, "passed"))),
            stage("p2", "step", "root", 1)
        )
        val outcome = replay(REHAB, stages, t0 + 2 * day)
        assertEquals("current", outcome.states.of("p2"))
        assertTrue(outcome.states.getValue("p2").byEffect)
        assertEquals(listOf(Move(t0 + day, "current")), outcome.virtual["p2"])
        val undone = replay(REHAB, stages.map { if (it.id == "p1") it.copy(moves = emptyList()) else it }, t0 + 2 * day)
        assertEquals("waiting", undone.states.of("p2"))
    }

    @Test
    fun `effects that give each other go off until the ceiling, which the replay names`() {
        val stages = listOf(root(kind = "loop"), stage("x", "ping", "root", 0, listOf(Move(t0 + day, "a"))))
        val outcome = replay(PING_PONG, stages, t0 + 2 * day)
        assertTrue(outcome.problems.any { it.message.startsWith("path_problem_ceiling") })
        assertTrue(outcome.virtual.getValue("x").size <= PathReplay.CEILING)
    }

    @Test
    fun `a mark reads its parent's state, computed after every state`() {
        val stages = listOf(
            root(kind = "career"),
            stage("law", "way", "root", 0, listOf(Move(t0 + day, "dropped"))),
            stage("visit", "task", "law", 0, listOf(Move(t0 + day, "done")))
        )
        val outcome = replay(CAREER, stages, t0 + 2 * day)
        assertEquals("done", outcome.states.of("visit"))
        assertEquals(listOf("in_dropped_way"), outcome.states.getValue("visit").marks)
    }

    @Test
    fun `the root's state closes its run when it says so, and failing is the end date passed`() {
        val week = t0 + 7 * day
        val stages = listOf(root(kind = "attempt", plannedEnd = week), stage("run1", "criterion", "root", 0))
        assertEquals("ongoing", replay(ATTEMPT, stages, t0 + day).states.of("root"))
        val late = replay(ATTEMPT, stages, week + day)
        assertEquals("failed", late.states.of("root"))
        assertEquals(setOf("root"), late.closes)
    }

    @Test
    fun `a closed run computes nothing, its stages frozen as they were`() {
        val stages = listOf(
            root(moves = listOf(Move(t0 + 5 * day, Move.END)), finalState = "todo"),
            stage("july", "month", "root", 0, finalState = "todo"),
            stage("s1", "session", "july", 0, listOf(Move(t0 + day, "done")), finalState = "todo")
        )
        val outcome = replay(PLAN, stages, t0 + 6 * day)
        // The journal says done, the frozen state is what counts
        assertEquals("todo", outcome.states.of("s1"))
        assertTrue(outcome.rows.all { (it["data"] as Map<*, *>)[PathRows.CLOSED] == true })
    }

    @Test
    fun `a vaccine expired once its last injection plus its own validity is past`() {
        fun vaccine(id: String, years: Int, moves: List<Move> = listOf(Move(t0, "injected"))) =
            stage(id, "vaccine", "root", 0, moves).copy(fields = mapOf("validity" to years))
        val book = listOf(root(kind = "book"), vaccine("tetanus", 10), vaccine("flu", 1))
        val later = replay(VACCINES, book, t0 + 400 * day)
        assertEquals("injected", later.states.of("tetanus"))
        assertEquals("expired", later.states.of("flu"))
        assertEquals("expired", replay(VACCINES, book, t0 + 3700 * day).states.of("tetanus"))
        assertEquals("to_do", replay(VACCINES, listOf(root(kind = "book"), vaccine("tetanus", 10, emptyList())), t0).states.of("tetanus"))
        assertEquals(emptyList<PathReplay.Problem>(), later.problems)
    }

    @Test
    fun `a date of the stage judged becomes the instant a relative date starts from, and its id a filter's written value`() {
        val of = object : PathSelf.Of {
            override val stage = stage("p2", "step", "root", 1, listOf(Move(t0 + day, "current")))
            override val kind = PathGrammar.fromJson(JSONObject(REHAB)).kind("step")!!
            override fun previous() = null
            override fun siblings() = emptyList<Stage>()
            override fun content() = emptyList<Stage>()
            override fun lastMove() = stage.moves.last()
            override fun state() = null
            override fun runStart() = t0
            override fun runEnd() = null
        }
        val filters = org.json.JSONArray("""[
            {"left": {"field": "timestamp"}, "op": ">=", "right": {"constant": {"relative": {"unit": "DAY", "offset": 0, "edge": "START"}, "from": {"self": "last_move_at"}}}},
            {"left": {"field": "data.level"}, "op": "=", "right": {"self": "id"}},
            {"left": {"field": "id"}, "op": "in", "right": {"self": "previous"}}]""")
        val written = PathSelf.substituted(filters, of) { it } as org.json.JSONArray
        assertEquals(t0 + day, written.getJSONObject(0).getJSONObject("right").getJSONObject("constant").getLong("from"))
        assertEquals("p2", written.getJSONObject(1).getJSONObject("right").getString("constant"))
        // No stage before: a list of nothing, against which no row is kept
        assertEquals(0, written.getJSONObject(2).getJSONObject("right").getJSONArray("constant").length())
    }

    private companion object {
        const val PATH = "path"
        const val PRACTICE = "practice"

        /** A count of the Path's own stages, its tool named "self" as a rule written before its tool exists names it. */
        fun stages(filters: String) = """{"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [$filters]}, "reduction": "COUNT"}}"""
        fun filter(field: String, op: String, right: String) = """{"left": {"field": "$field"}, "op": "$op", "right": $right}"""

        val CHILDREN = filter("data.parent", "in", """{"self": "id"}""")
        val CHILDREN_DONE = "$CHILDREN, ${filter("data.state", "in", """{"constant": ["done"]}""")}"

        /** Plan: sessions noted by hand, a month done when its whole content is, and it has one. */
        val PLAN = """{"root_kind": "cycle", "kinds": [
            {"key": "cycle", "contains": ["month"], "states": [{"key": "todo"}]},
            {"key": "month", "contains": ["session"], "states": [
                {"key": "done", "when": {"all": [
                    {"left": ${stages(CHILDREN_DONE)}, "op": "=", "right": ${stages(CHILDREN)}},
                    {"left": ${stages(CHILDREN)}, "op": ">", "right": {"constant": 0}}]}},
                {"key": "todo"}]},
            {"key": "session", "states": [{"key": "done", "manual": true}, {"key": "todo"}]}]}"""

        val PREVIOUS = filter("id", "in", """{"self": "previous"}""")

        /** Levels: passed when five sessions of the practice name it (record), current when the one before has passed. */
        val LEVELS = """{"root_kind": "suite", "kinds": [
            {"key": "suite", "contains": ["level"], "states": [{"key": "ongoing"}]},
            {"key": "level", "states": [
                {"key": "passed", "record": true, "when": {"left": {"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "$PRACTICE"},
                    "filters": [${filter("data.level", "=", """{"self": "id"}""")}]}, "reduction": "COUNT"}}, "op": ">=", "right": {"constant": 5}}},
                {"key": "current", "when": {"left": ${stages("$PREVIOUS, ${filter("data.state", "in", """{"constant": ["passed"]}""")}")}, "op": "=", "right": ${stages(PREVIOUS)}}},
                {"key": "locked"}]}]}"""

        val REQUIRED = filter("id", "in", """{"self": "links", "type": "required"}""")

        /** Tree: available when all the stages required are done. */
        val TREE = """{"root_kind": "tree", "link_types": [{"key": "required", "name": "Required", "stroke": "normal"}, {"key": "see_also", "name": "See also", "stroke": "dotted"}],
            "kinds": [
            {"key": "tree", "contains": ["skill"], "states": [{"key": "ongoing"}]},
            {"key": "skill", "states": [
                {"key": "done", "manual": true},
                {"key": "available", "when": {"left": ${stages("$REQUIRED, ${filter("data.state", "in", """{"constant": ["done"]}""")}")}, "op": "=", "right": ${stages(REQUIRED)}}},
                {"key": "locked"}]}]}"""

        /** Rehabilitation: a step passed gives the next one « current » by an effect. */
        val REHAB = """{"root_kind": "rehab", "kinds": [
            {"key": "rehab", "contains": ["step"], "states": [{"key": "ongoing"}]},
            {"key": "step", "states": [{"key": "passed", "manual": true}, {"key": "current", "manual": true}, {"key": "waiting"}],
             "effects": [{"on": {"left": {"self": "last_move"}, "op": "in", "right": {"constant": ["passed"]}},
                          "set": {"stages": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [${filter("data.previous", "in", """{"self": "id"}""")}]}, "state": "current"}}]}]}"""

        /** Two effects that give each other, without end. */
        val PING_PONG = """{"root_kind": "loop", "kinds": [
            {"key": "loop", "contains": ["ping"], "states": [{"key": "ongoing"}]},
            {"key": "ping", "states": [{"key": "a", "manual": true}, {"key": "b", "manual": true}, {"key": "none"}],
             "effects": [
                {"on": {"left": {"self": "last_move"}, "op": "in", "right": {"constant": ["a"]}}, "set": {"stages": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [${filter("id", "in", """{"self": "id"}""")}]}, "state": "b"}},
                {"on": {"left": {"self": "last_move"}, "op": "in", "right": {"constant": ["b"]}}, "set": {"stages": {"target": {"kind": "TOOL_INSTANCE", "id": "self"}, "view": "stages", "filters": [${filter("id", "in", """{"self": "id"}""")}]}, "state": "a"}}]}]}"""

        /** Career change: what lies in a dropped way bears a mark, read from its parent. */
        val CAREER = """{"root_kind": "career", "kinds": [
            {"key": "career", "contains": ["way"], "states": [{"key": "ongoing"}]},
            {"key": "way", "contains": ["task"], "states": [{"key": "dropped", "manual": true}, {"key": "to_explore"}]},
            {"key": "task", "states": [{"key": "done", "manual": true}, {"key": "todo"}],
             "marks": [{"key": "in_dropped_way", "when": {"left": ${stages("${filter("id", "in", """{"self": "parent"}""")}, ${filter("data.state", "in", """{"constant": ["dropped"]}""")}")}, "op": ">", "right": {"constant": 0}}}]}]}"""

        /** An attempt: failed once its end date has passed, which closes its run. */
        val ATTEMPT = """{"root_kind": "attempt", "kinds": [
            {"key": "attempt", "contains": ["criterion"], "states": [
                {"key": "failed", "closes_run": true, "when": {"left": {"self": "run_end"}, "op": "<=", "right": {"constant": {"relative": "NOW"}}}},
                {"key": "ongoing"}]},
            {"key": "criterion", "states": [{"key": "done", "manual": true}, {"key": "todo"}]}]}"""

        /** Vaccines: expired when the last injection plus the vaccine's validity, in years, is past. */
        val VACCINES = """{"root_kind": "book", "kinds": [
            {"key": "book", "contains": ["vaccine"], "states": [{"key": "ongoing"}]},
            {"key": "vaccine", "plan_fields": [{"name": "validity", "display_name": "Validity", "type": "NUMERIC"}], "states": [
                {"key": "expired", "when": {"all": [
                    {"left": {"self": "last_move"}, "op": "in", "right": {"constant": ["injected"]}},
                    {"left": {"constant": {"relative": {"unit": "YEAR", "offset": {"self": "validity"}, "edge": "SAME"}, "from": {"self": "last_move_at"}}},
                     "op": "<=", "right": {"constant": {"relative": "NOW"}}}]}},
                {"key": "injected", "manual": true},
                {"key": "to_do"}]}]}"""
    }
}
