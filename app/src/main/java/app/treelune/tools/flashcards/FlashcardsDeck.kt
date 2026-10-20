package app.treelune.tools.flashcards

import app.treelune.core.selection.EntrySelection
import org.json.JSONArray
import org.json.JSONObject

/**
 * A deck's config read (docs/design/flashcards.md, « La config »): the source's entries it points
 * at, the field that cuts it into groups, its directions, and the day's limits.
 *
 * @property source The source's entries, without a period; the fields it keeps are not read
 * @property groupBy The path of the source's field the groups are the values of, none without groups
 * @property afterAnother A line's next direction enters once the one before it, in [directions]'s order, is acquired
 * @property newPerDay How many new cards the deck takes on in a day, whatever the group
 * @property reviewsPerDay How many cards already seen it reviews in a day, whatever the group
 */
data class FlashcardsDeck(
    val source: EntrySelection,
    val groupBy: String?,
    val directions: List<Direction>,
    val afterAnother: Boolean,
    val newPerDay: Int,
    val reviewsPerDay: Int,
    val desiredRetention: Double
) {
    /**
     * One way of asking a line: its fields on the front, those on the back, by path. The first
     * field of a face is the question or the answer, the others details.
     *
     * @property key Given at its creation and kept (FlashcardsToolType.completeConfig): renaming a direction keeps its reviews
     */
    data class Direction(val key: String, val name: String, val front: List<String>, val back: List<String>) {
        val fields: List<String> get() = front + back
    }

    /** The source's tool. */
    val sourceId: String get() = source.target.id ?: error("a deck's source names no tool")

    fun direction(key: String): Direction? = directions.firstOrNull { it.key == key }

    /** Every field of the source the deck names: its groups' and its directions'. */
    val namedFields: List<String> get() = (listOfNotNull(groupBy) + directions.flatMap { it.fields }).distinct()

    /**
     * Where the deck names a field the source no longer has ([exists] false), as the error says it:
     * the grouping, or a direction's front or back, the place to correct in the deck's config. The
     * field itself is not named: gone from the source, its label is gone with it. Null when every
     * field it names exists.
     */
    fun goneField(exists: (String) -> Boolean, sourceName: String, text: (String) -> String): String? {
        if (groupBy != null && !exists(groupBy)) return text("error_group_by_gone").format(sourceName)
        directions.forEach { direction ->
            if (direction.front.any { !exists(it) }) return text("error_front_gone").format(direction.name, sourceName)
            if (direction.back.any { !exists(it) }) return text("error_back_gone").format(direction.name, sourceName)
        }
        return null
    }

    companion object {
        const val SOURCE = "source"
        const val GROUP_BY = "group_by"
        const val DIRECTIONS = "directions"
        const val KEY = "key"
        const val NAME = "name"
        const val FRONT = "front"
        const val BACK = "back"
        const val AFTER_ANOTHER = "one_direction_after_another"
        const val NEW_PER_DAY = "new_per_day"
        const val REVIEWS_PER_DAY = "reviews_per_day"
        const val DESIRED_RETENTION = "desired_retention"

        /**
         * [config] read, as the schema, its defaults and completeConfig leave it.
         *
         * @throws IllegalArgumentException on a source that does not read
         * @throws org.json.JSONException on a setting missing, which its declared default always fills
         */
        fun of(config: JSONObject, text: (String) -> String): FlashcardsDeck = FlashcardsDeck(
            source = EntrySelection.fromJson(config.getJSONObject(SOURCE), text),
            groupBy = config.optString(GROUP_BY).takeIf { it.isNotEmpty() },
            directions = objects(config.optJSONArray(DIRECTIONS)).map { d ->
                Direction(d.optString(KEY), d.optString(NAME), strings(d.optJSONArray(FRONT)), strings(d.optJSONArray(BACK)))
            },
            afterAnother = config.getBoolean(AFTER_ANOTHER),
            newPerDay = config.getInt(NEW_PER_DAY),
            reviewsPerDay = config.getInt(REVIEWS_PER_DAY),
            desiredRetention = config.getDouble(DESIRED_RETENTION)
        )

        fun objects(array: JSONArray?): List<JSONObject> = array?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

        fun strings(array: JSONArray?): List<String> = array?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } } } ?: emptyList()
    }
}
