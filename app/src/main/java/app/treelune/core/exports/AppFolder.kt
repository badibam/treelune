package app.treelune.core.exports

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.treelune.core.backup.AutoBackup
import app.treelune.core.utils.FileNames
import java.io.OutputStream

/**
 * The app's folder, chosen once in Settings › Data (AppSettings.DATA_FOLDER, the automatic
 * backups' too), where the files the app writes out go without asking anything: an export's CSV,
 * a part of the app to share.
 */
object AppFolder {

    /** The folder, or why it cannot be written in. */
    sealed interface Read {
        data class Ready(val folder: Uri) : Read
        /** No folder chosen yet */
        object NoFolder : Read
        /** Chosen, its right lost (the app reinstalled, the folder moved) */
        object Lost : Read
    }

    suspend fun read(context: Context): Read {
        val folder = when (val state = AutoBackup.state(context)) {
            AutoBackup.State.NoFolder -> return Read.NoFolder
            is AutoBackup.State.Lost -> return Read.Lost
            is AutoBackup.State.Off -> state.folder
            is AutoBackup.State.Ready -> state.folder
        }
        return if (AutoBackup.writable(context, folder)) Read.Ready(folder) else Read.Lost
    }

    /**
     * [folder] made the app's folder and the automatic backup switched [autoBackup], the other data
     * settings as they are (the history's depth), through the settings service as the user; the
     * refusal's message, null when saved.
     */
    suspend fun save(context: Context, folder: Uri, autoBackup: Boolean): String? {
        val coordinator = app.treelune.core.coordinator.Coordinator(context)
        val category = app.treelune.core.database.entities.AppSettingCategories.DATA
        val read = coordinator.processUserAction("app_config.get", mapOf("category" to category))
        if (read.status != app.treelune.core.commands.CommandStatus.SUCCESS) return read.error
        @Suppress("UNCHECKED_CAST")
        val settings = (read.data!!["settings"] as Map<String, Any?>).toMutableMap().apply {
            put(app.treelune.core.config.AppSettings.DATA_FOLDER, folder.toString())
            put(app.treelune.core.config.AppSettings.DATA_AUTO_BACKUP, autoBackup)
        }
        val result = coordinator.processUserAction("app_config.set", mapOf("category" to category, "settings" to settings))
        return if (result.status == app.treelune.core.commands.CommandStatus.SUCCESS) null else result.error
    }

    /**
     * A file written by [content] in [folder] under `[base].[extension]`, or `[base]_2`… when the
     * name is taken (FileNames.unique), through a `.part` file renamed once complete: a file of
     * the final name is complete, one that failed midway is removed. The document and its name.
     */
    fun write(context: Context, folder: Uri, base: String, extension: String, content: (OutputStream) -> Unit): Pair<Uri, String> {
        val resolver = context.contentResolver
        val name = FileNames.unique(base, extension, namesIn(context, folder))
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        // A type of no extension: the folder's provider would otherwise add the extension after ".part"
        val part = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$name.part")
            ?: throw IllegalStateException("$name.part not created")
        try {
            (resolver.openOutputStream(part) ?: throw IllegalStateException("$name.part not opened")).use(content)
            val done = DocumentsContract.renameDocument(resolver, part, name) ?: throw IllegalStateException("$name.part not renamed")
            return done to name
        } catch (e: Exception) {
            DocumentsContract.deleteDocument(resolver, part)
            throw e
        }
    }

    /** The names of the files in [folder]. */
    private fun namesIn(context: Context, folder: Uri): Set<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        return context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        } ?: throw IllegalStateException("the folder's files cannot be listed")
    }
}
