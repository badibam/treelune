package app.treelune.core.history

import android.content.Context
import app.treelune.core.config.AppSettings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.utils.LogManager

/**
 * How deep the history of writes goes (docs/DATA.md, « History of writes »): the steps of
 * the last days the data settings give (history_days), within the room they give
 * (history_megabytes). The scheduler's tick prunes the rest.
 */
object HistoryDepth {

    /**
     * The steps and noted writes of [steps] (the most recent first) to delete: those older than
     * [maxAge] before [now], then, from the most recent down, every one past [maxBytes] — the
     * latest step always kept, whatever its size, so that a big import alone above the room stays
     * undoable; and once one is past the room, all older go too, an undo never reaching past a
     * step it cannot undo.
     */
    fun gone(steps: List<StepSize>, now: Long, maxAge: Long, maxBytes: Long): List<String> {
        val latest = steps.firstOrNull { it.isStep }?.id
        var total = 0L
        var full = false
        return steps.filter { step ->
            when {
                step.id == latest -> { total += step.sizeBytes; false }
                step.at < now - maxAge -> true
                full || total + step.sizeBytes > maxBytes -> { full = true; true }
                else -> { total += step.sizeBytes; false }
            }
        }.map { it.id }
    }

    /** Prunes the history to its depth; called by the scheduler's tick. */
    suspend fun prune(context: Context) {
        val settings = Coordinator(context).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.DATA))
        if (!settings.isSuccess) {
            LogManager.service("History: depth unreadable, nothing pruned: ${settings.error}", "ERROR")
            return
        }
        val data = settings.data!!["settings"] as Map<*, *>
        val days = (data[AppSettings.DATA_HISTORY_DAYS] as Number).toLong()
        val megabytes = (data[AppSettings.DATA_HISTORY_MEGABYTES] as Number).toLong()
        val dao = AppDatabase.getDatabase(context).historyDao()
        val gone = gone(dao.sizes(), System.currentTimeMillis(), days * DAY, megabytes * MEGABYTE)
        if (gone.isEmpty()) return
        gone.chunked(500).forEach { dao.deleteSteps(it) }
        LogManager.service("History: ${gone.size} step(s) past $days days or $megabytes MB pruned", "INFO")
    }

    private const val DAY = 24L * 60 * 60 * 1000
    private const val MEGABYTE = 1024L * 1024
}
