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
import app.treelune.core.utils.DateUtils
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * Restart the application
 * Used after import/reset to reload all data with clean state
 */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    exitProcess(0)
}

/**
 * Data Management Settings Screen - Backup & Restore
 *
 * Features:
 * - Export: Generate JSON backup via SAF document creation
 * - Import: Read JSON from SAF document picker with confirmation
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
    var showImportConfirm by rememberSaveable { mutableStateOf(false) }
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }

    // Toast for errors
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            errorMessage = null
        }
    }

    // SAF launcher for export (create document)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    // The service writes the zip into the chosen file itself: its images are copied
                    // there file by file, never held in memory
                    val result = coordinator.processUserAction(
                        "backup.export",
                        mapOf("uri" to uri.toString())
                    )

                    if (result.status == CommandStatus.SUCCESS) {
                        // Images whose file was missing went without it: said, not hidden
                        val missing = (result.data?.get("missing_images") as? Number)?.toInt() ?: 0
                        if (missing == 0) Toast.makeText(context, s.shared("backup_export_success"), Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, s.shared("backup_export_success_missing_images").format(missing), Toast.LENGTH_LONG).show()
                    } else {
                        errorMessage = result.error ?: s.shared("backup_export_failed")
                    }
                } catch (e: Exception) {
                    errorMessage = "${s.shared("backup_export_failed")}: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
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
    /** The data settings written whole, [folder] and [on], then read again. */
    fun saveAuto(folder: android.net.Uri?, on: Boolean) = scope.launch {
        val settings = buildMap<String, Any> {
            folder?.let { put(app.treelune.core.config.AppSettings.DATA_FOLDER, it.toString()) }
            put(app.treelune.core.config.AppSettings.DATA_AUTO_BACKUP, on)
        }
        val result = coordinator.processUserAction("app_config.set", mapOf("category" to app.treelune.core.database.entities.AppSettingCategories.DATA, "settings" to settings))
        if (result.status != CommandStatus.SUCCESS) errorMessage = result.error
        autoRead++
    }
    val folderOf: (app.treelune.core.backup.AutoBackup.State?) -> android.net.Uri? = { state ->
        when (state) {
            is app.treelune.core.backup.AutoBackup.State.Ready -> state.folder
            is app.treelune.core.backup.AutoBackup.State.Lost -> state.folder
            is app.treelune.core.backup.AutoBackup.State.Off -> state.folder
            else -> null
        }
    }
    // A folder chosen once: Android gives the app a lasting right to write in it, the one before given back
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            errorMessage = s.shared("auto_backup_folder_refused")
            return@rememberLauncherForActivityResult
        }
        folderOf(autoState)?.takeIf { it != uri }?.let { old ->
            try { context.contentResolver.releasePersistableUriPermission(old, flags) } catch (_: SecurityException) {}
        }
        saveAuto(uri, autoState !is app.treelune.core.backup.AutoBackup.State.Off)
    }

    // SAF launcher for import (pick document)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = uri
            showImportConfirm = true
        }
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
                    onClick = { folderLauncher.launch(folder) }
                ) {
                    UI.Text(text = s.shared(if (folder == null) "auto_backup_choose_folder" else "auto_backup_change_folder"), type = TextType.BODY)
                }
            }
        }

        // Export button
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Text(
                    text = s.shared("backup_export"),
                    type = TextType.SUBTITLE
                )
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = {
                        val timestamp = DateUtils.format(System.currentTimeMillis(), "yyyyMMdd_HHmmss")
                        val version = app.treelune.BuildConfig.VERSION_NAME
                        exportLauncher.launch("treelune_backup_${timestamp}_v$version.zip")
                    }
                ) {
                    UI.Text(
                        text = s.shared("backup_export"),
                        type = TextType.BODY
                    )
                }
            }
        }

        // Import button
        UI.Card(type = CardType.DEFAULT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UI.Space.L),
                verticalArrangement = Arrangement.spacedBy(UI.Space.S)
            ) {
                UI.Text(
                    text = s.shared("backup_import"),
                    type = TextType.SUBTITLE
                )
                UI.Button(
                    type = ButtonType.PRIMARY,
                    size = Size.M,
                    state = if (isLoading) ComponentState.DISABLED else ComponentState.NORMAL,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/json"))
                    }
                ) {
                    UI.Text(
                        text = s.shared("backup_import"),
                        type = TextType.BODY
                    )
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

    // Import confirmation dialog
    if (showImportConfirm) {
        UI.ConfirmDialog(
            title = s.shared("backup_import"),
            message = s.shared("backup_import_confirm"),
            confirmText = s.shared("action_confirm"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                showImportConfirm = false
                pendingImportUri?.let { uri ->
                    isLoading = true
                    scope.launch {
                        try {
                            // The service reads the chosen file itself: a zip, or an older .json
                            val result = coordinator.processUserAction(
                                "backup.import",
                                mapOf("uri" to uri.toString())
                            )

                            if (result.status == CommandStatus.SUCCESS) {
                                Toast.makeText(
                                    context,
                                    s.shared("backup_import_success"),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Restart app with clean state
                                restartApp(context)
                            } else {
                                errorMessage = result.error ?: s.shared("backup_import_failed")
                            }
                        } catch (e: Exception) {
                            errorMessage = "${s.shared("backup_import_failed")}: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportUri = null
            }
        )
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
