package app.treelune.core.ui.exports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.backup.AutoBackup
import app.treelune.core.backup.PartElement
import app.treelune.core.backup.PartLink
import app.treelune.core.backup.PartLinks
import app.treelune.core.backup.PartSelection
import app.treelune.core.backup.PartTree
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A file written, as the export answers it. */
private data class FileWritten(val name: String, val uri: String, val elements: Int, val entries: Int, val missingImages: Int)

private val SelectionSaver: Saver<PartSelection, String> = Saver(
    save = { it.toJson().toString() },
    restore = { PartSelection.read(JSONObject(it)) }
)

private val NOTHING = PartSelection(emptySet(), emptyMap(), emptySet(), emptySet())

/**
 * The export (docs/design/share.md, « Les écrans »): a part of the app, ticked zone by zone, to
 * share or take back elsewhere, or the whole app, a backup. The file is written into the app's
 * folder, chosen here when there is none; the screen then says its name and what it holds, with
 * Share.
 *
 * A zone's box says whether it goes, its count how many of its elements go with it: ticking the
 * zone takes all it holds, ticking an element takes its zone. A tool with entries carries a
 * switch for them, off by default. A link from what is ticked to what is not is written under
 * its element (PartLinks): a blocking one keeps the file from being created.
 */
@Composable
fun ExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    var part by rememberSaveable { mutableStateOf(true) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(NOTHING) }
    var opened by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tree by remember { mutableStateOf<PartTree?>(null) }
    var treeError by remember { mutableStateOf<String?>(null) }
    var written by remember { mutableStateOf<FileWritten?>(null) }
    var running by remember { mutableStateOf(false) }

    // What the app holds, read once
    LaunchedEffect(Unit) {
        val result = Coordinator(context).processUserAction("backup.tree")
        if (result.isSuccess) {
            @Suppress("UNCHECKED_CAST")
            tree = PartTree.read(JsonUtils.toJSONObject(result.data!!["tree"] as Map<String, Any?>))
        } else treeError = result.error ?: ""
    }

    // The app's folder, read again once one is chosen
    var folderState by remember { mutableStateOf<AutoBackup.State?>(null) }
    var folderRead by remember { mutableStateOf(0) }
    LaunchedEffect(folderRead) {
        folderState = withContext(Dispatchers.IO) { AutoBackup.state(context) }
    }
    val chooseFolder = rememberAppFolderPicker(folderState) { refused ->
        refused?.let { UI.Toast(it) }
        folderRead++
    }
    val folderReady = folderState is AutoBackup.State.Ready || folderState is AutoBackup.State.Off

    val shown = tree
    val left = shown?.let { PartLinks.left(it.links, selection) }.orEmpty()
    val blocking = left.count { it.blocking }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(title = s.shared("share_export_title"), leftButton = ButtonAction.BACK, onLeftClick = onBack)

        Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.BooleanField(
                label = s.shared("share_export_what"),
                value = part,
                onValueChange = { part = it },
                trueLabel = s.shared("share_export_part"),
                falseLabel = s.shared("share_export_all")
            )
            UI.Text(s.shared(if (part) "share_export_part_help" else "share_export_all_help"), TextType.CAPTION)
        }

        // Without a folder to write in, choosing one comes first
        when (folderState) {
            null -> {}
            AutoBackup.State.NoFolder, is AutoBackup.State.Lost -> UI.Card(type = CardType.DEFAULT) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    UI.Text(s.shared(if (folderState == AutoBackup.State.NoFolder) "share_export_no_folder" else "export_error_folder_lost"), TextType.BODY)
                    UI.Button(type = ButtonType.PRIMARY, size = Size.M, onClick = chooseFolder) {
                        UI.Text(s.shared(if (folderState == AutoBackup.State.NoFolder) "auto_backup_choose_folder" else "auto_backup_change_folder"), TextType.BODY)
                    }
                }
            }
            else -> {}
        }

        if (part) {
            when {
                treeError != null -> UI.Text(treeError!!, TextType.ERROR)
                shown == null -> UI.LoadingIndicator()
                else -> PartTreeChoice(shown, selection, { selection = it }, opened, { opened = it }, left)
            }
        }

        Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            if (part && blocking > 0) {
                UI.Text(if (blocking == 1) s.shared("share_links_left_one") else s.shared("share_links_left_other").format(blocking), TextType.WARNING)
            }
            UI.FormActions {
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (!running && folderReady && (!part || (selection.zones.isNotEmpty() && blocking == 0))) ComponentState.NORMAL else ComponentState.DISABLED,
                    onClick = {
                        running = true
                        scope.launch {
                            val result = Coordinator(context).processUserAction("backup.export", if (part) mapOf("selection" to JsonUtils.toMap(selection.toJson())) else emptyMap())
                            running = false
                            if (result.isSuccess) {
                                val data = result.data!!
                                written = FileWritten(data["file_name"] as String, data["uri"] as String, (data["elements"] as Number).toInt(),
                                    (data["entries"] as Number).toInt(), (data["missing_images"] as Number).toInt())
                            } else UI.Toast(result.error ?: "", app.treelune.core.ui.Duration.LONG)
                        }
                    }
                ) {
                    UI.Text(s.shared(if (running) "message_loading" else "share_create"), TextType.BODY)
                }
            }
        }
    }

    written?.let { done ->
        UI.Dialog(type = DialogType.INFO, onConfirm = { written = null }, onCancel = { written = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("share_written").format(done.name), TextType.BODY)
                UI.Text(s.shared("share_written_counts").format(done.elements, done.entries), TextType.CAPTION)
                if (done.missingImages > 0) UI.Text(s.shared("share_written_missing_images").format(done.missingImages), TextType.WARNING)
                UI.Button(type = ButtonType.PRIMARY, onClick = { shareFile(context, done.uri, "application/zip") }) {
                    UI.Text(s.shared("file_share"), TextType.LABEL)
                }
            }
        }
    }
}
