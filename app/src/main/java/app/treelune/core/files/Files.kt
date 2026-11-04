package app.treelune.core.files

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.utils.LogManager
import java.io.File

/**
 * The files the app keeps (FileEntity describes each), one per row, named by its id and its
 * format's extension (docs/DATA.md, « FILE »).
 *
 * Three folders, by who owns the file:
 * - `files/attachments/`: a message's files, deleted with their session by the service that
 *   deletes it; a file there without its row is a hard stop or a forgotten deletion (WARN);
 * - `files/attachments/entries/`: an entry's files, and a tool's (those its config holds); an
 *   entry or a tool goes by many paths (itself, its tool, its zone), its rows by cascade, its
 *   files at the next startup sweep (INFO);
 * - `files/attachments/pending/`: a file prepared in a form, owned by no row until the entry
 *   is written, which moves it into `entries/`; one the form never wrote is swept (INFO). A link
 *   prepared waits there too, as `<id>.link` (LinkedFiles), the description of the phone's file.
 *
 * A linked file (FileEntity.uri) has no file here: it is read where it is, through its address
 * ([Source]), and never written nor deleted by the app (docs/design/linked-files.md).
 */
object Files {

    private const val DIR = "attachments"
    private const val ENTRIES = "entries"
    private const val PENDING = "pending"
    private const val PART = ".part"
    private const val NAME = ".name"
    private const val LINK = ".link"

    /** The folder of the messages' files, holding the two others. */
    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun entriesDir(context: Context): File = File(dir(context), ENTRIES)

    fun pendingDir(context: Context): File = File(dir(context), PENDING)

    /**
     * The file of [row], in its owner's folder.
     *
     * @throws IllegalStateException for a linked file, which has none of the app's
     */
    fun file(context: Context, row: FileEntity): File {
        check(!row.isLinked) { "File ${row.id}: a link, read through its address (Files.source)" }
        return File(if (row.sessionId == null) entriesDir(context) else dir(context), row.fileFormat.fileName(row.id))
    }

    /**
     * Where a file is read from: [file], one the app keeps, or only [uri], the address of a
     * linked file of the phone. A player or a reader that takes an address reads both by [uri].
     */
    class Source(val file: File?, val uri: android.net.Uri)

    /** The source of [row]: its file, kept or linked; null when it cannot be read (a link whose file is gone). */
    fun source(context: Context, row: FileEntity): Source? {
        val linked = row.uri ?: return file(context, row).takeIf { it.exists() }?.let { Source(it, android.net.Uri.fromFile(it)) }
        val uri = android.net.Uri.parse(linked)
        return Source(null, uri).takeIf { LinkedFiles.readable(context, uri) }
    }

    /** The file of a message's file [id], written as [format]. */
    fun sessionFile(context: Context, id: String, format: FileFormat): File = File(dir(context), format.fileName(id))

    /** The file prepared as [id] in a form, none written by any entry yet; null when there is none. */
    fun pendingFile(context: Context, id: String): File? =
        FileFormat.entries.map { File(pendingDir(context), it.fileName(id)) }.firstOrNull { it.exists() }

    /** Where the link prepared as [id] waits (LinkedFiles), the description of the file it designates. */
    fun pendingLink(context: Context, id: String): File = File(pendingDir(context), "$id$LINK")

    /** Where the name of the file prepared as [id] waits beside it, for a file that came with one. */
    fun pendingName(context: Context, id: String): File = File(pendingDir(context), "$id$NAME")

    /**
     * The file [id] wherever it is: its row's, or the one a form prepared, a link prepared
     * included. Null when none can be read: a row without its file is an error the caller says,
     * a link whose file is gone is the phone's state ([isLink] tells the two apart).
     */
    suspend fun locate(context: Context, id: String): Source? {
        AppDatabase.getDatabase(context).fileDao().getById(id)?.let { return source(context, it) }
        LinkedFiles.pending(context, id)?.let { (uri, _) -> return Source(null, uri).takeIf { LinkedFiles.readable(context, uri) } }
        return pendingFile(context, id)?.let { Source(it, android.net.Uri.fromFile(it)) }
    }

    /** Whether [id] is a link, kept or prepared: one that cannot be read is then « not found », not an error of the app. */
    suspend fun isLink(context: Context, id: String): Boolean =
        AppDatabase.getDatabase(context).fileDao().getById(id)?.isLinked ?: pendingLink(context, id).exists()

    /** A file found: where it is read from, and what is known of it. */
    class Found(val source: Source, val description: FileDescription) {
        /** Its file of the app; a linked file has none, and is never an image or a text (LinkedFiles.KINDS). */
        val file: File get() = source.file ?: error("A linked ${description.kind} has no file of the app")
    }

    /**
     * The file [id] wherever it is, with its description: its row's, or, for one a form prepared,
     * read from its file and its name beside it, or from the link prepared. Null when none can be
     * read, or a prepared file no longer reads as its kind: a row without its file is an error the
     * caller says, a link whose file is gone the phone's state ([isLink]).
     */
    suspend fun inspect(context: Context, id: String): Found? {
        AppDatabase.getDatabase(context).fileDao().getById(id)?.let { row ->
            return source(context, row)?.let { Found(it, FileDescription.of(row)) }
        }
        LinkedFiles.pending(context, id)?.let { (uri, description) ->
            return Found(Source(null, uri), description).takeIf { LinkedFiles.readable(context, uri) }
        }
        val pending = pendingFile(context, id) ?: return null
        val format = FileFormat.ofExtension(pending.extension) ?: return null
        val name = pendingName(context, id).takeIf { it.exists() }?.readText(Charsets.UTF_8)
        return try {
            Found(Source(pending, android.net.Uri.fromFile(pending)), FileDescription.read(pending, format, name))
        } catch (e: IllegalArgumentException) {
            LogManager.service("File $id prepared but no longer read: ${e.message}", "ERROR")
            null
        }
    }

    /**
     * The text of a TEXT file, read whole.
     *
     * @throws IllegalStateException if its file is missing: a row without its file is an error
     */
    fun text(context: Context, row: FileEntity): String {
        val file = file(context, row)
        check(file.exists()) { "File ${row.id}: its file is missing" }
        return file.readText(Charsets.UTF_8)
    }

    /**
     * The text of the TEXT file [id], read whole: one kept (joined to a message), or one set aside
     * and written by no entry yet (sent through the connector, McpUploads), which stays where it
     * is until the startup sweep, to be read again; null when no text bears that id.
     */
    suspend fun textOf(context: Context, id: String): String? {
        AppDatabase.getDatabase(context).fileDao().getById(id)?.let { row ->
            return row.takeIf { it.fileKind == FileKind.TEXT }?.let { text(context, it) }
        }
        return pendingFile(context, id)?.takeIf { FileFormat.ofExtension(it.extension)?.kind == FileKind.TEXT }?.readText(Charsets.UTF_8)
    }

    /**
     * [bytes] written as [target]: first as `<name>.part`, then renamed, so a final file is always
     * complete.
     */
    fun write(target: File, bytes: ByteArray) {
        target.parentFile!!.mkdirs()
        val part = File(target.parentFile, target.name + PART)
        part.writeBytes(bytes)
        check(part.renameTo(target)) { "File ${target.name}: ${part.name} not renamed" }
    }

    /**
     * [source] copied as [target], through a part renamed, as [write] does.
     */
    fun copy(source: File, target: File) {
        target.parentFile!!.mkdirs()
        val part = File(target.parentFile, target.name + PART)
        source.copyTo(part, overwrite = true)
        check(part.renameTo(target)) { "File ${target.name}: ${part.name} not renamed" }
    }

    /** The file of [row] deleted; one already gone is no error. A link's file is the phone's, never touched. */
    fun delete(context: Context, row: FileEntity) {
        if (!row.isLinked) deleteFile(file(context, row))
    }

    fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) LogManager.service("File ${file.name} not deleted", "ERROR")
    }

    /** Where a backup's files wait while its tables are imported: beside the folder, never swept. */
    fun importDir(context: Context): File = File(context.filesDir, "$DIR.import")

    /**
     * A backup's files, set aside flat in [staging], sorted the way the folder holds them: an
     * entry's or a tool's into `entries/`, by [ownedByEntries], the names of their files.
     */
    fun sortImport(staging: File, ownedByEntries: Collection<String>) {
        if (ownedByEntries.isEmpty()) return
        val entries = File(staging, ENTRIES).apply { mkdirs() }
        ownedByEntries.forEach { name ->
            val file = File(staging, name)
            if (file.exists()) check(file.renameTo(File(entries, name))) { "The backup's file $name could not be moved into place" }
        }
    }

    /**
     * The folder emptied, and given [replacement]'s files when there is one (a backup's, set
     * aside while its tables were imported).
     */
    fun replaceDir(context: Context, replacement: File?) {
        val dir = dir(context)
        check(!dir.exists() || dir.deleteRecursively()) { "The files' folder could not be emptied" }
        if (replacement != null && replacement.exists()) check(replacement.renameTo(dir)) { "The backup's files could not be moved into place" }
    }

    /** What the startup sweep removes from one folder: the parts left, and the files no row names. */
    data class Sweep(val parts: List<String>, val orphans: List<String>)

    /**
     * The files of a folder, by [names], that no row of [expected] (the names of their files)
     * is: a `.part` is a write the app was killed in; a kept file without a row is one whose
     * owner went. Anything else in the folder, its subfolders among them, is left alone.
     */
    fun sweep(names: List<String>, expected: Set<String>): Sweep = Sweep(
        parts = names.filter { it.endsWith(PART) },
        orphans = names.filter { name -> FileFormat.ofExtension(name.substringAfterLast('.', "")) != null && name !in expected }
    )

    @Volatile private var swept = false

    /**
     * The folders confronted with the table once per process, before any screen: nothing can be
     * joining a file or writing an entry then. Each file removed is logged: INFO for a part, a
     * form's file never written and an entry's file whose entry went, cases the design expects;
     * WARN by its name for a message's file without a row, which comes from a hard stop or a
     * deletion path that forgot the file. The rows of the files their entry no longer names go
     * too, with their files.
     *
     * What the history of writes still names stays, row and file: a file whose row a step keeps,
     * a file a copy of an entry names (docs/DATA.md, « History of writes ») — an undo puts them back.
     */
    suspend fun sweepOnce(context: Context) {
        if (swept) return
        swept = true
        val dao = AppDatabase.getDatabase(context).fileDao()

        // The app's own write, at its start: noted by the history, never a step
        val writes = app.treelune.core.history.Writes(context)
        val noted = app.treelune.core.history.Step(app.treelune.core.coordinator.Source.SYSTEM, isStep = false, sessionId = null)
        val history = AppDatabase.getDatabase(context).historyDao()
        val kept = history.keptFiles().toSet()
        dao.unnamedByTheirEntry().filterNot { history.ownersName(it.id) }.forEach { row ->
            kotlinx.coroutines.withContext(noted) { writes.deleteFile(row.id) }
            delete(context, row)
            LogManager.service("Startup sweep: file ${row.id} no longer named by its entry ${row.entryId}, removed", "INFO")
        }
        dao.unnamedByTheirTool().filterNot { history.ownersName(it.id) }.forEach { row ->
            kotlinx.coroutines.withContext(noted) { writes.deleteFile(row.id) }
            delete(context, row)
            LogManager.service("Startup sweep: file ${row.id} no longer named by its tool ${row.toolInstanceId}, removed", "INFO")
        }

        val rows = dao.getAll()
        fun names(owned: List<FileEntity>) = owned.filter { !it.isLinked }.map { it.fileFormat.fileName(it.id) }.toSet()
        // A file is named by its row's id and its format's extension
        fun keptByHistory(name: String) = name.substringBeforeLast('.') in kept

        dir(context).list()?.toList()?.let { found ->
            val sweep = sweep(found, names(rows.filter { it.sessionId != null }))
            sweep.parts.forEach { name ->
                File(dir(context), name).delete()
                LogManager.service("Startup sweep: interrupted file write $name removed", "INFO")
            }
            sweep.orphans.filterNot(::keptByHistory).forEach { name ->
                File(dir(context), name).delete()
                LogManager.service("Startup sweep: file $name named by no row, removed", "WARN")
            }
        }
        entriesDir(context).list()?.toList()?.let { found ->
            val sweep = sweep(found, names(rows.filter { it.sessionId == null }))
            (sweep.parts + sweep.orphans.filterNot(::keptByHistory)).forEach { name ->
                File(entriesDir(context), name).delete()
                LogManager.service("Startup sweep: entry or tool file $name, its owner or its write gone, removed", "INFO")
            }
        }
        pendingDir(context).list()?.toList()?.takeIf { it.isNotEmpty() }?.let { found ->
            pendingDir(context).deleteRecursively()
            LogManager.service("Startup sweep: ${found.size} files prepared in a form and never written, removed", "INFO")
        }
    }
}
