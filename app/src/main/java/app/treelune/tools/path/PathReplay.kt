package app.treelune.tools.path

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.views.RowsReading
import org.json.JSONArray

/**
 * The replay of a Path (« Les `marks` et les `effects` », the replay): the state of each stage
 * computed from its moves, never stored.
 *
 * An open run's moves are replayed in the order of their instant; after each, the states are
 * computed in the order of computation (PathOrder), then each effect whose `on` has just become
 * true gives its virtual moves at the same instant, and the states are computed again while an
 * effect goes off — at most [CEILING] virtual moves per real move. After the last, the states are
 * judged once more at the instant read; when it judges, the Path's judge writes there each
 * `record` state whose rule holds on a stage not in it, one per stage, the stages computed after
 * it seeing it. Then the marks, top-down. A closed run computes nothing: its stages show the
 * state frozen on them.
 *
 * @param grammar The Path's config read
 * @param pathId The Path's tool, which its rules' readings name
 * @param calendar The calendar a relative date resolves on, against an instant (TimeResolver.at)
 * @param shared The core's strings, [own] the Path's
 */
class PathReplay(
    private val grammar: PathGrammar,
    private val pathId: String,
    private val rules: PathRules,
    private val calendar: (Long) -> app.treelune.core.selection.TimeResolver,
    private val shared: (String) -> String,
    private val own: (String) -> String
) {

    /** What the replay says of one stage. */
    data class StageState(
        val state: String?,
        val since: Long?,
        val lastMove: Move?,
        val marks: List<String>,
        /** Whether its last move is virtual: an effect gave its state */
        val byEffect: Boolean
    )

    /** Something the replay could not do, said on the stage (or the Path) it concerns. */
    data class Problem(val stage: String?, val message: String)

    /**
     * What a replay gives.
     *
     * @property records The moves the judge writes, by stage: real moves to store
     * @property virtual The virtual moves effects gave, by stage
     * @property closes Whether a run's root is in a state that closes it
     */
    data class Outcome(
        val states: Map<String, StageState>,
        val rows: List<Map<String, Any?>>,
        val moves: List<Map<String, Any?>>,
        val records: List<Pair<String, Move>>,
        val virtual: Map<String, List<Move>>,
        val closes: Set<String>,
        val problems: List<Problem>
    )

    /**
     * Every run of [stages] replayed at [now]: the moves after it ignored, a closed run as it was
     * frozen. With [judge], the judge writes the `record` states of the open runs.
     */
    suspend fun replay(stages: List<Stage>, now: Long, judge: Boolean): Outcome {
        val byRun = stages.groupBy { it.run }
        val closedRuns = byRun.filter { (run, members) -> members.firstOrNull { it.id == run }?.let { isClosed(it, now) } == true }
        val frozen = closedRuns.values.flatten().map { frozenKnown(it, stages, now) }
        val frozenRows = frozen.map { PathRows.stageRow(it, pathId) }
        val frozenMoves = closedRuns.values.flatten().flatMap { stage -> stage.moves.filter { it.at <= now }.mapIndexed { i, m -> PathRows.moveRow(stage, m, i, false, pathId) } }
        val outcomes = byRun.filterKeys { it !in closedRuns }.map { (_, members) -> Run(members, frozenRows, frozenMoves, now).replay(judge) }
        return Outcome(
            states = frozen.associate { it.stage.id to StageState(it.state, it.since, it.lastMove, emptyList(), false) } + outcomes.flatMap { it.states.entries }.associate { it.toPair() },
            rows = frozenRows + outcomes.flatMap { it.rows },
            moves = frozenMoves + outcomes.flatMap { it.moves },
            records = outcomes.flatMap { it.records },
            virtual = outcomes.flatMap { it.virtual.entries }.associate { it.toPair() },
            closes = outcomes.flatMap { it.closes }.toSet(),
            problems = outcomes.flatMap { it.problems }
        )
    }

    /** Whether the run [root] heads is closed at [now]: its journal holds its end. */
    private fun isClosed(root: Stage, now: Long) = root.moves.any { it.state == Move.END && it.at <= now }

    private fun frozenKnown(stage: Stage, all: List<Stage>, now: Long): PathRows.Known {
        val root = all.first { it.id == stage.run }
        val moves = stage.moves.filter { it.at <= now && it.state != Move.START && it.state != Move.END }
        return PathRows.Known(
            stage = stage,
            state = stage.finalState,
            since = moves.lastOrNull()?.at,
            lastMove = moves.lastOrNull(),
            previous = PathOrder.previous(stage, all.groupBy { it.parent })?.id,
            ancestors = ancestors(stage, all.associateBy { it.id }),
            marks = emptyList(),
            runStart = root.moves.firstOrNull { it.state == Move.START }?.at,
            runEnd = root.moves.firstOrNull { it.state == Move.END }?.at ?: root.plannedEnd,
            closed = true
        )
    }

    private fun ancestors(stage: Stage, byId: Map<String, Stage>): List<String> =
        generateSequence(stage.parent?.let { byId[it] }) { it.parent?.let { p -> byId[p] } }.map { it.id }.toList()

    /** One open run being replayed. */
    private inner class Run(
        private val stages: List<Stage>,
        private val otherRows: List<Map<String, Any?>>,
        private val otherMoves: List<Map<String, Any?>>,
        private val now: Long
    ) {
        private val byId = stages.associateBy { it.id }
        private val children = stages.groupBy { it.parent }
        private val root = stages.first { it.id == it.run }
        private val runStart = root.moves.firstOrNull { it.state == Move.START && it.at <= now }?.at
        private val runEnd = root.plannedEnd
        private val fields: Map<String, FieldDefinition> = PathRows.stageFields(grammar)

        /** Each stage's moves applied so far, real and virtual, in the order applied. */
        private val applied = stages.associate { it.id to mutableListOf<Pair<Move, Boolean>>() }
        private val states = mutableMapOf<String, Pair<String?, Long?>>()
        private val marks = mutableMapOf<String, List<String>>()
        private val rows = LinkedHashMap<String, Map<String, Any?>>()
        private val problems = mutableListOf<Problem>()
        private val records = mutableListOf<Pair<String, Move>>()

        /** Whether each effect's `on` held on each stage after the last step: an effect goes off when it becomes true. */
        private val onBefore = mutableMapOf<Pair<String, Int>, Boolean>()

        private val readRows = object : PathRules.Rows {
            override fun stages() = otherRows + rows.values
            override fun moves() = otherMoves + movesRows()
            override val stageFields get() = fields
            override val moveFields = PathRows.moveFields(grammar)
        }

        suspend fun replay(judge: Boolean): Outcome {
            val order = when (val result = PathOrder.of(stages, PathOrder.linkTypesRead(grammar))) {
                is PathOrder.Result.Ordered -> result.stages
                is PathOrder.Result.Loop -> {
                    problems += Problem(null, own("path_problem_loop").format(result.stages.joinToString(", ") { it.name }))
                    return outcome()
                }
            }
            stages.forEach { refreshRow(it) }
            val real = stages.flatMap { stage -> stage.moves.filter { it.at <= now }.map { stage to it } }.sortedBy { it.second.at }
            // Before the first move: what each effect's `on` already says, which going off is measured against
            val first = real.firstOrNull()?.second?.at ?: runStart ?: now
            computeAll(order, first, judge = false)
            effects(order, first, fire = false)
            for ((stage, move) in real) {
                applied.getValue(stage.id) += move to false
                refreshRow(stage)
                computeAll(order, move.at, judge = false)
                effects(order, move.at, fire = true)
            }
            computeAll(order, now, judge)
            // The marks, top-down: a mark may read its parent's
            for (stage in PathOrder.planOrder(stages)) {
                val kind = grammar.kind(stage.kind) ?: continue
                marks[stage.id] = kind.marks.filter { mark -> holds(mark.whenRule, stage, now, "${kind.name} › ${mark.name}") == true }.map { it.key }
                refreshRow(stage)
            }
            return outcome()
        }

        private fun outcome(): Outcome = Outcome(
            states = stages.associate { stage ->
                val last = lastApplied(stage.id)
                stage.id to StageState(states[stage.id]?.first, states[stage.id]?.second, last?.first, marks[stage.id].orEmpty(), last?.second == true)
            },
            rows = rows.values.toList(),
            moves = movesRows(),
            records = records.toList(),
            virtual = applied.mapValues { (_, moves) -> moves.filter { it.second }.map { it.first } }.filterValues { it.isNotEmpty() },
            closes = setOfNotNull(root.id.takeIf { states[root.id]?.first?.let { key -> grammar.kind(root.kind)?.state(key)?.closesRun } == true }),
            problems = problems.toList()
        )

        /** Each stage's state at [at], in the order of computation; with [judge], the judge writing its records. */
        private suspend fun computeAll(order: List<Stage>, at: Long, judge: Boolean) {
            for (stage in order) {
                compute(stage, at)
                if (!judge) continue
                val kind = grammar.kind(stage.kind) ?: continue
                val current = states[stage.id]?.first
                val written = kind.states.firstOrNull { rule ->
                    rule.record && rule.whenRule != null && rule.key != current && holds(rule.whenRule, stage, at, "${kind.name} › ${rule.name}") == true
                } ?: continue
                val move = Move(at, written.key)
                applied.getValue(stage.id) += move to false
                records += stage.id to move
                compute(stage, at)
            }
        }

        /** The state of [stage] at [at]: the first of its kind's list that is active. */
        private suspend fun compute(stage: Stage, at: Long) {
            val kind = grammar.kind(stage.kind) ?: run {
                problems += Problem(stage.id, own("path_problem_kind").format(stage.kind))
                return
            }
            val last = lastApplied(stage.id)?.first
            var found: StateRule? = null
            for ((i, rule) in kind.states.withIndex()) {
                val active = when {
                    rule.byMove && last?.state == rule.key -> true
                    !rule.record && rule.whenRule != null -> holds(rule.whenRule, stage, at, "${kind.name} › ${rule.name}") == true
                    // The last, without a rule: otherwise
                    i == kind.states.lastIndex && !rule.byMove -> true
                    else -> false
                }
                if (active) { found = rule; break }
            }
            val before = states[stage.id]
            val key = found?.key
            val since = when {
                found?.byMove == true -> last?.at
                before?.first == key -> before?.second
                else -> at
            }
            states[stage.id] = key to since
            refreshRow(stage)
        }

        /**
         * The effects at [at]: those whose `on` has just become true give their moves, until none
         * goes off. Each sweep judges every `on` on the same states, then applies what goes off:
         * an effect never sees the moves another gave in the same sweep.
         */
        private suspend fun effects(order: List<Stage>, at: Long, fire: Boolean) {
            var given = 0
            while (true) {
                val going = mutableListOf<Pair<Stage, EffectRule>>()
                for (stage in order) {
                    val kind = grammar.kind(stage.kind) ?: continue
                    for ((i, effect) in kind.effects.withIndex()) {
                        val on = holds(effect.on, stage, at, kind.name) == true
                        val key = stage.id to i
                        val was = onBefore[key] ?: false
                        onBefore[key] = on
                        if (fire && on && !was) going += stage to effect
                    }
                }
                if (going.isEmpty()) return
                for ((stage, effect) in going) {
                    val targets = targets(effect, stage, at)
                    if (given + targets.size > CEILING) {
                        problems += Problem(stage.id, own("path_problem_ceiling").format(CEILING, grammar.kind(stage.kind)?.name ?: stage.kind, effect.setState))
                        return
                    }
                    for (target in targets) {
                        applied.getValue(target.id) += Move(at, effect.setState) to true
                        refreshRow(target)
                    }
                    given += targets.size
                }
                computeAll(order, at, judge = false)
            }
        }

        /** The stages of this run [effect]'s filters keep, judged on `stages` with `self` the stage it went off on. */
        private fun targets(effect: EffectRule, stage: Stage, at: Long): List<Stage> = try {
            val filters = PathSelf.substituted(effect.setFilters, self(stage), own) as JSONArray
            val selection = EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, pathId), filters = filters, view = PathRows.STAGES)
            RowsReading.selected(rows.values.toList(), selection, fields, calendar(at), shared).mapNotNull { byId[it["id"]] }
        } catch (e: IllegalArgumentException) {
            problems += Problem(stage.id, e.message ?: "")
            emptyList()
        } catch (e: IllegalStateException) {
            problems += Problem(stage.id, e.message ?: "")
            emptyList()
        }

        /** [rule] judged on [stage]; a rule that cannot be judged is said, named by [name], and does not hold. */
        private suspend fun holds(rule: org.json.JSONObject, stage: Stage, at: Long, name: String): Boolean? =
            when (val judged = rules.judge(rule, self(stage), readRows, at)) {
                is PathRules.Judged.Answer -> judged.holds
                is PathRules.Judged.Failed -> {
                    val problem = Problem(stage.id, "$name: ${judged.message}")
                    if (problem !in problems) problems += problem
                    null
                }
            }

        private fun lastApplied(id: String): Pair<Move, Boolean>? =
            applied[id]?.lastOrNull { it.first.state != Move.START && it.first.state != Move.END }

        private fun self(stage: Stage): PathSelf.Of = object : PathSelf.Of {
            override val stage = stage
            override val kind = grammar.kind(stage.kind) ?: throw IllegalArgumentException(own("path_problem_kind").format(stage.kind))
            override fun previous() = PathOrder.previous(stage, children)
            override fun siblings() = children[stage.parent].orEmpty().filter { it.id != stage.id }.sortedBy { it.position }
            override fun content() = children[stage.id].orEmpty().sortedBy { it.position }
            override fun lastMove() = lastApplied(stage.id)?.first
            override fun state() = states[stage.id]?.let { (it.first ?: return null) to it.second }
            override fun runStart() = runStart
            override fun runEnd() = runEnd
        }

        private fun refreshRow(stage: Stage) {
            rows[stage.id] = PathRows.stageRow(PathRows.Known(
                stage = stage,
                state = states[stage.id]?.first,
                since = states[stage.id]?.second,
                lastMove = lastApplied(stage.id)?.first,
                previous = PathOrder.previous(stage, children)?.id,
                ancestors = ancestors(stage, byId),
                marks = marks[stage.id].orEmpty(),
                runStart = runStart,
                runEnd = runEnd,
                closed = false
            ), pathId)
        }

        private fun movesRows(): List<Map<String, Any?>> = stages.flatMap { stage ->
            val moves = applied.getValue(stage.id)
            val real = moves.filter { !it.second }.mapIndexed { i, (m, _) -> PathRows.moveRow(stage, m, i, false, pathId) }
            real + moves.filter { it.second }.mapIndexed { i, (m, _) -> PathRows.moveRow(stage, m, i, true, pathId) }
        }
    }

    companion object {
        /** The most virtual moves one real move may give, past which the replay stops there and says so. */
        const val CEILING = 50
    }
}
