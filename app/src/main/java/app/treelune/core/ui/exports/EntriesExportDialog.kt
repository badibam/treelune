package app.treelune.core.ui.exports

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.selectors.SelectionDraft
import app.treelune.core.ui.selectors.SelectionPicker
import app.treelune.core.ui.selectors.rememberToolFields
import app.treelune.core.ui.selectors.toolPath
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What the export of a tool's entries chooses among: the tool itself, never another. */
private val TOOL = ReferenceTarget(setOf(ReferenceKind.TOOL_INSTANCE), emptyList())

/** The file written, for the window that says so. */
private data class Written(val name: String, val uri: String, val entries: Int)

private val DraftSaver: Saver<SelectionDraft?, String> = Saver(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { SelectionDraft.fromJson(JSONObject(it)) } }
)

/**
 * "Export the entries…" of a tool (docs/design/export.md): its period, its filters and the
 * fields kept, chosen by SelectionPicker held on the tool, then the file written by
 * `exports.csv` into the app's folder; a window then says its name and its number of entries,
 * with Share, which hands the file to the app the user chooses.
 */
@Composable
fun EntriesExportDialog(toolInstanceId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<SelectionDraft?>(null) }
    var written by remember { mutableStateOf<Written?>(null) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(toolInstanceId) {
        if (draft != null) return@LaunchedEffect
        val path = toolPath(toolInstanceId, context)
        if (path == null) {
            UI.Toast(context, s.shared("service_error_tool_instance_not_found"))
            onDismiss()
            return@LaunchedEffect
        }
        draft = SelectionDraft(zone = path.zone, tool = path.tool)
    }
    val fields = rememberToolFields(toolInstanceId)

    val done = written
    if (done != null) {
        UI.Dialog(type = DialogType.INFO, onConfirm = onDismiss, onCancel = onDismiss) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("export_csv_written").format(done.name), TextType.BODY)
                UI.Text(s.shared("export_csv_entries").format(done.entries), TextType.CAPTION)
                UI.Button(type = ButtonType.PRIMARY, onClick = { shareFile(context, done.uri, "text/csv") }) {
                    UI.Text(s.shared("file_share"), TextType.LABEL)
                }
            }
        }
        return
    }

    val current = draft
    UI.Dialog(
        type = DialogType.CONFIRM,
        onCancel = onDismiss,
        confirmEnabled = current != null && !running,
        onConfirm = {
            val chosen = current ?: return@Dialog
            running = true
            scope.launch {
                val result = try {
                    // Relative dates resolve now, when the file is written
                    val selection = chosen.selection()
                    val filters = selection.storedFilters(fields, TimeResolver.at(System.currentTimeMillis())) { s.shared(it) }
                    Coordinator(context).processUserAction("exports.csv", buildMap {
                        put("tool_instance_id", toolInstanceId)
                        if (filters.length() > 0) put("filters", JsonUtils.toList(filters))
                        selection.fields?.let { put("fields", it) }
                    })
                } catch (e: IllegalArgumentException) {
                    running = false
                    UI.Toast(context, e.message ?: "")
                    return@launch
                }
                running = false
                if (result.isSuccess) {
                    val data = result.data!!
                    written = Written(data["file_name"] as String, data["uri"] as String, (data["entries"] as Number).toInt())
                } else UI.Toast(context, result.error ?: s.shared("export_error_read"), app.treelune.core.ui.Duration.LONG)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.shared("export_csv_title"), TextType.SUBTITLE)
            if (current == null) UI.LoadingIndicator()
            else SelectionPicker(current, { draft = it }, TOOL, fields, reference = null, offerFields = true, emptyPeriod = s.shared("export_csv_all_entries"), fixed = true)
        }
    }
}

/**
 * The file at [uri], a document of the app's folder of type [mimeType] as an export answers it,
 * handed to the app the user chooses: a CSV from the export's window and from the chat's card of
 * an export the AI made, a part of the app or a backup from the export's screen.
 */
fun shareFile(context: Context, uri: String, mimeType: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, Strings.`for`(context = context).shared("file_share")))
}
