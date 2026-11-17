package app.treelune.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * The « back to top » button (docs/UI.md, « Back to top »): at the bottom left of the places one
 * reads, facing the chat's button at the bottom right, shown once the top of the page is more than
 * a screen away, and taking the page back there. The page's scroll belongs to its screen, which
 * hands it over ([pageScroll], [rememberPageListState]); the button lives beside the chat's
 * (MainScreen), on the scroll of the screen shown.
 */
object ScrollTop {

    /** The scroll of the screen shown: whether its top is more than a screen away, and the way back there. */
    internal class Followed(val far: () -> Boolean, val toTop: suspend () -> Unit)

    internal var followed by mutableStateOf<Followed?>(null)

    /** Whether the button shows: the page shown scrolled more than a screen down. */
    val shown: Boolean get() = followed?.far?.invoke() == true

    /** The page shown taken back to its top, scrolling. */
    suspend fun toTop() { followed?.toTop?.invoke() }

    /** [followed] handed over while this is composed, taken back when it leaves unless another took its place. */
    @Composable
    internal fun Follow(key: Any, make: () -> Followed) {
        DisposableEffect(key) {
            val mine = make()
            followed = mine
            onDispose { if (followed === mine) followed = null }
        }
    }
}

/**
 * The vertical scroll of a screen of a place one reads: it scrolls, leaves the chat's button room
 * at its end (chatButtonSpace), and hands its scroll to the « back to top » button.
 *
 * @param state The scroll, when the screen reads it too (the Guide); its own otherwise
 */
fun Modifier.pageScroll(state: ScrollState? = null): Modifier = composed {
    val scroll = state ?: rememberScrollState()
    ScrollTop.Follow(scroll) {
        // A screen down: the visible height, once laid out
        ScrollTop.Followed(far = { scroll.viewportSize > 0 && scroll.value > scroll.viewportSize }, toTop = { scroll.animateScrollTo(0) })
    }
    verticalScroll(scroll).chatButtonSpace()
}

/**
 * The state of a lazy list that is the page of a place one reads, handed to the « back to top »
 * button; the list leaves the chat's button room by its content padding (chatButtonEnd).
 */
@Composable
fun rememberPageListState(): LazyListState {
    val state = rememberLazyListState()
    ScrollTop.Follow(state) {
        ScrollTop.Followed(far = {
            // How far down the top is: the items above the first shown, at the visible ones' mean height
            val info = state.layoutInfo
            val viewport = info.viewportSize.height
            val shown = info.visibleItemsInfo
            if (viewport <= 0 || shown.isEmpty()) false
            else state.firstVisibleItemIndex * (shown.sumOf { it.size } / shown.size) + state.firstVisibleItemScrollOffset > viewport
        }, toTop = { state.animateScrollToItem(0) })
    }
    return state
}
