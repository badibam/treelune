package app.treelune.core.ai.orchestration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.treelune.MainActivity
import app.treelune.core.ai.data.SessionType
import app.treelune.core.ai.database.AIDao
import app.treelune.core.ai.domain.AIState
import app.treelune.core.ai.domain.AIStateMachine
import app.treelune.core.ai.domain.Phase
import app.treelune.core.ai.scheduling.SessionSlotPolicy
import app.treelune.core.icons.Icons
import app.treelune.core.notifications.NotificationService
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A routine (an AUTOMATION session) running: a foreground service and a partial wake lock, so
 * Android lets its round finish with no screen open. The scheduler's alarm keeps the app awake
 * for its tick alone (SchedulerAlarm, 50 s at most), and a round can last minutes. Its
 * notification names the routine.
 *
 * Started and stopped by [AutomationRun.follow], never by hand.
 */
class AutomationRunService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notification(intent?.getStringExtra(EXTRA_ROUTINE_NAME))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Taken again at each start, a routine following another one starting its time over
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_LIMIT)
        }
        // Stopped by Android, it is not started again by itself: the next round starts it
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    /** The notification of the routine running, under its name. */
    private fun notification(routineName: String?): Notification {
        val s = Strings.`for`(context = this)
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, s.shared("automation_run_channel"), NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(Icons.lucideDrawable(this, NotificationService.APP_ICON) ?: android.R.drawable.ic_dialog_info)
            .setContentTitle(routineName ?: s.shared("automation_run_notification_text"))
            .setContentText(routineName?.let { s.shared("automation_run_notification_text") })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val EXTRA_ROUTINE_NAME = "routine_name"
        const val CHANNEL = "treelune_automation_run"
        private const val NOTIFICATION_ID = 0x4152 // "AR"
        private const val WAKE_LOCK_TAG = "treelune:automation_run"

        /**
         * The longest the phone is kept awake for one routine: its working time
         * (SessionSlotPolicy.AUTOMATION_GLOBAL_TIMEOUT), its wait for the network, and room for
         * the watchdog, which only sees the overrun at a heartbeat.
         */
        private const val WAKE_LOCK_LIMIT = SessionSlotPolicy.AUTOMATION_GLOBAL_TIMEOUT + AIStateMachine.AUTOMATION_NETWORK_WAIT_LIMIT + 3 * 60_000L
    }
}

/**
 * Runs [AutomationRunService] while a routine runs, read off the AI's state: on from the moment
 * an AUTOMATION session is active to the moment the slot is free again. A routine never waits
 * for the user (no validation, no question, no data confirmation: AI.md), so it has nothing to
 * do only once closed.
 */
object AutomationRun {

    /**
     * How long the service outlives a routine: the next one starts at once after the closing
     * (AIEventProcessor.processNextSessionActivation), and Android refuses to start the service
     * again from the background, outside the alarm's own moment.
     */
    private const val STOP_DELAY = 2_000L

    /** The routine's session running, or null. */
    private fun runningRoutine(state: AIState): String? =
        state.sessionId?.takeIf { state.sessionType == SessionType.AUTOMATION && state.phase != Phase.IDLE && state.phase != Phase.CLOSED }

    /** Follows [state] on [scope] for as long as the process lives. */
    fun follow(context: Context, state: StateFlow<AIState>, aiDao: AIDao, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            state.map { runningRoutine(it) }.distinctUntilChanged().collectLatest { sessionId ->
                if (sessionId != null) {
                    start(appContext, routineName(aiDao, sessionId))
                } else {
                    delay(STOP_DELAY)
                    appContext.stopService(Intent(appContext, AutomationRunService::class.java))
                }
            }
        }
    }

    private suspend fun routineName(aiDao: AIDao, sessionId: String): String? {
        val automationId = aiDao.getSession(sessionId)?.automationId
        val name = automationId?.let { aiDao.getAutomationById(it)?.name }
        if (name == null) LogManager.aiSession("AutomationRun: no routine found for session $sessionId (automation $automationId)", "ERROR")
        return name
    }

    private fun start(context: Context, routineName: String?) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, AutomationRunService::class.java).putExtra(AutomationRunService.EXTRA_ROUTINE_NAME, routineName))
        } catch (e: Exception) {
            // Android refuses a foreground service started from the background, outside the
            // alarm's moment: a routine started by the in-app heartbeat once the app is left.
            // The round runs on unprotected, as long as Android lets the process live.
            LogManager.aiSession("AutomationRun: the service could not start, the routine may be cut: ${e.message}", "ERROR", e)
        }
    }
}
