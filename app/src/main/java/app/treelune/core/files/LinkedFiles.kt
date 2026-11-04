package app.treelune.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.utils.LogManager
import org.json.JSONObject
import java.util.UUID

/**
 * The files of the phone a FILE field links to rather than copies (docs/design/linked-files.md):
 * a sound or a video in a folder opened to the app, read where it is through its address, never
 * written nor deleted by the app.
 *
 * - A folder is opened by the user, in Settings › Data, through Android's folder picker: the app
 *   keeps the right to read it ([OpenedFolder], AppSettings.DATA_OPENED_FOLDERS). A linked file
 *   always comes from one of them, from the screen as from the connector: one right, the folder's.
 * - A link is the address of the file in its folder's tree (`buildDocumentUriUsingTree`), found
 *   from the folder and the path under it ([prepare]).
 * - Prepared, a link waits in the pending folder as `<id>.link`, the description of the file it
 *   designates; the write of the entry that holds its id makes it the entry's row (OwnedFiles),
 *   and the startup sweep removes one never written, as a prepared file.
 * - A link whose file can no longer be read (deleted, renamed, moved, its folder closed) is
 *   « not found »: the phone's state, said where the file is shown or played, never repaired.
 */
object LinkedFiles {

    /** The kinds a field links: the heavy ones. An image or a text is small, and read through a file of the app. */
    val KINDS = listOf(FileKind.AUDIO, FileKind.VIDEO)

    /** A folder opened to the app: its [id], the address of its tree, and its [name] as the picker gave it. */
    data class OpenedFolder(val id: String, val uri: String, val name: String) {
        val tree: Uri get() = Uri.parse(uri)
    }

    /** What a folder holds, one level: a subfolder, or a file with what is known of it. */
    sealed interface Item {
        val name: String
        data class Folder(override val name: String) : Item
        /** A file; [format] null when no format of the app takes it. */
        data class File(override val name: String, val format: FileFormat?, val sizeBytes: Long?) : Item
    }

    /** The folders opened to the app, from the data settings, in the order they were opened. */
    suspend fun folders(context: Context): List<OpenedFolder> {
        val json = AppDatabase.getDatabase(context).appSettingsCategoryDao().getSettingsJsonForCategory(AppSettingCategories.DATA)
            ?: return emptyList()
        val list = JSONObject(json).optJSONArray(AppSettings.DATA_OPENED_FOLDERS) ?: return emptyList()
        return (0 until list.length()).map { list.getJSONObject(it) }.map {
            OpenedFolder(it.getString("id"), it.getString("uri"), it.getString("name"))
        }
    }

    /** [folders] as the data settings hold them. */
    fun stored(folders: List<OpenedFolder>): List<Map<String, Any>> =
        folders.map { mapOf("id" to it.id, "uri" to it.uri, "name" to it.name) }

    /**
     * The folder [tree], chosen in Android's folder picker, opened to the app: the right to read
     * it kept, then added to the data settings, through the settings service as the user. One
     * already open stays as it is. The refusal's message, null once saved.
     */
    suspend fun open(context: Context, tree: Uri, text: (String) -> String): String? {
        try {
            context.contentResolver.takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            return text("opened_folders_refused")
        }
        val folders = folders(context)
        if (folders.any { it.uri == tree.toString() }) return null
        return save(context, folders + OpenedFolder(UUID.randomUUID().toString(), tree.toString(), folderName(context, tree)))
    }

    /** How many links designate a file of [folder], which closing it leaves unread. */
    suspend fun linksIn(context: Context, folder: OpenedFolder): Int =
        AppDatabase.getDatabase(context).fileDao().countLinkedIn(folder.uri)

    /**
     * [folder] closed: out of the data settings, then its right given back. Its links stay, and
     * read « not found » (docs/design/dependents.md: said before, nothing cleaned). The refusal's
     * message, null once saved.
     */
    suspend fun close(context: Context, folder: OpenedFolder): String? {
        save(context, folders(context).filter { it.id != folder.id })?.let { return it }
        try {
            context.contentResolver.releasePersistableUriPermission(folder.tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            LogManager.service("Opened folder ${folder.name}: its right was already gone", "INFO")
        }
        return null
    }

    /** The data settings with [folders] as the opened ones, the others as they are. */
    private suspend fun save(context: Context, folders: List<OpenedFolder>): String? {
        val coordinator = app.treelune.core.coordinator.Coordinator(context)
        val read = coordinator.processUserAction("app_config.get", mapOf("category" to AppSettingCategories.DATA))
        if (read.status != app.treelune.core.commands.CommandStatus.SUCCESS) return read.error
        @Suppress("UNCHECKED_CAST")
        val settings = (read.data!!["settings"] as Map<String, Any?>).toMutableMap().apply { put(AppSettings.DATA_OPENED_FOLDERS, stored(folders)) }
        val result = coordinator.processUserAction("app_config.set", mapOf("category" to AppSettingCategories.DATA, "settings" to settings))
        return if (result.status == app.treelune.core.commands.CommandStatus.SUCCESS) null else result.error
    }

    /** How many links the phone no longer has, read each: said after a backup is restored. */
    suspend fun unreachable(context: Context): Int =
        AppDatabase.getDatabase(context).fileDao().getLinked().count { !readable(context, Uri.parse(it.uri)) }

    /** The name Android gives the folder [tree], its last part when it says none. */
    fun folderName(context: Context, tree: Uri): String {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return context.contentResolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        } ?: DocumentsContract.getTreeDocumentId(tree).substringAfterLast('/').substringAfterLast(':')
    }

    /** Whether the file at [uri] can be read now: opened then closed at once. */
    fun readable(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        // Deleted, renamed, moved, or its folder closed: the phone's state, not the app's error
        LogManager.service("Linked file $uri: not readable: ${e.message}", "WARN")
        false
    }

    /** Why a path cannot be followed in a folder. */
    class NotFound(message: String) : Exception(message)

    /** The parts of [path], a path under a folder: no empty part, no `.` nor `..`. */
    fun parts(path: String): List<String> {
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == "." || it == ".." }) throw NotFound(path)
        return parts
    }

    /**
     * The document [path] designates under [folder], followed name by name from its root, and
     * whether it is a folder; [NotFound] when a name is not there.
     */
    private fun follow(context: Context, folder: OpenedFolder, path: String): Pair<String, Boolean> {
        var documentId = DocumentsContract.getTreeDocumentId(folder.tree)
        var isFolder = true
        for (part in parts(path)) {
            if (!isFolder) throw NotFound(path)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.tree, documentId)
            val found = context.contentResolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                var match: Pair<String, Boolean>? = null
                while (match == null && cursor.moveToNext()) {
                    if (cursor.getString(1) == part) match = cursor.getString(0) to (cursor.getString(2) == Document.MIME_TYPE_DIR)
                }
                match
            } ?: throw NotFound(path)
            documentId = found.first
            isFolder = found.second
        }
        return documentId to isFolder
    }

    /**
     * What [path] holds under [folder], one level: its subfolders, then its files, each by name.
     *
     * @throws NotFound when the path is no folder there
     * @throws SecurityException when the folder is no longer open to the app
     */
    fun list(context: Context, folder: OpenedFolder, path: String): List<Item> {
        val (documentId, isFolder) = follow(context, folder, path)
        if (!isFolder) throw NotFound(path)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.tree, documentId)
        val items = mutableListOf<Item>()
        context.contentResolver.query(children, arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name: String = cursor.getString(0) ?: continue
                val type: String? = cursor.getString(1)
                val size: Long? = if (cursor.isNull(2)) null else cursor.getLong(2)
                items.add(if (type == Document.MIME_TYPE_DIR) Item.Folder(name) else Item.File(name, FileFormat.ofMedia(type, name), size))
            }
        } ?: throw NotFound(path)
        return items.filterIsInstance<Item.Folder>().sortedBy { it.name.lowercase() } +
            items.filterIsInstance<Item.File>().sortedBy { it.name.lowercase() }
    }

    /** A link prepared, or why it could not be. */
    sealed interface Result {
        class Done(val id: String, val description: FileDescription) : Result
        class Refused(val message: String) : Result
    }

    /**
     * The file [path] under [folder] prepared as a link, if it is a sound or a video of [kinds]:
     * its length read where it is, its description waiting as `<id>.link` in the pending folder.
     *
     * @param text The shared strings, for a refusal
     */
    fun prepare(context: Context, folder: OpenedFolder, path: String, kinds: Collection<FileKind>, text: (String) -> String): Result {
        val (documentId, isFolder) = try {
            follow(context, folder, path)
        } catch (e: NotFound) {
            return Result.Refused(text("file_link_not_found").format(path, folder.name))
        } catch (e: SecurityException) {
            return Result.Refused(text("file_link_folder_closed").format(folder.name))
        }
        if (isFolder) return Result.Refused(text("file_link_is_folder").format(path))
        val uri = DocumentsContract.buildDocumentUriUsingTree(folder.tree, documentId)
        val (name, type, size) = context.contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else Triple(cursor.getString(0) ?: path.substringAfterLast('/'), cursor.getString(1), if (cursor.isNull(2)) 0L else cursor.getLong(2))
        } ?: return Result.Refused(text("file_link_not_found").format(path, folder.name))
        val format = FileFormat.ofMedia(type, name)
        val linkable = kinds.filter { it in KINDS }
        if (format == null || format.kind !in linkable) {
            return Result.Refused(text("file_error_not_accepted").format(name, linkable.joinToString(", ") { text("file_kind_${it.name.lowercase()}") }))
        }
        val description = try {
            FileDescription.readLinked(context, uri, format, name, size)
        } catch (e: IllegalArgumentException) {
            return Result.Refused(text("file_error_not_readable").format(name, e.message ?: ""))
        }
        val id = UUID.randomUUID().toString()
        Files.write(Files.pendingLink(context, id).apply { parentFile?.mkdirs() }, JSONObject()
            .put("uri", uri.toString())
            .put("format", description.format.name)
            .put("name", name)
            .put("size_bytes", description.sizeBytes)
            .put("duration_ms", description.durationMs)
            .toString().toByteArray(Charsets.UTF_8))
        return Result.Done(id, description)
    }

    /** The link prepared as [id]: its address and the description of its file; null when there is none. */
    fun pending(context: Context, id: String): Pair<Uri, FileDescription>? {
        val file = Files.pendingLink(context, id).takeIf { it.exists() } ?: return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return Uri.parse(json.getString("uri")) to FileDescription(
            FileFormat.valueOf(json.getString("format")), json.getString("name"), json.getLong("size_bytes"),
            durationMs = if (json.isNull("duration_ms")) null else json.getLong("duration_ms"))
    }

    /** A link [row] waiting as a prepared one under [id], for the owner a part's import makes (PartImport). */
    fun pendAs(context: Context, id: String, row: JSONObject) {
        Files.write(Files.pendingLink(context, id).apply { parentFile?.mkdirs() }, JSONObject()
            .put("uri", row.getString("uri"))
            .put("format", row.getString("format"))
            .put("name", row.optString("name"))
            .put("size_bytes", row.getLong("size_bytes"))
            .put("duration_ms", if (row.has("duration_ms") && !row.isNull("duration_ms")) row.getLong("duration_ms") else JSONObject.NULL)
            .toString().toByteArray(Charsets.UTF_8))
    }

    /** The answer of a link prepared, as an upload's: its id and what goes with its kind. */
    fun answer(id: String, description: FileDescription): Map<String, Any> = buildMap {
        put("file_id", id)
        put("kind", description.kind.name)
        put("format", description.format.name)
        put("size_bytes", description.sizeBytes)
        description.name?.let { put("name", it) }
        // A duration faces the AI in ISO 8601, as an upload's answer gives it (McpUploads)
        description.durationMs?.let { put("duration", java.time.Duration.ofMillis(it).toString()) }
    }
}
