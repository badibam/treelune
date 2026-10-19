package app.treelune.tools.flashcards.ui

/**
 * What flashcards.counts answers, read for the tile and the page: the whole deck, then each group,
 * each with its due and new cards in every direction and in each.
 */
data class DeckCounts(val all: Counts, val groups: List<Counts>) {

    /**
     * @property group The group's value, null for the whole deck
     * @property label The group's value as its field shows it, null for the whole deck
     */
    data class Counts(val group: String?, val label: String?, val due: Int, val new: Int, val directions: List<Direction>) {
        val empty: Boolean get() = due == 0 && new == 0
    }

    data class Direction(val key: String, val name: String, val due: Int, val new: Int) {
        val empty: Boolean get() = due == 0 && new == 0
    }

    companion object {
        /** @throws IllegalStateException on an answer not of flashcards.counts */
        fun of(data: Map<String, Any?>?): DeckCounts {
            fun counts(map: Map<*, *>) = Counts(
                group = map["group"] as? String,
                label = map["label"] as? String,
                due = (map["due"] as Number).toInt(),
                new = (map["new"] as Number).toInt(),
                directions = (map["directions"] as List<*>).map { it as Map<*, *> }.map {
                    Direction(it["direction"] as String, it["name"] as String, (it["due"] as Number).toInt(), (it["new"] as Number).toInt())
                }
            )
            val all = data?.get("all") as? Map<*, *> ?: error("not the counts of a deck")
            return DeckCounts(counts(all), (data["groups"] as List<*>).map { counts(it as Map<*, *>) })
        }
    }
}
