package app.treelune.tools.flashcards

import android.content.Context
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.SortKey
import app.treelune.core.selection.Edge
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.views.ToolView
import app.treelune.core.views.View
import app.treelune.core.views.ViewPeriod
import org.json.JSONObject

/**
 * A deck's cards as a view (core/views, its name [NAME]): one row per card — a line of the source
 * in a direction it asks, new cards included, those of lines gone from the source left out — what
 * the deck itself counts, read as entries. « 12 cards to review » is a count of [DUE], a gauge
 * « acquired / all » two counts. A card has no date of its own: the view takes no period.
 *
 * Its rows say the deck's own rules (FlashcardsDue): due as the session offers it (its last review
 * falling due before today ends, or failed), acquired from a stability of
 * FlashcardsDue.ACQUIRED_DAYS days, whatever the day's limits. A row is named by its question,
 * the first field of its direction's front, and its direction (« cane · Italian »), which a
 * reference to it (ROW) shows.
 */
object FlashcardsCards : ToolView {

    const val NAME = "cards"

    const val LINE = "line"
    const val DIRECTION = "direction"
    const val GROUPS = "groups"
    const val NEW = "new"
    const val DUE = "due"
    const val ACQUIRED = "acquired"
    const val DUE_AT = "due_at"
    const val STARTED = "started"
    const val LAST_RATING = "last_rating"
    const val REVIEWS = "reviews"

    override val name = NAME

    /** The soonest to come back first, a new card last. */
    override val defaultSort = listOf(SortKey("data.$DUE_AT", ascending = true))

    override suspend fun open(context: Context, toolInstanceId: String, config: JSONObject, s: StringsContext): View {
        val deck = deckOf(config, s)
        return object : View {
            override val fields = fields(deck, Strings.`for`(tool = "flashcards", context = context))
            override val period = ViewPeriod.NONE
            override suspend fun rows(start: Long?, end: Long?, at: Long): List<ToolDataEntity> = rows(context, toolInstanceId, deck, at)
        }
    }

    override suspend fun fields(context: Context, config: JSONObject, s: StringsContext): Map<String, FieldDefinition> =
        fields(deckOf(config, s), Strings.`for`(tool = "flashcards", context = context))

    /** The deck [config] makes. @throws IllegalStateException when it does not read */
    private fun deckOf(config: JSONObject, s: StringsContext): FlashcardsDeck = try {
        FlashcardsDeck.of(config) { s.shared(it) }
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException(e.message ?: "")
    }

    /** The fields of a row, by path. */
    private fun fields(deck: FlashcardsDeck, s: StringsContext): Map<String, FieldDefinition> {
        fun field(key: String, type: FieldType, config: Map<String, Any>? = null) =
            "data.$key" to FieldDefinition(key, s.tool("card_$key"), null, type, false, config)
        val ratings = Fsrs.Rating.entries.map { it.name }
        return mapOf(
            field(LINE, FieldType.REFERENCE, mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.ENTRY.name),
                ReferenceTarget.TOOL_INSTANCES to listOf(deck.sourceId)))),
            field(DIRECTION, FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(deck.directions.map { it.key },
                deck.directions.associate { it.key to it.name }))),
            field(GROUPS, FieldType.TEXT, mapOf("length" to "SHORT")),
            field(NEW, FieldType.BOOLEAN),
            field(DUE, FieldType.BOOLEAN),
            field(ACQUIRED, FieldType.BOOLEAN),
            field(DUE_AT, FieldType.DATETIME),
            field(STARTED, FieldType.DATETIME),
            field(LAST_RATING, FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(ratings, ratings.associateWith { s.tool("rating_${it.lowercase()}") }))),
            field(REVIEWS, FieldType.NUMERIC, mapOf("decimals" to 0))
        )
    }

    /**
     * The deck's cards at [at]: each line of its source in each direction it asks, with what its
     * journal says of it.
     *
     * @throws IllegalStateException when the source or the journal cannot be read
     */
    private suspend fun rows(context: Context, deckId: String, deck: FlashcardsDeck, at: Long): List<ToolDataEntity> {
        val dayEnd = TimeResolver.at(at).instant(TimePoint.Relative(PeriodType.DAY, 1, Edge.START))
        val source = FlashcardsSource.read(context, deck, at)
        val cards = FlashcardsJournal.cards(context, deckId)
        val reviews = FlashcardsJournal.reviewCounts(context, deckId)
        return source.lines.flatMap { line ->
            deck.directions.filter { it.key in line.asks }.map { direction ->
                val key = FlashcardsDue.CardKey(line.id, direction.key)
                val card = cards[key]
                val data = JSONObject()
                    .put(LINE, JSONObject().put("kind", ReferenceKind.ENTRY.name).put("id", line.id))
                    .put(DIRECTION, direction.key)
                    // Its groups as one text, which « contains » filters
                    .put(GROUPS, line.groups.sorted().joinToString(", ").ifEmpty { null } ?: JSONObject.NULL)
                    .put(NEW, card == null)
                    .put(DUE, card != null && (card.lastRating == Fsrs.Rating.AGAIN || card.last.due < dayEnd))
                    .put(ACQUIRED, card != null && card.last.stability >= FlashcardsDue.ACQUIRED_DAYS)
                    .put(DUE_AT, card?.last?.due ?: JSONObject.NULL)
                    .put(STARTED, card?.first ?: JSONObject.NULL)
                    .put(LAST_RATING, card?.lastRating?.name ?: JSONObject.NULL)
                    .put(REVIEWS, reviews[key] ?: 0)
                ToolDataEntity(
                    id = "${line.id}@${direction.key}",
                    toolInstanceId = deckId,
                    tooltype = "flashcards",
                    timestamp = card?.last?.reviewedAt,
                    // Named by its question and its direction, as a reference shows it
                    name = listOfNotNull(source.values(line.id, direction.front.take(1)).firstOrNull()?.second?.toString(), direction.name).joinToString(" · "),
                    data = data.toString(),
                    createdAt = card?.first ?: 0L,
                    updatedAt = card?.last?.reviewedAt ?: 0L,
                    extra = null,
                    state = null
                )
            }
        }
    }
}
