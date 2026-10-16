package app.treelune.core.identities.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.orchestration.ChatRequests
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.identities.Identity
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.OptionIcon
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.CardType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager

/** An identity as a screen offers it: by its name and its icon. */
data class IdentityChoice(val id: String, val name: String, val iconName: String, val iconColor: String?)

/**
 * The identities, read again whenever one changes; null until first read, and when they cannot
 * be read (logged).
 */
@Composable
fun rememberIdentities(): List<IdentityChoice>? {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    var identities by remember { mutableStateOf<List<IdentityChoice>?>(null) }
    suspend fun read() {
        val result = coordinator.processUserAction("identities.list", emptyMap())
        if (!result.isSuccess) {
            LogManager.ui("Identities not read: ${result.error}", "ERROR")
            return
        }
        identities = (result.data?.get("identities") as List<*>).map { item ->
            val map = item as Map<*, *>
            IdentityChoice(map["id"] as String, map["name"] as String,
                map["icon_name"] as? String ?: Identity.DEFAULT_ICON, map[IconColor.KEY] as? String)
        }
    }
    LaunchedEffect(Unit) {
        read()
        DataChangeNotifier.changes.collect { if (it is DataChangeEvent.IdentitiesChanged) read() }
    }
    return identities
}

/**
 * A choice of an identity among [identities], each shown by its icon, or none, written
 * [noneLabel] ("None" for a zone, "The app's AI" for a conversation or a routine). An identity
 * [selected] names that no longer exists shows as its id, for the choice to be made again.
 */
@Composable
fun IdentitySelector(
    identities: List<IdentityChoice>,
    selected: String?,
    onSelect: (String?) -> Unit,
    label: String,
    noneLabel: String
) {
    val gone = selected?.takeIf { id -> identities.none { it.id == id } }
    val options = listOf(noneLabel) + identities.map { it.name } + listOfNotNull(gone)
    val icons = listOf<OptionIcon?>(null) + identities.map { OptionIcon(it.iconName, IconColor.of(it.iconColor)) } + (if (gone != null) listOf(null) else emptyList())
    val index = when (selected) {
        null -> 0
        gone -> options.lastIndex
        else -> 1 + identities.indexOfFirst { it.id == selected }
    }
    UI.SectionedSelection(
        label = label,
        options = options,
        sections = options.map { null },
        selected = index,
        onSelect = { chosen ->
            when {
                chosen == 0 -> onSelect(null)
                chosen <= identities.size -> onSelect(identities[chosen - 1].id)
                else -> onSelect(gone)
            }
        },
        required = false,
        icons = icons
    )
}

/**
 * The tile at the top of a zone that has an identity (docs/design/incarnations.md, « Où elle
 * intervient »): its icon, its name, and a button opening a new conversation with it -- the gesture
 * that opens one from a zone, with its rules. Nothing of the zone is attached: a pointer adds it.
 */
@Composable
fun IdentityTile(identity: IdentityChoice) {
    UI.Card(type = CardType.DEFAULT) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(UI.Space.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
        ) {
            UI.ItemIcon(identity.iconName, IconColor.of(identity.iconColor))
            Box(modifier = Modifier.weight(1f)) {
                UI.Text(text = identity.name, type = TextType.SUBTITLE)
            }
            UI.ActionButton(
                action = ButtonAction.AI_CHAT,
                display = ButtonDisplay.ICON,
                onClick = { ChatRequests.open(emptyList(), identity.id) }
            )
        }
    }
}

/**
 * The chip of a conversation's composer, right of its enrichments: the identity it speaks as
 * (its icon; the chat's for the app's own AI). Before the first message [fixed] is false and a
 * touch opens the choice among [identities] and the app's AI; after it, a touch says that another
 * conversation changes who speaks.
 */
@Composable
fun IdentityChip(identities: List<IdentityChoice>, selected: String?, fixed: Boolean, onSelect: (String?) -> Unit) {
    val context = LocalContext.current
    val s = remember { app.treelune.core.strings.Strings.`for`(context = context) }
    var choosing by remember { mutableStateOf(false) }
    val identity = identities.firstOrNull { it.id == selected }
    UI.Button(
        type = app.treelune.core.ui.ButtonType.DEFAULT,
        size = app.treelune.core.ui.Size.M,
        onClick = {
            if (fixed) UI.Toast(context, s.shared("identity_session_fixed"), app.treelune.core.ui.Duration.LONG)
            else choosing = true
        }
    ) {
        if (identity != null) UI.ItemIcon(identity.iconName, IconColor.of(identity.iconColor))
        else UI.Icon(ButtonAction.AI_CHAT.iconName, contentDescription = s.shared("identity_app_ai"))
    }
    if (choosing) {
        var chosen by remember { mutableStateOf(selected) }
        UI.Dialog(
            type = app.treelune.core.ui.DialogType.CONFIRM,
            onConfirm = { choosing = false; if (chosen != selected) onSelect(chosen) },
            onCancel = { choosing = false }
        ) {
            IdentitySelector(identities, chosen, { chosen = it }, s.shared("label_identity"), s.shared("identity_app_ai"))
        }
    }
}
