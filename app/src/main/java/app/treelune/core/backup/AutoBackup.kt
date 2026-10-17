package app.treelune.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.treelune.core.backup.AutoBackupRotation.Slot
import app.treelune.core.config.AppSettings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.AppSettingCategories
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
 *
 * No folder chosen, or the folder lost, is something to look at in Settings › Data: [pending]
 * marks the way there (the settings button of the home screen, the Data tile) until the screen
 * is opened ([seen]), the case seen kept in AppSettings.DATA_SEEN; another case marks it again.
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

    /** This phone's note of the last copy: the hash of its data, and when the data was last compared to it. */
    private const val STORE = "auto_backup.json"
    private const val DATA_HASH = "data_hash"
    private const val CHECKED_AT = "checked_at"

    private val _pending = MutableStateFlow(false)
    /**
     * Whether Settings › Data has something not yet looked at: no folder chosen, or the folder lost.
     * A flow, not a Compose state: a state held by this object would be made on its first touch,
     * which may fall in a recomposition, and read from another snapshot it throws.
     */
    val pending: StateFlow<Boolean> = _pending

    /** The data settings as stored. */
    private suspend fun settings(context: Context): JSONObject {
        val result = Coordinator(context).processUserAction("app_config.get", mapOf("category" to AppSettingCategories.DATA))
        @Suppress("UNCHECKED_CAST")
        return (result.data?.get("settings") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: throw IllegalStateException(result.error ?: "data settings unread")
    }

    /** The case to look at in [settings], by a key that changes when the case does; null when there is none. */
    private fun attention(context: Context, settings: JSONObject): String? {
        val folder = settings.optString(AppSettings.DATA_FOLDER).takeIf { it.isNotEmpty() } ?: return "no_folder"
        if (!settings.optBoolean(AppSettings.DATA_AUTO_BACKUP, true)) return null
        return if (writable(context, Uri.parse(folder))) null else "lost:$folder"
    }

    /** [pending] read again: the case to look at, unless it is the one seen. */
    suspend fun refresh(context: Context) {
        _pending.value = try {
            val settings = settings(context)
            attention(context, settings)?.let { it != settings.optString(AppSettings.DATA_SEEN) } ?: false
        } catch (e: Exception) {
            LogManager.service("AutoBackup: the mark not read: ${e.message}", "WARN")
            false
        }
    }

    /** Settings › Data opened: its case is seen, and the mark goes. */
    suspend fun seen(context: Context) {
        val settings = settings(context)
        val case = attention(context, settings)
        if (case != null && case != settings.optString(AppSettings.DATA_SEEN)) {
            val result = Coordinator(context).processUserAction("app_config.set", mapOf(
                "category" to AppSettingCategories.DATA,
                "settings" to JsonUtils.toMap(settings.put(AppSettings.DATA_SEEN, case))
            ))
            if (!result.isSuccess) LogManager.service("AutoBackup: the case seen not written: ${result.error}", "WARN")
        }
        _pending.value = false
    }

    /** Where the automatic backup stands, from the settings and the folder's files. */
    suspend fun state(context: Context): State {
        val settings = settings(context)
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
     * The scheduler's turn: a copy when one is due. Cheap when nothing is due: the settings and
     * the folder's names are read, nothing else. A folder lost is marked ([refresh]), not notified.
     */
    suspend fun tick(context: Context) {
        val state = state(context)
        if (state is State.Ready && due(context, System.currentTimeMillis(), state.copies)) {
            val result = Coordinator(context).processScheduledTask("backup.auto")
            if (!result.isSuccess) LogManager.service("AutoBackup: no copy: ${result.error}", "WARN")
        }
        refresh(context)
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

    /**
     * Whether a copy is due at [now]: the rolling asks for one, and the data was not found unchanged
     * within the hour — comparing it reads every table, so data left alone is read once an hour.
     */
    fun due(context: Context, now: Long, copies: Map<Slot, Copy>): Boolean =
        AutoBackupRotation.due(now, copies.mapValues { it.value.at }) &&
            read(context).optLong(CHECKED_AT, 0L).let { now - it >= Slot.HOUR.period }

    /** The data found unchanged at [now]: no copy, and no other comparison within the hour. */
    fun unchanged(context: Context, now: Long) = note(context) { it.put(CHECKED_AT, now) }

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
