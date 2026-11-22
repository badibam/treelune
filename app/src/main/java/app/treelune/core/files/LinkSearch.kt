package app.treelune.core.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import app.treelune.core.database.AppDatabase
import app.treelune.core.history.Writes
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finding again the files of the links that no longer read (docs/design/linked-files.md,
 * « Relier un lien introuvable »): a file is recognised by its name and its size to the byte, in
 * every authorized folder, at any depth. Only the address of a link found changes: its description
 * stays, and a link not found keeps the address it had.
 */
object LinkSearch {

    /** A link to find again: its row's [id], and the [name] and [sizeBytes] of its file. */
    data class Wanted(val id: String, val name: String?, val sizeBytes: Long)

    /** A file seen in an authorized folder; [documentId] is the same through two folders that overlap. */
    data class Found(val documentId: String, val uri: String, val name: String, val sizeBytes: Long?)

    /**
     * What the files seen say of the links wanted.
     *
     * @property relinked The new address of each link with exactly one file of its name and size
     * @property notFound The links with no file of their name
     * @property otherSize The links whose name is there with another size: said, not taken
     * @property several The links with two files or more of their name and size: said, the app does not choose
     */
    data class Outcome(
        val relinked: Map<String, String>,
        val notFound: List<Wanted>,
        val otherSize: List<Wanted>,
        val several: List<Wanted>
    )

    /** Each link of [wanted] set against the files [found]. Two links may find the same file. */
    fun match(wanted: List<Wanted>, found: List<Found>): Outcome {
        val byName = found.distinctBy { it.documentId }.groupBy { it.name }
        val relinked = mutableMapOf<String, String>()
        val notFound = mutableListOf<Wanted>()
        val otherSize = mutableListOf<Wanted>()
        val several = mutableListOf<Wanted>()
        wanted.forEach { link ->
            val named = link.name?.let { byName[it] }.orEmpty()
            val same = named.filter { it.sizeBytes == link.sizeBytes }
            when {
                named.isEmpty() -> notFound += link
                same.isEmpty() -> otherSize += link
                same.size > 1 -> several += link
                else -> relinked[link.id] = same.single().uri
            }
        }
        return Outcome(relinked, notFound, otherSize, several)
    }

    /**
     * What a search did, as the screens show it: a count of the links relinked, the names of the others.
     *
     * @property checked How many links were looked at
     * @property foldersRefused The authorized folders Android no longer lets the app read
     */
    data class Report(
        val checked: Int,
        val relinked: Int,
        val notFound: List<String>,
        val otherSize: List<String>,
        val several: List<String>,
        val foldersRefused: List<String>
    ) {
        /** Whether something is left that a new search could find. */
        val unresolved: Boolean get() = notFound.isNotEmpty() || otherSize.isNotEmpty() || several.isNotEmpty() || foldersRefused.isNotEmpty()

        fun toMap(): Map<String, Any> = mapOf(
            "checked" to checked,
            "relinked" to relinked,
            "not_found" to notFound,
            "other_size" to otherSize,
            "several" to several,
            "folders_refused" to foldersRefused
        )

        companion object {
            /** A report as [toMap] gave it. */
            fun of(data: Map<*, *>): Report {
                fun names(key: String) = (data[key] as List<*>).map { it as String }
                return Report((data["checked"] as Number).toInt(), (data["relinked"] as Number).toInt(),
                    names("not_found"), names("other_size"), names("several"), names("folders_refused"))
            }
        }
    }

    /**
     * Checks each link, or those of [only], and relinks those that no longer read and whose file
     * is found. Never in the background: at an import and at the user's touch.
     *
     * @param cancelled Asked between two folders and two links
     * @return What it did; null when it was cancelled, the links relinked so far being none
     */
    suspend fun run(context: Context, only: Set<String>?, cancelled: () -> Boolean): Report? = withContext(Dispatchers.IO) {
        val links = AppDatabase.getDatabase(context).fileDao().getLinked().filter { only == null || it.id in only }
        val broken = mutableListOf<FileEntity>()
        for (link in links) {
            if (cancelled()) return@withContext null
            if (!LinkedFiles.readable(context, Uri.parse(link.uri))) broken += link
        }
        if (broken.isEmpty()) return@withContext Report(links.size, 0, emptyList(), emptyList(), emptyList(), emptyList())

        val names = broken.mapNotNull { it.name }.toSet()
        val found = mutableListOf<Found>()
        val refused = mutableListOf<String>()
        for (folder in LinkedFiles.folders(context)) {
            if (cancelled()) return@withContext null
            try {
                walk(context, folder.tree, names, found, cancelled)
            } catch (e: SecurityException) {
                LogManager.service("Link search: folder ${folder.name} no longer readable: ${e.message}", "WARN")
                refused += folder.name
            } catch (e: IllegalArgumentException) {
                // A provider's way of saying its tree is gone
                LogManager.service("Link search: folder ${folder.name} no longer readable: ${e.message}", "WARN")
                refused += folder.name
            }
        }
        if (cancelled()) return@withContext null

        val outcome = match(broken.map { Wanted(it.id, it.name, it.sizeBytes) }, found)
        val writes = Writes(context)
        outcome.relinked.forEach { (id, uri) -> writes.setFileUri(id, uri) }
        LogManager.service("Link search: ${links.size} links, ${broken.size} broken, ${outcome.relinked.size} relinked, " +
            "${outcome.notFound.size} not found, ${outcome.otherSize.size} of another size, ${outcome.several.size} with several candidates")
        fun shown(list: List<Wanted>) = list.map { it.name ?: it.id }.sorted()
        Report(links.size, outcome.relinked.size, shown(outcome.notFound), shown(outcome.otherSize), shown(outcome.several), refused)
    }

    /** Adds to [found] the files of [names] under [tree], at any depth, one folder after the other. */
    private fun walk(context: Context, tree: Uri, names: Set<String>, found: MutableList<Found>, cancelled: () -> Boolean) {
        val folders = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        while (folders.isNotEmpty() && !cancelled()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folders.removeFirst())
            context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    if (cursor.getString(2) == Document.MIME_TYPE_DIR) folders.addLast(id)
                    else if (name in names) found += Found(id, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name,
                        if (cursor.isNull(3)) null else cursor.getLong(3))
                }
            }
        }
    }
}
