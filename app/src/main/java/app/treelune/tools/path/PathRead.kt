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
        val reader = TermReader(context)
        val rules = PathRules(toolId, { term, at -> reader.read(term, at, null) }, { TimeResolver.at(it) }, { s.shared(it) }, { s.tool(it) })
        return PathReplay(grammar, toolId, rules, { TimeResolver.at(it) }, { s.shared(it) }, { s.tool(it) }).replay(stages, now, judge)
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
