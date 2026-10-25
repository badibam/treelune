package app.treelune.core.events.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.events.EventValues
import app.treelune.core.fields.settings.SettingsForm
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The events of a zone (docs/CORE.md, « Events »), under its identity: one concise card naming
 * them all, and one button opening their editor. Nothing while the zone has none: an event is
 * then made where it is chosen (« New event… » of the browser).
 */
@Composable
fun EventsCard(zoneId: String) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var events by remember { mutableStateOf<List<Map<*, *>>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    suspend fun load() {
        val result = Coordinator(context).processUserAction("events.list", mapOf("zone_id" to zoneId))
        events = (result.data?.get("events") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    LaunchedEffect(zoneId) {
        load()
        DataChangeNotifier.changes.collect { if (it is DataChangeEvent.ZonesChanged) load() }
    }
    if (events.isEmpty()) return
    UI.Card(type = CardType.DEFAULT) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(UI.Space.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.ItemIcon("zap", null)
            Column(modifier = Modifier.weight(1f)) {
                UI.Text(text = s.shared("event_card_title"), type = TextType.LABEL)
                UI.Text(text = events.joinToString(" · ") { it["name"] as String }, type = TextType.BODY)
            }
            UI.ActionButton(action = ButtonAction.EDIT, display = ButtonDisplay.ICON, onClick = { editing = true })
        }
    }
    if (editing) EventsEditor(zoneId, events, s) { editing = false }
}

/** The events of [zoneId], each opened to change it or deleted, and a new one: closed by [onClose]. */
@Composable
private fun EventsEditor(zoneId: String, events: List<Map<*, *>>, s: StringsContext, onClose: () -> Unit) {
    // The event a form is open on: its id (null for a new one) and what it holds
    var open by remember { mutableStateOf<Pair<String?, JSONObject>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    UI.Dialog(type = DialogType.INFO, onConfirm = onClose, onCancel = onClose) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(text = s.shared("event_card_title"), type = TextType.TITLE)
            events.forEach { event ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    Column(modifier = Modifier.weight(1f)) {
                        UI.Text(text = event["name"] as String, type = TextType.BODY)
                        (event[EventValues.DESCRIPTION] as? String)?.let { UI.Text(text = it, type = TextType.CAPTION) }
                        UI.Text(text = summary(event["values"] as? List<*>, s), type = TextType.CAPTION)
                    }
                    UI.ActionButton(action = ButtonAction.EDIT, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                        open = event["id"] as String to JSONObject().put(EventValues.NAME, event["name"]).put(EventValues.VALUES, JsonUtils.toJSONArray(event["values"] as? List<*> ?: emptyList<Any>()))
                            .apply { (event[EventValues.DESCRIPTION] as? String)?.let { put(EventValues.DESCRIPTION, it) } }
                    })
                    UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, requireConfirmation = true, onClick = {
                        scope.launch {
                            val result = Coordinator(context).processUserAction("events.delete", mapOf("event_id" to event["id"]))
                            if (!result.isSuccess) UI.Toast(result.error ?: s.shared("error_unknown"))
                        }
                    })
                }
            }
            Box {
                UI.Button(type = ButtonType.SECONDARY, onClick = { open = null to JSONObject().put(EventValues.VALUES, JSONArray()) }) {
                    UI.Text(s.shared("event_new"), TextType.BODY)
                }
            }
        }
    }
    open?.let { (id, held) ->
        var draft by remember(id) { mutableStateOf(held) }
        UI.Dialog(type = if (id == null) DialogType.CREATE else DialogType.EDIT, onConfirm = {
            scope.launch {
                // A description the form emptied is sent as null, which removes it
                val params = JsonUtils.toMap(draft) + (if (id == null) mapOf("zone_id" to zoneId) else mapOf("event_id" to id)) +
                    (if (id != null && !draft.has(EventValues.DESCRIPTION)) mapOf(EventValues.DESCRIPTION to null) else emptyMap())
                val result = Coordinator(context).processUserAction(if (id == null) "events.create" else "events.update", params)
                if (result.isSuccess) open = null else UI.Toast(result.error ?: s.shared("error_unknown"))
            }
        }, onCancel = { open = null }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.L)) {
                SettingsForm(remember { EventValues.nodes(s::shared) }, draft, { draft = it }, context)
            }
        }
    }
}

/** What an event carries, said in a line: each value's name and type, or that it carries nothing. */
private fun summary(values: List<*>?, s: StringsContext): String = values.orEmpty().filterIsInstance<Map<*, *>>()
    .joinToString(", ") { "${it[EventValues.NAME]} (${s.shared("event_value_type_${it[EventValues.TYPE]}")})" }
    .ifEmpty { s.shared("event_values_none") }
