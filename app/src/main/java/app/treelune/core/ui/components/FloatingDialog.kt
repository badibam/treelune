package app.treelune.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * A window floating over the screen, its content centred in what the bars and the keyboard
 * leave, [margin] from the sides: a theme's dialog frame goes in it.
 *
 * The window spans the screen and keeps its content clear of the keyboard itself, Android being
 * told to do nothing of it. Left to Android, the window slid up to show the line being typed, its
 * buttons under the keys, and depending on when the keyboard finished opening the line could stay
 * under them; here the content shrinks above the keyboard, and the field's scrolling keeps the
 * cursor in sight. A touch around the content dismisses, as outside a dialog.
 */
@Composable
fun FloatingDialog(onDismissRequest: () -> Unit, margin: Dp, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The app's messages over this window while it is the one on top (Toasts)
        app.treelune.core.ui.ToastLayer()
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect { window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest)
                .systemBarsPadding()
                .imePadding()
                .padding(margin),
            contentAlignment = Alignment.Center
        ) {
            // The content takes the touches that land on it, so that they do not dismiss
            Box(modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})) {
                content()
            }
        }
    }
}
