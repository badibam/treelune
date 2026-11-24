package app.treelune.core.tools

import androidx.compose.runtime.saveable.Saver

/**
 * Where a tool's screen opens at once, as its tool type understands opening there: one of its
 * entries (touched on its tile, or named by a notification: an event's reminder, a Session
 * running), a new one (a tile's button that writes one: a journal entry, a note, a record), or a
 * row of one of its views (core/views: a program of a Session, which a Path's brick launches).
 * Nothing to open is no OpenAt at all. A tool type that has no meaning for a kind of place opens
 * its screen as usual.
 */
sealed interface OpenAt {
    data class Entry(val id: String) : OpenAt
    data object New : OpenAt
    data class Row(val view: String, val row: String) : OpenAt

    companion object {
        /** Kept across recreation as "new", "entry:" and the id, or "row:", the view, ":" and the row. */
        val Saver: Saver<OpenAt?, String> = Saver(
            save = {
                when (it) {
                    New -> "new"
                    is Entry -> "entry:${it.id}"
                    is Row -> "row:${it.view}:${it.row}"
                    null -> null
                }
            },
            restore = { saved ->
                when {
                    saved == "new" -> New
                    saved.startsWith("row:") -> saved.removePrefix("row:").split(":", limit = 2).let { Row(it[0], it.getOrElse(1) { "" }) }
                    else -> Entry(saved.removePrefix("entry:"))
                }
            }
        )
    }
}
