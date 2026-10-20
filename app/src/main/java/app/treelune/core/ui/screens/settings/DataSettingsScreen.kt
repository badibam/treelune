package app.treelune.core.ui.screens.settings

import androidx.compose.runtime.saveable.rememberSaveable
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import kotlin.system.exitProcess
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ui.exports.folderOf
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * Restart the application
 * Used after a restore or a reset to reload all data with clean state
 */
fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    exitProcess(0)
}

/**
 * Data Management Settings Screen - Backup & Restore
 *
 * Features:
 * - The automatic backup: the app's folder, on or off
 * - Export: opens the export's screen (ExportScreen), a part or the whole app
 * - Import: the file chosen opened on the import's screen (ImportScreen), a part taken back or
 *   the whole app restored
 * - Reset: Wipe all data with confirmation
 *
 * Architecture:
 * - UI handles SAF (file I/O via Android intents)
 * - Service handles logic (JSON generation/parsing)
 * - App restart after import/reset via Intent + exitProcess
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = Strings.`for`(context = context)
    val coordinator = Coordinator(context)
    val scope = rememberCoroutineScope()

    // UI states
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    // Toast for errors
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            errorMessage = null
        }
    }

    // The automatic backup: where it stands, read again after each change
    var autoState by remember { mutableStateOf<app.treelune.core.backup.AutoBackup.State?>(null) }
    var autoRead by remember { mutableStateOf(0) }
    LaunchedEffect(autoRead) {
        autoState = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // The screen shows the case: it is seen, the marks on the way here go
                app.treelune.core.backup.AutoBackup.seen(context)
                app.treelune.core.backup.AutoBackup.state(context)
            }
        } catch (e: Exception) { errorMessage = e.message; null }
    }
    /** The app's folder kept, the automatic backup switched [on], then read again. */
    fun saveAuto(folder: android.net.Uri, on: Boolean) = scope.launch {
        app.treelune.core.exports.AppFolder.save(context, folder, on)?.let { errorMessage = it }
        autoRead++
    }
    // A folder chosen once: Android gives the app a lasting right to write in it
    val chooseFolder = app.treelune.core.ui.exports.rememberAppFolderPicker(autoState) { refused ->
        refused?.let { errorMessage = it }
        autoRead++
    }

    // The file a part is taken from, then the import's screen on it
    val partLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { app.treelune.core.navigation.Navigator.push(app.treelune.core.navigation.Place.Import(it.toString())) }
    }

    // Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        // Header
        UI.PageHeader(
            title = s.shared("settings_backup"),
            subtitle = s.shared("settings_data_description"),
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = null
        )

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L)
            ) {
                UI.Text(
                    text = s.shared("message_loading"),
                    type = TextType.BODY
                )
            }
        }

        // The automatic backup: its folder, on or off, its last copy
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Text(text = s.shared("auto_backup_title"), type = TextType.SUBTITLE)
                val state = autoState
                val folder = folderOf(state)
                UI.Text(
                    text = when (state) {
                        null -> s.shared("message_loading")
                        app.treelune.core.backup.AutoBackup.State.NoFolder -> s.shared("auto_backup_no_folder")
                        is app.treelune.core.backup.AutoBackup.State.Off -> s.shared("auto_backup_off")
                        is app.treelune.core.backup.AutoBackup.State.Lost -> s.shared("auto_backup_lost_content")
                        is app.treelune.core.backup.AutoBackup.State.Ready -> state.copies.values.maxOfOrNull { it.at }
                            ?.let { s.shared("auto_backup_last").format(app.treelune.core.utils.FormatUtils.formatRelativeTimePast(it, context)) }
                            ?: s.shared("auto_backup_none_yet")
                    },
                    type = TextType.CAPTION
                )
                // The folder by its path on the phone ("Documents/Treelune")
                folder?.let { UI.Text(s.shared("auto_backup_folder").format(android.provider.DocumentsContract.getTreeDocumentId(it).substringAfter(':')), TextType.CAPTION) }
                if (folder != null) UI.BooleanField(
                    label = s.shared("settings_data_auto_backup"),
                    value = state !is app.treelune.core.backup.AutoBackup.State.Off,
                    onValueChange = { on -> saveAuto(folder, on) }
                )
                UI.Button(
                    type = if (folder == null || state is app.treelune.core.backup.AutoBackup.State.Lost) ButtonType.PRIMARY else ButtonType.DEFAULT,
                    size = Size.M,
                    state = if (state == null) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = chooseFolder
                ) {
                    UI.Text(text = s.shared(if (folder == null) "auto_backup_choose_folder" else "auto_backup_change_folder"), type = TextType.BODY)
                }
            }
        }

        // Export: a part to share, or the whole app
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Text(text = s.shared("share_export_title"), type = TextType.SUBTITLE)
                UI.Text(text = s.shared("share_export_card"), type = TextType.CAPTION)
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = { app.treelune.core.navigation.Navigator.push(app.treelune.core.navigation.Place.Export) }
                ) {
                    UI.Text(text = s.shared("share_export_open"), type = TextType.BODY)
                }
            }
        }

        // Import: a part of a file added to what there is, or a whole backup restored
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Text(text = s.shared("share_import_title"), type = TextType.SUBTITLE)
                UI.Text(text = s.shared("share_import_card"), type = TextType.CAPTION)
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = { partLauncher.launch(arrayOf("application/zip", "application/json")) }
                ) {
                    UI.Text(text = s.shared("share_import_open"), type = TextType.BODY)
                }
            }
        }

        // Reset button
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Text(
                    text = s.shared("backup_reset"),
                    type = TextType.SUBTITLE
                )
                UI.Button(
                    type = ButtonType.DANGER,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = {
                        showResetConfirm = true
                    }
                ) {
                    UI.Text(
                        text = s.shared("backup_reset"),
                        type = TextType.BODY
                    )
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        UI.ConfirmDialog(
            title = s.shared("backup_reset"),
            message = s.shared("backup_reset_confirm"),
            confirmText = s.shared("action_confirm"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                showResetConfirm = false
                isLoading = true
                scope.launch {
                    try {
                        // Call reset service
                        val result = coordinator.processUserAction(
                            "backup.reset",
                            emptyMap()
                        )

                        if (result.status == CommandStatus.SUCCESS) {
                            Toast.makeText(
                                context,
                                s.shared("backup_reset_success"),
                                Toast.LENGTH_SHORT
                            ).show()
                            // Restart app with clean state
                            restartApp(context)
                        } else {
                            errorMessage = result.error ?: s.shared("backup_reset_failed")
                        }
                    } catch (e: Exception) {
                        errorMessage = "${s.shared("backup_reset_failed")}: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            onDismiss = {
                showResetConfirm = false
            }
        )
    }
}
