package app.treelune.core.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The folders opened to the app (LinkedFiles), in Settings › Data: each by its name, closed by its
 * cross after a confirmation that counts the links it leaves unread; one more opened by Android's
 * folder picker, which gives the app the right to read it (docs/design/linked-files.md).
 */
@Composable
fun OpenedFoldersCard() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<LinkedFiles.OpenedFolder>?>(null) }
    var read by remember { mutableStateOf(0) }
    // The folder about to close, with the links it leaves
    var closing by remember { mutableStateOf<Pair<LinkedFiles.OpenedFolder, Int>?>(null) }
    LaunchedEffect(read) { folders = withContext(Dispatchers.IO) { LinkedFiles.folders(context) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        scope.launch {
            LinkedFiles.open(context, tree, s::shared)?.let { UI.Toast(it, Duration.LONG) }
            read++
        }
    }

    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(text = s.shared("opened_folders_title"), type = TextType.SUBTITLE)
            UI.Text(text = s.shared("opened_folders_card"), type = TextType.CAPTION)
            val current = folders
            when {
                current == null -> UI.LoadingIndicator()
                current.isEmpty() -> UI.Text(text = s.shared("opened_folders_none"), type = TextType.BODY)
                else -> current.forEach { folder ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
                        UI.Icon("folder", contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            UI.Text(text = folder.name, type = TextType.BODY)
                            // Its path on the phone ("Documents/Music")
                            UI.Text(text = android.provider.DocumentsContract.getTreeDocumentId(folder.tree).substringAfter(':'), type = TextType.CAPTION)
                        }
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                            scope.launch { closing = folder to withContext(Dispatchers.IO) { LinkedFiles.linksIn(context, folder) } }
                        })
                    }
                }
            }
            UI.Button(type = ButtonType.DEFAULT, size = Size.M, onClick = { picker.launch(null) }) {
                UI.Text(text = s.shared("opened_folders_add"), type = TextType.BODY)
            }
        }
    }

    closing?.let { (folder, links) ->
        UI.ConfirmDialog(
            title = s.shared("opened_folders_close"),
            message = if (links == 0) s.shared("opened_folders_close_confirm").format(folder.name)
                else s.shared("opened_folders_close_confirm_links").format(folder.name, links),
            confirmText = s.shared("action_confirm"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                closing = null
                scope.launch {
                    LinkedFiles.close(context, folder)?.let { UI.Toast(it, Duration.LONG) }
                    read++
                }
            },
            onDismiss = { closing = null }
        )
    }
}
