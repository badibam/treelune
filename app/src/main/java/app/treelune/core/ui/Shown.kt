package app.treelune.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * What a screen or a tile last showed, kept while the app runs, so that coming back to it shows
 * the same thing at once while it reads again (docs/UI.md, « What a place shows, coming back »).
 *
 * A place left in the stack keeps only its rememberSaveable; what it read into a plain remember
 * is gone, and coming back it would start empty or « loading », a chart computing for a second:
 * the page shorter meanwhile, the scroll position it saved is cut to what fits and lost, and
 * the page jumps when the content arrives. Read from here, the content comes back as it was, of
 * the same height: the position holds, and the new reading replaces the old one in place.
 *
 * Memory only, never stored, the oldest dropped past [CAPACITY]. A value is a reading to show,
 * never a source of truth: whatever reads it reads again behind it.
 */
object Shown {

    /** Enough for the tiles of several zones and the pages above them. */
    private const val CAPACITY = 512

    private val values = object : LinkedHashMap<String, Any?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any?>?): Boolean = size > CAPACITY
    }

    @Synchronized
    internal fun has(key: String): Boolean = values.containsKey(key)

    @Synchronized
    internal fun get(key: String): Any? = values[key]

    @Synchronized
    internal fun put(key: String, value: Any?) {
        values[key] = value
    }
}

/** A state whose every value is kept in [Shown] under [key]. */
private class ShownState<T>(private val key: String, private val state: MutableState<T>) : MutableState<T> by state {
    override var value: T
        get() = state.value
        set(next) {
            Shown.put(key, next)
            state.value = next
        }
}

/**
 * A state that starts from what [key] last showed, [initial] the first time: `var reading by
 * rememberShown("chart:$id") { null }`. [key] names the thing shown, unique in the app (its kind,
 * its id, what part of it).
 */
@Composable
fun <T> rememberShown(key: String, initial: () -> T): MutableState<T> = remember(key) {
    @Suppress("UNCHECKED_CAST")
    val start = if (Shown.has(key)) Shown.get(key) as T else initial()
    ShownState(key, mutableStateOf(start))
}
