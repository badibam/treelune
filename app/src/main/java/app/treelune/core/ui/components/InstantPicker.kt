package app.treelune.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.Edge
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.ShiftUnit
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DateUtils
import app.treelune.core.ui.FieldType as UIFieldType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The four ways an instant is chosen. */
private enum class InstantMode { CUSTOM, RELATIVE, NOW, NONE }

/**
 * Where a relative date stands in its unit: an edge of the period (TimePoint.Relative), or its
 * place kept, the same moment or an hour of the day (TimePoint.Shift).
 */
private enum class Moment { START, END, SAME, AT }

/** A time of day as a shift stores it. */
private val STORED_TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The one input of a date or an instant in the app (docs/design/missing-tools.md, « Le temps
 * relatif »): an entry's DATE or DATETIME field, each bound of a period.
 *
 * It offers a custom date, always absolute, with the time for a DATETIME; a relative date, a unit
 * and an offset ("the day before") with its moment, the start or the end of that period; now;
 * and, for a bound, no limit.
 *
 * With a reference (an automation's scheduled time, the instant a variable is read at), a
 * relative date and now are stored as they are, resolved each time against it, and now is
 * labelled "the moment itself". Without one (a chat, an entry), the clock is the reference at the
 * moment of the choice: what is stored is the date obtained, the relative choice being only the
 * label of a fixed period, shown with the date it gives.
 *
 * @param value A TimePoint, null for none (no limit, or no answer)
 * @param precision DATE (a day, "2026-09-15") or DATETIME (milliseconds)
 * @param hasReference Whether the context resolves relative dates later, against its reference
 * @param edge The moment a relative date starts with: the start for a period's start, the end for its end
 * @param allowNone Whether "no limit" is offered, for a bound
 * @param required Whether the value must be answered; an optional one chosen can be emptied
 * @param shifts Whether a relative date may keep its place in its unit (the same moment, the
 *        minute, an hour of the day: TimePoint.Shift), for an instant against a moment of its own (a reminder)
 * @param relativeOnly Whether a relative date is the only choice: no custom date, no now
 */
@Composable
fun InstantPicker(
    label: String,
    value: TimePoint?,
    onChange: (TimePoint?) -> Unit,
    precision: FieldType,
    hasReference: Boolean,
    edge: Edge = Edge.START,
    allowNone: Boolean = false,
    required: Boolean = false,
    shifts: Boolean = false,
    relativeOnly: Boolean = false
) {
    require(precision == FieldType.DATE || precision == FieldType.DATETIME) { "an instant is a DATE or a DATETIME, not $precision" }
    require(!shifts || precision == FieldType.DATETIME) { "a shift keeps a time, which a day has not" }
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // An hour is no unit for a day; the minute keeps its place, and comes only with shifts
    val units = buildList {
        if (shifts) add(ShiftUnit.MINUTE.name)
        addAll((if (precision == FieldType.DATE) PeriodType.entries - PeriodType.HOUR else PeriodType.entries).map { it.name })
    }
    // The mode and the relative date are the picker's own: without a reference, what is stored
    // is a fixed date, which no longer says how it was chosen
    var mode by rememberSaveable {
        mutableStateOf(when (value) {
            is TimePoint.Relative, is TimePoint.Shift -> InstantMode.RELATIVE
            TimePoint.Now -> if (relativeOnly) InstantMode.RELATIVE else InstantMode.NOW
            null -> if (relativeOnly) InstantMode.RELATIVE else if (allowNone) InstantMode.NONE else InstantMode.CUSTOM
            is TimePoint.Fixed -> if (relativeOnly) InstantMode.RELATIVE else InstantMode.CUSTOM
        })
    }
    val relative = value as? TimePoint.Relative
    val shift = value as? TimePoint.Shift
    var unit by rememberSaveable { mutableStateOf(relative?.unit?.name ?: shift?.unit?.name ?: PeriodType.DAY.name) }
    var offset by rememberSaveable { mutableStateOf(relative?.offset ?: shift?.offset ?: 0) }
    var moment by rememberSaveable {
        mutableStateOf(when {
            relative != null -> if (relative.edge == Edge.START) Moment.START else Moment.END
            shift != null -> if (shift.at != null) Moment.AT else Moment.SAME
            else -> if (edge == Edge.START) Moment.START else Moment.END
        })
    }
    var atTime by rememberSaveable { mutableStateOf(shift?.at?.format(STORED_TIME) ?: "09:00") }

    /** [point] as stored: as it is with a reference, resolved against the clock without one. */
    fun emit(point: TimePoint?) = onChange(
        if (point == null || hasReference || point is TimePoint.Fixed) point else atClock(point, precision)
    )
    fun emitRelative() = emit(when (moment) {
        Moment.START -> TimePoint.Relative(PeriodType.valueOf(unit), offset, Edge.START)
        Moment.END -> TimePoint.Relative(PeriodType.valueOf(unit), offset, Edge.END)
        Moment.SAME -> TimePoint.Shift(ShiftUnit.valueOf(unit), offset)
        Moment.AT -> TimePoint.Shift(ShiftUnit.DAY, offset, LocalTime.parse(atTime, STORED_TIME))
    })

    val modes = buildList {
        add(InstantMode.CUSTOM to s.shared("instant_mode_custom"))
        add(InstantMode.RELATIVE to s.shared("instant_mode_relative"))
        add(InstantMode.NOW to s.shared(if (hasReference) "instant_mode_reference" else "instant_mode_now"))
        if (allowNone) add(InstantMode.NONE to s.shared("instant_mode_none"))
    }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        if (relativeOnly) { if (label.isNotEmpty()) UI.FieldLabel(label, required) }
        else UI.FormSelection(
            label = label,
            options = modes.map { it.second },
            selected = modes.first { it.first == mode }.second,
            onSelect = { chosen ->
                mode = modes.first { it.second == chosen }.first
                when (mode) {
                    // A custom date starts at what the value stood for, the clock resolving it
                    InstantMode.CUSTOM -> onChange(value?.let { atClock(it, precision) })
                    InstantMode.RELATIVE -> emitRelative()
                    InstantMode.NOW -> emit(TimePoint.Now)
                    InstantMode.NONE -> onChange(null)
                }
            },
            required = required
        )

        when (mode) {
            InstantMode.CUSTOM -> CustomDate(value as? TimePoint.Fixed, precision, clearable = !required && !allowNone, onChange = onChange)
            InstantMode.RELATIVE -> {
                RelativeDate(unit, offset, moment, atTime, units, shifts, s,
                    onUnit = {
                        unit = it
                        offset = 0
                        // The minute has no edge, an hour of the day goes with the day alone
                        if (it == ShiftUnit.MINUTE.name) moment = Moment.SAME
                        else if (moment == Moment.AT && it != ShiftUnit.DAY.name) moment = Moment.SAME
                        emitRelative()
                    },
                    onOffset = { offset = it; emitRelative() },
                    onMoment = { moment = it; emitRelative() },
                    onTime = { atTime = it; emitRelative() }
                )
                if (!hasReference) Resolved(value, precision, s)
            }
            InstantMode.NOW -> if (!hasReference) Resolved(value, precision, s)
            InstantMode.NONE -> Unit
        }
    }
}

/**
 * A period as two instants, from and until, each optional. With a reference, it is named once,
 * above ("Relative to: the instant read"): the labels of relative dates stay the same everywhere.
 *
 * @param reference The name of the reference the context gives, null for none (the clock)
 */
@Composable
fun PeriodPicker(
    period: EntryPeriod,
    onChange: (EntryPeriod) -> Unit,
    precision: FieldType,
    reference: String?
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
        reference?.let { UI.Text(text = s.shared("instant_reference").format(it), type = TextType.CAPTION) }
        InstantPicker(
            label = s.shared("period_from"),
            value = period.start,
            onChange = { onChange(period.copy(start = it)) },
            precision = precision,
            hasReference = reference != null,
            edge = Edge.START,
            allowNone = true
        )
        InstantPicker(
            label = s.shared("period_until"),
            value = period.end,
            onChange = { onChange(period.copy(end = it)) },
            precision = precision,
            hasReference = reference != null,
            edge = Edge.END,
            allowNone = true
        )
    }
}

/** [point] resolved against the clock, in the stored form of [precision]. */
private fun atClock(point: TimePoint, precision: FieldType): TimePoint.Fixed =
    point as? TimePoint.Fixed ?: TimePoint.Fixed(TimeResolver.at(System.currentTimeMillis()).resolve(point, precision))

/** A fixed date in words, as the user reads it: "15/09/2026", or with its time. */
private fun shown(fixed: TimePoint.Fixed, precision: FieldType): String = when (val v = fixed.value) {
    is Number -> if (precision == FieldType.DATE) DateUtils.formatDateForDisplay(v.toLong()) else DateUtils.formatFullDateTime(v.toLong())
    is String -> DateUtils.parseIso8601Date(v)?.let { DateUtils.formatDateForDisplay(it) } ?: v
    else -> v.toString()
}

/** The date a choice gave, without a reference: what is stored. */
@Composable
private fun Resolved(value: TimePoint?, precision: FieldType, s: StringsContext) {
    val fixed = value as? TimePoint.Fixed ?: return
    UI.Text(text = s.shared("instant_resolved").format(shown(fixed, precision)), type = TextType.CAPTION)
}

/**
 * A unit, an offset stepped by arrows ("the day before"), and where it stands in that unit: the
 * start or the end of the period, or, with [shifts], the same moment or an hour of the day.
 */
@Composable
private fun RelativeDate(
    unit: String,
    offset: Int,
    moment: Moment,
    atTime: String,
    units: List<String>,
    shifts: Boolean,
    s: StringsContext,
    onUnit: (String) -> Unit,
    onOffset: (Int) -> Unit,
    onMoment: (Moment) -> Unit,
    onTime: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UI.ActionButton(action = ButtonAction.LEFT, display = ButtonDisplay.ICON, size = Size.M, onClick = { onOffset(offset - 1) })
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            UI.Text(text = offsetLabel(unit, offset, s), type = TextType.BODY)
        }
        UI.ActionButton(action = ButtonAction.RIGHT, display = ButtonDisplay.ICON, size = Size.M, onClick = { onOffset(offset + 1) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        fun unitLabel(u: String) = s.shared("period_type_${u.lowercase()}")
        Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("instant_unit"), options = units.map(::unitLabel), selected = unitLabel(unit),
                onSelect = { l -> onUnit(units.first { unitLabel(it) == l }) }, required = true)
        }
        val moments = buildList {
            if (unit != ShiftUnit.MINUTE.name) {
                add(Moment.START to s.shared("instant_edge_start"))
                add(Moment.END to s.shared("instant_edge_end"))
            }
            if (shifts) {
                add(Moment.SAME to s.shared("instant_edge_same"))
                if (unit == ShiftUnit.DAY.name) add(Moment.AT to s.shared("instant_edge_at"))
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("instant_edge"), options = moments.map { it.second }, selected = moments.first { it.first == moment }.second,
                onSelect = { l -> onMoment(moments.first { it.second == l }.first) }, required = true)
        }
    }
    if (moment == Moment.AT) TimeOfDay(atTime, s, onTime)
}

/** The hour of the day a shift by the day stands at, picked on the clock. */
@Composable
private fun TimeOfDay(atTime: String, s: StringsContext, onTime: (String) -> Unit) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val time = LocalTime.parse(atTime, STORED_TIME)
    UI.FormField(label = s.shared("instant_at_time"), value = DateUtils.formatTimeOfDay(time), onChange = {}, fieldType = UIFieldType.TEXT,
        required = true, readonly = true, onClick = { showTimePicker = true })
    if (showTimePicker) {
        UI.TimePicker(
            selectedTime = atTime,
            onTimeSelected = { picked ->
                // The clock answers "HH:mm"; an answer that does not read leaves the time as it was
                try { onTime(LocalTime.parse(picked, STORED_TIME).format(STORED_TIME)) } catch (e: DateTimeParseException) { }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/** [offset] [unit]s in words ("the day before", "15 minutes before"), the minute included. */
private fun offsetLabel(unit: String, offset: Int, s: StringsContext): String {
    if (unit != ShiftUnit.MINUTE.name) return generateRelativePeriodLabel(RelativePeriod(offset, PeriodType.valueOf(unit)), s)
    return when {
        offset == 0 -> s.shared("instant_mode_reference")
        offset == -1 -> s.shared("period_relative_one_minute_before")
        offset == 1 -> s.shared("period_relative_one_minute_after")
        offset < 0 -> s.shared("period_relative_minutes_before").format(-offset)
        else -> s.shared("period_relative_minutes_after").format(offset)
    }
}

/**
 * A shift in words, against its reference: "15 minutes before", "1 hour before" (the minute and
 * the hour keep their place by themselves), "the day before, at the same moment", "the day before at 18:00".
 */
fun shiftLabel(point: TimePoint.Shift, s: StringsContext): String {
    val at = point.at
    if (at == null && point.offset == 0) return s.shared("instant_mode_reference")
    val offset = offsetLabel(point.unit.name, point.offset, s)
    return when {
        at != null -> s.shared("instant_shift_at").format(offset, DateUtils.formatTimeOfDay(at))
        point.unit == ShiftUnit.MINUTE || point.unit == ShiftUnit.HOUR -> offset
        else -> s.shared("instant_shift_same").format(offset)
    }
}

/**
 * A date picked on the calendar, with its time for a DATETIME: a day ("2026-09-15") or
 * milliseconds. An unreadable pick leaves the value as it was, rather than recording today.
 */
@Composable
private fun CustomDate(fixed: TimePoint.Fixed?, precision: FieldType, clearable: Boolean, onChange: (TimePoint?) -> Unit) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    // A day is a day, "2026-09-15"; an instant, milliseconds
    val millis: Long? = (fixed?.value as? Number)?.toLong()
    val day: java.time.LocalDate? = (fixed?.value as? String)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val displayDate = day?.let { DateUtils.formatDisplayDate(it) } ?: millis?.let { DateUtils.formatDateForDisplay(it) } ?: ""
    val displayTime = if (precision == FieldType.DATETIME) millis?.let { DateUtils.formatTimeForDisplay(it) } ?: "" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            UI.FormField(label = "", value = displayDate, onChange = {}, fieldType = UIFieldType.TEXT, required = false,
                readonly = true, onClick = { showDatePicker = true })
        }
        if (precision == FieldType.DATETIME) {
            Box(modifier = Modifier.weight(1f)) {
                UI.FormField(label = "", value = displayTime, onChange = {}, fieldType = UIFieldType.TEXT, required = false,
                    readonly = true, onClick = { showTimePicker = true })
            }
        }
        if (clearable && fixed != null) {
            UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { onChange(null) })
        }
    }

    if (showDatePicker) {
        // A day's today is the app's; the date of an instant, the calendar's
        UI.DatePicker(
            selectedDate = displayDate,
            today = if (precision == FieldType.DATE) TimeResolver.at(System.currentTimeMillis()).today() else DateUtils.calendarToday(),
            onDateSelected = { date ->
                if (precision == FieldType.DATE) {
                    DateUtils.parseDisplayDate(date)?.let { onChange(TimePoint.Fixed(it.toString())) }
                } else {
                    DateUtils.combineDateTime(date, displayTime.ifEmpty { "00:00" })?.let { onChange(TimePoint.Fixed(it)) }
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        UI.TimePicker(
            selectedTime = displayTime.ifEmpty { DateUtils.getCurrentTimeFormatted() },
            onTimeSelected = { time ->
                DateUtils.combineDateTime(displayDate.ifEmpty { DateUtils.getTodayFormatted() }, time)?.let { onChange(TimePoint.Fixed(it)) }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
