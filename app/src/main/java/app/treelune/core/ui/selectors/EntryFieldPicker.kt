package app.treelune.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * One field of one entry, reached through the app's one way to things (ThingBrowser) down to its
 * last notch, among the fields [accepts] takes; chosen once a field is reached, as an entry
 * designated down to it (Reference with its field). What it is for — a value copied once, a field
 * followed — is the caller's, and [title] says it.
 */
@Composable
fun EntryFieldPicker(title: String, accepts: (FieldDefinition) -> Boolean, onDismiss: () -> Unit, onPick: (Reference) -> Unit) {
    var path by rememberSaveable(stateSaver = PathSaver) { mutableStateOf(ThingPath()) }
    val target = ReferenceTarget(setOf(ReferenceKind.ENTRY), emptyList())
    UI.Dialog(
        type = DialogType.CONFIRM,
        confirmEnabled = path.field != null,
        onConfirm = { onPick(path.reference) },
        onCancel = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(text = title, type = TextType.TITLE, fillMaxWidth = true)
            ThingBrowser(path, { path = it }, target, fields = accepts)
        }
    }
}

/** The place reached, across a rotation. */
private val PathSaver: Saver<ThingPath, String> = Saver(save = { it.toJson() }, restore = { ThingPath.fromJson(it) })
