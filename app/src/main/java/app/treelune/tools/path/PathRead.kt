package app.treelune.tools.path

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.terms.TermReader
import org.json.JSONObject

/**
 * One Path read from the app: its config's grammar, its stages, and their replay at an instant
 * (PathReplay), the core's terms read by the core (TermReader), relative dates on the user's
 * calendar.
 */
class PathRead private constructor(
    val toolId: String,
    val config: JSONObject,
    val grammar: PathGrammar,
    val entities: List<ToolDataEntity>,
    val stages: List<Stage>,
    private val context: Context
) {

    private val s = Strings.`for`(tool = PathRows.TOOLTYPE, context = context)

    /** The replay at [now]; with [judge], the judge's records to write among what it gives. */
    suspend fun replay(now: Long, judge: Boolean = false): PathReplay.Outcome {
        return PathReplay(grammar, toolId, rules(), { TimeResolver.at(it) }, { s.shared(it) }, { s.tool(it) }).replay(stages, now, judge)
    }

    /** The roots of its runs, the latest opened first. */
    fun runs(): List<Stage> = stages.filter { it.isRoot }.sortedByDescending { root -> root.moves.firstOrNull { it.state == Move.START }?.at ?: Long.MIN_VALUE }

    /** Whether the run [root] heads is closed: its journal holds its end. */
    fun isClosed(root: Stage): Boolean = root.moves.any { it.state == Move.END }

    /** The roots of the open runs awaiting their validation, the latest opened first. */
    fun awaiting(): List<Stage> = runs().filter { it.awaiting != null && !isClosed(it) }

    /** The current run: the latest opened of those still open; null when none is. */
    fun currentRun(): Stage? = runs().firstOrNull { !isClosed(it) }

    fun stage(id: String): Stage? = stages.firstOrNull { it.id == id }

    /** [stage] as `self` reads it in the replay [outcome]: its relations in its run, its state and its last move there. */
    fun selfOf(stage: Stage, outcome: PathReplay.Outcome): PathSelf.Of {
        val kind = grammar.kind(stage.kind) ?: throw IllegalStateException(s.tool("path_problem_kind").format(stage.kind))
        val children = stages.filter { it.run == stage.run }.groupBy { it.parent }
        return object : PathSelf.Of {
            override val stage = stage
            override val kind = kind
            override fun previous() = PathOrder.previous(stage, children)
            override fun siblings() = children[stage.parent].orEmpty().filter { it.id != stage.id }.sortedBy { it.position }
            override fun content() = children[stage.id].orEmpty().sortedBy { it.position }
            override fun lastMove() = outcome.states[stage.id]?.lastMove
            override fun state() = outcome.states[stage.id]?.let { (it.state ?: return null) to it.since }
            override fun runStart() = stage(stage.run)?.moves?.firstOrNull { it.state == Move.START }?.at
            override fun runEnd() = stage(stage.run)?.plannedEnd
        }
    }

    /** The rows of its views in the replay [outcome], as a rule reads them. */
    fun rowsOf(outcome: PathReplay.Outcome): PathRules.Rows = object : PathRules.Rows {
        override fun stages() = outcome.rows
        override fun moves() = outcome.moves
        override val stageFields = PathRows.stageFields(grammar)
        override val moveFields = PathRows.moveFields(grammar)
    }

    /** How its rules read: its own views in the rows given, anything else by the core. */
    fun rules(): PathRules {
        val reader = TermReader(context)
        return PathRules(toolId, { term, at -> reader.read(term, at, null) }, { TimeResolver.at(it) }, { s.shared(it) }, { s.tool(it) })
    }

    /**
     * The readout of each of [of] whose kind has one (« L'apparence d'une brique »), computed on
     * it at [now] in the replay [outcome]: its values read with `self`, its short form and its gauge.
     */
    suspend fun readouts(of: List<Stage>, outcome: PathReplay.Outcome, now: Long): Map<String, app.treelune.core.readouts.ReadoutComputed> {
        val rules = rules()
        val rows = rowsOf(outcome)
        return of.mapNotNull { stage ->
            val readout = grammar.kind(stage.kind)?.readout ?: return@mapNotNull null
            val self = selfOf(stage, outcome)
            stage.id to app.treelune.core.readouts.ReadoutComputed.of(readout, now, stage.name, context) { json, _ -> rules.read(json, self, rows, now) }
        }.toMap()
    }

    fun entity(id: String): ToolDataEntity? = entities.firstOrNull { it.id == id }

    companion object {
        /**
         * The Path [toolId] as it is now; null when it is no Path.
         *
         * @throws IllegalStateException on a stage that does not read
         */
        suspend fun of(context: Context, toolId: String): PathRead? {
            val database = AppDatabase.getDatabase(context)
            val tool = database.toolInstanceDao().getToolInstanceById(toolId)?.takeIf { it.tooltype == PathRows.TOOLTYPE } ?: return null
            return of(context, toolId, JSONObject(tool.config_json))
        }

        /** The Path [toolId] read with [config]. */
        suspend fun of(context: Context, toolId: String, config: JSONObject): PathRead {
            val entities = AppDatabase.getDatabase(context).toolDataDao().getByToolInstance(toolId)
            return PathRead(toolId, config, PathGrammar.fromJson(config), entities, PathStages.all(entities), context)
        }
    }
}
