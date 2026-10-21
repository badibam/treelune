package app.treelune.tools.flashcards

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.formatValue
import app.treelune.core.selection.Edge
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.selection.PeriodType
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import org.json.JSONObject

/**
 * A deck's operations, for the screen and the AI alike (docs/TOOLS.md, « Flashcards »):
 * - due: `group`, `direction`, both optional — the cards to offer now (FlashcardsDue), each with
 *   its faces, the values of its line's fields
 * - counts: the due and new cards of the whole deck and of each group (its value and its label),
 *   in every direction and in each, what the page and its launch window show
 * - review: `source_entry`, `direction`, `rating` — a card rated: its state computed by FSRS and
 *   written as a review, the only way one is written
 *
 * Reading the reviews is the ordinary tool_data.get; undoing one, the ordinary delete of a card's
 * last review (FlashcardsToolType.refuseDelete).
 */
class FlashcardsService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "flashcards", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val deckId = params.optString("tool_instance_id").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        val config = config(deckId) ?: return OperationResult.error(s.shared("service_error_tool_instance_not_found").format(deckId))
        return try {
            val deck = FlashcardsDeck.of(config) { s.shared(it) }
            when (operation) {
                "due" -> due(deckId, deck, params)
                "counts" -> counts(deckId, deck)
                "review" -> review(deckId, deck, params)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: IllegalStateException) {
            // A source or a field gone, named by the error
            OperationResult.error(e.message ?: s.shared("service_error_operation_failed"))
        }
    }

    private suspend fun config(deckId: String): JSONObject? {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to deckId))
        val tool = result.data?.get("tool_instance") as? Map<*, *> ?: return null
        if (tool["tooltype"] != "flashcards") return null
        @Suppress("UNCHECKED_CAST")
        return (tool["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
    }

    private suspend fun due(deckId: String, deck: FlashcardsDeck, params: JSONObject): OperationResult {
        val group = params.optString("group").takeIf { it.isNotEmpty() }
        val direction = params.optString("direction").takeIf { it.isNotEmpty() }
        if (direction != null && deck.direction(direction) == null) return OperationResult.error(s.tool("error_direction_unknown").format(direction))
        val now = System.currentTimeMillis()
        val read = read(context, deckId, deck, now)
        val offer = read.offer(group, direction)
        return OperationResult.success(mapOf(
            "due" to offer.due,
            "new" to offer.new,
            "cards" to offer.cards.map { card ->
                val d = deck.direction(card.key.direction)!!
                mapOf(
                    "source_entry" to card.key.line,
                    "direction" to card.key.direction,
                    "kind" to card.kind.name.lowercase(),
                    "front" to read.source.values(card.key.line, d.front).map { (path, value) -> mapOf("field" to path, "value" to value) },
                    "back" to read.source.values(card.key.line, d.back).map { (path, value) -> mapOf("field" to path, "value" to value) }
                )
            }
        ))
    }

    private suspend fun counts(deckId: String, deck: FlashcardsDeck): OperationResult {
        val read = read(context, deckId, deck, System.currentTimeMillis())
        fun of(group: String?): Map<String, Any?> {
            val all = read.offer(group, null)
            return mapOf(
                "group" to group,
                // A group's value as its field shows it: a choice's label, not its stored value
                "label" to group?.let { value -> deck.groupBy?.let { read.source.fields.getValue(it).formatValue(value, context) } },
                "due" to all.due,
                "new" to all.new,
                "directions" to deck.directions.map { d ->
                    val one = read.offer(group, d.key)
                    mapOf("direction" to d.key, "name" to d.name, "due" to one.due, "new" to one.new)
                }
            )
        }
        return OperationResult.success(mapOf(
            "all" to of(null),
            "groups" to read.source.groups.map { of(it) }
        ))
    }

    private suspend fun review(deckId: String, deck: FlashcardsDeck, params: JSONObject): OperationResult {
        val line = params.optString("source_entry").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("source_entry"))
        val direction = params.optString("direction").takeIf { it.isNotEmpty() }
            ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("direction"))
        val rating = Fsrs.Rating.entries.firstOrNull { it.name == params.optString("rating") }
            ?: return OperationResult.error(s.tool("error_rating").format(params.optString("rating")))
        val d = deck.direction(direction) ?: return OperationResult.error(s.tool("error_direction_unknown").format(direction))
        val now = System.currentTimeMillis()
        val source = FlashcardsSource.read(context, deck, now)
        val read = source.lines.firstOrNull { it.id == line } ?: return OperationResult.error(s.tool("error_not_a_line").format(line, source.name))
        if (d.key !in read.asks) return OperationResult.error(s.tool("error_no_question").format(line, d.name))

        val key = FlashcardsDue.CardKey(line, direction)
        val previous = FlashcardsJournal.last(context, deckId, key)?.let { FlashcardsJournal.state(it) }
        val state = Fsrs.review(previous, rating, now, deck.desiredRetention)
        val result = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to deckId,
            "timestamp" to now,
            "data" to mapOf(
                FlashcardsJournal.SOURCE_ENTRY to mapOf("kind" to "ENTRY", "id" to line),
                FlashcardsJournal.DIRECTION to direction,
                FlashcardsJournal.RATING to rating.name
            ),
            "state" to mapOf(
                FlashcardsJournal.STABILITY to state.stability,
                FlashcardsJournal.DIFFICULTY to state.difficulty,
                FlashcardsJournal.DUE to state.due,
                FlashcardsJournal.FSRS_VERSION to Fsrs.VERSION
            )
        ))
        if (!result.isSuccess) return OperationResult.error(result.error ?: "")
        return OperationResult.success(mapOf(
            "id" to (result.data?.get("id") as String),
            "stability" to state.stability,
            "difficulty" to state.difficulty,
            "due" to state.due
        ))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "flashcards", context = context)
        return s.tool("verbalize_$operation").takeIf { operation in setOf("due", "counts", "review") } ?: s.shared("action_verbalize_unknown")
    }

    /** A deck read at one instant: its source, its journal and today's bounds, offering any group and direction. */
    class Read(val deck: FlashcardsDeck, val source: FlashcardsSource, private val cards: Map<FlashcardsDue.CardKey, FlashcardsDue.Card>, private val dayStart: Long, private val dayEnd: Long) {
        fun offer(group: String?, direction: String?): FlashcardsDue.Offer =
            FlashcardsDue.offer(deck, source.lines, cards, dayStart, dayEnd, group, direction)
    }

    companion object {
        /**
         * The deck [deckId] read at [now], today in the app's calendar (its zone and the hour its
         * days start at). The time it takes is logged: it is measured on the phone before being kept.
         *
         * @throws IllegalStateException when the source or the journal cannot be read
         */
        suspend fun read(context: Context, deckId: String, deck: FlashcardsDeck, now: Long): Read {
            val started = System.nanoTime()
            val resolver = TimeResolver.at(now)
            val dayStart = resolver.instant(TimePoint.Relative(PeriodType.DAY, 0, Edge.START))
            val dayEnd = resolver.instant(TimePoint.Relative(PeriodType.DAY, 1, Edge.START))
            val read = Read(deck, FlashcardsSource.read(context, deck, now), FlashcardsJournal.cards(context, deckId), dayStart, dayEnd)
            LogManager.service("Flashcards: deck $deckId read in ${(System.nanoTime() - started) / 1_000_000} ms (${read.source.lines.size} lines)")
            return read
        }
    }
}
