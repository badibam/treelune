package app.treelune.core.scheduling

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Whether Android holds the app back in the background, the phone's Battery setting at
 * « Restricted »: then the scheduler's alarm (SchedulerAlarm) does not ring while the app is
 * closed, and as each ring sets the next, nothing wakes the app until it is opened again. What is
 * planned to happen app closed may not: scheduled automations, messages, lists' due dates,
 * questionnaires' and sessions' invitations, goals' ends of period.
 *
 * Measured on a phone (Android 16): « Restricted » blocks the alarm; « Optimized », the default,
 * lets it ring on time in deep sleep. Only the first is told.
 *
 * The user is told once each time the restriction appears: a mark kept on this phone alone says
 * it was told, cleared when the app finds itself free again.
 */
object BackgroundRestriction {

    private const val WARNED_FILE = "background_restriction_warned"

    /** Android has no such setting before 9: nothing to tell there. */
    fun isRestricted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java).isBackgroundRestricted

    /**
     * Whether the warning is to be shown now: restricted, and not told since it began. Found free,
     * the mark is cleared, so that a restriction that comes back is told again.
     */
    fun warningDue(context: Context): Boolean {
        val mark = File(context.noBackupFilesDir, WARNED_FILE)
        if (!isRestricted(context)) {
            mark.delete()
            return false
        }
        return !mark.exists()
    }

    /** The user was told: not again until the restriction ends and comes back. */
    fun markWarned(context: Context) {
        File(context.noBackupFilesDir, WARNED_FILE).writeText("")
    }

    /** The app's page in Android's settings, where its Battery setting is. */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
