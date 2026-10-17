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
import kotlinx.coroutines.launch

/** A provider as a list or a choice shows it (ai_provider_config.list): its name, and its model beside. */
fun providerLabel(provider: Map<*, *>): String =
    provider["name"] as String + ((provider["model"] as String?)?.let { " · $it" } ?: "")

/**
 * What the providers screen shows instead of the list: a provider's config, stored ([id]) or new
 * of [kind], copied from [copyOf] when duplicated; or the choice of a new provider's kind.
 */
private data class Editing(val id: String? = null, val kind: String? = null, val copyOf: String? = null, val choosingKind: Boolean = false)

private val EditingSaver = androidx.compose.runtime.saveable.listSaver<Editing?, Any?>(
    save = { listOf(it?.id, it?.kind, it?.copyOf, it?.choosingKind, it != null) },
    restore = { if (it[4] == true) Editing(it[0] as String?, it[1] as String?, it[2] as String?, it[3] as Boolean) else null }
)

/**
 * The AI providers the user created (docs/design/ai-providers.md): one card each, its name, its
 * model and its kind, the default marked; touched, its config. "Add a provider" chooses a kind, then
 * opens the config of a new one, named after its kind. The config of a stored one duplicates it,
 * makes it the default, or deletes it: refused while a routine names it, and the default deleted
 * while others remain asks which becomes it.
 */
@Composable
fun AIProvidersScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val kinds = remember { AIProviderKinds(context) }
    val coroutineScope = rememberCoroutineScope()

    var providers by remember { mutableStateOf<List<Map<*, *>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(stateSaver = EditingSaver) { mutableStateOf<Editing?>(null) }

    fun reload() {
        coroutineScope.launch {
            val result = coordinator.processUserAction("ai_provider_config.list")
            if (result.status == CommandStatus.SUCCESS) providers = (result.data?.get("providers") as List<*>).map { it as Map<*, *> }
            else errorMessage = result.error ?: s.shared("error_unknown")
        }
    }
    LaunchedEffect(Unit) { reload() }

    // Error display (toast pattern), before the config screen's early return: a refused save
    // shows while the config screen is still open, not once it is left
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            UI.Toast(context, error, Duration.LONG)
            errorMessage = null
        }
    }

    /** [operation] run with [params]; done, the list is read again and [then] runs. */
    fun act(operation: String, params: Map<String, Any?>, then: () -> Unit = { editing = null }) {
        coroutineScope.launch {
            val result = coordinator.processUserAction(operation, params)
            if (result.status == CommandStatus.SUCCESS) {
                then()
                reload()
            } else errorMessage = result.error ?: s.shared("error_unknown")
        }
    }

    val current = editing
    if (current != null && current.choosingKind) {
        androidx.activity.compose.BackHandler { editing = null }
        KindChoice(kinds, onBack = { editing = null }, onChoose = { editing = Editing(kind = it) })
        return
    }
    if (current != null) {
        ProviderConfig(current, kinds, providers.orEmpty(), coordinator,
            onError = { errorMessage = it },
            onCancel = { editing = null },
            onSave = { name, config ->
                if (current.id != null) act("ai_provider_config.update", mapOf("provider_id" to current.id, "name" to name, "config" to JsonUtils.toMap(config)))
                else act("ai_provider_config.create", mapOf("kind" to current.kind, "name" to name, "config" to JsonUtils.toMap(config)))
            },
            onDuplicate = { id, kind -> editing = Editing(kind = kind, copyOf = id) },
            onSetDefault = { id -> act("ai_provider_config.set_default", mapOf("provider_id" to id)) },
            onDelete = { id, newDefault -> act("ai_provider_config.delete", mapOf("provider_id" to id, "new_default_id" to newDefault)) })
        return
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
                            .clickable { editing = Editing(id = provider["id"] as String) }
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
            UI.Button(type = ButtonType.PRIMARY, onClick = { editing = Editing(choosingKind = true) }) {
                UI.Text(s.shared("ai_provider_add"), TextType.BODY)
            }
        }
    }
}

/** The kinds a new provider can be of, one card each with what it is. */
@Composable
private fun KindChoice(kinds: AIProviderKinds, onBack: () -> Unit, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
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
                    modifier = Modifier.fillMaxWidth().clickable { onChoose(kind.kind) }.padding(UI.Space.L),
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
 * and named after its kind when new. Deleting the default while others remain asks which becomes it.
 */
@Composable
private fun ProviderConfig(
    editing: Editing,
    kinds: AIProviderKinds,
    providers: List<Map<*, *>>,
    coordinator: Coordinator,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (String, org.json.JSONObject) -> Unit,
    onDuplicate: (String, String) -> Unit,
    onSetDefault: (String) -> Unit,
    onDelete: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Its kind, name and config once read
    var loaded by remember(editing) { mutableStateOf<Triple<String, String, String>?>(null) }
    LaunchedEffect(editing) {
        val source = editing.id ?: editing.copyOf
        if (source == null) {
            val kind = kinds.of(editing.kind!!)!!
            loaded = Triple(kind.kind, kind.kindName(context), "{}")
            return@LaunchedEffect
        }
        val result = coordinator.processUserAction("ai_provider_config.get", mapOf("provider_id" to source))
        if (result.status != CommandStatus.SUCCESS) {
            onError(result.error ?: s.shared("error_unknown"))
            onCancel()
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
        onCancel = onCancel,
        onDuplicate = id?.let { { onDuplicate(it, kind.kind) } },
        onSetDefault = id?.takeIf { !isDefault }?.let { { onSetDefault(it) } },
        onDelete = id?.let { { if (needsNewDefault) choosingNewDefault = true else onDelete(it, null) } },
        deleteConfirmed = needsNewDefault
    )
}
