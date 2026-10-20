package app.treelune.core.fields

import app.treelune.core.strings.StringsContext

/**
 * A unit a DURATION field is entered and shown in, largest first.
 *
 * A DURATION value is a number of milliseconds and carries no unit: the units live in the
 * field's config (DurationUnits), and only decide what the form offers and what the screen
 * shows. There is no month or year: neither has a fixed length, so neither is a duration.
 */
enum class DurationUnit(val millis: Long) {
    DAY(86_400_000L),
    HOUR(3_600_000L),
    MINUTE(60_000L),
    SECOND(1_000L);

    /** The short symbol written after a number: "h", "min". */
    fun symbol(s: StringsContext): String = s.shared("duration_unit_${name.lowercase()}")
}

/**
 * The units a DURATION field is written in, from its largest to its smallest, stored under
 * [KEY] in its config: « jours heures » writes 2 j 2 h and offers a box for each.
 *
 * Days are for a delay on the calendar (a goal's attempt, an event); hours for time spent (sleep,
 * work), whose total reads wrong in days: 42 h of work in a week are not « 1 j 18 h ».
 */
enum class DurationUnits(val largest: DurationUnit, val smallest: DurationUnit) {
    DAY(DurationUnit.DAY, DurationUnit.DAY),
    DAY_HOUR(DurationUnit.DAY, DurationUnit.HOUR),
    DAY_HOUR_MINUTE(DurationUnit.DAY, DurationUnit.MINUTE),
    HOUR(DurationUnit.HOUR, DurationUnit.HOUR),
    HOUR_MINUTE(DurationUnit.HOUR, DurationUnit.MINUTE),
    HOUR_MINUTE_SECOND(DurationUnit.HOUR, DurationUnit.SECOND),
    MINUTE(DurationUnit.MINUTE, DurationUnit.MINUTE),
    MINUTE_SECOND(DurationUnit.MINUTE, DurationUnit.SECOND),
    SECOND(DurationUnit.SECOND, DurationUnit.SECOND);

    /** Its units, largest first. */
    val units: List<DurationUnit> get() = Durations.between(largest, smallest)

    /** The name shown when choosing them: « heures minutes ». */
    fun displayName(s: StringsContext): String = s.shared("duration_units_${name.lowercase()}")

    /** In a field's config. */
    fun config(): Map<String, Any> = mapOf(KEY to name)

    companion object {
        /** The config key holding them. */
        const val KEY = "units"

        /** The units a field takes when its config names none. */
        val DEFAULT = HOUR_MINUTE

        fun fromConfig(config: Map<String, Any>?): DurationUnits =
            (config?.get(KEY) as? String)?.let { valueOf(it) } ?: DEFAULT
    }
}

/**
 * The arithmetic of a DURATION field, shared by its input, its display and its text form.
 */
object Durations {

    /** The units from [largest] down to [smallest], both included. */
    fun between(largest: DurationUnit, smallest: DurationUnit): List<DurationUnit> =
        DurationUnit.entries.filter { it.ordinal >= largest.ordinal && it.ordinal <= smallest.ordinal }

    /**
     * Splits [millis] into whole amounts of [units], largest first: the largest takes all it
     * holds (50 h in hours and minutes). What is left below the smallest unit is dropped: it is
     * the finest thing the field shows.
     */
    fun split(millis: Long, units: List<DurationUnit>): List<Pair<DurationUnit, Long>> {
        var rest = millis
        return units.map { unit ->
            val amount = rest / unit.millis
            rest -= amount * unit.millis
            unit to amount
        }
    }

    /** The milliseconds that amounts in several units add up to. */
    fun join(amounts: Map<DurationUnit, Long>): Long =
        amounts.entries.sumOf { (unit, amount) -> unit.millis * amount }

    /** The text form of a duration in the units of a field's [config]: "1 h 25 min". */
    fun format(millis: Long, config: Map<String, Any>?, s: StringsContext): String =
        format(millis, DurationUnits.fromConfig(config).units, s)

    /**
     * The text form of a duration in [units]: the units worth zero left out, and a duration
     * below the smallest is "0" in it, so that nothing is ever written as an empty string.
     */
    fun format(millis: Long, units: List<DurationUnit>, s: StringsContext): String {
        val parts = split(millis, units).filter { it.second > 0 }
        return if (parts.isEmpty()) amount(0, units.last(), s)
        else parts.joinToString(" ") { (unit, n) -> amount(n, unit, s) }
    }

    private fun amount(n: Long, unit: DurationUnit, s: StringsContext): String =
        s.shared("duration_amount").format(n.toString(), unit.symbol(s))
}
