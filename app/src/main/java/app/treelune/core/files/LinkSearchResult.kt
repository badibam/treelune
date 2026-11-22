package app.treelune.core.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.launch

/**
 * What a search for the missing files found (LinkSearch), the same at a part's import and in
 * Settings › Data: counts first, the names of the files not found folded, and in full what the app
 * cannot settle alone. « Search again » and « Authorize a folder » run it again on the same links.
 *
 * @param first The report to show first
 * @param ids The links searched; null for all of them
 * @param onFolderAdded Told when a folder was authorized from here
 */
@Composable
fun LinkSearchResult(first: LinkSearch.Report, ids: List<String>?, onFolderAdded: () -> Unit = {}) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    var report by remember(first) { mutableStateOf(first) }
    var running by remember { mutableStateOf(false) }
    var namesShown by remember { mutableStateOf(false) }

    suspend fun again() {
        running = true
        val result = Coordinator(context).processUserAction("files.relink", if (ids == null) emptyMap() else mapOf("ids" to ids))
        running = false
        if (result.isSuccess) result.data?.let { report = LinkSearch.Report.of(it) }
        else UI.Toast(result.error ?: "", Duration.LONG)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        scope.launch {
            LinkedFiles.open(context, tree, s::shared)?.let { UI.Toast(it, Duration.LONG) }
            onFolderAdded()
            again()
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        val shown = report
        if (shown.relinked == 0 && !shown.unresolved) UI.Text(s.shared("link_search_all_there"), TextType.BODY)
        if (shown.relinked > 0) UI.Text(
            if (shown.relinked == 1) s.shared("link_search_relinked_one") else s.shared("link_search_relinked_other").format(shown.relinked),
            TextType.BODY
        )
        if (shown.notFound.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { namesShown = !namesShown },
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UI.Icon(if (namesShown) "chevron-down" else "chevron-right", size = 20.dp)
                UI.Text(
                    if (shown.notFound.size == 1) s.shared("link_search_not_found_one") else s.shared("link_search_not_found_other").format(shown.notFound.size),
                    TextType.BODY
                )
            }
            if (namesShown) Names(shown.notFound)
        }
        if (shown.otherSize.isNotEmpty()) {
            UI.Text(s.shared("link_search_other_size"), TextType.WARNING)
            Names(shown.otherSize)
        }
        if (shown.several.isNotEmpty()) {
            UI.Text(s.shared("link_search_several"), TextType.WARNING)
            Names(shown.several)
        }
        if (shown.foldersRefused.isNotEmpty())
            UI.Text(s.shared("link_search_folders_refused").format(shown.foldersRefused.joinToString(", ")), TextType.WARNING)
        if (shown.unresolved) {
            val state = if (running) ComponentState.DISABLED else ComponentState.NORMAL
            UI.Button(type = ButtonType.DEFAULT, size = Size.M, state = state, onClick = { scope.launch { again() } }) {
                UI.Text(s.shared(if (running) "message_loading" else "link_search_again"), TextType.BODY)
            }
            UI.Button(type = ButtonType.DEFAULT, size = Size.M, state = state, onClick = { picker.launch(null) }) {
                UI.Text(s.shared("opened_folders_add"), TextType.BODY)
            }
        }
    }
}

/** The names of files, each once, in a list that scrolls past a few of them. */
@Composable
private fun Names(names: List<String>) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        names.distinct().forEach { UI.Text(it, TextType.CAPTION) }
    }
}
