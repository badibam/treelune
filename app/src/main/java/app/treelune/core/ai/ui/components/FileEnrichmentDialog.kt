package app.treelune.core.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.enrichments.FileEnrichment
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * The FILE enrichment's dialog (docs/DATA.md, « Import »): a text joined to the session, kept by
 * the files service, so only its id is held here. What will go: its name, size, line count and
 * first lines, and « Include it whole », ticked. Cancelling a text just joined ([joined]) deletes
 * it; editing a joined one only changes whether it goes whole.
 *
 * @param config The FileEnrichment as stored
 */
@Composable
fun FileEnrichmentDialog(
    config: String,
    joined: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (config: String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()

    var file by rememberSaveable { mutableStateOf(config) }
    var error by remember { mutableStateOf<String?>(null) }
    // Read back from the files service, never kept across a rotation
    var details by remember { mutableStateOf<Pair<String, String>?>(null) }

    val current = FileEnrichment.fromJson(file)
    LaunchedEffect(current.fileId) {
        val result = coordinator.processUserAction("files.read", mapOf("id" to current.fileId, "start_line" to 1, "lines" to PREVIEW_SHOWN))
        if (!result.isSuccess) { error = result.error; return@LaunchedEffect }
        val size = (result.data?.get("size_bytes") as? Number)?.toLong()
        details = listOfNotNull(
            size?.let { FormatUtils.formatFileSize(it, context) },
            s.shared("file_line_count").format(current.lineCount)
        ).joinToString(" · ") to (result.data?.get("text") as? String ?: "")
    }

    fun cancel() {
        if (joined) scope.launch {
            val result = coordinator.processUserAction("files.delete", mapOf("id" to current.fileId))
            if (!result.isSuccess) LogManager.aiEnrichment("File ${current.fileId} not deleted on cancel: ${result.error}", "ERROR")
        }
        onDismiss()
    }

    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = error == null,
        onCancel = { cancel() },
        onConfirm = { onConfirm(current.toJson().toString()) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.Text(text = s.shared("file_dialog_title"), type = TextType.TITLE, fillMaxWidth = true)
            error?.let { UI.Text(text = it, type = TextType.ERROR) }
            UI.Text(text = current.name, type = TextType.SUBTITLE)
            details?.let { (line, preview) ->
                UI.Text(text = line, type = TextType.CAPTION)
                UI.Text(text = s.shared("file_preview_label"), type = TextType.LABEL)
                UI.Text(text = preview, type = TextType.CAPTION)
            }
            UI.Checkbox(
                checked = current.whole,
                onCheckedChange = { whole -> file = current.copy(whole = whole).toJson().toString() },
                label = s.shared("file_include_whole")
            )
        }
    }
}

/** The lines of a file shown in the dialog, fewer than the AI's preview: a glance. */
private const val PREVIEW_SHOWN = 8
