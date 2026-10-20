package app.treelune.core.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.treelune.core.themes.CurrentTheme
import kotlinx.coroutines.delay

/**
 * What the app says after a gesture, in a band of the theme at the bottom of the screen (UI.Toast):
 * one queue, the oldest message shown first, whole, however long, for a time that grows with it,
 * and gone at a touch. Android's toast is not used: it cuts a message at two lines.
 *
 * The page and each window open above it (a dialog, the chat) hold a [ToastLayer]; only the one of
 * the window on top shows the message, over that window, so that a dialog never hides it.
 */
object Toasts {

    data class Shown(val id: Long, val message: String, val millis: Long)

    private val queue = mutableStateListOf<Shown>()
    private val layers = mutableStateListOf<Long>()
    private var nextMessage = 0L
    private var nextLayer = 0L

    /** [message] queued, from any thread. */
    fun show(message: String, duration: Duration) {
        Handler(Looper.getMainLooper()).post { queue.add(Shown(nextMessage++, message, millis(message, duration))) }
    }

    /**
     * [message] said once the app has started again, for a gesture that restarts it (a backup
     * restored, the data reset): kept in a file of the app until then.
     */
    fun showAfterRestart(context: android.content.Context, message: String) {
        java.io.File(context.filesDir, AFTER_RESTART).writeText(message)
    }

    /** The message left by [showAfterRestart], shown and forgotten; called as the app starts. */
    fun showLeftBeforeRestart(context: android.content.Context) {
        val file = java.io.File(context.filesDir, AFTER_RESTART)
        if (!file.exists()) return
        show(file.readText(), Duration.LONG)
        file.delete()
    }

    /** How long [message] stays: the time asked, longer for a long one, never past [MAX_MS]. */
    internal fun millis(message: String, duration: Duration): Long {
        val base = if (duration == Duration.SHORT) SHORT_MS else LONG_MS
        return (base + (message.length - READ_AT_A_GLANCE).coerceAtLeast(0) * MS_PER_CHARACTER).coerceAtMost(MAX_MS)
    }

    internal val current: Shown? get() = queue.firstOrNull()

    internal fun dismiss(id: Long) {
        queue.removeAll { it.id == id }
    }

    internal fun newLayer(): Long = nextLayer++

    internal fun open(layer: Long) {
        layers.add(layer)
    }

    internal fun close(layer: Long) {
        layers.remove(layer)
    }

    internal fun onTop(layer: Long) = layers.lastOrNull() == layer

    private const val SHORT_MS = 2500L
    private const val LONG_MS = 4000L
    private const val READ_AT_A_GLANCE = 60
    private const val MS_PER_CHARACTER = 50L
    private const val MAX_MS = 10_000L
    private const val AFTER_RESTART = "toast_after_restart.txt"
}

/**
 * The messages' place in a window: the page's, or a dialog's. Shows the current message when its
 * window is the one on top, drawn by the theme (ThemeContract.Snackbar) in a window of its own
 * above it; a touch on it takes it away.
 */
@Composable
fun ToastLayer() {
    val layer = remember { Toasts.newLayer() }
    DisposableEffect(layer) {
        Toasts.open(layer)
        onDispose { Toasts.close(layer) }
    }
    if (!Toasts.onTop(layer)) return
    val shown = Toasts.current ?: return
    LaunchedEffect(shown.id) {
        delay(shown.millis)
        Toasts.dismiss(shown.id)
    }
    Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(UI.Space.L)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Toasts.dismiss(shown.id) }
        ) {
            CurrentTheme.current.Snackbar(FeedbackType.INFO, shown.message, null, null)
        }
    }
}
