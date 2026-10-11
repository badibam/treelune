package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.treelune.core.scheduling.BackgroundRestriction
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * At startup, over the home screen: Android may hold the app back in the background and the user
 * has not been told since it began (BackgroundRestriction). After the Guide's welcome and a
 * tutorial's introduction, so that the app shows itself before it asks. Allowing or leaving both
 * count as told.
 */
@Composable
fun BackgroundRestrictionDialog() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var shown by remember { mutableStateOf(BackgroundRestriction.warningDue(context)) }
    if (!shown) return
    val guide = app.treelune.core.guide.Guide
    if (guide.progress?.welcomeSeen != true || guide.introducing != null) return
    UI.ConfirmDialog(
        title = s.shared("background_restricted_title"),
        message = s.shared("background_restricted_text"),
        confirmText = s.shared("background_restricted_open"),
        cancelText = s.shared("background_restricted_later"),
        onConfirm = {
            BackgroundRestriction.markWarned(context)
            shown = false
            BackgroundRestriction.requestUnrestricted(context)
        },
        onDismiss = {
            BackgroundRestriction.markWarned(context)
            shown = false
        }
    )
}

/**
 * On the settings screen, while Android may hold the app back in the background: what it may stop
 * and the button that asks for « Unrestricted ». Read again each time the app comes back, from
 * Android's question among others.
 */
@Composable
fun BackgroundRestrictionCard() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var heldBack by remember { mutableStateOf(BackgroundRestriction.isHeldBack(context)) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) heldBack = BackgroundRestriction.isHeldBack(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!heldBack) return
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(s.shared("background_restricted_title"), TextType.SUBTITLE)
            UI.Text(s.shared("background_restricted_text"), TextType.BODY)
            UI.Button(type = ButtonType.SECONDARY, onClick = { BackgroundRestriction.requestUnrestricted(context) }) {
                UI.Text(s.shared("background_restricted_open"), TextType.LABEL)
            }
        }
    }
}
