package app.treelune.core.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The folders opened to the app (LinkedFiles), browsed to choose a file a FILE field in linked
 * mode links (docs/design/linked-files.md): the folders first, then, once in one, its subfolders
 * and its files of [kinds], a trail to go back up. A file touched is [onPick] with its folder and
 * its path under it. Without any folder opened, it says so and leads to Settings › Data, where one
 * is opened.
 */
@Composable
fun FolderBrowser(kinds: List<FileKind>, onDismiss: () -> Unit, onPick: (LinkedFiles.OpenedFolder, String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // Where the browser stands: a folder's id and the path under it; none, the list of folders
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var path by rememberSaveable { mutableStateOf("") }

    val folders by produceState<List<LinkedFiles.OpenedFolder>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { LinkedFiles.folders(context) }
    }
    val folder = folders?.firstOrNull { it.id == folderId }

    /** What the place reached holds, or why it cannot be read. */
    class Listing(val items: List<LinkedFiles.Item>?, val error: String?)
    val listing by produceState<Listing?>(initialValue = null, folder, path) {
        value = null
        val current = folder ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                Listing(LinkedFiles.list(context, current, path), null)
            } catch (e: LinkedFiles.NotFound) {
                Listing(null, s.shared("file_link_not_found").format(path, current.name))
            } catch (e: SecurityException) {
                Listing(null, s.shared("file_link_folder_closed").format(current.name))
            }
        }
    }

    UI.Dialog(type = DialogType.SELECTION, onConfirm = onDismiss, onCancel = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(text = s.shared("file_browse_title"), type = TextType.TITLE, fillMaxWidth = true)
            val all = folders
            when {
                all == null -> UI.LoadingIndicator()
                all.isEmpty() -> {
                    UI.Text(text = s.shared("file_browse_no_folder"), type = TextType.BODY)
                    UI.Button(type = ButtonType.PRIMARY, size = Size.M, onClick = {
                        onDismiss()
                        Navigator.push(Place.SettingsPage("data"))
                    }) { UI.Text(text = s.shared("file_browse_open_settings"), type = TextType.BODY) }
                }
                folder == null -> all.forEach { opened ->
                    BrowserRow("folder", opened.name, null) { folderId = opened.id; path = "" }
                }
                else -> {
                    // The trail: back to the folders, then each folder of the path
                    UI.Text(text = (listOf(folder.name) + LinkedFiles.parts(path)).joinToString(" › "), type = TextType.CAPTION)
                    BrowserRow("arrow-up", s.shared("file_browse_up"), null) {
                        if (path.isEmpty()) folderId = null else path = path.substringBeforeLast('/', "")
                    }
                    val current = listing
                    when {
                        current == null -> UI.LoadingIndicator()
                        current.error != null -> UI.Text(text = current.error, type = TextType.ERROR)
                        else -> {
                            val shown = current.items!!.filter { it is LinkedFiles.Item.Folder || (it as LinkedFiles.Item.File).format?.kind in kinds }
                            if (shown.isEmpty()) UI.Text(text = s.shared("file_browse_empty"), type = TextType.BODY)
                            shown.forEach { item ->
                                val under = if (path.isEmpty()) item.name else "$path/${item.name}"
                                when (item) {
                                    is LinkedFiles.Item.Folder -> BrowserRow("folder", item.name, null) { path = under }
                                    is LinkedFiles.Item.File -> BrowserRow(
                                        if (item.format?.kind == FileKind.VIDEO) "video" else "audio-lines",
                                        item.name,
                                        item.sizeBytes?.let { s.shared("file_size_mb").format(it / (1024.0 * 1024.0)) }
                                    ) { onPick(folder, under) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row of the browser: its icon, its name, a detail under it; [onClick] on a touch. */
@Composable
private fun BrowserRow(icon: String, name: String, detail: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UI.Icon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            UI.Text(text = name, type = TextType.BODY)
            detail?.let { UI.Text(text = it, type = TextType.CAPTION) }
        }
    }
}
