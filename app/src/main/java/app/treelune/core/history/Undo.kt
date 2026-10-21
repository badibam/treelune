package app.treelune.core.history

import android.content.Context
import androidx.room.useWriterConnection
import app.treelune.core.coordinator.Source
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.transaction
import app.treelune.core.strings.Strings
import app.treelune.core.utils.DateUtils
import org.json.JSONObject

/**
 * Undoing a step, and redoing it (docs/DATA.md, « History of writes »).
 *
 * An undo puts each row the step touched back as it was before it; a redo, as the step left it.
 * Neither is a step: each changes the state of the step it acts on, and writes its rows past the
 * door, which would make one of them. Before a row is written, it must still be as the step left
 * it (its copy after it, for an undo; before it, for a redo), the parts the app alone writes
 * aside (Kept); and a row about to be deleted must take nothing with it that the step did not
 * write, a cascade deleting it unseen. Otherwise the whole is refused, naming what changed the
 * row since: never a merge, never an overwrite.
 */
class Undo(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val kept = Kept(context, database)
    private val s = Strings.`for`(context = context)

    /** What came of an undo or a redo: done, or refused with the reason. */
    sealed interface Outcome {
        data object Done : Outcome
        data class Refused(val reason: String) : Outcome
    }

    /** Undoes the step [stepId], as [by] asks. */
    suspend fun undo(stepId: String, by: Source): Outcome = act(stepId, by, undo = true)

    /** Redoes the step [stepId], undone, as [by] asks. */
    suspend fun redo(stepId: String, by: Source): Outcome = act(stepId, by, undo = false)

    /** One row of a step, as the step changed it from its first copy before to its last after. */
    private data class Change(val table: CoveredTable, val id: String, val before: JSONObject?, val after: JSONObject?, val lastRowId: Long)

    private suspend fun act(stepId: String, by: Source, undo: Boolean): Outcome = database.transaction {
        val step = database.historyDao().getStep(stepId)
            ?: return@transaction Outcome.Refused(s.shared("history_error_step_not_found").format(stepId))
        if (!step.isStep) return@transaction Outcome.Refused(s.shared("history_error_not_a_step"))
        val expected = if (undo) HistoryState.DONE else HistoryState.UNDONE
        if (step.state != expected) {
            return@transaction Outcome.Refused(s.shared(if (undo) "history_error_already_undone" else "history_error_not_undone"))
        }

        val changes = changesOf(step)
        // What each row must still be, and what it becomes
        val from: (Change) -> JSONObject? = { if (undo) it.after else it.before }
        val to: (Change) -> JSONObject? = { if (undo) it.before else it.after }

        for (change in changes) {
            val current = Rows.read(database, change.table, change.id)
            if (!stillAs(change.table, current, from(change))) return@transaction refusal(change, undo)
        }
        // A row deleted takes along what hangs from it: all of it must be the step's
        val written = changes.map { it.table.tableName to it.id }.toSet()
        for (change in changes.filter { to(it) == null && from(it) != null }) {
            val stranger = childrenOf(change.table, change.id).firstOrNull { it !in written }
            if (stranger != null) return@transaction refusal(stranger, undo)
        }

        // Parents first when rows come back, children first when they go
        changes.filter { to(it) == null }.sortedByDescending { it.table.ordinal }.forEach { Rows.delete(database, it.table, it.id) }
        changes.filter { to(it) != null }.sortedBy { it.table.ordinal }.forEach { change ->
            val current = Rows.read(database, change.table, change.id)
            val target = to(change)!!
            val row = if (current == null) target else kept.over(target, current, kept.of(change.table, current))
            Rows.put(database, change.table, row)
        }

        database.useWriterConnection { connection ->
            connection.usePrepared("UPDATE history_steps SET state = ?, state_by = ?, state_at = ? WHERE id = ?") {
                it.bindText(1, if (undo) HistoryState.UNDONE else HistoryState.DONE)
                it.bindText(2, by.name)
                it.bindLong(3, System.currentTimeMillis())
                it.bindText(4, stepId)
                it.step()
            }
        }
        Outcome.Done
    }

    /** The rows of [step], each from its first copy before to its last copy after, should it touch one twice. */
    private suspend fun changesOf(step: HistoryStepEntity): List<Change> =
        database.historyDao().getRows(step.id)
            .groupBy { it.tableName to it.rowId }
            .map { (key, rows) ->
                Change(
                    table = CoveredTable.of(key.first),
                    id = key.second,
                    before = rows.first().before?.let { JSONObject(it) },
                    after = rows.last().after?.let { JSONObject(it) },
                    lastRowId = rows.last().id
                )
            }

    /** Whether [current] is still as [copy], the parts the app alone writes and the secrets aside. */
    private suspend fun stillAs(table: CoveredTable, current: JSONObject?, copy: JSONObject?): Boolean {
        if (current == null || copy == null) return current == null && copy == null
        val parts = kept.of(table, current)
        return kept.same(kept.without(current, parts), kept.without(copy, parts))
    }

    /** The rows that hang by cascade from the row [id] of [table], down to the last level, as table and id. */
    private suspend fun childrenOf(table: CoveredTable, id: String): List<Pair<String, String>> =
        table.children.flatMap { (childTable, column) ->
            val child = CoveredTable.of(childTable)
            Rows.read(database, child, column, id).flatMap { row ->
                val childId = row.getString(child.idColumn)
                listOf(child.tableName to childId) + childrenOf(child, childId)
            }
        }

    /** The refusal of an undo, or a redo, for [change] a row has gone through since. */
    private suspend fun refusal(change: Change, undo: Boolean): Outcome.Refused =
        refusal(change.table.tableName to change.id, undo, after = change.lastRowId)

    /** The refusal for the row [row], naming the last write that kept it after [after], if there is one. */
    private suspend fun refusal(row: Pair<String, String>, undo: Boolean, after: Long = 0): Outcome.Refused {
        val since = database.historyDao().getRowsOf(row.first, row.second).lastOrNull { it.id > after }
            ?.let { database.historyDao().getStep(it.stepId) }
        val head = s.shared(if (undo) "history_refused_undo" else "history_refused_redo")
        val why = when {
            since == null -> s.shared("history_refused_changed")
            !since.isStep -> s.shared("history_refused_changed_by_app").format(DateUtils.formatFullDateTime(since.at))
            else -> s.shared("history_refused_changed_by_step").format(since.phrase ?: "", DateUtils.formatFullDateTime(since.at))
        }
        return Outcome.Refused("$head $why")
    }
}
