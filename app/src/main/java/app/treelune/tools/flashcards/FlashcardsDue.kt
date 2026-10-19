package app.treelune.tools.flashcards

/**
 * The one calculation of a deck's cards to offer (docs/design/flashcards.md, « Le calcul des cartes
 * à proposer »), whence the tile, the page, the launch window and the session all come: at an
 * instant, from the source's lines, the last review of each card and the deck's config.
 *
 * - Due: a card whose last review falls due before today ends, or was failed — a failed card not
 *   passed again stays due, the session having brought it back itself.
 * - New: a line without a review in a direction, in the source's order, up to what is left of the
 *   day's new cards.
 * - The day's limits count cards for the whole deck, whatever the group: a card started today
 *   counts against the new ones, a card seen before and reviewed today against the reviews; a card
 *   failed today and offered again takes nothing more.
 * - Sister cards: two directions of one line are never offered the same day, a review today in one
 *   keeping the others away; among those offered together, the due before the new, then the
 *   directions in their order.
 * - One direction after another: a line's next direction enters once the one before it is acquired
 *   for that line, a stability of [ACQUIRED_DAYS] days at least.
 *
 * Pure: the caller reads the lines and the journal, and gives today's bounds.
 */
object FlashcardsDue {

    /** A card is acquired once its memory holds a week: a stability of 7 days at least. */
    const val ACQUIRED_DAYS = 7.0

    /** A card: one line of the source in one direction. */
    data class CardKey(val line: String, val direction: String)

    /**
     * A line of the source as the deck reads it, in the source's order.
     *
     * @property groups The values of its field group_by, a line being in each of them
     * @property asks The directions it makes a card in: those whose first front field it fills
     */
    data class Line(val id: String, val groups: Set<String>, val asks: Set<String>)

    /**
     * What the journal says of a card.
     *
     * @property first The instant of its first review
     * @property last Its state after its last review
     * @property lastRating The rating of its last review
     */
    data class Card(val first: Long, val last: Fsrs.State, val lastRating: Fsrs.Rating)

    enum class Kind { DUE, NEW }

    data class Offered(val key: CardKey, val kind: Kind)

    /**
     * The cards offered at once, in the order a session asks them: the due, the soonest first,
     * then the new in the source's order.
     */
    data class Offer(val cards: List<Offered>) {
        val due: Int get() = cards.count { it.kind == Kind.DUE }
        val new: Int get() = cards.count { it.kind == Kind.NEW }
    }

    /**
     * The cards to offer in [group] (every line when null) and [direction] (every direction when
     * null), today being [dayStart] (included) to [dayEnd] (excluded).
     *
     * @param lines The source's lines, filtered by the deck, in the source's order
     * @param cards The cards of the journal by key, those of lines gone from the source included
     */
    fun offer(
        deck: FlashcardsDeck,
        lines: List<Line>,
        cards: Map<CardKey, Card>,
        dayStart: Long,
        dayEnd: Long,
        group: String? = null,
        direction: String? = null
    ): Offer {
        val present = lines.map { it.id }.toSet()
        // The day's limits, over the whole deck: only the lines still there count
        val ofToday = cards.filter { (key, card) -> key.line in present && card.last.reviewedAt >= dayStart }
        val newLeft = (deck.newPerDay - ofToday.count { it.value.first >= dayStart }).coerceAtLeast(0)
        var reviewsLeft = (deck.reviewsPerDay - ofToday.count { it.value.first < dayStart }).coerceAtLeast(0)
        // The direction a line was reviewed in today, which keeps its sisters away
        val touched = ofToday.keys.associate { it.line to it.direction }

        val keys = deck.directions.map { it.key }.filter { direction == null || it == direction }
        val inScope = lines.filter { group == null || group in it.groups }

        val due = mutableListOf<Pair<Offered, Long>>()
        val new = mutableListOf<Offered>()
        for (line in inScope) {
            for (key in keys) {
                if (key !in line.asks) continue
                val cardKey = CardKey(line.id, key)
                val card = cards[cardKey]
                when {
                    card == null -> if (enters(deck, line, key, cards)) new += Offered(cardKey, Kind.NEW)
                    card.lastRating == Fsrs.Rating.AGAIN || card.last.due < dayEnd -> due += Offered(cardKey, Kind.DUE) to card.last.due
                }
            }
        }

        // The due first, the soonest first; one direction a day per line
        val chosen = mutableMapOf<String, String>().apply { putAll(touched) }
        val offered = mutableListOf<Offered>()
        for ((card, _) in due.sortedBy { it.second }) {
            if (chosen[card.key.line]?.let { it != card.key.direction } == true) continue
            // A card reviewed today and offered again (failed) has taken its place in the limit already
            val againToday = ofToday.containsKey(card.key)
            if (!againToday) {
                if (reviewsLeft == 0) continue
                reviewsLeft--
            }
            chosen[card.key.line] = card.key.direction
            offered += card
        }
        var newTaken = 0
        for (card in new) {
            if (newTaken == newLeft) break
            if (chosen[card.key.line]?.let { it != card.key.direction } == true) continue
            chosen[card.key.line] = card.key.direction
            offered += card
            newTaken++
        }
        return Offer(offered)
    }

    /**
     * Whether [line]'s new card in [direction] may enter: always, unless the deck asks one
     * direction after another and the direction before it is not acquired for that line — the one
     * before it the line asks in, a direction whose question it leaves empty making no card.
     */
    private fun enters(deck: FlashcardsDeck, line: Line, direction: String, cards: Map<CardKey, Card>): Boolean {
        if (!deck.afterAnother) return true
        val index = deck.directions.indexOfFirst { it.key == direction }
        val before = deck.directions.take(index.coerceAtLeast(0)).lastOrNull { it.key in line.asks } ?: return true
        val card = cards[CardKey(line.id, before.key)] ?: return false
        return card.last.stability >= ACQUIRED_DAYS
    }
}
