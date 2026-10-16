package app.treelune.core.identities.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.guide.ui.GuideText
import app.treelune.core.identities.IdentityLanguage
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.IconColor
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import kotlinx.coroutines.launch

/**
 * Settings › Identities (docs/design/incarnations.md): the identities the app's AI may speak as,
 * each opened on its own screen; a personae export imported after a preview of what it replaces;
 * a new identity written in the app from a name and a language.
 */
@Composable
fun IdentitiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }
    val identities = rememberIdentities()

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    // An export read, waiting for its confirmation: its text and its preview
    var importing by remember { mutableStateOf<Pair<String, Map<*, *>>?>(null) }

    openId?.let { id ->
        IdentityScreen(id, onBack = { openId = null })
        return
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } catch (e: Exception) {
                UI.Toast(context, s.shared("identity_import_unreadable").format(e.message ?: ""), Duration.LONG)
                return@launch
            }
            val result = coordinator.processUserAction("identities.import_preview", mapOf("text" to text))
            if (result.isSuccess) importing = text to result.data!!
            else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = s.shared("settings_identities"),
            subtitle = s.shared("settings_identities_description"),
            leftButton = ButtonAction.BACK,
            onLeftClick = onBack
        )
        UI.FormActions {
            UI.Button(type = ButtonType.PRIMARY, onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                UI.Text(s.shared("identity_import"), TextType.BODY)
            }
            UI.Button(type = ButtonType.DEFAULT, onClick = { creating = true }) {
                UI.Text(s.shared("identity_new"), TextType.BODY)
            }
        }
        when {
            identities == null -> UI.Text(s.shared("message_loading"), TextType.BODY)
            identities.isEmpty() -> UI.Text(s.shared("identity_none_yet"), TextType.BODY)
            else -> identities.forEach { identity ->
                CurrentTheme.current.ZoneCardContainer(texture = null, onClick = { openId = identity.id }, onLongClick = {}) {
                    UI.TileHeader(identity.iconName, IconColor.of(identity.iconColor), identity.name, waiting = false, running = false, textType = TextType.HEADING)
                }
            }
        }
    }

    if (creating) NewIdentityDialog(onDismiss = { creating = false }, onCreated = { creating = false; openId = it })
    importing?.let { (text, preview) ->
        ImportDialog(preview, onDismiss = { importing = null }) { language ->
            scope.launch {
                val result = coordinator.processUserAction("identities.import", mapOf("text" to text, "language" to language))
                if (result.isSuccess) {
                    importing = null
                    UI.Toast(context, s.shared("identity_import_done"), Duration.SHORT)
                } else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
            }
        }
    }
}

/** A new identity, from its name and the language it speaks; its texts are written on its screen. */
@Composable
private fun NewIdentityDialog(onDismiss: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf(context.resources.configuration.locales[0].language) }
    UI.Dialog(
        type = DialogType.CREATE,
        confirmEnabled = name.isNotBlank() && language.isNotBlank(),
        onConfirm = {
            scope.launch {
                val result = Coordinator(context).processUserAction("identities.create", mapOf("name" to name, "language" to language))
                if (result.isSuccess) onCreated(result.data!!["identity_id"] as String)
                else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
            }
        },
        onCancel = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.shared("identity_new"), TextType.TITLE)
            UI.FormField(label = s.shared("label_name"), value = name, onChange = { name = it }, required = true)
            UI.FormField(label = s.shared("identity_language_code"), value = language, onChange = { language = it.trim() }, required = true)
        }
    }
}

/**
 * The confirmation of an import: what the export is, and, replacing an identity of its id, what
 * changes and the retouches lost; the language it speaks chosen among the export's, the app's own
 * preselected when it holds it, or the one it spoke before.
 */
@Composable
private fun ImportDialog(preview: Map<*, *>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val languages = (preview["languages"] as List<*>).map { it as String }
    val existing = preview["existing"] as? Map<*, *>
    val before = existing?.get("language") as? String
    val appLanguage = context.resources.configuration.locales[0].language
    var language by rememberSaveable {
        mutableStateOf(when {
            languages.size == 1 -> languages.single()
            before != null && before in languages -> before
            appLanguage in languages -> appLanguage
            else -> null
        })
    }
    UI.Dialog(type = DialogType.CONFIRM, confirmEnabled = language != null, onConfirm = { language?.let(onConfirm) }, onCancel = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.shared("identity_import_title").format(preview["name"] as String), TextType.TITLE)
            UI.Text(s.shared("identity_import_published").format(preview["published_at"] as String, languages.joinToString(", ")), TextType.BODY)
            if (existing != null) {
                val published = existing["published_at"] as? String
                UI.Text(if (published != null) s.shared("identity_import_replaces").format(published) else s.shared("identity_import_replaces_written"), TextType.BODY)
                if (existing["older"] == true) UI.Text(s.shared("identity_import_older"), TextType.WARNING)
                (existing["languages_gained"] as? List<*>)?.takeIf { it.isNotEmpty() }?.let { UI.Text(s.shared("identity_import_gained").format(it.joinToString(", ")), TextType.BODY) }
                (existing["languages_lost"] as? List<*>)?.takeIf { it.isNotEmpty() }?.let { UI.Text(s.shared("identity_import_lost").format(it.joinToString(", ")), TextType.WARNING) }
                (existing["retouched"] as? List<*>)?.takeIf { it.isNotEmpty() }?.let { UI.Text(s.shared("identity_import_retouched").format(it.joinToString(", ")), TextType.WARNING) }
                if (before != null && before !in languages) UI.Text(s.shared("identity_import_language_lost").format(before), TextType.WARNING)
            }
            UI.FormSelection(
                label = s.shared("identity_language"),
                options = languages,
                selected = language ?: "",
                onSelect = { language = it },
                required = true
            )
        }
    }
}

/**
 * One identity: its name, icon and the language it speaks; its three texts and its bagage in the
 * language shown, each read laid out and edited as written; its deletion, refused while a zone
 * or a routine names it.
 */
@Composable
private fun IdentityScreen(id: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scope = rememberCoroutineScope()
    val coordinator = remember { Coordinator(context) }

    var identity by remember { mutableStateOf<Map<*, *>?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(id, version) {
        val result = coordinator.processUserAction("identities.get", mapOf("identity_id" to id))
        if (result.isSuccess) identity = result.data!!["identity"] as Map<*, *>
        else { UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG); onBack() }
    }
    LaunchedEffect(id) {
        DataChangeNotifier.changes.collect { if (it is DataChangeEvent.IdentitiesChanged) version++ }
    }
    val shown = identity ?: run {
        UI.Text(s.shared("message_loading"), TextType.BODY)
        return
    }
    val sections = shown["sections"] as Map<*, *>
    val languages = sections.keys.map { it as String }

    var name by rememberSaveable(id) { mutableStateOf(shown["name"] as String) }
    var iconName by rememberSaveable(id) { mutableStateOf(shown["icon_name"] as String) }
    var iconColor by rememberSaveable(id) { mutableStateOf(shown[IconColor.KEY] as? String) }
    var language by rememberSaveable(id) { mutableStateOf(shown["language"] as String) }
    var section by rememberSaveable(id) { mutableStateOf(shown["language"] as String) }
    // What is being written: a text by its key, a resource by its key ("" for a new one)
    var editingPiece by rememberSaveable { mutableStateOf<String?>(null) }
    var editingResource by rememberSaveable { mutableStateOf<String?>(null) }

    fun update(params: Map<String, Any?>, after: () -> Unit = {}) = scope.launch {
        val result = coordinator.processUserAction("identities.update", mapOf("identity_id" to id) + params)
        if (result.isSuccess) after() else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
    }

    val current = sections[section] as? Map<*, *> ?: sections[shown["language"]] as Map<*, *>
    val pieces = current["pieces"] as Map<*, *>
    val resources = (current["resources"] as List<*>).map { it as Map<*, *> }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(title = shown["name"] as String, icon = iconName, iconColor = IconColor.of(iconColor),
            leftButton = ButtonAction.BACK, onLeftClick = onBack)

        editingPiece?.let { key ->
            PieceEditor(s.shared("identity_piece_$key"), (pieces[key] as? Map<*, *>)?.get("content") as? String ?: "",
                onSave = { text -> update(mapOf("section" to section, key to text)) { editingPiece = null } },
                onCancel = { editingPiece = null })
            return@Column
        }
        editingResource?.let { key ->
            ResourceEditor(resources.firstOrNull { it["key"] == key },
                onSave = { title, description, content ->
                    val resourceKey = key.ifEmpty { newKey(title, resources.map { it["key"] as String }) }
                    update(mapOf("section" to section, "resources" to mapOf(resourceKey to mapOf("title" to title, "description" to description, "content" to content)))) { editingResource = null }
                },
                onCancel = { editingResource = null })
            return@Column
        }

        // Its name, icon and the language it speaks
        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.FormField(label = s.shared("label_name"), value = name, onChange = { name = it }, required = true)
                app.treelune.core.ui.components.IconSelector(current = iconName, color = IconColor.of(iconColor), onChange = { iconName = it })
                app.treelune.core.ui.components.IconColorSelector(current = iconColor, iconName = iconName, onChange = { iconColor = it })
                UI.FormSelection(label = s.shared("identity_language"), options = languages, selected = language, onSelect = { language = it }, required = true)
                UI.FormActions {
                    UI.ActionButton(action = ButtonAction.SAVE, onClick = {
                        update(mapOf("name" to name, "icon_name" to iconName, IconColor.KEY to iconColor, "language" to language)) {
                            UI.Toast(context, s.shared("identity_saved"), Duration.SHORT)
                        }
                    })
                }
            }
        }

        // The language its texts are shown and written in
        if (languages.size > 1) UI.FormSelection(label = s.shared("identity_section"), options = languages, selected = section, onSelect = { section = it }, required = true)

        IdentityLanguage.PIECES.forEach { key ->
            val piece = pieces[key] as? Map<*, *>
            UI.Card(type = CardType.DEFAULT) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    UI.Text(s.shared("identity_piece_$key"), TextType.HEADING)
                    if (piece == null) UI.Text(s.shared("identity_piece_empty"), TextType.CAPTION)
                    else {
                        val content = piece["content"] as String
                        UI.Text(origin(s, content.length, piece["address"] as? String), TextType.CAPTION)
                        GuideText(content)
                    }
                    UI.FormActions { UI.ActionButton(action = ButtonAction.EDIT, onClick = { editingPiece = key }) }
                }
            }
        }

        // The bagage: resources read when needed
        UI.Text(s.shared("identity_bagage"), TextType.HEADING)
        UI.Text(s.shared("identity_bagage_hint"), TextType.CAPTION)
        resources.forEach { resource -> ResourceCard(resource, onEdit = { editingResource = resource["key"] as String }, onDelete = {
            update(mapOf("section" to section, "resources" to mapOf(resource["key"] as String to null)))
        }) }
        UI.FormActions {
            UI.Button(type = ButtonType.DEFAULT, onClick = { editingResource = "" }) { UI.Text(s.shared("identity_resource_add"), TextType.BODY) }
        }

        UI.FormActions {
            UI.ActionButton(
                action = ButtonAction.DELETE,
                requireConfirmation = true,
                confirmMessage = s.shared("identity_delete_confirm").format(shown["name"] as String),
                onClick = {
                    scope.launch {
                        val result = coordinator.processUserAction("identities.delete", mapOf("identity_id" to id))
                        if (result.isSuccess) onBack() else UI.Toast(context, result.error ?: s.shared("error_operation_failed"), Duration.LONG)
                    }
                }
            )
        }
    }
}

/** A text's size and where it comes from: personae's address, or the app. */
private fun origin(s: app.treelune.core.strings.StringsContext, chars: Int, address: String?): String =
    s.shared("identity_size").format(chars) + " · " +
        (address?.let { s.shared("identity_origin_personae").format(it) } ?: s.shared("identity_origin_app"))

/** A resource: its title, size, origin and description; its content shown on demand, laid out. */
@Composable
private fun ResourceCard(resource: Map<*, *>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var open by rememberSaveable(resource["key"]) { mutableStateOf(false) }
    val content = resource["content"] as String
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(resource["title"] as String, TextType.SUBTITLE)
            UI.Text(origin(s, content.length, resource["address"] as? String), TextType.CAPTION)
            UI.Text(resource["description"] as String, TextType.BODY, selectable = true)
            if (open) GuideText(content)
            UI.FormActions {
                UI.Button(type = ButtonType.DEFAULT, onClick = { open = !open }) {
                    UI.Text(s.shared(if (open) "identity_resource_fold" else "identity_resource_read"), TextType.BODY)
                }
                UI.ActionButton(action = ButtonAction.EDIT, onClick = onEdit)
                UI.ActionButton(action = ButtonAction.DELETE, requireConfirmation = true,
                    confirmMessage = s.shared("identity_resource_delete_confirm").format(resource["title"] as String), onClick = onDelete)
            }
        }
    }
}

/** A text written as it is, raw: the screen lays it out once saved. Empty, it is removed. */
@Composable
private fun PieceEditor(title: String, initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    UI.Text(title, TextType.HEADING)
    UI.FormField(label = title, value = text, onChange = { text = it }, fieldType = FieldType.TEXT_UNLIMITED, required = false)
    UI.FormActions {
        UI.ActionButton(action = ButtonAction.SAVE, onClick = { onSave(text) })
        UI.ActionButton(action = ButtonAction.CANCEL, onClick = onCancel)
    }
}

/** A resource written whole: its title, its description (when to call it up), its content. */
@Composable
private fun ResourceEditor(resource: Map<*, *>?, onSave: (String, String, String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var title by rememberSaveable { mutableStateOf(resource?.get("title") as? String ?: "") }
    var description by rememberSaveable { mutableStateOf(resource?.get("description") as? String ?: "") }
    var content by rememberSaveable { mutableStateOf(resource?.get("content") as? String ?: "") }
    UI.FormField(label = s.shared("identity_resource_title"), value = title, onChange = { title = it }, required = true)
    UI.FormField(label = s.shared("identity_resource_description"), value = description, onChange = { description = it }, fieldType = FieldType.TEXT_MEDIUM, required = true)
    UI.FormField(label = s.shared("identity_resource_content"), value = content, onChange = { content = it }, fieldType = FieldType.TEXT_UNLIMITED, required = true)
    UI.FormActions {
        UI.ActionButton(action = ButtonAction.SAVE, onClick = { onSave(title.trim(), description.trim(), content) })
        UI.ActionButton(action = ButtonAction.CANCEL, onClick = onCancel)
    }
}

/** The key of a resource written here: its title in lower-case ASCII, made unique among [taken]. */
private fun newKey(title: String, taken: List<String>): String {
    val base = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifEmpty { "resource" }
    return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
}
