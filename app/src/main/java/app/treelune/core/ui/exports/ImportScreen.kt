package app.treelune.core.ui.exports

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.backup.PartElement
import app.treelune.core.backup.PartImport
import app.treelune.core.backup.PartLinks
import app.treelune.core.backup.PartSelection
import app.treelune.core.backup.PartTree
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
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

/** What the file holds, as the service read it. */
private data class Read(val tree: PartTree, val partial: Boolean, val takenNames: Set<String>)

/** A zone of the app a zone of the file may go into. */
private data class AppZone(val id: String, val name: String, val group: String?)

/** What arrived, as the import answers it. */
private data class Imported(val zones: Int, val elements: Int, val entries: Int, val imagesMissing: Int)

private val SelectionSaver: Saver<PartSelection?, String> = Saver(
    save = { it?.toJson()?.toString() ?: "" },
    restore = { saved -> saved.takeIf { it.isNotEmpty() }?.let { PartSelection.read(JSONObject(it)) } }
)

private val ArrivalsSaver: Saver<Map<String, String>, String> = Saver(
    save = { JSONObject(it).toString() },
    restore = { saved -> JSONObject(saved).let { json -> json.keys().asSequence().associateWith { json.getString(it) } } }
)

/**
 * The import of the file at [uri] (docs/design/share.md, « Les écrans »). A whole backup offers
 * to take a part back, the default, or to restore everything, which replaces the whole app after
 * a confirmation and restarts it; a share only offers a part. A part is taken with the same boxes
 * as the export's, a share ticked whole, a whole backup not at all; each zone ticked goes into a
 * new zone or into one of the app's, one of the same name first in the list. Under an element,
 * how it will arrive: a variable whose name is taken under another, a tool that may send
 * notifications. Everything is added, nothing replaced (backup.import_part).
 */
@Composable
fun ImportScreen(uri: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()

    var read by remember { mutableStateOf<Read?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var appZones by remember { mutableStateOf<List<AppZone>>(emptyList()) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf<PartSelection?>(null) }
    var arrivals by rememberSaveable(stateSaver = ArrivalsSaver) { mutableStateOf(emptyMap<String, String>()) }
    var opened by rememberSaveable { mutableStateOf(listOf<String>()) }
    var running by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<Imported?>(null) }
    // A whole backup may also restore everything, which a part never does
    var restore by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        fileName = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            } catch (e: Exception) {
                null
            }
        }
        val coordinator = Coordinator(context)
        @Suppress("UNCHECKED_CAST")
        appZones = (coordinator.processUserAction("zones.list").data?.get("zones") as? List<Map<String, Any?>>).orEmpty()
            .map { AppZone(it["id"] as String, it["name"] as String, it["group"] as? String) }
        val result = coordinator.processUserAction("backup.tree", mapOf("uri" to uri))
        if (!result.isSuccess) {
            readError = result.error ?: ""
            return@LaunchedEffect
        }
        val data = result.data!!
        @Suppress("UNCHECKED_CAST")
        val tree = PartTree.read(JsonUtils.toJSONObject(data["tree"] as Map<String, Any?>))
        val partial = data["partial"] as Boolean
        @Suppress("UNCHECKED_CAST")
        read = Read(tree, partial, (data["taken_variable_names"] as List<String>).toSet())
        // A share is meant whole, its entries included; a whole backup is taken from piece by piece
        if (selection == null) selection = if (partial) everything(tree) else PartSelection(emptySet(), emptyMap(), emptySet(), emptySet())
    }

    val shown = read
    val chosen = selection
    val left = if (shown != null && chosen != null) PartLinks.left(shown.tree.links, chosen) else emptySet()
    val blocking = left.count { it.blocking }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(title = s.shared("share_import_title"), subtitle = fileName, icon = null, leftButton = ButtonAction.BACK, rightButton = null, onLeftClick = onBack, onRightClick = null)

        when {
            readError != null -> Column(modifier = Modifier.padding(horizontal = UI.Space.L)) { UI.Text(readError!!, TextType.ERROR) }
            shown == null || chosen == null -> UI.LoadingIndicator()
            else -> {
                Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    if (!shown.partial) UI.BooleanField(
                        label = s.shared("share_import_mode"),
                        value = !restore,
                        onValueChange = { restore = !it },
                        trueLabel = s.shared("share_import_part_mode"),
                        falseLabel = s.shared("share_import_restore_mode")
                    )
                    UI.Text(s.shared(if (restore) "share_import_restore_help" else "share_import_part_help"), if (restore) TextType.WARNING else TextType.CAPTION)
                }
                if (restore) {
                    Column(modifier = Modifier.padding(horizontal = UI.Space.L)) {
                        UI.FormActions {
                            UI.Button(type = ButtonType.DANGER, size = Size.M, state = if (running) ComponentState.DISABLED else ComponentState.NORMAL, onClick = { confirmRestore = true }) {
                                UI.Text(s.shared(if (running) "message_loading" else "share_import_restore_mode"), TextType.BODY)
                            }
                        }
                    }
                    return@Column
                }

                // The variables ticked, under the name each arrives with
                val variables = shown.tree.elements.filter { it.kind == PartElement.Kind.VARIABLE && it.id in chosen.variables }
                val names = PartImport.arrivalNames(variables.map { it.id to it.name }, shown.takenNames)

                PartTreeChoice(
                    shown.tree, chosen, { selection = it }, opened, { opened = it }, left,
                    zoneExtra = { zone -> Arrival(zone, appZones, arrivals[zone.id]) { arrivals = if (it == null) arrivals - zone.id else arrivals + (zone.id to it) } },
                    elementExtra = { element ->
                        when {
                            element.kind == PartElement.Kind.VARIABLE && names[element.id] != element.name ->
                                UI.Text(s.shared("share_import_variable_renamed").format(names[element.id]), TextType.CAPTION)
                            element.kind == PartElement.Kind.TOOL && element.tooltype?.let { ToolTypeManager.getToolType(it)?.getScheduler() } != null ->
                                UI.Text(s.shared("share_import_notifications"), TextType.CAPTION)
                        }
                    }
                )

                Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    if (blocking > 0) UI.Text(if (blocking == 1) s.shared("share_import_links_left_one") else s.shared("share_import_links_left_other").format(blocking), TextType.WARNING)
                    UI.FormActions {
                        UI.Button(
                            type = ButtonType.PRIMARY,
                            size = Size.M,
                            state = if (!running && chosen.zones.isNotEmpty() && blocking == 0) ComponentState.NORMAL else ComponentState.DISABLED,
                            onClick = {
                                running = true
                                scope.launch {
                                    val result = Coordinator(context).processUserAction("backup.import_part", mapOf(
                                        "uri" to uri,
                                        "selection" to JsonUtils.toMap(chosen.toJson()),
                                        // Only the zones ticked arrive: an arrival kept for one unticked would name nothing
                                        "arrivals" to arrivals.filterKeys { it in chosen.zones }
                                    ))
                                    running = false
                                    if (result.isSuccess) {
                                        val data = result.data!!
                                        imported = Imported((data["zones"] as Number).toInt(), (data["elements"] as Number).toInt(),
                                            (data["entries"] as Number).toInt(), (data["images_missing"] as Number).toInt())
                                    } else UI.Toast(context, result.error ?: "", app.treelune.core.ui.Duration.LONG)
                                }
                            }
                        ) {
                            UI.Text(s.shared(if (running) "message_loading" else "share_import_button"), TextType.BODY)
                        }
                    }
                }
            }
        }
    }

    if (confirmRestore) {
        UI.ConfirmDialog(
            title = s.shared("share_import_restore_mode"),
            message = s.shared("backup_import_confirm"),
            confirmText = s.shared("action_confirm"),
            cancelText = s.shared("action_cancel"),
            onConfirm = {
                confirmRestore = false
                running = true
                scope.launch {
                    // The service reads the file itself: a zip, or an older .json
                    val result = Coordinator(context).processUserAction("backup.import", mapOf("uri" to uri))
                    running = false
                    if (result.isSuccess) {
                        UI.Toast(context, s.shared("backup_import_success"))
                        // Every screen reads the restored data from a clean start
                        app.treelune.core.ui.screens.settings.restartApp(context)
                    } else UI.Toast(context, result.error ?: "", app.treelune.core.ui.Duration.LONG)
                }
            },
            onDismiss = { confirmRestore = false }
        )
    }

    imported?.let { done ->
        UI.Dialog(type = DialogType.INFO, onConfirm = onBack, onCancel = onBack) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("share_import_done"), TextType.BODY)
                UI.Text(s.shared("share_import_done_counts").format(done.zones, done.elements, done.entries), TextType.CAPTION)
                if (done.imagesMissing > 0) UI.Text(s.shared("share_import_images_missing").format(done.imagesMissing), TextType.WARNING)
            }
        }
    }
}

/**
 * Where [zone] arrives: a new zone, or one of [appZones] — [into] the one chosen, null for a new
 * one. The app's zones of the same name come first in the list.
 */
@Composable
private fun Arrival(zone: PartTree.Zone, appZones: List<AppZone>, into: String?, onInto: (String?) -> Unit) {
    if (appZones.isEmpty()) return
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val listed = appZones.sortedBy { if (it.name == zone.name) 0 else 1 }
    Column(modifier = Modifier.padding(start = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.BooleanField(
            label = s.shared("share_import_into"),
            value = into == null,
            onValueChange = { new -> onInto(if (new) null else listed.first().id) },
            trueLabel = s.shared("share_import_new_zone"),
            falseLabel = s.shared("share_import_existing_zone")
        )
        if (into != null) UI.SectionedSelection(
            label = s.shared("share_import_zone_pick"),
            options = listed.map { it.name },
            sections = listed.map { it.group },
            selected = listed.indexOfFirst { it.id == into }.takeIf { it >= 0 },
            onSelect = { onInto(listed[it].id) },
            required = true
        )
    }
}

/** Everything [tree] holds ticked, each tool with its entries when it has some. */
private fun everything(tree: PartTree): PartSelection = PartSelection(
    tree.zones.map { it.id }.toSet(),
    tree.elements.filter { it.kind == PartElement.Kind.TOOL }.associate { it.id to (it.entries > 0) },
    tree.elements.filter { it.kind == PartElement.Kind.VARIABLE }.map { it.id }.toSet(),
    tree.elements.filter { it.kind == PartElement.Kind.ROUTINE }.map { it.id }.toSet()
)
