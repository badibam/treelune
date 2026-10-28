package app.treelune.core.scheduling

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import java.io.File

/**
 * Whether the app runs in the background without Android holding it back: its Battery setting at
 * « Unrestricted », which the scheduler's alarm (SchedulerAlarm) is surest to ring under while the
 * app is closed. Below it, what is planned to happen app closed may not: scheduled routines,
 * messages, lists' due dates, questionnaires' and sessions' invitations, goals' ends of period.
 *
 * Measured on a phone (Android 16): « Restricted » blocks the alarm, and as each ring sets the
 * next, nothing wakes the app until it is opened; « Optimized », the default, let it ring on time
 * in deep sleep there, which other makers may not.
 *
 * The user is told once each time the app finds itself held back: a mark kept on this phone alone
 * says it was told, cleared when the app is found unrestricted again.
 */
object BackgroundRestriction {

    private const val WARNED_FILE = "background_restriction_warned"

    /** Whether Android may hold the app back in the background: it is not « Unrestricted ». */
    fun isHeldBack(context: Context): Boolean =
        !context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Whether the warning is to be shown now: held back, and not told since it began. Found
     * unrestricted, the mark is cleared, so that being held back again is told again.
     */
    fun warningDue(context: Context): Boolean {
        val mark = File(context.noBackupFilesDir, WARNED_FILE)
        if (!isHeldBack(context)) {
            mark.delete()
            return false
        }
        return !mark.exists()
    }

    /** The user was told: not again until the app is unrestricted and then held back anew. */
    fun markWarned(context: Context) {
        File(context.noBackupFilesDir, WARNED_FILE).writeText("")
    }

    /**
     * Android's own question, « Allow the app to always run in the background? »: accepted, the
     * app is « Unrestricted » in one touch, « Restricted » lifted with it.
     */
    fun requestUnrestricted(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
