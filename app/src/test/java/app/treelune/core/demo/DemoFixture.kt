package app.treelune.core.demo

import android.content.Context
import android.net.Uri
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.BackupService
import java.io.File

/**
 * The app holding the demo alone, for a test that starts from it: installed once in the test JVM,
 * on an app emptied first (backup.reset), then kept as a backup; each later test restores that
 * backup (backup.import), a few seconds where an install takes some 25. A test of the install
 * itself installs it as the app does (DemoStartup, demo.install).
 */
object DemoFixture {

    /** The backup of the app just as the demo left it, outside any test's own folders. */
    private var snapshot: File? = null

    /** The app made the demo alone, as just installed: what was there before is gone. */
    suspend fun installed(context: Context) {
        val coordinator = Coordinator(context)
        snapshot?.let { saved ->
            val restored = coordinator.processUserAction("backup.import", mapOf("uri" to Uri.fromFile(saved).toString()))
            check(restored.isSuccess) { "Demo fixture not restored: ${restored.error}" }
            return
        }
        val reset = coordinator.processUserAction("backup.reset", emptyMap())
        check(reset.isSuccess) { "App not emptied for the demo fixture: ${reset.error}" }
        val installed = coordinator.processUserAction("demo.install", emptyMap())
        check(installed.isSuccess) { "Demo fixture not installed: ${installed.error}" }
        val file = File.createTempFile("demo_fixture", ".zip").apply { deleteOnExit() }
        val service = BackupService(context)
        val token = CancellationToken()
        check(service.writeZip(file.outputStream(), service.build(token)!!, token)) { "Demo fixture not saved" }
        snapshot = file
    }
}
