package app.treelune.tools.path

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Origin
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * When the Path's judge passes (« Les états », who writes a `record`): after a step that wrote
 * another tool's entries, a rule reading it may have become true; at each tick of the scheduler,
 * a date may have come. It writes as the app, out of any step of the history, outside the
 * writer's context; after the Path's own operations, PathService judges itself.
 */
object PathJudge : ToolScheduler {

    /** One judging at a time: a write's and the tick's would otherwise write the same record twice. */
    private val judging = Mutex()

    /**
     * Every Path, after a step that wrote another tool's entries. A step that wrote Paths alone
     * starts nothing: a Path's own operation judges itself, and the judge's writes, which pass
     * here too, must not start it again while it runs.
     */
    suspend fun afterWrites(context: Context, written: Set<String>) {
        val paths = pathIds(context)
        if (written.all { it in paths }) return
        detached { judgeAll(context) { true } }
    }

    private suspend fun pathIds(context: Context): Set<String> =
        AppDatabase.getDatabase(context).toolInstanceDao().getAllToolInstances().filter { it.tooltype == PathRows.TOOLTYPE }.map { it.id }.toSet()

    override suspend fun checkScheduled(context: Context) = detached { judgeAll(context) { true } }

    /**
     * The next instant a run's planned end comes, or a run is to open at its unit's start: the
     * core wakes the app then rather than at its next ring.
     */
    override suspend fun nextAction(context: Context): Long? {
        val now = System.currentTimeMillis()
        val ids = pathIds(context)
        return ids.mapNotNull { id ->
            val path = PathRead.of(context, id) ?: return@mapNotNull null
            val ends = path.runs().filter { !path.isClosed(it) }.mapNotNull { it.plannedEnd }
            val next = path.grammar.unit?.takeIf { path.grammar.opens == Opens.SCHEDULED }?.let { unit ->
                app.treelune.core.selection.TimeResolver.at(now).instant(app.treelune.core.selection.TimePoint.Relative(unit, 1, app.treelune.core.selection.Edge.START))
            }
            (ends + listOfNotNull(next)).filter { it > now }.minOrNull()
        }.minOrNull()
    }

    private suspend fun judgeAll(context: Context, which: (String) -> Boolean) = judging.withLock {
        val paths = AppDatabase.getDatabase(context).toolInstanceDao().getAllToolInstances().filter { it.tooltype == PathRows.TOOLTYPE && which(it.id) }
        for (path in paths) {
            val result = Coordinator(context).processScheduledTask("path.judge", mapOf("tool_instance_id" to path.id))
            if (!result.isSuccess) LogManager.service("Path ${path.id}: not judged: ${result.error}", "ERROR")
        }
    }

    /** Out of the writer's context and step, as the scheduler: what the judge writes is the app's. */
    private suspend fun <T> detached(block: suspend () -> T): T =
        CoroutineScope(Dispatchers.IO + Origin(Source.SCHEDULER)).async { block() }.await()
}
