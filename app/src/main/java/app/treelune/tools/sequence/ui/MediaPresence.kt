package app.treelune.tools.sequence.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.database.AppDatabase
import app.treelune.core.fields.FileField
import app.treelune.core.files.LinkedFiles
import app.treelune.core.files.isLinked
import app.treelune.core.reading.FieldReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** How many links are tested before « What to play? » shows its list: about a screen of it. */
private const val FIRST_TESTS = 12

/** The longest the list waits for them, a slow folder never holding it back. */
private const val LONGEST_WAIT_MS = 300L

/**
 * Which entries of « What to play? » have their medium missing from the phone
 * (docs/design/linked-files.md, « Le lien attendu »).
 *
 * @property absent The ids of the entries known to be missing theirs; an entry not tested yet is not in it
 * @property ready Whether the list can show: the links around its opening tested, or the wait over
 */
internal class MediaPresence {
    var absent by mutableStateOf(emptySet<String>())
    var ready by mutableStateOf(false)
}

/** Of [indexes], the one nearest to [around], the lower of two as near; null when there is none. */
internal fun nearestTo(indexes: Collection<Int>, around: Int): Int? =
    indexes.minWithOrNull(compareBy({ abs(it - around) }, { it }))

/**
 * Tests the links of [entries], whose medium is in the field [field]: an expected link is missing
 * at once, a link with an address is opened, one at a time, the nearest to what [list] shows first.
 * Nothing is kept once the window closes, which stops the tests.
 */
@Composable
internal fun rememberMediaPresence(entries: List<Map<String, Any?>>, field: String, list: LazyListState): MediaPresence {
    val context = LocalContext.current
    val presence = remember(entries) { MediaPresence() }
    LaunchedEffect(entries) {
        val wait = launch {
            delay(LONGEST_WAIT_MS)
            presence.ready = true
        }
        withContext(Dispatchers.IO) {
            val media = entries.mapIndexedNotNull { index, entry ->
                FileField.ids(FieldReading.valueAt(entry, field)).firstOrNull()?.let { Triple(index, entry["id"] as String, it) }
            }
            val rows = AppDatabase.getDatabase(context).fileDao().getByIds(media.map { it.third }).associateBy { it.id }
            val links = media.filter { rows[it.third]?.isLinked == true }
            presence.absent = links.filter { rows.getValue(it.third).uri == null }.map { it.second }.toSet()
            val untested = links.filter { rows.getValue(it.third).uri != null }.associateBy { it.first }.toMutableMap()
            var tested = 0
            while (untested.isNotEmpty()) {
                coroutineContext.ensureActive()
                // The middle of a screen of the list, from the first line it shows
                val index = nearestTo(untested.keys, list.firstVisibleItemIndex + FIRST_TESTS / 2)!!
                val (_, entryId, fileId) = untested.remove(index)!!
                if (!LinkedFiles.reads(context, rows.getValue(fileId))) presence.absent = presence.absent + entryId
                if (++tested == FIRST_TESTS) presence.ready = true
            }
        }
        wait.cancel()
        presence.ready = true
    }
    return presence
}
