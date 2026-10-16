package app.treelune.tools.agenda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.commands.CommandResult
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.CustomFieldsDisplay
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldsLayout
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.NullablePeriodSaver
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.chatButtonSpace
import app.treelune.core.ui.components.Period
import app.treelune.core.ui.components.PeriodType
import app.treelune.core.ui.components.SinglePeriodSelector
import app.treelune.core.ui.components.getPeriodEndTimestamp
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.agenda.AgendaDays
import app.treelune.tools.agenda.AgendaEvent
import app.treelune.tools.agenda.AgendaSeries
import app.treelune.tools.agenda.AgendaToolType
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** What the screen shows: what is to come, or one day, week or month, which arrows move. */
internal enum class AgendaView { UPCOMING, DAY, WEEK, MONTH }

/**
 * The screen of a calendar (docs/design/agenda-tool.md, « L'écran »): the view chosen at the top,
 * « Upcoming » first, then a day, a week or a month with their arrows and their calendar; the
 * events grouped by day, each line its time or « All day », its name, its duration and the
 * calendar's fields; the removed occurrences of the period folded at the bottom. « + » makes an
 * event, or a series; an event touched opens, and from an occurrence, its series.
 *
 * @param openEntryId An event to open at once: the one a reminder or the tile is about
 */
@Composable
fun AgendaScreen(
    toolInstanceId: String,
    onNavigateBack: () -> Unit,
    onConfigureClick: () -> Unit,
    openEntryId: String? = null
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    val scope = rememberCoroutineScope()
    val zone = remember { AgendaToolType.zone() }

    // Reloaded, never saved: the calendar's config and its events
    var config by remember { mutableStateOf<JSONObject?>(null) }
    var events by remember { mutableStateOf<List<AgendaEvent>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var configVersion by remember { mutableIntStateOf(0) }
    var eventsVersion by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // What the user chose: the view, its period, the event, series or creation open, the removed shown
    var view by rememberSaveable { mutableStateOf(AgendaView.UPCOMING) }
    var period by rememberSaveable(stateSaver = NullablePeriodSaver) { mutableStateOf<Period?>(null) }
    var openEventId by rememberSaveable { mutableStateOf(openEntryId) }
    var openSeriesKey by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var showRemoved by rememberSaveable { mutableStateOf(false) }
    // A series change the service counted, waiting for its agreement: the config to send, and how many occurrences go
    var pendingConfig by rememberSaveable(stateSaver = app.treelune.core.ui.NullableJsonObjectSaver) { mutableStateOf<JSONObject?>(null) }
    var pendingCount by rememberSaveable { mutableIntStateOf(0) }
    var pendingDeletes by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(toolInstanceId, configVersion) {
        val result = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { loadFailed = true; errorMessage = result.error; return@LaunchedEffect }
        val tool = result.data?.get("tool_instance") as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        config = JsonUtils.toJSONObject(tool["config"] as Map<String, Any?>)
    }
    LaunchedEffect(toolInstanceId, eventsVersion) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) { loadFailed = true; errorMessage = result.error; return@LaunchedEffect }
        events = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { AgendaEvent.of(it) }
    }
    // An event written anywhere, by this screen, the scheduler or the AI, reloads the events; a
    // config saved reloads the series and the calendar's fields
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event ->
            when (event) {
                is DataChangeEvent.ToolDataChanged -> if (event.toolInstanceId == toolInstanceId) eventsVersion++
                is DataChangeEvent.ToolsChanged -> configVersion++
                else -> {}
            }
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            UI.Toast(context, message, Duration.LONG)
            errorMessage = null
        }
    }

    /** Runs a write, and says why when the service refuses it; the reload follows the change notice. */
    fun write(action: suspend () -> CommandResult, onDone: () -> Unit = {}) {
        scope.launch {
            val result = action()
            if (result.isSuccess) onDone() else errorMessage = result.error ?: s.shared("message_error_simple")
        }
    }

    val loadedConfig = config
    val loadedEvents = events
    if (loadedConfig == null || loadedEvents == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadFailed) UI.Text(s.shared("message_error_simple"), TextType.ERROR) else UI.LoadingIndicator()
        }
        return
    }

    /**
     * The config sent with its series changed by [change]: written whole, as tools.update takes
     * it. A change that deletes occurrences to come is refused until agreed to: the count waits
     * for it.
     */
    fun saveSeries(change: (JSONArray) -> JSONArray, deletes: Boolean = false, onDone: () -> Unit = {}) {
        val next = JSONObject(loadedConfig.toString()).put(AgendaToolType.SERIES_LIST, change(loadedConfig.optJSONArray(AgendaToolType.SERIES_LIST) ?: JSONArray()))
        scope.launch {
            val result = coordinator.processUserAction("tools.update", buildMap {
                put("tool_instance_id", toolInstanceId)
                put("config", next)
            })
            val deleted = ((result.data?.get("migration") as? Map<*, *>)?.get("deleted_entries") as? Number)?.toInt() ?: 0
            when {
                result.isSuccess -> onDone()
                deleted > 0 -> { pendingConfig = next; pendingCount = deleted; pendingDeletes = deletes; onDone() }
                else -> errorMessage = result.error ?: s.shared("message_error_simple")
            }
        }
    }

    val settings = ToolConfigSettings.read(AgendaToolType, loadedConfig, context)
    val fields: List<FieldDefinition> = loadedConfig.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
    val series = AgendaSeries.of(loadedConfig)
    val now = System.currentTimeMillis()
    // The period a view other than Upcoming shows, the current one at first
    val shownPeriod = when (view) {
        AgendaView.UPCOMING -> null
        AgendaView.DAY -> period?.takeIf { it.type == PeriodType.DAY } ?: Period.now(PeriodType.DAY)
        AgendaView.WEEK -> period?.takeIf { it.type == PeriodType.WEEK } ?: Period.now(PeriodType.WEEK)
        AgendaView.MONTH -> period?.takeIf { it.type == PeriodType.MONTH } ?: Period.now(PeriodType.MONTH)
    }
    val range = shownPeriod?.let { it.timestamp to getPeriodEndTimestamp(it) }
    val days = AgendaDays.of(loadedEvents, range, now, zone)
    val removed = AgendaDays.removed(loadedEvents, range, now, zone)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).chatButtonSpace()
            .padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = settings.string("name")!!,
            subtitle = settings.string("description")?.takeIf { it.isNotBlank() },
            icon = settings.string("icon_name")!!,
            iconColor = app.treelune.core.themes.IconColor.of(settings.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )

        // The view, and the button making an event on its line
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            val labels = AgendaView.entries.associateWith { s.tool("view_${it.name.lowercase()}") }
            Box(modifier = Modifier.weight(1f)) {
                UI.FormSelection(label = "", options = labels.values.toList(), selected = labels.getValue(view), required = true,
                    onSelect = { chosen -> view = labels.entries.first { it.value == chosen }.key; period = null })
            }
            UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.ICON, size = Size.S, onClick = { creating = true })
        }
        shownPeriod?.let { SinglePeriodSelector(period = it, onPeriodChange = { changed -> period = changed }) }

        if (days.isEmpty()) UI.Text(s.tool(if (view == AgendaView.UPCOMING) "upcoming_empty" else "period_empty"), TextType.CAPTION)
        days.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                UI.Text(dayLabel(day.start, now, s), TextType.HEADING)
                day.events.forEach { event ->
                    EventRow(event, loadedConfig, now, onOpen = { openEventId = event.id })
                }
            }
        }

        // The removed occurrences of what is shown, folded: each one can come back
        if (removed.isNotEmpty()) {
            UI.Divider()
            UI.Button(type = ButtonType.DEFAULT, onClick = { showRemoved = !showRemoved }) {
                UI.Text(s.tool("removed_title").format(removed.size), TextType.LABEL)
            }
            if (showRemoved) removed.forEach { event ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        UI.Text("${AgendaDays.timeLabel(event, s)} · ${dayLabel(AgendaDays.dayStart(event.start, event.allDay, zone), now, s)} · ${event.name}", TextType.CAPTION)
                    }
                    UI.Button(type = ButtonType.DEFAULT, size = Size.S, onClick = { write({ restore(coordinator, event.id) }) }) {
                        UI.Text(s.tool("action_restore"), TextType.LABEL)
                    }
                }
            }
        }
    }

    if (creating) {
        NewEventDialog(
            fields = fields,
            initialStart = AgendaDays.nextHour(now, zone),
            onEvent = { draft, extra ->
                write({
                    coordinator.processUserAction("tool_data.create", mapOf(
                        "tool_instance_id" to toolInstanceId,
                        "name" to draft.name.trim(),
                        "timestamp" to draft.start!!,
                        "data" to draft.data().filterValues { it != null },
                        "extra" to extra.filterValues { it != null },
                        "state" to mapOf(AgendaToolType.STATUS to AgendaToolType.Status.PLANNED)
                    ))
                }) { creating = false }
            },
            onSeries = { draft, schedule ->
                saveSeries({ list -> JSONArray(list.toString()).put(seriesJson(null, draft, schedule)) }) { creating = false }
            },
            onCancel = { creating = false }
        )
    }

    // The event open is kept by its id and found again in the events loaded; one gone meanwhile closes it
    val openEvent = openEventId?.let { id -> loadedEvents.find { it.id == id } }
    if (openEvent != null) {
        EventDialog(
            event = openEvent,
            fields = fields,
            onSave = { draft, extra ->
                write({
                    coordinator.processUserAction("tool_data.update", mapOf(
                        "id" to openEvent.id,
                        "name" to draft.name.trim(),
                        "timestamp" to draft.start!!,
                        "data" to draft.data(),
                        "extra" to extra
                    ))
                }) { openEventId = null }
            },
            onCancelEvent = { write({ coordinator.processUserAction("agenda.cancel", mapOf("id" to openEvent.id)) }) { openEventId = null } },
            onRestore = { write({ restore(coordinator, openEvent.id) }) { openEventId = null } },
            onDelete = { write({ coordinator.processUserAction("tool_data.delete", mapOf("id" to openEvent.id)) }) { openEventId = null } },
            onRemove = { write({ coordinator.processUserAction("agenda.remove", mapOf("id" to openEvent.id)) }) { openEventId = null } },
            onSeries = openEvent.series?.takeIf { key -> series.any { it.key == key } }?.let { key -> { openEventId = null; openSeriesKey = key } },
            onCancel = { openEventId = null }
        )
    }
    LaunchedEffect(loadedEvents, openEventId) {
        if (openEventId != null && loadedEvents.none { it.id == openEventId }) openEventId = null
    }

    val openSeries = openSeriesKey?.let { key -> series.find { it.key == key } }
    if (openSeries != null) {
        SeriesDialog(
            series = openSeries,
            onSave = { draft, schedule ->
                saveSeries({ list -> replaced(list, openSeries.key) { seriesJson(openSeries.key, draft, schedule) } }) { openSeriesKey = null }
            },
            onDelete = { saveSeries({ list -> replaced(list, openSeries.key) { null } }, deletes = true) { openSeriesKey = null } },
            onCancel = { openSeriesKey = null }
        )
    }

    // A series change that remakes or deletes occurrences to come, agreed to before it is sent again
    pendingConfig?.let { next ->
        UI.Dialog(
            type = DialogType.CONFIRM,
            onConfirm = {
                pendingConfig = null
                write({
                    coordinator.processUserAction("tools.update", mapOf("tool_instance_id" to toolInstanceId, "config" to next, "confirm_migration" to true))
                })
            },
            onCancel = { pendingConfig = null }
        ) {
            UI.Text(s.tool(if (pendingDeletes) "series_delete_count" else "series_change_confirm").format(pendingCount), TextType.BODY)
        }
    }
}

/** An event planned again, cancelled or removed before. */
private suspend fun restore(coordinator: Coordinator, id: String): CommandResult =
    coordinator.processUserAction("agenda.restore", mapOf("id" to id))

/** A series as the config stores it; its key kept, or given by the service for a new one. */
private fun seriesJson(key: String?, draft: EventDraft, schedule: JSONObject): JSONObject = JSONObject().apply {
    key?.let { put("key", it) }
    put("name", draft.name.trim())
    put("schedule", schedule)
    put(AgendaToolType.ALL_DAY, draft.allDay)
    if (draft.allDay) put(AgendaToolType.DAYS, draft.days) else draft.duration?.let { put(AgendaToolType.DURATION, it) }
}

/** [list] with its series [key] replaced by what [by] gives, taken out when it gives none. */
private fun replaced(list: JSONArray, key: String, by: () -> JSONObject?): JSONArray {
    val next = JSONArray()
    for (i in 0 until list.length()) {
        val item = list.getJSONObject(i)
        if (item.optString("key") == key) by()?.let { next.put(it) } else next.put(item)
    }
    return next
}

/** A day's title: today, tomorrow, yesterday, or the day in words. */
private fun dayLabel(dayStart: Long, now: Long, s: app.treelune.core.strings.StringsContext): String {
    val zone = AgendaToolType.zone()
    val day = java.time.Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()
    val today = AgendaDays.dayOfInstant(now, zone)
    return when (day) {
        today -> s.tool("day_today")
        today.plusDays(1) -> s.tool("day_tomorrow")
        today.minusDays(1) -> s.tool("day_yesterday")
        else -> app.treelune.core.utils.DateUtils.format(dayStart, "EEEE d MMMM", zone).replaceFirstChar { it.uppercase() }
    }
}

/**
 * One event: its time or « All day » at the start, its name, its duration below, then the
 * calendar's fields, compact; going on, it says so; cancelled, it says so before its name; an
 * occurrence of a series has the icon of a repetition. Touched, it opens.
 */
@Composable
private fun EventRow(event: AgendaEvent, config: JSONObject, now: Long, onOpen: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    val zone = AgendaToolType.zone()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = UI.Space.S),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
    ) {
        UI.Text(AgendaDays.timeLabel(event, s), if (event.isOngoing(now, zone)) TextType.STRONG else TextType.BODY)
        Column(modifier = Modifier.weight(1f)) {
            val name = if (event.isPlanned) event.name else s.tool("cancelled_name").format(event.name)
            UI.Text(name, if (event.isPlanned) TextType.SUBTITLE else TextType.CAPTION)
            AgendaDays.lengthLabel(event, s)?.let { UI.Text(it, TextType.CAPTION) }
            if (event.isOngoing(now, zone) && event.isPlanned) UI.Text(s.tool("ongoing"), TextType.STRONG)
            CustomFieldsDisplay(AgendaToolType, config, event.extra, FieldsLayout.COMPACT, context)
        }
        if (event.series != null) UI.Icon("repeat", size = 16.dp)
    }
}
