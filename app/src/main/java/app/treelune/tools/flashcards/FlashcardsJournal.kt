package app.treelune.tools.flashcards

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import org.json.JSONObject

/**
 * A deck's journal read in SQL (docs/design/flashcards.md, « Les entrées : une par révision »): the
 * state of each card is that of its last review, and its first review says when it was started.
 * No table of cards and no count stored: nothing can drift from the journal.
 */
object FlashcardsJournal {

    /** What a review holds. */
    const val SOURCE_ENTRY = "source_entry"
    const val DIRECTION = "direction"
    const val RATING = "rating"
    const val STABILITY = "stability"
    const val DIFFICULTY = "difficulty"
    const val DUE = "due"
    const val FSRS_VERSION = "fsrs_version"

    /**
     * Each card's review that comes first in [order] (DESC: its last; ASC: its first), one entry a
     * card; ties of instant broken by when they were written.
     */
    private fun ends(deckId: String, order: String) = SimpleSQLiteQuery(
        """
        SELECT * FROM tool_data WHERE id IN (
            SELECT id FROM (
                SELECT id, ROW_NUMBER() OVER (
                    PARTITION BY json_extract(data, '$.$SOURCE_ENTRY.id'), json_extract(data, '$.$DIRECTION')
                    ORDER BY timestamp $order, created_at $order
                ) AS n FROM tool_data WHERE tool_instance_id = ?
            ) WHERE n = 1
        )
        """.trimIndent(),
        arrayOf(deckId)
    )

    /**
     * Every card of the deck [deckId] that has a review, those of lines gone from the source
     * included: when it was started, its state now and its last rating.
     *
     * @throws IllegalStateException on a review that does not read, a corrupt journal
     */
    suspend fun cards(context: Context, deckId: String): Map<FlashcardsDue.CardKey, FlashcardsDue.Card> {
        val dao = AppDatabase.getDatabase(context).toolDataDao()
        val firsts = dao.getFiltered(ends(deckId, "ASC")).associate { key(it) to (it.timestamp ?: error("a review ${it.id} without its instant")) }
        return dao.getFiltered(ends(deckId, "DESC")).associate { last ->
            val key = key(last)
            key to FlashcardsDue.Card(firsts[key] ?: error("a card without a first review"), state(last), rating(last))
        }
    }

    /** The last review of one card, null for a new card. */
    suspend fun last(context: Context, deckId: String, key: FlashcardsDue.CardKey): ToolDataEntity? =
        AppDatabase.getDatabase(context).toolDataDao().getFiltered(SimpleSQLiteQuery(
            """
            SELECT * FROM tool_data WHERE tool_instance_id = ?
                AND json_extract(data, '$.$SOURCE_ENTRY.id') = ? AND json_extract(data, '$.$DIRECTION') = ?
            ORDER BY timestamp DESC, created_at DESC LIMIT 1
            """.trimIndent(),
            arrayOf(deckId, key.line, key.direction)
        )).firstOrNull()

    fun key(review: ToolDataEntity): FlashcardsDue.CardKey {
        val data = JSONObject(review.data)
        val line = data.optJSONObject(SOURCE_ENTRY)?.optString("id")?.takeIf { it.isNotEmpty() } ?: error("a review ${review.id} without its line")
        return FlashcardsDue.CardKey(line, data.optString(DIRECTION).takeIf { it.isNotEmpty() } ?: error("a review ${review.id} without its direction"))
    }

    fun rating(review: ToolDataEntity): Fsrs.Rating = Fsrs.Rating.valueOf(JSONObject(review.data).getString(RATING))

    /** The card's state a review left. */
    fun state(review: ToolDataEntity): Fsrs.State {
        val state = JSONObject(review.state ?: error("a review ${review.id} without its state"))
        return Fsrs.State(state.getDouble(STABILITY), state.getDouble(DIFFICULTY), review.timestamp ?: error("a review ${review.id} without its instant"), state.getLong(DUE))
    }
}
