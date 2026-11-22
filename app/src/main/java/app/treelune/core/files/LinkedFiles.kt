package app.treelune.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.treelune.core.config.AppSettings
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.utils.LogManager
import org.json.JSONArray
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
 * - A link is the address of the file in its folder's tree (DocumentsContract.buildDocumentUriUsingTree), found
 *   from the folder and the path under it ([prepare]).
 * - Prepared, a link waits in the pending folder as `<id>.link`, the description of the file it
 *   designates; the write of the entry that holds its id makes it the entry's row (OwnedFiles),
 *   and the startup sweep removes one never written, as a prepared file.
 * - A link whose file can no longer be read (deleted, renamed, moved, its folder closed) is
 *   « not found »: the phone's state, said where the file is shown or played, never repaired.
 * - An expected link is described without its file ([readExpected], [expect]), from a library
 *   read on a computer: it has no address and behaves as a link not found, until a search finds
 *   its file by its name and size (LinkSearch).
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
        AppDatabase.getDatabase(context).fileDao().getLinked().count { !reads(context, it) }

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

    /** Whether the link [row] reads now: an expected link, without an address, never does. */
    fun reads(context: Context, row: FileEntity): Boolean = row.uri?.let { readable(context, Uri.parse(it)) } == true

    /** Whether a file's row in a backup is a link: one written before `linked` is by its address. */
    fun isLinkRow(row: JSONObject): Boolean = row.optBoolean("linked") || (row.has("uri") && !row.isNull("uri"))

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
        writePending(context, id, uri.toString(), description)
        return Result.Done(id, description)
    }

    /** The link [id] waiting as `<id>.link`, with its [uri] when it has one. */
    private fun writePending(context: Context, id: String, uri: String?, description: FileDescription) {
        val json = JSONObject()
            .put("format", description.format.name)
            .put("name", description.name)
            .put("size_bytes", description.sizeBytes)
            .put("duration_ms", description.durationMs ?: JSONObject.NULL)
        uri?.let { json.put("uri", it) }
        Files.write(Files.pendingLink(context, id).apply { parentFile?.mkdirs() }, json.toString().toByteArray(Charsets.UTF_8))
    }

    /** A list of expected files read, or why it is refused whole. */
    sealed interface Expected {
        class Read(val files: List<FileDescription>) : Expected
        class Refused(val message: String) : Expected
    }

    /**
     * The descriptions of [files], each with `name` (without its folder), `size_bytes`, `format`
     * (a sound's or a video's) and optionally `duration_ms`. One that does not read refuses all.
     *
     * @param text The shared strings, for a refusal
     */
    fun readExpected(files: JSONArray, text: (String) -> String): Expected {
        val formats = FileFormat.entries.filter { it.kind in KINDS }
        val read = (0 until files.length()).map { index ->
            val item = files.optJSONObject(index)
            val name = item?.takeIf { !it.isNull("name") }?.optString("name")?.trim()?.takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it }
                ?: return Expected.Refused(text("file_expect_name").format(index + 1))
            val size = (item.opt("size_bytes") as? Number)?.takeIf { it.toDouble() > 0 && it.toLong().toDouble() == it.toDouble() }?.toLong()
                ?: return Expected.Refused(text("file_expect_size").format(name))
            val format = formats.firstOrNull { it.name == item.optString("format") }
                ?: return Expected.Refused(text("file_expect_format").format(name, formats.joinToString(", ") { it.name }))
            // A duration the transformation could not read stays under `duration`
            if (item.has("duration")) return Expected.Refused(text("file_expect_duration").format(name))
            val duration = if (item.isNull("duration_ms")) null
                else (item.opt("duration_ms") as? Number)?.toLong()?.takeIf { it > 0 } ?: return Expected.Refused(text("file_expect_duration").format(name))
            FileDescription(format, name, size, durationMs = duration)
        }
        return Expected.Read(read)
    }

    /** Each of [files] prepared as an expected link, in the same order, with its id. */
    fun expect(context: Context, files: List<FileDescription>): List<Pair<String, FileDescription>> =
        files.map { description ->
            val id = UUID.randomUUID().toString()
            writePending(context, id, null, description)
            id to description
        }

    /** The link prepared as [id]: its address, none for an expected link, and the description of its file; null when there is none. */
    fun pending(context: Context, id: String): Pair<Uri?, FileDescription>? {
        val file = Files.pendingLink(context, id).takeIf { it.exists() } ?: return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return json.optString("uri").takeIf { json.has("uri") && it.isNotEmpty() }?.let(Uri::parse) to FileDescription(
            FileFormat.valueOf(json.getString("format")), json.getString("name"), json.getLong("size_bytes"),
            durationMs = if (json.isNull("duration_ms")) null else json.getLong("duration_ms"))
    }

    /** A link [row] waiting as a prepared one under [id], for the owner a part's import makes (PartImport). */
    fun pendAs(context: Context, id: String, row: JSONObject) {
        writePending(context, id, if (row.isNull("uri")) null else row.getString("uri"), FileDescription(
            FileFormat.valueOf(row.getString("format")), row.optString("name"), row.getLong("size_bytes"),
            durationMs = if (row.isNull("duration_ms")) null else row.getLong("duration_ms")))
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
