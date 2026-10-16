package app.treelune.core.selection

import app.treelune.core.ui.components.PeriodType
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Which side of a period a relative date stands on. */
enum class Edge { START, END }

/** The units a shifted instant moves by (TimePoint.Shift): a period's, and the minute. */
enum class ShiftUnit { MINUTE, HOUR, DAY, WEEK, MONTH, YEAR }

/**
 * A date or an instant as it is stored wherever the app keeps one to compare with: a period's
 * bound, a filter's value on a date field.
 *
 * A fixed date keeps the stored form of its field (milliseconds for a DATETIME, "2026-09-15" for
 * a DATE). A relative one is an object, resolved each time against the reference its context
 * gives (TimeResolver): `{"relative": {"unit": "DAY", "offset": -1, "edge": "START"}}` for the
 * start of the day before, `{"relative": "NOW"}` for the reference itself. The presence of
 * `relative` alone says a value is to be resolved, never the type of a field nor the look of a
 * string. An absent bound is no bound: nothing stands for it here.
 *
 * A relative date may also keep its place instead of standing on an edge (Shift): `"edge":
 * "SAME"`, the same position so many units away ("an hour before", "the day before at the same
 * time"), the minute included; or, by the day, an hour of it, `"edge": {"at": "18:00"}` ("the day
 * before at 18:00"). A reminder is one, against the start of its event.
 *
 * The AI writes the same objects; its fixed dates alone are ISO 8601, turned into the stored
 * form on the way in (FilterValues).
 */
sealed interface TimePoint {

    /** A date or an instant in the stored form of its field. */
    data class Fixed(val value: Any) : TimePoint

    /** The [edge] of the [unit] [offset] units away from the one the reference falls in. */
    data class Relative(val unit: PeriodType, val offset: Int, val edge: Edge) : TimePoint

    /** The reference itself, labelled "the moment itself" where there is one. */
    data object Now : TimePoint

    /**
     * The reference moved [offset] [unit]s on the calendar, keeping its place in the unit: the
     * same time the day before, the same day of the month the month before (the last day of a
     * shorter month). With [at], by the day only, that day at that time instead.
     */
    data class Shift(val unit: ShiftUnit, val offset: Int, val at: LocalTime? = null) : TimePoint {
        init {
            require(at == null || unit == ShiftUnit.DAY) { "an hour of the day goes with the day, not $unit" }
        }
    }

    /** The stored form: the fixed value as it is, a relative one as its object. */
    fun toJson(): Any = when (this) {
        is Fixed -> value
        is Relative -> JSONObject().put(RELATIVE, JSONObject()
            .put("unit", unit.name)
            .put("offset", offset)
            .put("edge", edge.name))
        Now -> JSONObject().put(RELATIVE, NOW)
        is Shift -> JSONObject().put(RELATIVE, JSONObject()
            .put("unit", unit.name)
            .put("offset", offset)
            .put("edge", at?.let { JSONObject().put(AT, it.format(TIME)) } ?: SAME))
    }

    companion object {
        const val RELATIVE = "relative"
        const val NOW = "NOW"
        const val SAME = "SAME"
        const val AT = "at"
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")

        /** Whether [value] is a relative date, as JSON or as the map a command carries. */
        fun isRelative(value: Any?): Boolean = when (value) {
            is JSONObject -> value.has(RELATIVE)
            is Map<*, *> -> value.containsKey(RELATIVE)
            else -> false
        }

        /**
         * [value] read: a relative date when it is one, fixed otherwise.
         *
         * @param text The string system, for the error the AI or the log reads
         * @throws IllegalArgumentException on a relative date that does not read
         */
        fun read(value: Any, text: (String) -> String): TimePoint {
            if (!isRelative(value)) return Fixed(value)
            val relative = when (value) {
                is JSONObject -> value.get(RELATIVE)
                else -> (value as Map<*, *>)[RELATIVE]
            }
            if (relative == NOW) return Now

            val fields: Map<*, *> = when (relative) {
                is JSONObject -> relative.keys().asSequence().associateWith { relative.get(it) }
                is Map<*, *> -> relative
                else -> throw invalid(value, text)
            }
            val unitName = fields["unit"] as? String ?: throw invalid(value, text)
            // An offset is a whole number, which a JSON reader may hand over as 1.0
            val offset = (fields["offset"] as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt() ?: throw invalid(value, text)
            val edge = fields["edge"]

            // A shift keeps its place in the unit, or takes an hour of the day
            val at: Any? = when (edge) {
                is JSONObject -> edge.opt(AT)
                is Map<*, *> -> edge[AT]
                else -> null
            }
            if (edge == SAME || at != null) {
                val unit = ShiftUnit.entries.firstOrNull { it.name == unitName }
                    ?: throw IllegalArgumentException(text("ai_error_period_unknown_type").format(unitName, ShiftUnit.entries.joinToString(", ") { it.name }))
                if (at == null) return Shift(unit, offset)
                if (unit != ShiftUnit.DAY) throw invalid(value, text)
                val time = (at as? String)?.let {
                    try { LocalTime.parse(it, TIME) } catch (e: DateTimeParseException) { null }
                } ?: throw invalid(value, text)
                return Shift(unit, offset, time)
            }

            val unit = PeriodType.entries.firstOrNull { it.name == unitName }
                ?: throw IllegalArgumentException(text("ai_error_period_unknown_type").format(unitName, PeriodType.entries.joinToString(", ") { it.name }))
            val side = Edge.entries.firstOrNull { it.name == edge } ?: throw invalid(value, text)
            return Relative(unit, offset, side)
        }

        private fun invalid(value: Any, text: (String) -> String) =
            IllegalArgumentException(text("error_relative_date_invalid").format(value.toString()))
    }
}
