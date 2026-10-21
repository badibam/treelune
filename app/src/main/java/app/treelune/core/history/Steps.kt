package app.treelune.core.history

import android.content.Context
import androidx.room.useWriterConnection
import app.treelune.core.database.AppDatabase
import app.treelune.core.utils.LogManager

/** What the coordinator does with a step once its operation is done. */
object Steps {

    /**
     * Writes the phrase of [step] from [phrase], when the operation wrote anything into it: said
     * once, as it was done, and never again (docs/DATA.md, « History of writes »). A step its
     * operation wrote nothing into has no row, and [phrase] is not asked.
     */
    suspend fun close(context: Context, step: Step, phrase: suspend () -> String) {
        val database = AppDatabase.getDatabase(context)
        if (database.historyDao().getStep(step.id) == null) return
        val said = try {
            phrase()
        } catch (e: Exception) {
            // The step stays, without its phrase: what it wrote is kept all the same
            LogManager.service("History: step ${step.id} not given its phrase: ${e.message}", "ERROR", e)
            return
        }
        database.useWriterConnection { connection ->
            connection.usePrepared("UPDATE history_steps SET phrase = ? WHERE id = ? AND phrase IS NULL") {
                it.bindText(1, said)
                it.bindText(2, step.id)
                it.step()
            }
        }
    }
}
