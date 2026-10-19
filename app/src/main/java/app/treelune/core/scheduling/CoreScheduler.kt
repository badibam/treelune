package app.treelune.core.scheduling

import android.content.Context
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized scheduler for all periodic background tasks.
 *
 * Architecture: Single point of entry for scheduling across the app
 * - AI automations (via AIOrchestrator)
 * - Tool schedulers (via discovery pattern)
 *
 * Heartbeat mechanism:
 * - 1 minute coroutine (app-open, high reactivity)
 * - 10 minute exact alarm (SchedulerAlarm), which also rings while the phone sleeps, set again
 *   after every tick, sooner when a tool scheduler's next action comes before
 *
 * Discovery pattern:
 * - No hardcoded dependencies on specific tools
 * - Tools register schedulers via ToolTypeContract.getScheduler()
 * - CoreScheduler discovers and calls all registered schedulers
 *
 * Lifecycle:
 * - attach() called from TreeluneApplication.onCreate(), whatever starts the process
 * - initialize() called from MainActivity.onCreate(), for the heartbeat
 * - shutdown() called from MainActivity.onDestroy()
 * - tick() called periodically by internal heartbeat + SchedulerAlarm
 */
object CoreScheduler {

    private lateinit var context: Context
    private val schedulerScope = CoroutineScope(Dispatchers.Default)
    private var heartbeatJob: Job? = null

    /**
     * Gives the scheduler the app's context, before any tick: the process may be started by the
     * alarm alone, with no screen to do it.
     */
    fun attach(appContext: Context) {
        context = appContext.applicationContext
    }

    /**
     * Starts the internal 1-minute heartbeat for app-open scenarios.
     *
     * @param appContext Application context
     */
    fun initialize(appContext: Context) {
        attach(appContext)
        startHeartbeat()
        LogManager.service("CoreScheduler initialized with 1-minute internal heartbeat", "INFO")
    }

    /**
     * Start internal heartbeat coroutine.
     * Ticks every 1 minute when app is open for better reactivity.
     */
    private fun startHeartbeat() {
        // Cancel existing heartbeat if any
        heartbeatJob?.cancel()

        heartbeatJob = schedulerScope.launch {
            LogManager.service("CoreScheduler: Starting 1-minute heartbeat", "INFO")

            while (true) {
                try {
                    // Wait 1 minute
                    delay(60_000L)

                    tick()

                } catch (e: kotlinx.coroutines.CancellationException) {
                    LogManager.service("CoreScheduler: Heartbeat cancelled", "INFO")
                    throw e // Re-throw to stop the loop
                } catch (e: Exception) {
                    LogManager.service("CoreScheduler: Heartbeat error: ${e.message}", "ERROR", e)
                    // Continue despite error
                }
            }
        }
    }

    /**
     * Main tick function called periodically.
     *
     * Execution order:
     * 1. AI scheduling (core functionality, high priority)
     * 2. Tool scheduling (discovery via ToolTypeManager)
     * 3. The automatic backup (AutoBackup)
     *
     * Nothing while the demo is being written (DemoService.writing).
     *
     * Error handling: Each component logs errors independently,
     * one failure doesn't block others.
     */
    suspend fun tick() = kotlinx.coroutines.withContext(app.treelune.core.coordinator.Origin(app.treelune.core.coordinator.Source.SCHEDULER)) {
        // Everything a tick starts is the scheduler's, unless it is the AI's (processAICommand)

        // The demo being written, nothing is acted on: the next tick finds it whole
        if (app.treelune.core.demo.DemoService.writing) {
            LogManager.service("CoreScheduler: tick skipped, the demo is being written", "INFO")
            return@withContext
        }

        try {
            // 1. AI scheduling (AIOrchestrator handles automations + session management), set up
            // here when the alarm started the process and no screen did
            AIOrchestrator.initialize(context)
            AIOrchestrator.tick()

        } catch (e: Exception) {
            LogManager.service("CoreScheduler: AI scheduling error: ${e.message}", "ERROR", e)
            // Continue to tool scheduling despite AI error
        }

        try {
            // 2. Tool scheduling (discovery pattern)

            val allTools = ToolTypeManager.getAllToolTypes()

            allTools.forEach { (toolTypeName, toolType) ->
                try {
                    val scheduler = toolType.getScheduler()
                    if (scheduler != null) {
                        scheduler.checkScheduled(context)
                    }
                } catch (e: Exception) {
                    LogManager.service(
                        "CoreScheduler: Error in scheduler for tool type '$toolTypeName': ${e.message}",
                        "ERROR",
                        e
                    )
                    // Continue to next tool despite error
                }
            }

        } catch (e: Exception) {
            LogManager.service("CoreScheduler: Tool scheduling error: ${e.message}", "ERROR", e)
        }

        // The alarm set again after every tick, the minute's included: what the screen added while
        // the app was open is known to it before the app closes
        armForNextAction(context)

        try {
            // 3. The automatic backup, last: the tick's other work is due at its minute, a copy within the hour
            app.treelune.core.backup.AutoBackup.tick(context)
        } catch (e: Exception) {
            LogManager.service("CoreScheduler: automatic backup error: ${e.message}", "ERROR", e)
        }

    }

    /**
     * Runs the scheduler of the tool type [tooltype] now, as the scheduler, rather than at the
     * next tick, then sets the alarm again: a tool's config just created or changed is acted on
     * at once (ToolInstanceService), a schedule given its first occurrences. Nothing for a tool
     * type without a scheduler, nor while the demo is being written. [appContext] is given, the scheduler's own not being set yet
     * where no tick ran.
     */
    suspend fun runTool(appContext: Context, tooltype: String) {
        val scheduler = ToolTypeManager.getToolType(tooltype)?.getScheduler() ?: return
        // The demo's tools are acted on at the first tick after it is written whole (tick)
        if (app.treelune.core.demo.DemoService.writing) return
        kotlinx.coroutines.withContext(app.treelune.core.coordinator.Origin(app.treelune.core.coordinator.Source.SCHEDULER)) {
            try {
                scheduler.checkScheduled(appContext)
            } catch (e: Exception) {
                LogManager.service("CoreScheduler: scheduler of tool type '$tooltype' run at once failed: ${e.message}", "ERROR", e)
            }
            armForNextAction(appContext)
        }
    }

    /**
     * Sets the alarm for the soonest of the tool schedulers' next actions (ToolScheduler.nextAction),
     * within its usual interval. A scheduler that fails to say is logged and left out.
     */
    private suspend fun armForNextAction(appContext: Context) {
        val next = ToolTypeManager.getAllToolTypes().mapNotNull { (toolTypeName, toolType) ->
            try {
                toolType.getScheduler()?.nextAction(appContext)
            } catch (e: Exception) {
                LogManager.service("CoreScheduler: next action of tool type '$toolTypeName' unknown: ${e.message}", "ERROR", e)
                null
            }
        }.filter { it > System.currentTimeMillis() }.minOrNull()
        SchedulerAlarm.arm(appContext, next)
    }

    /**
     * Shutdown the scheduler and cleanup resources.
     * Cancels the internal heartbeat coroutine.
     */
    fun shutdown() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        LogManager.service("CoreScheduler shutdown", "INFO")
    }
}
