package app.treelune.core.ui.components

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.treelune.core.ui.*
import app.treelune.core.utils.DateUtils
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.selection.Period
import app.treelune.core.selection.PeriodType
import app.treelune.core.selection.RelativePeriod
import app.treelune.core.selection.TimeResolver
import app.treelune.core.selection.daysBetween
import app.treelune.core.selection.weeksBetween
import app.treelune.core.selection.getNextPeriod
import app.treelune.core.selection.getPreviousPeriod
import app.treelune.core.selection.hourOnDay
import app.treelune.core.selection.normalizeTimestampWithConfig
import app.treelune.core.selection.periodOfDay
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.coordinator.isSuccess
import kotlinx.coroutines.launch
import java.util.*
import java.time.ZoneId

/**
 * Reusable period filter types
 */
enum class PeriodFilterType {
    ALL,    // All data
    HOUR,   // Filter by hour
    DAY,    // Filter by day  
    WEEK,   // Filter by week
    MONTH,  // Filter by month
    YEAR    // Filter by year
}

/**
 * Single period selector with navigation arrows (the LEFT and RIGHT action buttons).
 * Displays relative labels (Today, This week, etc.) with navigation arrows
 * and ability to click to open date selector. Loads its own temporal configuration.
 */
@Composable
fun SinglePeriodSelector(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
    showDatePicker: Boolean = true,
    useOnlyRelativeLabels: Boolean = false
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // Cached at startup, refreshed when the format settings are saved
    val dayStartHour = AppConfigManager.getDayStartHour()
    val weekStartDay = AppConfigManager.getWeekStartDay()

    // State for date selector
    var showPicker by rememberSaveable { mutableStateOf(false) }

    // Smart label generation
    val label = remember(period, dayStartHour, weekStartDay, useOnlyRelativeLabels) {
        generatePeriodLabel(period, app.treelune.core.selection.TimeResolver.at(System.currentTimeMillis()), s, useOnlyRelativeLabels)
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        UI.ActionButton(
            action = ButtonAction.LEFT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                val previousPeriod = getPreviousPeriod(period)
                onPeriodChange(previousPeriod)
            }
        )
        
        // Clickable label in center
        Box(modifier = Modifier.weight(1f)) {
            if (showDatePicker) {
                UI.Button(
                    type = ButtonType.DEFAULT,
                    size = Size.M,
                    onClick = { showPicker = true }
                ) {
                    UI.Text(
                        text = label,
                        type = TextType.BODY,
                        fillMaxWidth = true,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                UI.Text(
                    text = label,
                    type = TextType.BODY,
                    fillMaxWidth = true,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Next button
        UI.ActionButton(
            action = ButtonAction.RIGHT,
            display = ButtonDisplay.ICON,
            size = Size.M,
            onClick = {
                val nextPeriod = getNextPeriod(period)
                onPeriodChange(nextPeriod)
            }
        )
    }
    
    // Date selector if enabled
    if (showPicker && showDatePicker) {
        when (period.type) {
            PeriodType.HOUR -> {
                // For HOUR: DatePicker keeps existing hour
                UI.DatePicker(
                    selectedDate = DateUtils.formatDateForDisplay(period.timestamp),
                    today = DateUtils.calendarToday(),
                    onDateSelected = { selectedDate ->
                        // The hour kept, at its time of the clock on the day chosen
                        val date = DateUtils.parseDisplayDate(selectedDate) ?: return@DatePicker
                        onPeriodChange(hourOnDay(period, date, app.treelune.core.selection.TimeResolver.at(System.currentTimeMillis())))
                    },
                    onDismiss = { showPicker = false }
                )
            }
            else -> {
                // For other types: classic DatePicker
                val calendar = app.treelune.core.selection.TimeResolver.at(System.currentTimeMillis())
                UI.DatePicker(
                    selectedDate = DateUtils.formatDateForDisplay(period.timestamp),
                    today = calendar.today(),
                    onDateSelected = { selectedDate ->
                        DateUtils.parseDisplayDate(selectedDate)?.let { onPeriodChange(periodOfDay(it, period.type, calendar)) }
                    },
                    onDismiss = { showPicker = false }
                )
            }
        }
    }
}

/**
 * Generates smart label for given period
 * @param calendar The user's calendar, against now: the days and weeks counted on it
 * @param useOnlyRelativeLabels if true, always use relative labels ("dans 145 ans"),
 *                             if false, fallback to absolute dates for distant periods
 */
fun generatePeriodLabel(
    period: Period,
    calendar: TimeResolver,
    s: StringsContext,
    useOnlyRelativeLabels: Boolean = false
): String {
    val now = calendar.reference

    return when (period.type) {
        PeriodType.HOUR -> generateHourLabel(period.timestamp, now, s, useOnlyRelativeLabels)
        PeriodType.DAY -> generateDayLabel(period.timestamp, calendar, s, useOnlyRelativeLabels)
        PeriodType.WEEK -> generateWeekLabel(period.timestamp, calendar, s, useOnlyRelativeLabels)
        PeriodType.MONTH -> generateMonthLabel(period.timestamp, now, s, useOnlyRelativeLabels)
        PeriodType.YEAR -> generateYearLabel(period.timestamp, now, s, useOnlyRelativeLabels)
    }
}

/**
 * Labels for hours
 */
private fun generateHourLabel(timestamp: Long, now: Long, s: StringsContext, useOnlyRelativeLabels: Boolean): String {
    // Normalize both timestamps to compare hours correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.HOUR)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.HOUR)

    val diffHours = ((normalizedNow - normalizedTimestamp) / (60 * 60 * 1000)).toInt()

    return when {
        diffHours == 0 -> s.shared("period_this_hour")
        diffHours > 0 -> {
            if (useOnlyRelativeLabels || diffHours <= 12) {
                if (diffHours == 1) {
                    s.shared("period_hours_ago_singular").format(diffHours)
                } else {
                    s.shared("period_hours_ago_plural").format(diffHours)
                }
            } else {
                val date = DateUtils.formatDateForDisplay(timestamp)
                val hour = DateUtils.calendarAt(timestamp).get(Calendar.HOUR_OF_DAY)
                "$date ${hour}h"
            }
        }
        diffHours < 0 -> {
            val absDiff = -diffHours
            if (useOnlyRelativeLabels || absDiff <= 12) {
                if (absDiff == 1) {
                    s.shared("period_hours_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_hours_from_now_plural").format(absDiff)
                }
            } else {
                val date = DateUtils.formatDateForDisplay(timestamp)
                val hour = DateUtils.calendarAt(timestamp).get(Calendar.HOUR_OF_DAY)
                "$date ${hour}h"
            }
        }
        else -> {
            // Fallback case should never happen
            val date = DateUtils.formatDateForDisplay(timestamp)
            val hour = DateUtils.calendarAt(timestamp).get(Calendar.HOUR_OF_DAY)
            "$date ${hour}h"
        }
    }
}

/**
 * Labels for days
 */
private fun generateDayLabel(timestamp: Long, calendar: TimeResolver, s: StringsContext, useOnlyRelativeLabels: Boolean): String {
    val diffDays = daysBetween(timestamp, calendar)

    return when {
        diffDays == 0 -> s.shared("period_today")
        diffDays == 1 -> s.shared("period_yesterday")
        diffDays == -1 -> s.shared("period_tomorrow")
        diffDays > 0 -> {
            if (useOnlyRelativeLabels || diffDays <= 7) {
                if (diffDays == 1) {
                    s.shared("period_days_ago_singular").format(diffDays)
                } else {
                    s.shared("period_days_ago_plural").format(diffDays)
                }
            } else {
                DateUtils.formatDateForDisplay(timestamp)
            }
        }
        diffDays < 0 -> {
            val absDiff = -diffDays
            if (useOnlyRelativeLabels || absDiff <= 7) {
                if (absDiff == 1) {
                    s.shared("period_days_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_days_from_now_plural").format(absDiff)
                }
            } else {
                DateUtils.formatDateForDisplay(timestamp)
            }
        }
        else -> DateUtils.formatDateForDisplay(timestamp)
    }
}

/**
 * Labels for weeks
 */
private fun generateWeekLabel(timestamp: Long, calendar: TimeResolver, s: StringsContext, useOnlyRelativeLabels: Boolean = false): String {
    val diffWeeks = weeksBetween(timestamp, calendar)

    return when {
        diffWeeks == 0 -> s.shared("period_this_week")
        diffWeeks == 1 -> s.shared("period_last_week")
        diffWeeks == -1 -> s.shared("period_next_week")
        diffWeeks > 0 -> {
            if (useOnlyRelativeLabels || diffWeeks <= 4) {
                if (diffWeeks == 1) {
                    s.shared("period_weeks_ago_singular").format(diffWeeks)
                } else {
                    s.shared("period_weeks_ago_plural").format(diffWeeks)
                }
            } else {
                val weekStart = normalizeTimestampWithConfig(timestamp, PeriodType.WEEK, calendar.dayStartHour, calendar.weekStartDay, calendar.zone)
                s.shared("period_week_of").format(DateUtils.formatDateForDisplay(weekStart))
            }
        }
        diffWeeks < 0 -> {
            val absDiff = -diffWeeks
            if (useOnlyRelativeLabels || absDiff <= 4) {
                if (absDiff == 1) {
                    s.shared("period_weeks_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_weeks_from_now_plural").format(absDiff)
                }
            } else {
                val weekStart = normalizeTimestampWithConfig(timestamp, PeriodType.WEEK, calendar.dayStartHour, calendar.weekStartDay, calendar.zone)
                s.shared("period_week_of").format(DateUtils.formatDateForDisplay(weekStart))
            }
        }
        else -> {
            // Fallback case should never happen
            val weekStart = normalizeTimestampWithConfig(timestamp, PeriodType.WEEK, calendar.dayStartHour, calendar.weekStartDay, calendar.zone)
            s.shared("period_week_of").format(DateUtils.formatDateForDisplay(weekStart))
        }
    }
}

/**
 * Labels for months
 */
private fun generateMonthLabel(timestamp: Long, now: Long, s: StringsContext, useOnlyRelativeLabels: Boolean = false): String {
    // Normalize timestamps to compare months correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.MONTH)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.MONTH)

    val nowCal = DateUtils.calendarAt(normalizedNow)
    val tsCal = DateUtils.calendarAt(normalizedTimestamp)

    val diffMonths = (nowCal.get(Calendar.YEAR) - tsCal.get(Calendar.YEAR)) * 12 +
                    (nowCal.get(Calendar.MONTH) - tsCal.get(Calendar.MONTH))

    return when {
        diffMonths == 0 -> s.shared("period_this_month")
        diffMonths == 1 -> s.shared("period_last_month")
        diffMonths == -1 -> s.shared("period_next_month")
        diffMonths > 0 -> {
            if (useOnlyRelativeLabels || diffMonths <= 6) {
                s.shared("period_months_ago").format(diffMonths)
            } else {
                val monthKeys = arrayOf(
                    "month_january", "month_february", "month_march", "month_april", "month_may", "month_june",
                    "month_july", "month_august", "month_september", "month_october", "month_november", "month_december"
                )
                val month = s.shared(monthKeys[tsCal.get(Calendar.MONTH)])
                val year = tsCal.get(Calendar.YEAR)
                "$month $year"
            }
        }
        diffMonths < 0 -> {
            val absDiff = -diffMonths
            if (useOnlyRelativeLabels || absDiff <= 6) {
                s.shared("period_months_from_now").format(absDiff)
            } else {
                val monthKeys = arrayOf(
                    "month_january", "month_february", "month_march", "month_april", "month_may", "month_june",
                    "month_july", "month_august", "month_september", "month_october", "month_november", "month_december"
                )
                val month = s.shared(monthKeys[tsCal.get(Calendar.MONTH)])
                val year = tsCal.get(Calendar.YEAR)
                "$month $year"
            }
        }
        else -> {
            // Fallback case should never happen
            val monthKeys = arrayOf(
                "month_january", "month_february", "month_march", "month_april", "month_may", "month_june",
                "month_july", "month_august", "month_september", "month_october", "month_november", "month_december"
            )
            val month = s.shared(monthKeys[tsCal.get(Calendar.MONTH)])
            val year = tsCal.get(Calendar.YEAR)
            "$month $year"
        }
    }
}

/**
 * Labels for years
 */
private fun generateYearLabel(timestamp: Long, now: Long, s: StringsContext, useOnlyRelativeLabels: Boolean = false): String {
    // Normalize timestamps to compare years correctly
    val normalizedNow = normalizeTimestampWithConfig(now, PeriodType.YEAR)
    val normalizedTimestamp = normalizeTimestampWithConfig(timestamp, PeriodType.YEAR)

    val nowYear = DateUtils.calendarAt(normalizedNow).get(Calendar.YEAR)
    val tsYear = DateUtils.calendarAt(normalizedTimestamp).get(Calendar.YEAR)
    val diffYears = nowYear - tsYear

    return when {
        diffYears == 0 -> s.shared("period_this_year")
        diffYears == 1 -> s.shared("period_last_year")
        diffYears == -1 -> s.shared("period_next_year")
        diffYears > 0 -> {
            if (useOnlyRelativeLabels || diffYears <= 3) {
                if (diffYears == 1) {
                    s.shared("period_years_ago_singular").format(diffYears)
                } else {
                    s.shared("period_years_ago_plural").format(diffYears)
                }
            } else {
                tsYear.toString()
            }
        }
        diffYears < 0 -> {
            val absDiff = -diffYears
            if (useOnlyRelativeLabels || absDiff <= 3) {
                if (absDiff == 1) {
                    s.shared("period_years_from_now_singular").format(absDiff)
                } else {
                    s.shared("period_years_from_now_plural").format(absDiff)
                }
            } else {
                tsYear.toString()
            }
        }
        else -> {
            // Fallback case should never happen
            tsYear.toString()
        }
    }
}

/**
 * Generate truly relative label for RelativePeriod
 * Examples: "Le jour-même", "La veille", "Le lendemain", "2 jours avant", "3 semaines après"
 */
fun generateRelativePeriodLabel(relativePeriod: RelativePeriod, s: app.treelune.core.strings.StringsContext): String {
    val offset = relativePeriod.offset
    val type = relativePeriod.type

    return when {
        // Current period (offset = 0)
        offset == 0 -> when (type) {
            PeriodType.HOUR -> s.shared("period_relative_current_hour")    // "L'heure en cours"
            PeriodType.DAY -> s.shared("period_relative_current_day")       // "Le jour-même"
            PeriodType.WEEK -> s.shared("period_relative_current_week")     // "La semaine en cours"
            PeriodType.MONTH -> s.shared("period_relative_current_month")   // "Le mois en cours"
            PeriodType.YEAR -> s.shared("period_relative_current_year")     // "L'année en cours"
        }
        // Previous period (offset = -1)
        offset == -1 -> when (type) {
            PeriodType.HOUR -> s.shared("period_relative_one_hour_before")     // "1 heure avant"
            PeriodType.DAY -> s.shared("period_relative_one_day_before")        // "La veille"
            PeriodType.WEEK -> s.shared("period_relative_one_week_before")      // "La semaine précédente"
            PeriodType.MONTH -> s.shared("period_relative_one_month_before")    // "Le mois précédent"
            PeriodType.YEAR -> s.shared("period_relative_one_year_before")      // "L'année précédente"
        }
        // Next period (offset = 1)
        offset == 1 -> when (type) {
            PeriodType.HOUR -> s.shared("period_relative_one_hour_after")      // "1 heure après"
            PeriodType.DAY -> s.shared("period_relative_one_day_after")         // "Le lendemain"
            PeriodType.WEEK -> s.shared("period_relative_one_week_after")       // "La semaine suivante"
            PeriodType.MONTH -> s.shared("period_relative_one_month_after")     // "Le mois suivant"
            PeriodType.YEAR -> s.shared("period_relative_one_year_after")       // "L'année suivante"
        }
        // Past periods (offset < -1)
        offset < -1 -> {
            val absOffset = -offset
            when (type) {
                PeriodType.HOUR -> String.format(s.shared("period_relative_hours_before"), absOffset)    // "X heures avant"
                PeriodType.DAY -> String.format(s.shared("period_relative_days_before"), absOffset)       // "X jours avant"
                PeriodType.WEEK -> String.format(s.shared("period_relative_weeks_before"), absOffset)     // "X semaines avant"
                PeriodType.MONTH -> String.format(s.shared("period_relative_months_before"), absOffset)   // "X mois avant"
                PeriodType.YEAR -> String.format(s.shared("period_relative_years_before"), absOffset)     // "X années avant"
            }
        }
        // Future periods (offset > 1)
        else -> {
            when (type) {
                PeriodType.HOUR -> String.format(s.shared("period_relative_hours_after"), offset)     // "X heures après"
                PeriodType.DAY -> String.format(s.shared("period_relative_days_after"), offset)        // "X jours après"
                PeriodType.WEEK -> String.format(s.shared("period_relative_weeks_after"), offset)      // "X semaines après"
                PeriodType.MONTH -> String.format(s.shared("period_relative_months_after"), offset)    // "X mois après"
                PeriodType.YEAR -> String.format(s.shared("period_relative_years_after"), offset)      // "X années après"
            }
        }
    }
}
