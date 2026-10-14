package app.treelune.core.ui

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Whether the keyboard is open over the app's window. The window is resized above it
 * (adjustResize, AndroidManifest): a page scrolls down to its end over the keyboard, and what
 * stands fixed at the bottom of the screen (the Guide's band, the chat's button) rises with
 * it, which is why it hides while this is true. Read from the window's insets at each layout,
 * the keyboard's opening or closing resizing the window.
 */
@Composable
fun keyboardOpen(): Boolean {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val read = ViewTreeObserver.OnGlobalLayoutListener {
            open = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(read)
        read.onGlobalLayout()
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(read) }
    }
    return open
}
