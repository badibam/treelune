package app.treelune.core.reading

/**
 * A streak (Reduction.STREAK, docs/BRICKS.md, « Reading »): how many elements in a row hold a
 * condition, `while`, from the first: the rows of a reading in its selection's order, the
 * element's value of the reading's field on the left; or, grouped by a unit of time, the groups
 * from the latest back, the group's value on the left. It cannot be a filter: a filter would take
 * the elements missed out, and the streak would no longer see where it stops. The unit in
 * progress (today, not done yet) can break it: a period ending the day before leaves it out.
 */
object Streaks {

    /** The key of a streak's condition, beside its reduction: a condition without its left side. */
    const val WHILE = "while"

    /**
     * Why a reading reduced by [reduction], with [rowWhile] beside it and grouped by [group],
     * does not read as a streak; null when it does: a streak of rows has its condition, a
     * grouped reading's streak is its groups' (ReadingGroup), and a condition goes with a streak.
     */
    fun problem(reduction: Reduction, rowWhile: org.json.JSONObject?, group: ReadingGroup?, text: (String) -> String): String? = when {
        reduction == Reduction.STREAK && group != null -> text("reading_error_streak_in_group")
        reduction == Reduction.STREAK && rowWhile == null -> text("reading_error_streak_while")
        reduction != Reduction.STREAK && rowWhile != null -> text("reading_error_while_alone")
        else -> null
    }

    /** How many of [elements], from the first, hold [holds] in a row. */
    fun <T> count(elements: List<T>, holds: (T) -> Boolean): Int = elements.takeWhile(holds).size
}
