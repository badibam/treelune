package app.treelune.tools.agenda.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ImageField
import app.treelune.core.fields.formatValue
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.ui.components.TileLine
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateUtils
import app.treelune.tools.agenda.AgendaDays
import app.treelune.tools.agenda.AgendaEvent
import app.treelune.tools.agenda.AgendaToolType
import org.json.JSONObject

/**
 * A calendar's tile (docs/design/agenda-tool.md, « La tuile »): the summary says the event going
 * on or the next one, its name and its moment in relative words, « Nothing coming » otherwise; the
 * body lists the events to come, one line each, the moment at the start, the name, the
 * calendar's fields below; in FULL, all of them. A cancelled event says so, a removed one
 * is not there. An event touched opens; elsewhere, the tool.
 */
@Composable
fun rememberAgendaTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "agenda", context = context) }
    val zone = remember { AgendaToolType.zone() }
    val fields = remember(tool.config_json) { JSONObject(tool.config_json).optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList() }

    var events by remember { mutableStateOf<List<AgendaEvent>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id))
        if (result.isSuccess) events = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { AgendaEvent.of(it) }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }

    return remember(tool.id, fields) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = events ?: return
                val now = System.currentTimeMillis()
                val next = coming(loaded, now).firstOrNull()
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    UI.Text(
                        text = next?.let { s.tool("tile_next").format(it.name, whenLabel(it, now, s)) } ?: s.tool("tile_nothing"),
                        type = TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center, maxLines = 2
                    )
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = events ?: return
                val now = System.currentTimeMillis()
                TileGrid(rows, coming(loaded, now), columns = 1) { event ->
                    TileLine(
                        text = if (event.isPlanned) event.name else s.tool("cancelled_name").format(event.name),
                        modifier = Modifier.clickable { open(EntryToOpen.Existing(event.id)) },
                        secondary = fieldsLine(event, fields, context),
                        leading = { UI.Text(shortWhen(event, now, s), TextType.LABEL) },
                        trailing = images(event, fields).takeIf { it.isNotEmpty() }?.let { ids -> { app.treelune.core.images.ImageAbbreviation(ids) } }
                    )
                }
            }
        }
    }
}

/** The events going on or to come, removed ones left out, by start. */
private fun coming(events: List<AgendaEvent>, now: Long): List<AgendaEvent> {
    val zone = AgendaToolType.zone()
    return events.filter { !it.isRemoved && it.end(zone) >= now }.sortedBy { it.start }
}

/** The calendar's fields of [event] on one line, its images apart ([images]), or nothing when it has none. */
private fun fieldsLine(event: AgendaEvent, fields: List<FieldDefinition>, context: android.content.Context): String? =
    fields.filter { it.type != FieldType.IMAGE }.mapNotNull { field -> event.extra[field.name]?.let { field.formatValue(it, context) } }
        .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }

/** The images of [event]'s image fields, in the order of the fields, shown at the end of its line. */
private fun images(event: AgendaEvent, fields: List<FieldDefinition>): List<String> =
    fields.filter { it.type == FieldType.IMAGE }.flatMap { field -> event.extra[field.name]?.let { ImageField.ids(it) }.orEmpty() }

/** When an event is, for the summary: « going on », « in 2 h », « tomorrow 14:30 », « Thu 23 ». */
private fun whenLabel(event: AgendaEvent, now: Long, s: StringsContext): String {
    val zone = AgendaToolType.zone()
    if (event.isOngoing(now, zone)) return s.tool("ongoing").lowercase()
    val today = AgendaDays.dayOfInstant(now, zone)
    val day = if (event.allDay) java.time.Instant.ofEpochMilli(event.start).atZone(zone).toLocalDate() else AgendaDays.dayOfInstant(event.start, zone)
    val time = if (event.allDay) null else DateUtils.formatTimeForDisplay(event.start, zone)
    return when {
        day == today && time != null -> {
            val minutes = (event.start - now) / 60_000
            if (minutes < 60) s.tool("in_minutes").format(minutes.coerceAtLeast(1)) else s.tool("in_hours").format(minutes / 60)
        }
        day == today -> s.tool("day_today").lowercase()
        day == today.plusDays(1) -> listOfNotNull(s.tool("day_tomorrow").lowercase(), time).joinToString(" ")
        else -> listOfNotNull(DateUtils.format(event.start, "EEE d", zone), time).joinToString(" ")
    }
}

/** When an event is, at the start of its line: « 14:30 » today, « Tom. », « Thu 23 ». */
private fun shortWhen(event: AgendaEvent, now: Long, s: StringsContext): String {
    val zone = AgendaToolType.zone()
    val today = AgendaDays.dayOfInstant(now, zone)
    val day = if (event.allDay) java.time.Instant.ofEpochMilli(event.start).atZone(zone).toLocalDate() else AgendaDays.dayOfInstant(event.start, zone)
    return when {
        day <= today -> if (event.allDay) s.tool("all_day_short") else DateUtils.formatTimeForDisplay(event.start, zone)
        day == today.plusDays(1) -> s.tool("day_tomorrow_short")
        else -> DateUtils.format(event.start, "EEE d", zone)
    }
}
