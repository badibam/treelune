package app.treelune.core.ui.exports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.backup.AutoBackup
import app.treelune.core.exports.AppFolder
import app.treelune.core.strings.Strings
import kotlinx.coroutines.launch

/** The folder [state] names, null when none is chosen. */
fun folderOf(state: AutoBackup.State?): android.net.Uri? = when (state) {
    is AutoBackup.State.Ready -> state.folder
    is AutoBackup.State.Lost -> state.folder
    is AutoBackup.State.Off -> state.folder
    else -> null
}

/**
 * The choice of the app's folder (AppFolder), from Settings › Data or from a screen that writes
 * into it: Android gives the app a lasting right to write in the folder chosen, the one before
 * given back; the automatic backup stays as it was, on when no folder was chosen yet. [onDone]
 * gets the refusal's message, null once saved. Calling the result opens the picker.
 */
@Composable
fun rememberAppFolderPicker(state: AutoBackup.State?, onDone: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            onDone(s.shared("auto_backup_folder_refused"))
            return@rememberLauncherForActivityResult
        }
        folderOf(state)?.takeIf { it != uri }?.let { old ->
            try { context.contentResolver.releasePersistableUriPermission(old, flags) } catch (_: SecurityException) {}
        }
        scope.launch { onDone(AppFolder.save(context, uri, state !is AutoBackup.State.Off)) }
    }
    return { launcher.launch(folderOf(state)) }
}
