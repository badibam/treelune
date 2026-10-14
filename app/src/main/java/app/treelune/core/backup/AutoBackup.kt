package app.treelune.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.treelune.core.backup.AutoBackupRotation.Slot
import app.treelune.core.config.AppSettings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.strings.Strings
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import org.json.JSONObject
import java.io.File

/**
 * The automatic backup (docs/DATA.md, « Sauvegarde automatique »): the backup zip of the manual
 * export, written into the folder of the phone chosen once (AppSettings.DATA_FOLDER), at most once
 * an hour and only when the data changed, rolled over four files (AutoBackupRotation).
 *
 * The scheduler's tick asks [tick]; the copy itself is the long operation `backup.auto`, never run
 * beside an import, the demo or a manual export. What the folder holds is read from its files'
 * names: no state of the rolling is kept but the hash of the last data copied, on this phone
 * ([STORE], outside the database, which a backup would carry and the copy would change).
 */
object AutoBackup {

    /** One copy of the rolling in the folder: its document, its moment. */
    data class Copy(val document: Uri, val at: Long)

    /** Where the automatic backup stands. */
    sealed interface State {
        /** No folder chosen */
        data object NoFolder : State
        /** A folder, the automatic backup turned off */
        data class Off(val folder: Uri) : State
        /** A folder the app may no longer write in: its right taken away, or the folder gone */
        data class Lost(val folder: Uri) : State
        /** A folder written in, the copies it holds by slot */
        data class Ready(val folder: Uri, val copies: Map<Slot, Copy>) : State
    }

    /** This phone's note of the last copy: the hash of its data, and whether a lost folder was said. */
    private const val STORE = "auto_backup.json"
    private const val DATA_HASH = "data_hash"
    private const val LOST_SAID = "lost_said"

    /** The notification of a lost folder, one at a time. */
    private const val LOST_NOTIFICATION_ID = 7301

    /** Where the automatic backup stands, from the settings and the folder's files. */
    suspend fun state(context: Context): State {
        val result = Coordinator(context).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.DATA))
        @Suppress("UNCHECKED_CAST")
        val settings = (result.data?.get("settings") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: throw IllegalStateException(result.error ?: "data settings unread")
        val folder = settings.optString(AppSettings.DATA_FOLDER).takeIf { it.isNotEmpty() }?.let { Uri.parse(it) } ?: return State.NoFolder
        if (!settings.optBoolean(AppSettings.DATA_AUTO_BACKUP, true)) return State.Off(folder)
        if (!writable(context, folder)) return State.Lost(folder)
        val copies = try { list(context, folder) } catch (e: Exception) {
            LogManager.service("AutoBackup: the folder cannot be read: ${e.message}", "WARN")
            return State.Lost(folder)
        }
        return State.Ready(folder, copies)
    }

    /** Whether Android still gives the app the right to write in [folder]. */
    fun writable(context: Context, folder: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isWritePermission }

    /**
     * The scheduler's turn: a copy when one is due, the lost folder said once. Cheap when nothing
     * is due: the settings and the folder's names are read, nothing else.
     */
    suspend fun tick(context: Context) {
        when (val state = state(context)) {
            is State.Ready -> {
                note(context) { it.remove(LOST_SAID) }
                if (!AutoBackupRotation.due(System.currentTimeMillis(), state.copies.mapValues { it.value.at })) return
                val result = Coordinator(context).processScheduledTask("backup.auto")
                if (!result.isSuccess) LogManager.service("AutoBackup: no copy: ${result.error}", "WARN")
            }
            is State.Lost -> if (!read(context).optBoolean(LOST_SAID)) {
                val s = Strings.`for`(context = context)
                val sent = Coordinator(context).processScheduledTask("notifications.send", mapOf(
                    "title" to s.shared("auto_backup_lost_title"),
                    "content" to s.shared("auto_backup_lost_content"),
                    "priority" to "default",
                    "notification_id" to LOST_NOTIFICATION_ID
                ))
                if (sent.isSuccess) note(context) { it.put(LOST_SAID, true) }
            }
            State.NoFolder, is State.Off -> {}
        }
    }

    /** The copies of the rolling in [folder], by slot; any other file of it left alone. */
    fun list(context: Context, folder: Uri): Map<Slot, Copy> {
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val copies = mutableMapOf<Slot, Copy>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val (slot, at) = AutoBackupRotation.parse(cursor.getString(1), zone) ?: continue
                // Two files of one slot (one copied in by hand): the latest is the slot's
                if ((copies[slot]?.at ?: Long.MIN_VALUE) < at) copies[slot] = Copy(DocumentsContract.buildDocumentUriUsingTree(folder, cursor.getString(0)), at)
            }
        } ?: throw IllegalStateException("the folder's files cannot be listed")
        return copies
    }

    /** The hash of the data of the last copy written, null before the first. */
    fun lastHash(context: Context): String? = read(context).optString(DATA_HASH).takeIf { it.isNotEmpty() }

    /** [hash] noted as the last copy's. */
    fun copied(context: Context, hash: String) = note(context) { it.put(DATA_HASH, hash) }

    private fun read(context: Context): JSONObject {
        val file = File(context.filesDir, STORE)
        return if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }

    private fun note(context: Context, change: (JSONObject) -> Unit) {
        val json = read(context)
        val before = json.toString()
        change(json)
        if (json.toString() != before) File(context.filesDir, STORE).writeText(json.toString())
    }
}
