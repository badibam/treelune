package app.treelune.core.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Schedule configuration for automated executions
 * Supports 6 types of scheduling patterns
 *
 * Note: Timezone is resolved from AppConfig (user-configured global timezone)
 *
 * A schedule carries no on/off switch: what it drives has one. An automation is paused by its
 * is_enabled column, a Messages tool by the "enabled" at the root of its config.
 */
@Serializable
data class ScheduleConfig(
    val pattern: SchedulePattern
)

/**
 * Sealed class hierarchy for 6 different schedule patterns
 *
 * Note: Regular intervals (every X minutes/hours) are NOT included
 * as no real use case has been identified for them
 */
@Serializable
sealed class SchedulePattern {

    /**
     * Type 1: Daily - Multiple times per day
     * Example: "Every day at 9am, 2pm, and 6pm"; with [every] at 4, "every 4 days at 9am"
     *
     * @param times List of times in HH:mm format (24h)
     * @param every One day in [every] (see [Cycle])
     * @param startDate A day of the cycle, "yyyy-MM-dd", given when [every] is above 1
     */
    @Serializable
    @SerialName("DailyMultiple")
    data class DailyMultiple(
        val times: List<String>,  // ["09:00", "14:00", "18:00"]
        val every: Int = 1,
        @SerialName("start_date") val startDate: String? = null
    ) : SchedulePattern() {
        init { Cycle.check(every, startDate) }
    }

    /**
     * Type 2: Weekly Simple - Specific days with single common time
     * Example: "Monday, Wednesday, Friday at 9am"; with [every] at 3, "every 3 weeks, on Sunday at 8am"
     *
     * @param daysOfWeek List of days (1=Monday, 7=Sunday)
     * @param time Single time in HH:mm format (24h)
     * @param every One week in [every] (see [Cycle])
     * @param startDate A day of the cycle's weeks, "yyyy-MM-dd", given when [every] is above 1
     */
    @Serializable
    @SerialName("WeeklySimple")
    data class WeeklySimple(
        @SerialName("days_of_week") val daysOfWeek: List<Int>,  // 1-7
        val time: String,            // "09:00"
        val every: Int = 1,
        @SerialName("start_date") val startDate: String? = null
    ) : SchedulePattern() {
        init { Cycle.check(every, startDate) }
    }

    /**
     * Type 3: Monthly Recurrent - Specific months with fixed days
     * Example: "On the 1st and the 15th of January, March, and June at 10am"
     *
     * Note: For days > 28, if month doesn't have that day (e.g., Feb 31),
     * the execution is skipped for that month
     *
     * @param months List of months (1-12)
     * @param daysOfMonth Days of month (1-31)
     * @param time Time in HH:mm format (24h)
     */
    @Serializable
    @SerialName("MonthlyRecurrent")
    data class MonthlyRecurrent(
        val months: List<Int>,       // 1-12
        @SerialName("days_of_month") val daysOfMonth: List<Int>,  // 1-31
        val time: String
    ) : SchedulePattern()

    /**
     * Type 4: Weekly Custom - Different times for different days
     * Example: "Monday 9am, Wednesday 2pm, Friday 5pm"; with [every] at 2, one week in two
     *
     * @param moments List of day+time combinations
     * @param every One week in [every] (see [Cycle])
     * @param startDate A day of the cycle's weeks, "yyyy-MM-dd", given when [every] is above 1
     */
    @Serializable
    @SerialName("WeeklyCustom")
    data class WeeklyCustom(
        val moments: List<WeekMoment>,
        val every: Int = 1,
        @SerialName("start_date") val startDate: String? = null
    ) : SchedulePattern() {
        init { Cycle.check(every, startDate) }
    }

    /**
     * Type 5: Yearly Recurrent - Same dates every year
     * Example: "Every January 1st and December 25th at 8am"
     *
     * @param dates List of month+day+time combinations
     */
    @Serializable
    @SerialName("YearlyRecurrent")
    data class YearlyRecurrent(
        val dates: List<YearlyDate>
    ) : SchedulePattern()

    /**
     * Type 6: Specific Dates - One-shot executions (no repetition)
     * Example: "March 15, 2025 at 2:30pm and April 20, 2025 at 10am"
     *
     * @param timestamps List of exact timestamps (UTC milliseconds)
     */
    @Serializable
    @SerialName("SpecificDates")
    data class SpecificDates(
        val timestamps: List<Long>
    ) : SchedulePattern()
}

/**
 * One day or one week in [every], for the daily and weekly patterns: the days, or the weeks, counted
 * from the one holding the start date, those whose count divides by [every] kept. The start date only
 * places the cycle: any day of it gives the same series, and a date in the past is as good as one to
 * come. A week is the user's (its first day, as they set it). Without [every], or at 1, every day or
 * week and no start date; above 1, a start date, which the editor sets to the next time chosen.
 */
object Cycle {

    /** Refuses a cycle that does not read: [every] under 1, a start date missing, unneeded or not a date. */
    fun check(every: Int, startDate: String?) {
        require(every >= 1) { "every is $every: it counts the days or weeks of the cycle, 1 or more" }
        if (every == 1) {
            require(startDate == null) { "start_date given with every at 1: it places a cycle of more than one day or week, leave it out" }
            return
        }
        requireNotNull(startDate) { "every is $every without start_date: give the day of the next time, yyyy-MM-dd" }
        try {
            LocalDate.parse(startDate)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("start_date '$startDate' is not a day written yyyy-MM-dd")
        }
    }

    /** What a cycle counts. */
    enum class Unit { DAY, WEEK }

    /** What [pattern]'s cycle counts, null for a pattern without one. */
    fun unitOf(pattern: SchedulePattern): Unit? = when (pattern) {
        is SchedulePattern.DailyMultiple -> Unit.DAY
        is SchedulePattern.WeeklySimple, is SchedulePattern.WeeklyCustom -> Unit.WEEK
        else -> null
    }

    /** Whether [a] and [b] are the same day, or in the same week starting on [weekStart]. */
    fun sameUnit(a: LocalDate, b: LocalDate, unit: Unit, weekStart: DayOfWeek?): Boolean =
        if (unit == Unit.DAY) a == b else weekOf(a, weekStart!!) == weekOf(b, weekStart)

    /** The first day of the week [date] is in, weeks starting on [weekStart]. */
    private fun weekOf(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.minusDays(Math.floorMod(date.dayOfWeek.value - weekStart.value, 7).toLong())

    /** A cycle of [every] [unit]s placed by [start]; [weekStart] the first day of a week, for weeks. */
    data class Placed(val unit: Unit, val every: Int, val start: LocalDate, val weekStart: DayOfWeek?) {
        /** Whether [date]'s day, or week, is on the cycle. */
        fun holds(date: LocalDate): Boolean {
            val between = if (unit == Unit.DAY) ChronoUnit.DAYS.between(start, date)
            else ChronoUnit.WEEKS.between(weekOf(start, weekStart!!), weekOf(date, weekStart))
            return Math.floorMod(between, every.toLong()) == 0L
        }
    }
}

/**
 * Day of week + time combination for WeeklyCustom pattern
 */
@Serializable
data class WeekMoment(
    @SerialName("day_of_week") val dayOfWeek: Int,  // 1=Monday, 7=Sunday
    val time: String     // HH:mm format
)

/**
 * Month + day + time combination for YearlyRecurrent pattern
 */
@Serializable
data class YearlyDate(
    val month: Int,      // 1-12
    val day: Int,        // 1-31
    val time: String     // HH:mm format
)
