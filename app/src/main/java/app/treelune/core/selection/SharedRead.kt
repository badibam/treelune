package app.treelune.core.selection

/**
 * A selection read at several instants with one read of its entries: when its filters on values
 * do not move with the instant (EntrySelection.filtersMove), only its period does, and the entries
 * of every period are read at once — from the earliest start to the latest end — each instant
 * then keeping those its own period takes. What a reading at several instants (ReadingService)
 * and a variable's entries per term (VariableService) both do.
 *
 * @property union The period that holds every instant's: a side without limit at one instant is
 *   without limit for all
 * @property ranges Each instant with its own period's bounds, in the order given
 */
class SharedRead private constructor(val union: EntryPeriod, val ranges: List<Pair<Long, Pair<Long?, Long?>>>) {

    /** The rows of the union [rows] that the period of the instant at [index] takes, by their timestamp. */
    fun <T : Map<String, Any?>> at(index: Int, rows: List<T>): List<T> {
        val (start, end) = ranges[index].second
        return rows.filter { row ->
            val timestamp = (row["timestamp"] as? Number)?.toLong() ?: return@filter false
            (start == null || timestamp >= start) && (end == null || timestamp <= end)
        }
    }

    companion object {
        /** [period] at each of [instants], and the period that holds them all. */
        fun of(period: EntryPeriod, instants: List<Long>): SharedRead {
            val ranges = instants.map { at -> at to period.instants(TimeResolver.at(at)) }
            val union = EntryPeriod(
                start = ranges.map { it.second.first }.takeIf { starts -> starts.none { it == null } }?.minOfOrNull { it!! }?.let { TimePoint.Fixed(it) },
                end = ranges.map { it.second.second }.takeIf { ends -> ends.none { it == null } }?.maxOfOrNull { it!! }?.let { TimePoint.Fixed(it) }
            )
            return SharedRead(union, ranges)
        }
    }
}
