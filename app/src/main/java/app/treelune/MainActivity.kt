package app.treelune

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import app.treelune.core.coordinator.isSuccess
import androidx.compose.runtime.setValue
import app.treelune.core.ui.screens.MainScreen
import app.treelune.core.commands.CommandStatus
import app.treelune.core.ui.UI
import app.treelune.core.ui.*
import app.treelune.core.ui.sound.scrollEndSound
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.scheduling.CoreScheduler
import app.treelune.core.scheduling.SchedulerAlarm
import app.treelune.core.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import app.treelune.core.utils.LogManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

        /** The tool a notification opens, on its oldest entry that waits. */
        const val EXTRA_TOOL_INSTANCE_ID = "tool_instance_id"

        /** The place a notification opens, by its address (Place.address): a routine's run. */
        const val EXTRA_PLACE = "place"
        /** With a tool, the entry a notification is about, opened in it */
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    /** The tool asked for by the notification that opened the app, until the screens have opened it. */
    private var openToolId by androidx.compose.runtime.mutableStateOf<String?>(null)
    /** The entry of that tool the notification is about, null for its oldest that waits. */
    private var openEntryId by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** The place asked for by the notification that opened the app, until the screens have opened it. */
    private var openPlace by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TOOL_INSTANCE_ID)?.let {
            openEntryId = intent.getStringExtra(EXTRA_ENTRY_ID)
            openToolId = it
        }
        intent.getStringExtra(EXTRA_PLACE)?.let { openPlace = it }
    }

    // Said again whenever the window comes back (a dialog closed, the app reopened): the system
    // shows the bar again on its own then
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar(window)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Once only: a rotation recreates the activity with the same intent
        if (savedInstanceState == null) {
            openToolId = intent.getStringExtra(EXTRA_TOOL_INSTANCE_ID)
            openPlace = intent.getStringExtra(EXTRA_PLACE)
            openEntryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        }

        // The phone's dark theme setting from the first frame on
        CurrentTheme.systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        // A crash not yet seen comes first, before the AI, the scheduler or any screen is set up:
        // what crashed may be among them, and the report must be reachable all the same
        val crash = app.treelune.core.bugreport.CrashFile.unseen(this)
        if (crash == null) {
            startApp()
            return
        }
        setContent {
            MaterialTheme(colorScheme = CurrentTheme.getCurrentColorScheme()) {
                UI.FullScreen {
                    app.treelune.core.ui.screens.settings.CrashNoticeScreen(crash.timestamp, onContinue = {
                        app.treelune.core.bugreport.CrashFile.markSeen(this@MainActivity)
                        startApp()
                    })
                }
            }
        }
    }

    /** The app set up and its screens shown. */
    private fun startApp() {
        // Purge old logs at startup to prevent DB bloat and CursorWindow overflow
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LogManager.manualPurge()
            } catch (e: Exception) {
                println("LogManager: Startup purge failed: ${e.message}")
            }
        }

        // The images' folder confronted with their table, once per process, before any screen:
        // nothing can be joining an image yet
        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            try {
                app.treelune.core.ai.enrichments.AttachedImages.sweepOnce(this@MainActivity)
            } catch (e: Exception) {
                LogManager.aiEnrichment("Startup sweep of the images failed: ${e.message}", "ERROR", e)
            }
        }

        // Initialize notification channels (Android O+)
        NotificationChannels.initialize(this)

        // Request notification permission (Android 13+)
        requestNotificationPermissionIfNeeded()

        // Initialize AI orchestrator singleton (V2 - suspend function)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AIOrchestrator.initialize(this@MainActivity)
            } catch (e: Exception) {
                LogManager.service("Failed to initialize AIOrchestrator: ${e.message}", "ERROR", e)
            }
        }

        // Initialize CoreScheduler with 1-minute internal heartbeat for app-open reactivity
        CoreScheduler.initialize(this)

        // The alarm that runs the scheduler when the app is closed (10 min), complementing
        // CoreScheduler's 1-minute heartbeat while it is open
        SchedulerAlarm.arm(this)

        setContent {
            // The phone's dark theme setting, which the mode "as the phone" follows as it changes
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect { CurrentTheme.systemDark = systemDark }
            // The app's screens, moved whole from one theme's frame to another's when the theme
            // changes: each theme draws FullScreen its own way, and without moving them every
            // screen's state would start over, the interface settings being edited among them
            val app = androidx.compose.runtime.remember { androidx.compose.runtime.movableContentOf {
                // The demo first, installed afresh after an update, before the home screen
                // reads the zones; once per activity, a recreation finding it done
                var demoReady by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                if (!demoReady) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val failed = withContext(Dispatchers.IO) { app.treelune.core.demo.DemoStartup.run(this@MainActivity) }
                        if (failed != null) app.treelune.core.ui.UI.Toast(this@MainActivity, failed, app.treelune.core.ui.Duration.LONG)
                        demoReady = true
                    }
                    app.treelune.core.ui.components.DemoInstalling()
                } else {
                    // What waits for the user and the stopwatches running, marked on every tile of
                    // every screen: watched once the demo is written, not at each of its writes
                    androidx.compose.runtime.CompositionLocalProvider(
                        app.treelune.core.ui.LocalWaiting provides app.treelune.core.ui.rememberWaiting(null),
                        app.treelune.core.ui.LocalRunning provides app.treelune.core.ui.rememberRunning()
                    ) {
                        // The theme's sounds loaded
                        app.treelune.core.ui.sound.UISoundsLoader()
                        // A long operation running shows over every screen; one whose caller
                        // left (its screen closed) says once here how it ended
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            app.treelune.core.coordinator.LongOperation.unclaimed.collect { ended ->
                                val s = app.treelune.core.strings.Strings.`for`(context = this@MainActivity)
                                val message = if (ended.result.isSuccess) s.shared("long_operation_done").format(ended.label)
                                    else s.shared("long_operation_failed").format(ended.label, ended.result.error ?: "")
                                app.treelune.core.ui.UI.Toast(this@MainActivity, message, app.treelune.core.ui.Duration.LONG)
                            }
                        }
                        // The end of any list of the screens heard
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().scrollEndSound()) {
                            MainScreen(openToolId = openToolId, openEntryId = openEntryId, onToolOpened = { openToolId = null; openEntryId = null }, openPlace = openPlace, onPlaceOpened = { openPlace = null })
                            // A long operation running, floating over the screen without moving it
                            app.treelune.core.ui.components.LongOperationBar()
                            // A request for external access waiting for its code, over any screen
                            app.treelune.core.mcp.ui.McpApprovalDialog()
                            // Android restricting the app in the background, told once each time it begins
                            app.treelune.core.ui.components.BackgroundRestrictionDialog()
                        }
                    }
                }
            } }
            MaterialTheme(
                colorScheme = CurrentTheme.getCurrentColorScheme()
            ) {
                UI.FullScreen { app() }
            }
        }
    }
    
    /**
     * Schedule CoreScheduler periodic worker for when app is closed.
     * WorkManager minimum interval: 15 minutes.
     * For app-open reactivity, CoreScheduler has internal 1-minute heartbeat.
     *
     * CoreScheduler handles:
     * - AI automations (via AIOrchestrator)
     * - Tool schedulers (via discovery pattern)
     */
    /**
     * Request POST_NOTIFICATIONS permission for Android 13+ (API 33+)
     *
     * Android 13 introduced runtime permission for notifications.
     * Without this, notifications won't be shown even if channels are created.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}
