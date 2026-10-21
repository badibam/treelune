package app.treelune.core.demo

import android.content.Context
import app.treelune.core.commands.CommandResult
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.LongOperation
import app.treelune.core.services.ImportService
import app.treelune.core.strings.Strings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * While a file is imported (imports.apply, a long operation), reinstalling the demo and exporting
 * a backup, long too, are refused through the coordinator, the message naming the import.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoDuringImportTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `while an import runs, the demo's reinstall and a backup's export are refused, naming it`() = runBlocking<Unit> {
        assertTrue("an import takes the place", "apply" in ImportService(context).longOperations)
        // The import's place, held as the coordinator holds it, under the import's own name
        val label = ImportService(context).verbalize("apply", JSONObject(), context)
        val release = CompletableDeferred<Unit>()
        val importing = async { LongOperation.run(label, { CommandResult(status = CommandStatus.ERROR) }) { release.await(); CommandResult(status = CommandStatus.SUCCESS) } }
        withTimeout(5_000) { LongOperation.running.first { it != null } }

        val said = Strings.`for`(context = context).shared("long_operation_busy").format(label)
        val coordinator = Coordinator(context)
        for (action in listOf("demo.install", "backup.export")) {
            val refused = coordinator.processUserAction(action, emptyMap())
            assertEquals(action, CommandStatus.ERROR, refused.status)
            assertEquals(action, said, refused.error)
        }

        release.complete(Unit)
        importing.await()
    }
}
