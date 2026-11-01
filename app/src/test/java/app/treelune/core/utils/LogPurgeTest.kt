package app.treelune.core.utils

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.LogEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The log's purge reaches its ceiling even when the lines at the cut share one millisecond, the
 * newest kept, the other levels untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogPurgeTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `lines of the same millisecond are purged down to the ceiling, the newest kept`() = runBlocking<Unit> {
        val dao = AppDatabase.getDatabase(context).logDao()
        val levels = listOf("INFO")
        val before = dao.getLogCountForLevels(levels)
        val chatty = dao.getLogCountForLevels(listOf("VERBOSE"))
        // Twelve lines written within one millisecond, then three later ones
        repeat(12) { dao.insertLog(LogEntry(timestamp = 1_000L, level = "INFO", tag = "T", message = "same $it", throwableMessage = null)) }
        repeat(3) { dao.insertLog(LogEntry(timestamp = 2_000L + it, level = "INFO", tag = "T", message = "later $it", throwableMessage = null)) }
        val ceiling = before + 5

        val deleted = dao.deleteBeyondForLevels(levels, ceiling)

        assertEquals(10, deleted)
        assertEquals(ceiling, dao.getLogCountForLevels(levels))
        assertEquals(chatty, dao.getLogCountForLevels(listOf("VERBOSE")))
    }
}
