package app.treelune.core.apis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.apis.ApiSettings
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.fields.settings.labelOf
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.Duration
import app.treelune.core.ui.JsonObjectSaver
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The APIs the user or the AI declared (docs/CORE.md, « APIs »): one card each, its name, its
 * address, and « Key to enter » when it takes a key and holds none; touched, its config
 * (Place.Api). « Add an API » opens an empty one.
 */
@Composable
fun ApisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    var apis by remember { mutableStateOf<List<Map<*, *>>?>(null) }

    LaunchedEffect(Unit) {
        val result = coordinator.processUserAction("apis.list")
        if (result.status == CommandStatus.SUCCESS) apis = (result.data?.get("apis") as List<*>).map { it as Map<*, *> }
        else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = s.shared("settings_apis"),
            subtitle = s.shared("settings_apis_description"),
            icon = null,
            leftButton = ButtonAction.BACK,
            rightButton = null,
            onLeftClick = onBack,
            onRightClick = { }
        )
        val list = apis
        when {
            list == null -> UI.Text(text = s.shared("message_loading"), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
            list.isEmpty() -> UI.Text(text = s.shared("apis_empty"), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
            else -> list.forEach { api ->
                UI.Card(type = CardType.DEFAULT, size = Size.M) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { Navigator.push(Place.Api(api["id"] as String, null)) }.padding(UI.Space.L),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
                    ) {
                        UI.Text(text = api["name"] as String, type = TextType.STRONG)
                        UI.Text(text = listOfNotNull(api["url"] as String, s.shared("api_response_deferred").takeIf { api["deferred"] == true }).joinToString(" · "), type = TextType.CAPTION)
                        // An API that takes a key and holds none (declared by the AI): refused until it is entered
                        if (api["key_missing"] == true) UI.Text(text = s.shared("api_key_missing"), type = TextType.WARNING)
                    }
                }
            }
        }
        UI.FormActions {
            UI.Button(type = ButtonType.PRIMARY, onClick = { Navigator.push(Place.Api(null, null)) }) {
                UI.Text(s.shared("api_add"), TextType.BODY)
            }
        }
    }
}

/**
 * The config of the API [editing] names: read from the service when stored or copied, empty when
 * new. Its form is its declaration's (ApiSettings); « Try » calls it with the config as it stands
 * and the inputs typed beside, and shows the raw answer above what each value's path gives,
 * writing nothing. Saved or deleted, it leaves for the list; duplicated, the copy opens over it.
 */
@Composable
fun ApiScreen(editing: Place.Api, onName: @Composable (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val nodes = remember { ApiSettings.nodes(context) }

    var loaded by remember(editing) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(editing) {
        val source = editing.id ?: editing.copyOf
        if (source == null) {
            loaded = "" to "{}"
            return@LaunchedEffect
        }
        val result = coordinator.processUserAction("apis.get", mapOf("api_id" to source))
        if (result.status != CommandStatus.SUCCESS) {
            UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
            Navigator.drop(editing)
            return@LaunchedEffect
        }
        val data = result.data!!
        @Suppress("UNCHECKED_CAST")
        val config = JsonUtils.toJSONObject(data["config"] as Map<String, Any?>).toString()
        val name = data["name"] as String
        loaded = (if (editing.copyOf != null) s.shared("api_copy_name").format(name) else name) to config
        // A key sealed on another phone shows empty: said, so it is not taken for one never entered
        (data[app.treelune.core.secrets.SecretSettings.UNREADABLE] as? List<*>).orEmpty().takeIf { it.isNotEmpty() }?.let { names ->
            UI.Toast(s.shared("secrets_unreadable").format(names.joinToString(", ") { nodes.labelOf(it.toString()) ?: it.toString() }), Duration.LONG)
        }
    }
    val (storedName, storedConfig) = loaded ?: run {
        UI.Text(text = s.shared("message_loading"), type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
        return
    }

    var name by rememberSaveable(storedName) { mutableStateOf(storedName) }
    var settings by rememberSaveable(storedConfig, stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject(storedConfig)) }
    var inputs by rememberSaveable(storedConfig, stateSaver = JsonObjectSaver) { mutableStateOf(JSONObject()) }
    var tried by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var trying by remember { mutableStateOf(false) }
    onName(name.ifBlank { s.shared("api_new") })

    /** Back to the list of APIs, wherever the config was opened from. */
    fun toList() {
        val index = Navigator.stack.indexOf(Place.SettingsPage("apis"))
        if (index >= 0) Navigator.popTo(index) else Navigator.pop()
    }
    fun act(operation: String, params: Map<String, Any?>) {
        scope.launch {
            val result = coordinator.processUserAction(operation, params)
            if (result.status == CommandStatus.SUCCESS) toList() else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = UI.Space.L).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(title = name.ifBlank { s.shared("api_new") }, subtitle = s.shared("api_display_name"))
        val pages = app.treelune.core.fields.settings.rememberSettingsPages()
        UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
                if (pages.atRoot) UI.FormField(label = s.shared("api_name"), value = name, onChange = { name = it }, required = true)
                SettingsForm(nodes, settings, { settings = it }, context, pages = pages)
                if (pages.atRoot) UI.Text(s.shared("api_help"), TextType.CAPTION)
            }
        }

        if (pages.atRoot) {
            // « Try »: the inputs typed as text, the API reading each as its type says
            UI.Card(type = CardType.DEFAULT, size = Size.M) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                    UI.Text(s.shared("api_try_title"), TextType.STRONG)
                    val declared = settings.optJSONArray(ApiSettings.INPUTS)?.let { list -> (0 until list.length()).map { list.getJSONObject(it).optString(ApiSettings.NAME) } }.orEmpty()
                    declared.filter { it.isNotBlank() }.forEach { input ->
                        UI.FormField(label = input, value = inputs.optString(input), onChange = { inputs = JSONObject(inputs.toString()).put(input, it) }, required = false)
                    }
                    UI.Button(type = ButtonType.SECONDARY, state = if (trying) ComponentState.DISABLED else ComponentState.NORMAL, onClick = {
                        scope.launch {
                            trying = true
                            val given = declared.filter { inputs.optString(it).isNotEmpty() }.associateWith { inputs.optString(it) }
                            val result = coordinator.processUserAction("apis.try", mapOf("config" to JsonUtils.toMap(settings), "inputs" to given))
                            trying = false
                            if (result.status == CommandStatus.SUCCESS) tried = result.data else UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
                        }
                    }) { UI.Text(s.shared(if (trying) "api_trying" else "api_try"), TextType.BODY) }
                    tried?.let { Tried(it, s) }
                }
            }

            UI.FormActions {
                UI.ActionButton(action = ButtonAction.SAVE, onClick = {
                    val config = JsonUtils.toMap(settings)
                    if (editing.id != null) act("apis.update", mapOf("api_id" to editing.id, "name" to name, "config" to config))
                    else act("apis.create", mapOf("name" to name, "config" to config))
                })
                UI.ActionButton(action = ButtonAction.CANCEL, onClick = onBack)
            }
            editing.id?.let { id ->
                UI.FormActions {
                    UI.Button(type = ButtonType.SECONDARY, onClick = { Navigator.push(Place.Api(null, id)) }) { UI.Text(s.shared("action_duplicate"), TextType.BODY) }
                    UI.ActionButton(action = ButtonAction.DELETE, requireConfirmation = true, onClick = { act("apis.delete", mapOf("api_id" to id)) })
                }
            }
        }
    }
}

/** What « Try » gave: the status, the id of a deferred job, each value or why it has none, then the raw answer. */
@Composable
private fun Tried(result: Map<String, Any?>, s: app.treelune.core.strings.StringsContext) {
    UI.Text(s.shared("api_try_code").format(result["code"]), TextType.CAPTION)
    (result["job_id"] as? String)?.let { UI.Text(s.shared("label_value").format(s.shared("api_try_job"), it), TextType.BODY) }
    (result["values"] as List<*>).map { it as Map<*, *> }.forEach { value ->
        val shown = value["error"]?.toString() ?: value["value"].toString()
        UI.Text(s.shared("label_value").format(value["name"], shown), if (value.containsKey("error")) TextType.ERROR else TextType.BODY)
    }
    UI.Text(s.shared("api_try_raw"), TextType.LABEL)
    UI.Text((result["raw"] as String).take(SHOWN_RAW).ifEmpty { s.shared("api_raw_empty") }, TextType.CAPTION)
}

/** The most of a raw answer « Try » shows: enough to find a path, short enough to scroll past. */
private const val SHOWN_RAW = 6_000
