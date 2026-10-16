package app.treelune.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.SelectionAction
import app.treelune.core.ui.sound.UISignal
import app.treelune.core.ui.sound.rememberUISound
import kotlin.math.roundToInt

/**
 * [content] whose selected texts show the theme's bar (ThemeContract.SelectionBar) in place of
 * Android's: copy, paste, cut, select all, and the actions other apps add (translate…). Compose
 * asks the bar of the nearest provider, so the text components (UI.Selectable, UI.FormField) each
 * set one round themselves, which keeps the bar in the window of the text, a dialog's included.
 *
 * The core decides what the bar holds, where it stands and when; the theme only its look. The
 * actions Compose names are said in the app's language (Android names them in the phone's); those
 * other apps add keep their label. What Android proposes for the text itself (call a number, open a
 * link) Compose keeps to its own bar: it is not offered here.
 */
@Composable
fun SelectionMenu(content: @Composable () -> Unit) {
    val menu = remember { SelectionMenuState() }
    // Where the text's surroundings stand, read when the bar is placed: kept out of the state, a
    // layout pass never recomposes for it
    val anchor = remember { AnchorHolder() }
    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides menu) {
        Box(modifier = Modifier.onGloballyPositioned { anchor.coordinates = it }) {
            content()
            val shown = menu.shown
            val coordinates = anchor.coordinates
            if (shown != null && coordinates != null) SelectionBarPopup(shown.session, shown.data) { anchor.coordinates ?: coordinates }
        }
    }
}

private class AnchorHolder { var coordinates: LayoutCoordinates? = null }

/** A bar asked for by Compose: [data] says what it holds, [session] closes it. */
private class ShownMenu(val data: TextContextMenuDataProvider, val session: TextContextMenuSession)

/**
 * Compose's side of the bar: it asks for it by a call that lasts as long as the bar shows, and
 * cancels the call to take it away; the bar closes it too, once an action has run.
 */
private class SelectionMenuState : TextContextMenuProvider {
    var shown by mutableStateOf<ShownMenu?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val closed = CompletableDeferred<Unit>()
        val menu = ShownMenu(dataProvider, object : TextContextMenuSession {
            override fun close() { closed.complete(Unit) }
        })
        shown = menu
        try {
            closed.await()
        } finally {
            if (shown === menu) shown = null
        }
    }
}

@Composable
private fun SelectionBarPopup(
    session: TextContextMenuSession,
    dataProvider: TextContextMenuDataProvider,
    anchor: () -> LayoutCoordinates
) {
    val s = Strings.`for`(context = LocalContext.current)
    val sound = rememberUISound()
    val density = LocalDensity.current
    val actions = dataProvider.data().components.mapNotNull { component ->
        when (component) {
            is TextContextMenuItem -> SelectionAction(ownLabel(component.key, s) ?: component.label) {
                sound(UISignal.CONFIRM)
                component.onClick(session)
            }
            // A separator: the bar's actions are apart already; and what Android proposes for the
            // text itself, which Compose keeps to its own bar
            else -> null
        }
    }
    if (actions.isEmpty()) return
    val position = with(density) {
        AboveSelection(anchor, dataProvider, above = 8.dp.roundToPx(), below = 40.dp.roundToPx())
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = { session.close() },
        // Never the focus: a text field keeps its cursor and its keyboard
        properties = PopupProperties(focusable = false)
    ) {
        CurrentTheme.current.SelectionBar(actions)
    }
}

/** The app's word for an action Compose names, or null for one it does not. */
private fun ownLabel(key: Any, s: app.treelune.core.strings.StringsContext): String? = when (key) {
    TextContextMenuKeys.CutKey -> s.shared("text_selection_cut")
    TextContextMenuKeys.CopyKey -> s.shared("text_selection_copy")
    TextContextMenuKeys.PasteKey -> s.shared("text_selection_paste")
    TextContextMenuKeys.SelectAllKey -> s.shared("text_selection_select_all")
    TextContextMenuKeys.AutofillKey -> s.shared("text_selection_autofill")
    else -> null
}

/**
 * The bar over the selection, centred on it and kept inside the window; under it, past the
 * selection's handles, when there is no room above.
 */
private class AboveSelection(
    private val anchor: () -> LayoutCoordinates,
    private val data: TextContextMenuDataProvider,
    private val above: Int,
    private val below: Int
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val selection = data.contentBounds(anchor()).translate(anchorBounds.left.toFloat(), anchorBounds.top.toFloat())
        val widest = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (selection.center.x - popupContentSize.width / 2f).roundToInt().coerceIn(0, widest)
        val over = selection.top.roundToInt() - above - popupContentSize.height
        val y = if (over >= 0) over
            else (selection.bottom.roundToInt() + below).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
