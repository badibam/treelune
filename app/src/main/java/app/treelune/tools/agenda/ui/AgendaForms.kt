package app.treelune.tools.agenda.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.CustomFieldsInput
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.defaultValues
import app.treelune.core.fields.settings.ScheduleSettingEditor
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.FieldValuesSaver
import app.treelune.core.ui.NullableJsonObjectSaver
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.tools.agenda.AgendaDays
import app.treelune.tools.agenda.AgendaEvent
import app.treelune.tools.agenda.AgendaSeries
import app.treelune.tools.agenda.AgendaToolType
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * What an event or a series is made of on the screen: its name, whole days or not, its start,
 * its duration or its days. A whole day's start is its day, stored as the start of the user's day on it.
 */
@kotlinx.serialization.Serializable
internal data class EventDraft(
    val name: String,
    val allDay: Boolean,
    val start: Long?,
    val duration: Long?,
    val days: Int
) {
    /** The event's data as its entry stores it: a duration, or whole days, never both. */
    fun data(): Map<String, Any?> =
        if (allDay) mapOf(AgendaToolType.ALL_DAY to true, AgendaToolType.DAYS to days, AgendaToolType.DURATION to null)
        else mapOf(AgendaToolType.ALL_DAY to null, AgendaToolType.DAYS to null, AgendaToolType.DURATION to duration)
}

/** A day as a DATE field stores it, "2026-10-23", from a whole day's start. */
private fun dayOf(start: Long?): String? = start?.let { Instant.ofEpochMilli(it).atZone(AgendaToolType.zone()).toLocalDate().toString() }

/** The start of the user's day on a day stored as a DATE field holds it. */
private fun startOfDay(day: Any?): Long? = (day as? String)?.let {
    try { AgendaToolType.calendar(AgendaToolType.zone()).startOf(LocalDate.parse(it)) } catch (e: java.time.format.DateTimeParseException) { null }
}

/**
 * A start at a time made whole days: the start of the user's day that time is in — at 1:00 with
 * days starting at 4:00, the day before, as « today » everywhere else.
 */
private fun wholeDayOf(start: Long?): Long? = start?.let {
    val zone = AgendaToolType.zone()
    AgendaToolType.calendar(zone).startOf(AgendaDays.dayOfInstant(it, zone))
}

/**
 * The fields an event and a series share: the name, whole days or not, the start (a series has
 * none: its schedule gives it), the duration or the days.
 */
@Composable
private fun DraftFields(draft: EventDraft, withStart: Boolean, context: Context, onChange: (EventDraft) -> Unit) {
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    fun def(name: String, type: FieldType, config: Map<String, Any>? = null) = FieldDefinition(name, s.tool("field_$name"), null, type, false, config)
    UI.FormField(label = s.tool("field_name"), value = draft.name, onChange = { onChange(draft.copy(name = it)) },
        fieldType = app.treelune.core.ui.FieldType.TEXT, required = true)
    UI.Checkbox(checked = draft.allDay, onCheckedChange = { onChange(draft.copy(allDay = it, start = if (it) wholeDayOf(draft.start) else draft.start)) }, label = s.tool("field_all_day"))
    if (withStart) {
        if (draft.allDay) FieldInput(def("start_day", FieldType.DATE), dayOf(draft.start), { onChange(draft.copy(start = startOfDay(it))) }, context, required = true)
        else FieldInput(def("start", FieldType.DATETIME), draft.start, { onChange(draft.copy(start = (it as? Number)?.toLong())) }, context, required = true)
    }
    if (draft.allDay) FieldInput(def(AgendaToolType.DAYS, FieldType.NUMERIC, mapOf("min" to 1, "decimals" to 0)), draft.days,
        { onChange(draft.copy(days = (it as? Number)?.toInt()?.coerceAtLeast(1) ?: 1)) }, context, required = true)
    else FieldInput(def(AgendaToolType.DURATION, FieldType.DURATION, AgendaToolType.DURATION_UNITS), draft.duration, { onChange(draft.copy(duration = (it as? Number)?.toLong())) }, context)
}

/**
 * A new event: its fields, the calendar's own fields, and « Repeats »: checked, the schedule
 * instead of a start and of the calendar's fields, and what is saved is a series. What is typed
 * survives a recreation.
 *
 * @param onEvent The event typed, with the values of the calendar's fields
 * @param onSeries The series typed, with its schedule
 */
@Composable
internal fun NewEventDialog(
    fields: List<FieldDefinition>,
    initialStart: Long,
    onEvent: (EventDraft, Map<String, Any?>) -> Unit,
    onSeries: (EventDraft, JSONObject) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    var draft by rememberSaveable(stateSaver = draftSaver) { mutableStateOf(EventDraft("", false, initialStart, null, 1)) }
    var extra by rememberSaveable(stateSaver = FieldValuesSaver) { mutableStateOf(fields.defaultValues()) }
    var repeats by rememberSaveable { mutableStateOf(false) }
    var schedule by rememberSaveable(stateSaver = NullableJsonObjectSaver) { mutableStateOf<JSONObject?>(null) }

    val complete = draft.name.isNotBlank() && if (repeats) schedule != null else draft.start != null
    UI.Dialog(
        type = DialogType.CREATE,
        onConfirm = { if (repeats) onSeries(draft, schedule!!) else onEvent(draft, extra) },
        onCancel = onCancel,
        confirmEnabled = complete
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.tool("new_event_title"), TextType.SUBTITLE)
            DraftFields(draft, withStart = !repeats, context = context) { draft = it }
            UI.Checkbox(checked = repeats, onCheckedChange = { repeats = it }, label = s.tool("field_repeats"))
            if (repeats) remember { ScheduleSettingEditor(s.tool("field_series_schedule"), s) }.Edit(schedule) { schedule = it as? JSONObject }
            // The calendar's fields go with an event of its own: a series gives none (docs/design/agenda-tool.md)
            else if (fields.isNotEmpty()) {
                UI.Divider()
                CustomFieldsInput(customFieldsMetadata = fields, values = extra, onValuesChange = { extra = it }, context = context, newEntry = true)
            }
        }
    }
}

/**
 * An event opened: its fields and the calendar's, saved together; cancelling or restoring it;
 * deleting it, or removing it for an occurrence of a series, which also offers its series.
 * What is typed survives a recreation, kept by the event's id.
 */
@Composable
internal fun EventDialog(
    event: AgendaEvent,
    fields: List<FieldDefinition>,
    onSave: (EventDraft, Map<String, Any?>) -> Unit,
    onCancelEvent: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onRemove: () -> Unit,
    onSeries: (() -> Unit)?,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    var draft by rememberSaveable(event.id, stateSaver = draftSaver) {
        mutableStateOf(EventDraft(event.name, event.allDay, event.start, event.duration, event.days))
    }
    var extra by rememberSaveable(event.id, stateSaver = FieldValuesSaver) { mutableStateOf(event.extra) }

    UI.Dialog(
        type = DialogType.EDIT,
        onConfirm = { onSave(draft, extra) },
        onCancel = onCancel,
        confirmEnabled = draft.name.isNotBlank() && draft.start != null
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.tool("edit_event_title"), TextType.SUBTITLE)
            if (!event.isPlanned) UI.Text(s.tool("status_${event.status}"), TextType.WARNING)
            DraftFields(draft, withStart = true, context = context) { draft = it }
            if (fields.isNotEmpty()) {
                UI.Divider()
                CustomFieldsInput(customFieldsMetadata = fields, values = extra, onValuesChange = { extra = it }, context = context)
            }
            UI.Divider()
            UI.FormActions {
                if (event.isPlanned) UI.Button(type = app.treelune.core.ui.ButtonType.DEFAULT, onClick = onCancelEvent) { UI.Text(s.tool("action_cancel_event"), TextType.LABEL) }
                else UI.Button(type = app.treelune.core.ui.ButtonType.DEFAULT, onClick = onRestore) { UI.Text(s.tool("action_restore"), TextType.LABEL) }
                if (event.series == null) UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.LABEL, requireConfirmation = true,
                    confirmMessage = s.tool("delete_event_confirm").format(event.name), onClick = onDelete)
                else if (!event.isRemoved) UI.Button(type = app.treelune.core.ui.ButtonType.DEFAULT, onClick = onRemove) { UI.Text(s.tool("action_remove"), TextType.LABEL) }
            }
            onSeries?.let { open ->
                UI.Button(type = app.treelune.core.ui.ButtonType.DEFAULT, onClick = open) { UI.Text(s.tool("action_edit_series"), TextType.LABEL) }
            }
        }
    }
}

/**
 * A series opened: its name, whole days or not, its duration or days, its schedule; deleting it.
 * What its change does to the occurrences to come is the service's to say (AgendaScreen).
 */
@Composable
internal fun SeriesDialog(
    series: AgendaSeries,
    onSave: (EventDraft, JSONObject) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    var draft by rememberSaveable(series.key, stateSaver = draftSaver) {
        mutableStateOf(EventDraft(series.name, series.allDay, null, series.duration, series.days))
    }
    var schedule by rememberSaveable(series.key, stateSaver = NullableJsonObjectSaver) { mutableStateOf(series.schedule) }

    UI.Dialog(
        type = DialogType.EDIT,
        onConfirm = { onSave(draft, schedule!!) },
        onCancel = onCancel,
        confirmEnabled = draft.name.isNotBlank() && schedule != null
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.tool("edit_series_title"), TextType.SUBTITLE)
            DraftFields(draft, withStart = false, context = context) { draft = it }
            remember { ScheduleSettingEditor(s.tool("field_series_schedule"), s) }.Edit(schedule) { schedule = it as? JSONObject }
            UI.Divider()
            UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.LABEL, requireConfirmation = true,
                confirmMessage = s.tool("delete_series_confirm").format(series.name), onClick = onDelete)
        }
    }
}

/** An event draft across a recreation. */
private val draftSaver = app.treelune.core.ui.serializableSaver(EventDraft.serializer())
