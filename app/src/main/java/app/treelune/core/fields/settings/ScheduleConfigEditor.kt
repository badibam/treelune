package app.treelune.core.fields.settings

import kotlinx.serialization.builtins.serializer
import app.treelune.core.utils.DateUtils
import app.treelune.core.ui.StringListSaver
import app.treelune.core.ui.serializableSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.strings.Strings
import app.treelune.core.ui.*
import app.treelune.core.utils.Cycle
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.ScheduleConfig
import app.treelune.core.utils.SchedulePattern
import app.treelune.core.utils.WeekMoment
import app.treelune.core.utils.YearlyDate
import java.util.*

/**
 * Reusable dialog for configuring schedule patterns
 *
 * Supports 6 pattern types:
 * - DailyMultiple: Multiple times per day
 * - WeeklySimple: Specific days with same time
 * - MonthlyRecurrent: Specific months and day
 * - WeeklyCustom: Different times for different days
 * - YearlyRecurrent: Recurring yearly dates
 * - SpecificDates: One-shot specific dates
 *
 * Opened by an automation's editor, and by ScheduleSettingEditor for a tool's schedule.
 *
 * @param scheduled What belongs with a schedule and comes after it while one is chosen (a
 *   routine's missed runs); the caller keeps it as a draft until the dialog is confirmed
 */
@Composable
fun ScheduleConfigEditor(
    existingConfig: ScheduleConfig?,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleConfig?) -> Unit,
    scheduled: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val scrollState = rememberScrollState()

    // Pattern selection (with "None" option)
    val patternTypes = listOf(
        "None",
        "DailyMultiple",
        "WeeklySimple",
        "MonthlyRecurrent",
        "WeeklyCustom",
        "YearlyRecurrent",
        "SpecificDates"
    )

    var selectedPatternType by rememberSaveable {
        mutableStateOf(
            existingConfig?.pattern?.let { getPatternTypeName(it) } ?: "None"
        )
    }

    // Pattern-specific states
    // DailyMultiple
    var dailyTimes by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.DailyMultiple)?.times ?: listOf("09:00")
        )
    }

    // WeeklySimple
    var weeklyDays by rememberSaveable(stateSaver = serializableSaver(kotlinx.serialization.builtins.ListSerializer(Int.serializer()))) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.WeeklySimple)?.daysOfWeek ?: listOf(1)
        )
    }
    var weeklyTime by rememberSaveable {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.WeeklySimple)?.time ?: "09:00"
        )
    }

    // MonthlyRecurrent
    var monthlyMonths by rememberSaveable(stateSaver = serializableSaver(kotlinx.serialization.builtins.ListSerializer(Int.serializer()))) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.MonthlyRecurrent)?.months ?: listOf(1)
        )
    }
    var monthlyDays by rememberSaveable(stateSaver = serializableSaver(kotlinx.serialization.builtins.ListSerializer(Int.serializer()))) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.MonthlyRecurrent)?.daysOfMonth ?: listOf(1)
        )
    }
    var monthlyTime by rememberSaveable {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.MonthlyRecurrent)?.time ?: "09:00"
        )
    }

    // WeeklyCustom
    var weeklyCustomMoments by rememberSaveable(stateSaver = serializableSaver(kotlinx.serialization.builtins.ListSerializer(WeekMoment.serializer()))) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.WeeklyCustom)?.moments
                ?: listOf(WeekMoment(1, "09:00"))
        )
    }

    // YearlyRecurrent
    var yearlyDates by rememberSaveable(stateSaver = serializableSaver(kotlinx.serialization.builtins.ListSerializer(YearlyDate.serializer()))) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.YearlyRecurrent)?.dates
                ?: listOf(YearlyDate(1, 1, "09:00"))
        )
    }

    // SpecificDates
    var specificTimestamps by rememberSaveable(stateSaver = serializableSaver(kotlinx.serialization.builtins.ListSerializer(Long.serializer()))) {
        mutableStateOf(
            (existingConfig?.pattern as? SchedulePattern.SpecificDates)?.timestamps
                ?: listOf(System.currentTimeMillis())
        )
    }

    // The cycle of a daily or weekly pattern (Cycle): one day or week in cycleEvery, placed by
    // cycleStart, the day stored; shared by the three patterns that have one
    val storedCycle = existingConfig?.pattern?.let { cycleOf(it) }
    var cycleEvery by rememberSaveable { mutableStateOf(storedCycle?.first ?: 1) }
    var cycleStart by rememberSaveable { mutableStateOf(storedCycle?.second) }

    // The pattern as the editor stands, its cycle left aside: what the choices and the preview read
    val base = if (selectedPatternType == "None") null else buildSchedulePattern(
        patternType = selectedPatternType,
        dailyTimes = dailyTimes,
        weeklyDays = weeklyDays,
        weeklyTime = weeklyTime,
        monthlyMonths = monthlyMonths,
        monthlyDays = monthlyDays,
        monthlyTime = monthlyTime,
        weeklyCustomMoments = weeklyCustomMoments,
        yearlyDates = yearlyDates,
        specificTimestamps = specificTimestamps
    )
    val cycled = base != null && Cycle.unitOf(base) != null && cycleEvery > 1
    // The next times to choose from, one per place in the cycle; the one stored is the choice
    // on the same place, found again whatever day it was stored on
    val starts = remember(base, cycleEvery) {
        if (base != null && cycled) ScheduleCalculator.cycleStarts(base, cycleEvery) else emptyList()
    }
    val start = starts.firstOrNull { day -> cycleStart?.let { ScheduleCalculator.sameCycle(base!!, cycleEvery, java.time.LocalDate.parse(it), day) } == true }
        ?: starts.firstOrNull()
    // The pattern the dialog gives. A pattern without any time to come has no choice: its start
    // stays the one stored, or today, and the service refuses the empty pattern anyway
    val pattern = base?.let {
        if (!cycled) ScheduleCalculator.withCycle(it, 1, null)
        else ScheduleCalculator.withCycle(it, cycleEvery, (start?.toString() ?: cycleStart ?: DateUtils.calendarToday().toString()))
    }

    UI.Dialog(
        type = DialogType.CONFIGURE,
        onConfirm = {
            onConfirm(pattern?.let { ScheduleConfig(pattern = it) })
        },
        onCancel = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UI.Space.L)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(UI.Space.L)
        ) {
            // Title
            UI.Text(
                text = s.shared("schedule_config_title"),
                type = TextType.TITLE
            )

            // Pattern selector
            UI.FormSelection(
                required = false,
                label = s.shared("schedule_pattern_label"),
                options = patternTypes.map { s.shared("schedule_pattern_$it") },
                selected = s.shared("schedule_pattern_$selectedPatternType"),
                onSelect = { selectedLabel ->
                    selectedPatternType = patternTypes[patternTypes.map { s.shared("schedule_pattern_$it") }.indexOf(selectedLabel)]
                }
            )

            // Pattern-specific UI
            when (selectedPatternType) {
                "None" -> {
                    UI.Card(type = CardType.DEFAULT) {
                        Box(modifier = Modifier.padding(UI.Space.L)) {
                            UI.Text(
                                text = s.shared("schedule_no_schedule_message"),
                                type = TextType.BODY
                            )
                        }
                    }
                }
                "DailyMultiple" -> DailyMultipleEditor(
                    times = dailyTimes,
                    onTimesChange = { dailyTimes = it }
                )
                "WeeklySimple" -> WeeklySimpleEditor(
                    days = weeklyDays,
                    time = weeklyTime,
                    onDaysChange = { weeklyDays = it },
                    onTimeChange = { weeklyTime = it }
                )
                "MonthlyRecurrent" -> MonthlyRecurrentEditor(
                    months = monthlyMonths,
                    days = monthlyDays,
                    time = monthlyTime,
                    onMonthsChange = { monthlyMonths = it },
                    onDaysChange = { monthlyDays = it },
                    onTimeChange = { monthlyTime = it }
                )
                "WeeklyCustom" -> WeeklyCustomEditor(
                    moments = weeklyCustomMoments,
                    onMomentsChange = { weeklyCustomMoments = it }
                )
                "YearlyRecurrent" -> YearlyRecurrentEditor(
                    dates = yearlyDates,
                    onDatesChange = { yearlyDates = it }
                )
                "SpecificDates" -> SpecificDatesEditor(
                    timestamps = specificTimestamps,
                    onTimestampsChange = { specificTimestamps = it }
                )
            }

            // One day or week in N, and the next time when N is above 1
            if (base != null && Cycle.unitOf(base) != null) {
                CycleEditor(
                    days = Cycle.unitOf(base) == Cycle.Unit.DAY,
                    every = cycleEvery,
                    starts = starts,
                    start = start,
                    onEveryChange = { cycleEvery = it },
                    onStartChange = { cycleStart = it.toString() }
                )
            }

            // Preview label (only if not "None")
            if (pattern != null) {
                val previewLabel = withCycle(pattern, generatePreviewLabel(
                    context = context,
                    patternType = selectedPatternType,
                    dailyTimes = dailyTimes,
                    weeklyDays = weeklyDays,
                    weeklyTime = weeklyTime,
                    monthlyMonths = monthlyMonths,
                    monthlyDays = monthlyDays,
                    monthlyTime = monthlyTime,
                    weeklyCustomMoments = weeklyCustomMoments,
                    yearlyDates = yearlyDates,
                    specificTimestamps = specificTimestamps
                ), s)
                val nextTimes = remember(pattern) { nextTimes(pattern, 3) }

                UI.Card(type = CardType.DEFAULT) {
                    Column(modifier = Modifier.padding(UI.Space.M)) {
                        UI.Text(
                            text = s.shared("schedule_preview_label"),
                            type = TextType.LABEL
                        )
                        UI.Text(
                            text = previewLabel,
                            type = TextType.BODY
                        )
                        UI.Text(
                            text = if (nextTimes.isEmpty()) s.shared("schedule_next_none")
                                else s.shared("schedule_next_times").format(nextTimes.joinToString(" · ") {
                                    DateUtils.format(it, "EEE d MMM HH:mm")
                                }),
                            type = TextType.CAPTION
                        )
                    }
                }
                scheduled?.invoke()
            }
        }
    }
}

/**
 * The cycle of a daily or weekly pattern: every how many [days] or weeks, and, above 1, which of
 * the [starts] is the next time — one per place in the cycle, [start] the one chosen.
 */
@Composable
private fun CycleEditor(
    days: Boolean,
    every: Int,
    starts: List<java.time.LocalDate>,
    start: java.time.LocalDate?,
    onEveryChange: (Int) -> Unit,
    onStartChange: (java.time.LocalDate) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    // What is typed, kept apart from the cycle: emptied to type another number, it stays empty
    // instead of showing the last valid one again; the cycle takes each whole number from 1
    var typed by rememberSaveable { mutableStateOf(every.toString()) }
    if (typed.toIntOrNull() != every && typed.isNotEmpty() && typed.toIntOrNull()?.let { it >= 1 } == true) typed = every.toString()
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.FormField(
            required = false,
            label = s.shared(if (days) "schedule_every_days_field" else "schedule_every_weeks_field"),
            value = typed,
            onChange = { text ->
                typed = text
                text.toIntOrNull()?.takeIf { it >= 1 }?.let(onEveryChange)
            },
            fieldType = FieldType.NUMERIC
        )
        if (every > 1 && starts.isNotEmpty()) {
            val labels = starts.map { DateUtils.formatShortDay(it) }
            UI.FormSelection(
                required = false,
                label = s.shared("schedule_start_date_label"),
                options = labels,
                selected = start?.let { labels[starts.indexOf(it)] } ?: labels.first(),
                onSelect = { label -> onStartChange(starts[labels.indexOf(label)]) }
            )
        }
    }
}

/** The cycle stored in [pattern], every how many and its start; null for a pattern without one. */
private fun cycleOf(pattern: SchedulePattern): Pair<Int, String?>? = when (pattern) {
    is SchedulePattern.DailyMultiple -> pattern.every to pattern.startDate
    is SchedulePattern.WeeklySimple -> pattern.every to pattern.startDate
    is SchedulePattern.WeeklyCustom -> pattern.every to pattern.startDate
    else -> null
}

/** The [count] next times of [pattern] from now, fewer when it has no more. */
private fun nextTimes(pattern: SchedulePattern, count: Int): List<Long> {
    val times = mutableListOf<Long>()
    var from = System.currentTimeMillis()
    while (times.size < count) {
        from = ScheduleCalculator.calculateNextExecution(pattern, from) ?: break
        times += from
    }
    return times
}

/**
 * DailyMultiple pattern editor
 * Multiple times per day (e.g., "09:00", "14:00", "18:00")
 */
@Composable
private fun DailyMultipleEditor(
    times: List<String>,
    onTimesChange: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(
            text = s.shared("schedule_daily_times_label"),
            type = TextType.SUBTITLE
        )

        times.forEachIndexed { index, time ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TimePickerField(
                        label = s.shared("schedule_time_label").format(index + 1),
                        value = time,
                        onChange = { newTime ->
                            onTimesChange(times.toMutableList().apply { set(index, newTime) })
                        }
                    )
                }

                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = {
                        if (times.size > 1) {
                            onTimesChange(times.filterIndexed { i, _ -> i != index })
                        }
                    }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onTimesChange(times + "09:00")
            }
        )
    }
}

/**
 * WeeklySimple pattern editor
 * Specific days with same time (e.g., Monday, Wednesday, Friday at 9:00)
 */
@Composable
private fun WeeklySimpleEditor(
    days: List<Int>,
    time: String,
    onDaysChange: (List<Int>) -> Unit,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(
            text = s.shared("schedule_weekly_days_label"),
            type = TextType.SUBTITLE
        )

        // Day checkboxes (1=Monday to 7=Sunday)
        val dayNames = (1..7).map { s.shared("day_of_week_$it") }

        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
            dayNames.forEachIndexed { index, dayName ->
                val dayNumber = index + 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.S)
                ) {
                    UI.Checkbox(
                        label = dayName,
                        checked = days.contains(dayNumber),
                        onCheckedChange = { checked ->
                            onDaysChange(
                                if (checked) {
                                    (days + dayNumber).sorted()
                                } else {
                                    days.filter { it != dayNumber }
                                }
                            )
                        }
                    )
                }
            }
        }

        // Time selector
        TimePickerField(
            label = s.shared("schedule_time_label_single"),
            value = time,
            onChange = onTimeChange
        )
    }
}

/**
 * MonthlyRecurrent pattern editor
 * Specific months and days (e.g., 1st and 15th of January, March, June at 10:00)
 */
@Composable
private fun MonthlyRecurrentEditor(
    months: List<Int>,
    days: List<Int>,
    time: String,
    onMonthsChange: (List<Int>) -> Unit,
    onDaysChange: (List<Int>) -> Unit,
    onTimeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(
            text = s.shared("schedule_monthly_config_label"),
            type = TextType.SUBTITLE
        )

        // Month checkboxes
        val monthNames = (1..12).map { s.shared("month_$it") }

        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
            monthNames.forEachIndexed { index, monthName ->
                val monthNumber = index + 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UI.Checkbox(
                        label = monthName,
                        checked = months.contains(monthNumber),
                        onCheckedChange = { checked ->
                            onMonthsChange(
                                if (checked) {
                                    (months + monthNumber).sorted()
                                } else {
                                    months.filter { it != monthNumber }
                                }
                            )
                        }
                    )
                }
            }
        }

        // Days of the month (1-31), added and removed as the daily times are
        UI.Text(
            text = s.shared("schedule_days_of_month_label"),
            type = TextType.SUBTITLE
        )
        days.forEachIndexed { index, day ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.FormField(
                        required = false,
                        label = s.shared("schedule_day_of_month_label"),
                        value = day.toString(),
                        onChange = { newDay ->
                            newDay.toIntOrNull()?.let { value ->
                                if (value in 1..31) onDaysChange(days.toMutableList().apply { set(index, value) })
                            }
                        },
                        fieldType = FieldType.NUMERIC
                    )
                }

                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = {
                        if (days.size > 1) {
                            onDaysChange(days.filterIndexed { i, _ -> i != index })
                        }
                    }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                // The day after the last one, as the first one free from it
                onDaysChange(days + ((days.maxOrNull() ?: 0) % 31 + 1))
            }
        )

        // Time
        TimePickerField(
            label = s.shared("schedule_time_label_single"),
            value = time,
            onChange = onTimeChange
        )
    }
}

/**
 * WeeklyCustom pattern editor
 * Different times for different days (e.g., Monday 9:00, Wednesday 14:00, Friday 17:00)
 */
@Composable
private fun WeeklyCustomEditor(
    moments: List<WeekMoment>,
    onMomentsChange: (List<WeekMoment>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(
            text = s.shared("schedule_weekly_custom_label"),
            type = TextType.SUBTITLE
        )

        moments.forEachIndexed { index, moment ->
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(UI.Space.M),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.S)
                ) {
                    // Day selector
                    val dayNames = (1..7).map { s.shared("day_of_week_$it") }
                    UI.FormSelection(
                        required = false,
                        label = s.shared("schedule_day_label"),
                        options = dayNames,
                        selected = dayNames[moment.dayOfWeek - 1],
                        onSelect = { selectedDay ->
                            val newDayOfWeek = dayNames.indexOf(selectedDay) + 1
                            onMomentsChange(
                                moments.toMutableList().apply {
                                    set(index, moment.copy(dayOfWeek = newDayOfWeek))
                                }
                            )
                        }
                    )

                    // Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TimePickerField(
                                label = s.shared("schedule_time_label_single"),
                                value = moment.time,
                                onChange = { newTime ->
                                    onMomentsChange(
                                        moments.toMutableList().apply {
                                            set(index, moment.copy(time = newTime))
                                        }
                                    )
                                }
                            )
                        }

                        UI.ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                if (moments.size > 1) {
                                    onMomentsChange(moments.filterIndexed { i, _ -> i != index })
                                }
                            }
                        )
                    }
                }
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onMomentsChange(moments + WeekMoment(1, "09:00"))
            }
        )
    }
}

/**
 * YearlyRecurrent pattern editor
 * Recurring yearly dates (e.g., January 1st and December 25th at 8:00)
 */
@Composable
private fun YearlyRecurrentEditor(
    dates: List<YearlyDate>,
    onDatesChange: (List<YearlyDate>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(
            text = s.shared("schedule_yearly_recurrent_label"),
            type = TextType.SUBTITLE
        )

        dates.forEachIndexed { index, date ->
            UI.Card(type = CardType.DEFAULT) {
                Column(
                    modifier = Modifier.padding(UI.Space.M),
                    verticalArrangement = Arrangement.spacedBy(UI.Space.S)
                ) {
                    // Month selector
                    val monthNames = (1..12).map { s.shared("month_$it") }
                    UI.FormSelection(
                        required = false,
                        label = s.shared("schedule_month_label"),
                        options = monthNames,
                        selected = monthNames[date.month - 1],
                        onSelect = { selectedMonth ->
                            val newMonth = monthNames.indexOf(selectedMonth) + 1
                            onDatesChange(
                                dates.toMutableList().apply {
                                    set(index, date.copy(month = newMonth))
                                }
                            )
                        }
                    )

                    // Day
                    UI.FormField(
                        required = false,
                        label = s.shared("schedule_day_label"),
                        value = date.day.toString(),
                        onChange = { newDay ->
                            newDay.toIntOrNull()?.let { day ->
                                if (day in 1..31) {
                                    onDatesChange(
                                        dates.toMutableList().apply {
                                            set(index, date.copy(day = day))
                                        }
                                    )
                                }
                            }
                        },
                        fieldType = FieldType.NUMERIC
                    )

                    // Time + Delete button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            TimePickerField(
                                label = s.shared("schedule_time_label_single"),
                                value = date.time,
                                onChange = { newTime ->
                                    onDatesChange(
                                        dates.toMutableList().apply {
                                            set(index, date.copy(time = newTime))
                                        }
                                    )
                                }
                            )
                        }

                        UI.ActionButton(
                            action = ButtonAction.DELETE,
                            display = ButtonDisplay.ICON,
                            size = Size.S,
                            onClick = {
                                if (dates.size > 1) {
                                    onDatesChange(dates.filterIndexed { i, _ -> i != index })
                                }
                            }
                        )
                    }
                }
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onDatesChange(dates + YearlyDate(1, 1, "09:00"))
            }
        )
    }
}

/**
 * SpecificDates pattern editor
 * One-shot specific dates (e.g., March 15 2025 at 14:30, April 20 2025 at 10:00)
 */
@Composable
private fun SpecificDatesEditor(
    timestamps: List<Long>,
    onTimestampsChange: (List<Long>) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(
            text = s.shared("schedule_specific_dates_label"),
            type = TextType.SUBTITLE
        )

        timestamps.forEachIndexed { index, timestamp ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DateTimePickerField(
                        label = s.shared("schedule_date_label").format(index + 1),
                        timestamp = timestamp,
                        onChange = { newTimestamp ->
                            onTimestampsChange(timestamps.toMutableList().apply { set(index, newTimestamp) })
                        }
                    )
                }

                UI.ActionButton(
                    action = ButtonAction.DELETE,
                    display = ButtonDisplay.ICON,
                    size = Size.S,
                    onClick = {
                        if (timestamps.size > 1) {
                            onTimestampsChange(timestamps.filterIndexed { i, _ -> i != index })
                        }
                    }
                )
            }
        }

        UI.ActionButton(
            action = ButtonAction.ADD,
            display = ButtonDisplay.LABEL,
            size = Size.M,
            onClick = {
                onTimestampsChange(timestamps + System.currentTimeMillis())
            }
        )
    }
}

/**
 * Time picker field - clickable field that opens a time picker dialog
 */
@Composable
private fun TimePickerField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(false) }

    UI.FormField(
        required = false,
        label = label,
        value = value,
        onChange = { },
        fieldType = FieldType.TEXT,
        readonly = true,
        onClick = { showPicker = true }
    )

    if (showPicker) {
        UI.TimePicker(
            selectedTime = value,
            onTimeSelected = { newTime ->
                onChange(newTime)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Date+Time picker field - clickable field that opens date then time picker dialogs
 */
@Composable
private fun DateTimePickerField(
    label: String,
    timestamp: Long,
    onChange: (Long) -> Unit
) {
    val context = LocalContext.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var tempDate by rememberSaveable { mutableStateOf("") }

    val formattedValue = "${DateUtils.formatDateForDisplay(timestamp)} ${DateUtils.formatTimeForDisplay(timestamp)}"

    UI.FormField(
        required = false,
        label = label,
        value = formattedValue,
        onChange = { },
        fieldType = FieldType.TEXT,
        readonly = true,
        onClick = { showDatePicker = true }
    )

    if (showDatePicker) {
        UI.DatePicker(
            selectedDate = DateUtils.formatDateForDisplay(timestamp),
            today = DateUtils.calendarToday(),
            onDateSelected = { dateStr ->
                tempDate = dateStr
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        UI.TimePicker(
            selectedTime = DateUtils.formatTimeForDisplay(timestamp),
            onTimeSelected = { timeStr ->
                // A string the pickers did not produce is refused and the value left as it was
                DateUtils.combineDateTime(tempDate, timeStr)?.let { onChange(it) }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/**
 * Get pattern type name from SchedulePattern instance
 */
private fun getPatternTypeName(pattern: SchedulePattern?): String {
    return when (pattern) {
        null -> "None"
        is SchedulePattern.DailyMultiple -> "DailyMultiple"
        is SchedulePattern.WeeklySimple -> "WeeklySimple"
        is SchedulePattern.MonthlyRecurrent -> "MonthlyRecurrent"
        is SchedulePattern.WeeklyCustom -> "WeeklyCustom"
        is SchedulePattern.YearlyRecurrent -> "YearlyRecurrent"
        is SchedulePattern.SpecificDates -> "SpecificDates"
    }
}

/**
 * Build SchedulePattern from editor state
 */
private fun buildSchedulePattern(
    patternType: String,
    dailyTimes: List<String>,
    weeklyDays: List<Int>,
    weeklyTime: String,
    monthlyMonths: List<Int>,
    monthlyDays: List<Int>,
    monthlyTime: String,
    weeklyCustomMoments: List<WeekMoment>,
    yearlyDates: List<YearlyDate>,
    specificTimestamps: List<Long>
): SchedulePattern {
    return when (patternType) {
        "DailyMultiple" -> SchedulePattern.DailyMultiple(dailyTimes)
        "WeeklySimple" -> SchedulePattern.WeeklySimple(weeklyDays, weeklyTime)
        "MonthlyRecurrent" -> SchedulePattern.MonthlyRecurrent(monthlyMonths, monthlyDays.distinct().sorted(), monthlyTime)
        "WeeklyCustom" -> SchedulePattern.WeeklyCustom(weeklyCustomMoments)
        "YearlyRecurrent" -> SchedulePattern.YearlyRecurrent(yearlyDates)
        "SpecificDates" -> SchedulePattern.SpecificDates(specificTimestamps)
        else -> SchedulePattern.DailyMultiple(listOf("09:00")) // Fallback
    }
}

/**
 * Generate human-readable preview label
 */
private fun generatePreviewLabel(
    context: android.content.Context,
    patternType: String,
    dailyTimes: List<String>,
    weeklyDays: List<Int>,
    weeklyTime: String,
    monthlyMonths: List<Int>,
    monthlyDays: List<Int>,
    monthlyTime: String,
    weeklyCustomMoments: List<WeekMoment>,
    yearlyDates: List<YearlyDate>,
    specificTimestamps: List<Long>
): String {
    val s = Strings.`for`(context = context)

    return when (patternType) {
        "DailyMultiple" -> {
            s.shared("schedule_preview_daily").format(dailyTimes.joinToString(", "))
        }
        "WeeklySimple" -> {
            val dayNames = weeklyDays.map { s.shared("day_of_week_$it") }.joinToString(", ")
            s.shared("schedule_preview_weekly_simple").format(dayNames, weeklyTime)
        }
        "MonthlyRecurrent" -> {
            val monthNames = monthlyMonths.map { s.shared("month_$it") }.joinToString(", ")
            s.shared("schedule_preview_monthly").format(daysOfMonth(monthlyDays.distinct(), s), monthNames, monthlyTime)
        }
        "WeeklyCustom" -> {
            val momentsText = weeklyCustomMoments.joinToString(", ") { moment ->
                "${s.shared("day_of_week_${moment.dayOfWeek}")} ${moment.time}"
            }
            s.shared("schedule_preview_weekly_custom").format(momentsText)
        }
        "YearlyRecurrent" -> {
            val datesText = yearlyDates.joinToString(", ") { date ->
                "${date.day} ${s.shared("month_${date.month}")}"
            }
            s.shared("schedule_preview_yearly").format(datesText)
        }
        "SpecificDates" -> {
            val count = specificTimestamps.size
            s.shared("schedule_preview_specific").format(count)
        }
        else -> s.shared("schedule_preview_unknown")
    }
}
