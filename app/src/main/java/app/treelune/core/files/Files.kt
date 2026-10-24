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
 *   is written, which moves it into `entries/`; one the form never wrote is swept (INFO).
 */
object Files {

    private const val DIR = "attachments"
    private const val ENTRIES = "entries"
    private const val PENDING = "pending"
    private const val PART = ".part"

    /** The folder of the messages' files, holding the two others. */
    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun entriesDir(context: Context): File = File(dir(context), ENTRIES)

    fun pendingDir(context: Context): File = File(dir(context), PENDING)

    /** The file of [row], in its owner's folder. */
    fun file(context: Context, row: FileEntity): File =
        File(if (row.sessionId == null) entriesDir(context) else dir(context), row.fileFormat.fileName(row.id))

    /** The file of a message's file [id], written as [format]. */
    fun sessionFile(context: Context, id: String, format: FileFormat): File = File(dir(context), format.fileName(id))

    /** The file prepared as [id] in a form, none written by any entry yet; null when there is none. */
    fun pendingFile(context: Context, id: String): File? =
        FileFormat.entries.map { File(pendingDir(context), it.fileName(id)) }.firstOrNull { it.exists() }

    /**
     * The file [id] wherever it is: its row's, or the one a form prepared. Null when neither
     * exists: a row without its file is an error the caller says.
     */
    suspend fun locate(context: Context, id: String): File? {
        AppDatabase.getDatabase(context).fileDao().getById(id)?.let { return file(context, it).takeIf { it.exists() } }
        return pendingFile(context, id)
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

    /** The text of the TEXT file [id], read whole; null when no text bears that id. */
    suspend fun textOf(context: Context, id: String): String? =
        AppDatabase.getDatabase(context).fileDao().getById(id)?.takeIf { it.fileKind == FileKind.TEXT }?.let { text(context, it) }

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

    /** The file of [row] deleted; one already gone is no error. */
    fun delete(context: Context, row: FileEntity) = deleteFile(file(context, row))

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
        fun names(owned: List<FileEntity>) = owned.map { it.fileFormat.fileName(it.id) }.toSet()
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
