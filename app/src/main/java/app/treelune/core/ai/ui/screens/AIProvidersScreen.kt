package app.treelune.core.ai.ui.screens

import app.treelune.core.fields.settings.labelOf
import app.treelune.core.utils.JsonUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.ui.*
import app.treelune.core.strings.Strings
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.commands.CommandStatus
import app.treelune.core.ai.providers.AIProviderKinds
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import kotlinx.coroutines.launch

/** A provider as a list or a choice shows it (ai_provider_config.list): its name, and its model beside. */
fun providerLabel(provider: Map<*, *>): String =
    provider["name"] as String + ((provider["model"] as String?)?.let { " · $it" } ?: "")

/**
 * The AI providers the user created (docs/design/ai-providers.md): one card each, its name, its
 * model and its kind, the default marked; touched, its config (Place.AIProvider). "Add a provider"
 * opens the choice of a kind (Place.AIProviderKind), which gives way to the config of a new one,
 * named after its kind.
 */
@Composable
fun AIProvidersScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }

    var providers by remember { mutableStateOf<List<Map<*, *>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("ai_provider_config.list")
        if (result.status == CommandStatus.SUCCESS) providers = (result.data?.get("providers") as List<*>).map { it as Map<*, *> }
        else errorMessage = result.error ?: s.shared("error_unknown")
    }

    // Error display (toast pattern)
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            UI.Toast(context, error, Duration.LONG)
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = s.shared("settings_ai_providers"),
            subtitle = s.shared("settings_ai_providers_description"),
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = { }
        )

        val list = providers
        when {
            list == null -> UI.Text(text = s.shared("message_loading"), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
            // What a provider is for, when there is none yet
            list.isEmpty() -> UI.Text(text = s.shared("ai_providers_empty"), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
            else -> list.forEach { provider ->
                UI.Card(type = CardType.DEFAULT, size = Size.M) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { Navigator.push(Place.AIProvider(provider["id"] as String, null, null)) }
                            .padding(UI.Space.L),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (provider["is_default"] == true) UI.Icon(iconName = "circle-dot")
                            UI.Text(text = providerLabel(provider), type = TextType.SUBTITLE)
                        }
                        val kindLine = listOfNotNull(provider["kind_name"] as String?, s.shared("ai_provider_is_default").takeIf { provider["is_default"] == true })
                        UI.Text(text = kindLine.joinToString(" · "), type = TextType.CAPTION)
                    }
                }
            }
        }

        UI.FormActions {
            UI.Button(type = ButtonType.PRIMARY, onClick = { Navigator.push(Place.AIProviderKind) }) {
                UI.Text(s.shared("ai_provider_add"), TextType.BODY)
            }
        }
    }
}

/** The kinds a new provider can be of, one card each with what it is; one chosen gives way to the new provider's config. */
@Composable
fun AIProviderKindScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val kinds = remember { AIProviderKinds(context) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = s.shared("ai_provider_choose_kind"),
            subtitle = s.shared("ai_provider_choose_kind_description"),
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = { }
        )
        kinds.all().forEach { kind ->
            UI.Card(type = CardType.DEFAULT, size = Size.M) {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { Navigator.replace(Place.AIProvider(null, kind.kind, null)) }.padding(UI.Space.L),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                ) {
                    UI.Text(text = kind.kindName(context), type = TextType.SUBTITLE)
                    UI.Text(text = kind.kindDescription(context), type = TextType.CAPTION)
                }
            }
        }
    }
}

/**
 * The config of the provider [editing] names: read from the service when stored or copied, empty
 * and named after its kind when new. Saved, made the default or deleted, it leaves for the list;
 * duplicated, the copy opens over it. Deleting the default while others remain asks which becomes it.
 *
 * @param onName Names the place in the breadcrumb: the provider's name, once read
 */
@Composable
fun AIProviderScreen(editing: Place.AIProvider, onName: @Composable (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val kinds = remember { AIProviderKinds(context) }
    val coroutineScope = rememberCoroutineScope()

    // The others, for the default: which this one is, which may take its place
    var providers by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("ai_provider_config.list")
        if (result.status == CommandStatus.SUCCESS) providers = (result.data?.get("providers") as List<*>).map { it as Map<*, *> }
    }
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            UI.Toast(context, error, Duration.LONG)
            errorMessage = null
        }
    }
    val onError = { message: String -> errorMessage = message }

    /** Back to the list of providers, wherever the config was opened from. */
    fun toList() {
        val index = Navigator.stack.indexOf(Place.SettingsPage("ai_providers"))
        if (index >= 0) Navigator.popTo(index) else Navigator.pop()
    }

    /** [operation] run with [params]; done, back to the list. */
    fun act(operation: String, params: Map<String, Any?>) {
        coroutineScope.launch {
            val result = coordinator.processUserAction(operation, params)
            if (result.status == CommandStatus.SUCCESS) toList() else errorMessage = result.error ?: s.shared("error_unknown")
        }
    }
    val onSave = { name: String, config: org.json.JSONObject ->
        if (editing.id != null) act("ai_provider_config.update", mapOf("provider_id" to editing.id, "name" to name, "config" to JsonUtils.toMap(config)))
        else act("ai_provider_config.create", mapOf("kind" to editing.providerKind, "name" to name, "config" to JsonUtils.toMap(config)))
    }
    val onDuplicate = { id: String, kind: String -> Navigator.push(Place.AIProvider(null, kind, id)) }
    val onSetDefault = { id: String -> act("ai_provider_config.set_default", mapOf("provider_id" to id)) }
    val onDelete = { id: String, newDefault: String? -> act("ai_provider_config.delete", mapOf("provider_id" to id, "new_default_id" to newDefault)) }

    // Its kind, name and config once read
    var loaded by remember(editing) { mutableStateOf<Triple<String, String, String>?>(null) }
    LaunchedEffect(editing) {
        val source = editing.id ?: editing.copyOf
        if (source == null) {
            val kind = kinds.of(editing.providerKind!!)!!
            loaded = Triple(kind.kind, kind.kindName(context), "{}")
            return@LaunchedEffect
        }
        val result = coordinator.processUserAction("ai_provider_config.get", mapOf("provider_id" to source))
        if (result.status != CommandStatus.SUCCESS) {
            UI.Toast(context, result.error ?: s.shared("error_unknown"), Duration.LONG)
            Navigator.drop(editing)
            return@LaunchedEffect
        }
        val data = result.data!!
        val kind = data["kind"] as String
        @Suppress("UNCHECKED_CAST")
        val config = JsonUtils.toJSONObject(data["config"] as Map<String, Any?>).toString()
        val name = data["name"] as String
        loaded = Triple(kind, if (editing.copyOf != null) s.shared("ai_provider_copy_name").format(name) else name, config)
        // A key sealed on another phone shows empty: said, so it is not taken for one never entered
        (data[app.treelune.core.secrets.SecretSettings.UNREADABLE] as? List<*>).orEmpty().takeIf { it.isNotEmpty() }?.let { names ->
            val nodes = kinds.of(kind)?.getConfigSettings(context).orEmpty()
            onError(s.shared("secrets_unreadable").format(names.joinToString(", ") { name -> nodes.labelOf(name.toString()) ?: name.toString() }))
        }
    }

    val (kindName, name, config) = loaded ?: run {
        UI.Text(text = s.shared("message_loading"), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
        return
    }
    val kind = kinds.of(kindName) ?: run {
        UI.Text(text = s.shared("ai_error_unknown_provider").format(kindName), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
        return
    }

    onName(name)
    val id = editing.id
    val stored = providers.find { it["id"] == id }
    val isDefault = stored?.get("is_default") == true
    val others = providers.filter { it["id"] != id }
    var choosingNewDefault by rememberSaveable { mutableStateOf(false) }
    val needsNewDefault = isDefault && others.isNotEmpty()

    if (choosingNewDefault && id != null) {
        var chosen by remember { mutableStateOf<String?>(null) }
        UI.Dialog(type = DialogType.DANGER, onConfirm = { chosen?.let { choosingNewDefault = false; onDelete(id, it) } },
            onCancel = { choosingNewDefault = false }, confirmEnabled = chosen != null) {
            Column(modifier = Modifier.padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
                UI.Text(text = s.shared("ai_provider_new_default_title"), type = TextType.TITLE)
                UI.Text(text = s.shared("ai_provider_new_default_message").format(name), type = TextType.BODY)
                val labels = others.associate { providerLabel(it) to (it["id"] as String) }
                UI.FormSelection(label = s.shared("ai_provider_is_default"), options = labels.keys.toList(),
                    selected = labels.entries.find { it.value == chosen }?.key ?: "", onSelect = { chosen = labels[it] }, required = true)
            }
        }
    }

    app.treelune.core.ai.providers.ui.AIProviderConfigScreen(
        provider = kind,
        name = name,
        config = config,
        onSave = onSave,
        onCancel = onBack,
        onDuplicate = id?.let { { onDuplicate(it, kind.kind) } },
        onSetDefault = id?.takeIf { !isDefault }?.let { { onSetDefault(it) } },
        onDelete = id?.let { { if (needsNewDefault) choosingNewDefault = true else onDelete(it, null) } },
        deleteConfirmed = needsNewDefault
    )
}
