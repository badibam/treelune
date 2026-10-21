package app.treelune.tools.agenda

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.utils.DateUtils
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.StoredSchedule
import org.json.JSONObject

/**
 * The calendars' work in time (docs/TOOLS.md, « Agenda »): each series gives its occurrences up
 * to 90 days ahead, the next one always (AgendaRules.timesToGive), never before the last one it
 * gave; each event still planned sends its reminders when they come, one notification for those
 * due together, late too while the event is not over. Its next reminder is the core's next
 * action (nextAction), the app woken then rather than at its next 10-minute ring.
 */
object AgendaScheduler : ToolScheduler {

    /** One pass at a time: two overlapping passes would give the same occurrences twice. */
    private val passLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun checkScheduled(context: Context) {
        if (!passLock.tryLock()) return
        try {
            val coordinator = Coordinator(context)
            for (tool in calendars(coordinator)) {
                // One calendar that fails must not stop the others
                try {
                    pass(context, coordinator, tool)
                } catch (e: Exception) {
                    LogManager.service("AgendaScheduler: calendar ${tool.id} not handled: ${e.message}", "ERROR", e)
                }
            }
        } finally {
            passLock.unlock()
        }
    }

    override suspend fun nextAction(context: Context): Long? {
        val coordinator = Coordinator(context)
        val now = System.currentTimeMillis()
        val zone = AgendaToolType.zone()
        val text = Strings.`for`(context = context)::shared
        return calendars(coordinator).mapNotNull { tool ->
            val events = events(coordinator, tool.id) ?: return@mapNotNull null
            events.mapNotNull { event ->
                val reminders = try { AgendaRules.reminders(tool.config, event.allDay, text) } catch (e: IllegalArgumentException) { return@mapNotNull null }
                AgendaRules.nextReminder(event, AgendaRules.reminderTimes(event, reminders, ::resolve), now, AgendaToolType.calendar(zone))
            }.minOrNull()
        }.minOrNull()
    }

    private class Calendar(val id: String, val config: JSONObject)

    private suspend fun calendars(coordinator: Coordinator): List<Calendar> {
        val result = coordinator.processUserAction("tools.list_all", mapOf("include_config" to true))
        if (!result.isSuccess) {
            LogManager.service("AgendaScheduler: tools not listed: ${result.error}", "ERROR")
            return emptyList()
        }
        @Suppress("UNCHECKED_CAST")
        return (result.data?.get("tool_instances") as? List<Map<String, Any?>>).orEmpty()
            .filter { it["tooltype"] == "agenda" }
            .mapNotNull { tool -> (tool["config"] as? Map<String, Any?>)?.let { Calendar(tool["id"] as String, JsonUtils.toJSONObject(it)) } }
    }

    /** Every event of a calendar, or null when they do not read (logged). */
    private suspend fun events(coordinator: Coordinator, toolInstanceId: String): List<AgendaEvent>? {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) {
            LogManager.service("AgendaScheduler: events of $toolInstanceId not read: ${result.error}", "ERROR")
            return null
        }
        return (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { AgendaEvent.of(it) }
    }

    /** A reminder against the start of its event, on the user's calendar. */
    private fun resolve(reference: Long, point: TimePoint): Long = TimeResolver.at(reference).instant(point)

    private suspend fun pass(context: Context, coordinator: Coordinator, tool: Calendar) {
        val now = System.currentTimeMillis()
        val zone = AgendaToolType.zone()
        val events = events(coordinator, tool.id) ?: return
        giveOccurrences(coordinator, tool, events, now, zone)
        sendReminders(context, coordinator, tool, events, now, zone)
    }

    /** The occurrences each series owes at [now], created as the app, planned and marked with their series. */
    private suspend fun giveOccurrences(coordinator: Coordinator, tool: Calendar, events: List<AgendaEvent>, now: Long, zone: java.time.ZoneId) {
        for (series in AgendaSeries.of(tool.config)) {
            val pattern = when (val stored = AgendaRules.pattern(series.schedule)) {
                StoredSchedule.None -> continue
                is StoredSchedule.Unreadable -> {
                    LogManager.service("AgendaScheduler: schedule of series ${series.key} of ${tool.id} unreadable (${stored.cause}), nothing given", "ERROR")
                    continue
                }
                is StoredSchedule.Readable -> stored.schedule.pattern
            }
            val own = events.filter { it.series == series.key }
            val times = AgendaRules.timesToGive(pattern, own.mapNotNull { it.seriesAt }.maxOrNull(), own.any { it.start > now }, now, zone)
            for (time in times) {
                val data = mutableMapOf<String, Any>()
                if (series.allDay) {
                    data[AgendaToolType.ALL_DAY] = true
                    data[AgendaToolType.DAYS] = series.days
                } else series.duration?.let { data[AgendaToolType.DURATION] = it }
                val created = coordinator.processUserAction("tool_data.create", mapOf(
                    "tool_instance_id" to tool.id,
                    "name" to series.name,
                    "timestamp" to series.startOf(time, AgendaToolType.calendar(zone)),
                    "data" to data,
                    "state" to mapOf(
                        AgendaToolType.STATUS to AgendaToolType.Status.PLANNED,
                        AgendaToolType.SERIES to series.key,
                        AgendaToolType.SERIES_AT to time
                    )
                ))
                if (!created.isSuccess) {
                    LogManager.service("AgendaScheduler: occurrence of ${series.key} at $time not created: ${created.error}", "ERROR")
                    break
                }
            }
        }
    }

    /**
     * One notification per event whose reminders came — the event's name, its moment as its
     * text — and the latest of them marked sent, for the start they were sent for.
     */
    private suspend fun sendReminders(context: Context, coordinator: Coordinator, tool: Calendar, events: List<AgendaEvent>, now: Long, zone: java.time.ZoneId) {
        val text = Strings.`for`(context = context)::shared
        val s = Strings.`for`(tool = "agenda", context = context)
        for (event in events) {
            val reminders = try {
                AgendaRules.reminders(tool.config, event.allDay, text)
            } catch (e: IllegalArgumentException) {
                LogManager.service("AgendaScheduler: reminders of ${tool.id} unreadable: ${e.message}", "ERROR")
                return
            }
            val due = AgendaRules.dueReminders(event, AgendaRules.reminderTimes(event, reminders, ::resolve), now, AgendaToolType.calendar(zone))
            if (due.isEmpty()) continue
            val sent = coordinator.processUserAction("notifications.send", mapOf(
                "title" to event.name,
                "content" to momentOf(event, s),
                "priority" to "high",
                "icon_name" to tool.config.optString("icon_name").ifEmpty { AgendaToolType.getDefaultIconName() },
                "tool_instance_id" to tool.id,
                "entry_id" to event.id
            ))
            if (!sent.isSuccess) LogManager.service("AgendaScheduler: reminder of ${event.id} failed: ${sent.error}", "WARN")
            // Marked even when the notification failed: retried at every pass, it would come late and repeated
            val marked = coordinator.processUserAction("tool_data.update", mapOf(
                "id" to event.id,
                "state" to mapOf(AgendaToolType.REMINDED_UNTIL to due.max(), AgendaToolType.REMINDED_FOR to event.start)
            ))
            if (!marked.isSuccess) LogManager.service("AgendaScheduler: ${event.id} not marked reminded: ${marked.error}", "ERROR")
        }
    }

    /** When an event takes place, in words: « jeudi 23 octobre, 14:30 – 15:15 », « jeudi 23 octobre, journée ». */
    fun momentOf(event: AgendaEvent, s: app.treelune.core.strings.StringsContext): String {
        val zone = AgendaToolType.zone()
        val day = DateUtils.format(event.start, "EEEE d MMMM", zone)
        return when {
            event.allDay && event.days > 1 -> s.tool("moment_days").format(day, event.days)
            event.allDay -> s.tool("moment_all_day").format(day)
            event.duration != null -> s.tool("moment_range").format(day, DateUtils.formatTimeForDisplay(event.start, zone), DateUtils.formatTimeForDisplay(event.start + event.duration, zone))
            else -> s.tool("moment_at").format(day, DateUtils.formatTimeForDisplay(event.start, zone))
        }
    }
}
