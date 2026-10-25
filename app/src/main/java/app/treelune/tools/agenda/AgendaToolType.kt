package app.treelune.tools.agenda

import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.EntryStart
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolOperation
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.tools.ToolTypeContract
import app.treelune.core.utils.AppConfigManager
import app.treelune.tools.agenda.ui.AgendaScreen
import org.json.JSONObject
import java.text.Normalizer

/**
 * A calendar (docs/TOOLS.md, « Agenda »): events that have a time and pass by themselves — an
 * appointment happens, then it is past, nothing to check. An entry is an event, its timestamp its
 * start; it lasts a duration, or whole days, and is planned, cancelled, or removed (an occurrence
 * of a series only).
 *
 * Its config holds its series, which give real occurrences ahead (AgendaScheduler), and its
 * reminders, relative instants against an event's start, one list for events at a time and one
 * for whole days. Nothing ever waits.
 */
object AgendaToolType : ToolTypeContract {

    object Status {
        const val PLANNED = "planned"
        const val CANCELLED = "cancelled"
        const val REMOVED = "removed"
        val ALL = listOf(PLANNED, CANCELLED, REMOVED)
    }

    // An event's data
    const val DURATION = "duration"
    const val ALL_DAY = "all_day"
    const val DAYS = "days"

    // An event's state
    const val STATUS = EntryStart.STATUS
    const val SERIES = "series"
    const val SERIES_AT = "series_at"
    const val REMINDED_UNTIL = "reminded_until"
    const val REMINDED_FOR = "reminded_for"

    // The config
    const val REMINDERS = "reminders"
    const val ALL_DAY_REMINDERS = "all_day_reminders"
    const val SERIES_LIST = "series"

    private fun s(context: Context) = Strings.`for`(tool = "agenda", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getUsage(context: Context): String = s(context).tool("usage")
    override fun getAIDetail(context: Context): String = s(context).tool("ai_detail")
    override fun getGuideConfig(context: Context): String = s(context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = s(context).tool("guide_screen")
    override fun getFamily() = app.treelune.core.tools.ToolFamily.PLAN

    override fun getDefaultDisplayMode(): String = "EXTENDED"
    override fun getDefaultShowFieldLabels(): Boolean = false
    override fun getDefaultIconName(): String = "calendar"
    override fun getSuggestedIcons(): List<String> = listOf("calendar", "calendar-days", "calendar-clock", "stethoscope", "cake")

    private fun field(name: String, label: String, type: FieldType, description: String? = null, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    /** The units an event's duration is written in: days for an event that runs over several. */
    val DURATION_UNITS = app.treelune.core.fields.DurationUnits.DAY_HOUR_MINUTE.config()

    /** A duration in days, a whole number from one. */
    private val daysConfig = mapOf("min" to 1, "decimals" to 0)

    /**
     * Its reminders, two lists of instants against an event's start, and its series: each a
     * name, a schedule, and a duration or whole days. A series' key is the app's, given when it
     * is created and sent back unchanged.
     */
    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        return listOf(
            SettingNode.Section(s.tool("section_reminders"), listOf(
                SettingNode.Instants(REMINDERS, s.tool("field_reminders"), s.tool("reference_event_start")),
                SettingNode.Instants(ALL_DAY_REMINDERS, s.tool("field_all_day_reminders"), s.tool("reference_event_start")),
                // A reminder notifies, emits an event, or both
                SettingNode.Field(FieldDefinition("notify", s.tool("field_notify"), s.tool("schema_notify"), FieldType.BOOLEAN, false, null), required = true, default = true),
                app.treelune.core.events.EventPoints.node(shared::shared)
            )),
            SettingNode.Section(s.tool("section_series"), listOf(
                SettingNode.ListOf(SERIES_LIST, s.tool("field_series"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field("key", "key", FieldType.TEXT, s.tool("schema_series_key"), mapOf("length" to TextLength.SHORT.name)), systemWritten = true),
                    SettingNode.Field(field("name", s.tool("field_name"), FieldType.TEXT, s.tool("schema_series_name"), mapOf("length" to TextLength.SHORT.name)), required = true),
                    SettingNode.Group(ScheduleSettings.NAME, s.tool("field_series_schedule"), ScheduleSettings.nodes(shared::shared), required = true,
                        description = shared.shared("schedule_description")),
                    SettingNode.Field(field(ALL_DAY, s.tool("field_all_day"), FieldType.BOOLEAN, s.tool("schema_all_day")), default = false),
                    SettingNode.Field(field(DURATION, s.tool("field_duration"), FieldType.DURATION, s.tool("schema_duration"), DURATION_UNITS)),
                    SettingNode.Field(field(DAYS, s.tool("field_days"), FieldType.NUMERIC, s.tool("schema_days"), daysConfig))
                ), description = s.tool("schema_series_element")), summary = listOf("name"), description = s.tool("schema_series_list"))
            ))
        )
    }

    /** A series without its key gets one, from its name, fixed from then on. */
    override fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject {
        val completed = JSONObject(config.toString())
        val list = completed.optJSONArray(SERIES_LIST) ?: return completed
        val all = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        val taken = all.mapNotNull { it.optString("key").takeIf { k -> k.isNotEmpty() } }.toMutableSet()
        all.filter { it.optString("key").isEmpty() }.forEach { series ->
            val base = "s_" + Normalizer.normalize(series.optString("name"), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            var key = base
            var n = 2
            while (key in taken) key = "${base}_${n++}"
            taken.add(key)
            series.put("key", key)
        }
        return completed
    }

    /**
     * What the schema cannot say: a whole day lasts days, an event at a time a duration, never
     * both; and every reminder reads as a relative instant.
     */
    override suspend fun refuseConfig(config: JSONObject, context: Context): String? {
        val s = s(context)
        AgendaSeries.of(config).forEach { series ->
            val raw = config.getJSONArray(SERIES_LIST).let { list -> (0 until list.length()).map { list.getJSONObject(it) }.first { it.optString("key") == series.key } }
            if (series.allDay && raw.has(DURATION)) return s.tool("error_series_all_day_duration").format(series.name)
            if (!series.allDay && raw.has(DAYS)) return s.tool("error_series_days_without_all_day").format(series.name)
        }
        for (allDay in listOf(false, true)) {
            val read = try {
                AgendaRules.reminders(config, allDay) { Strings.`for`(context = context).shared(it) }
            } catch (e: IllegalArgumentException) {
                return e.message
            }
            if (read.any { it is TimePoint.Fixed }) return s.tool("error_reminder_not_relative")
        }
        return null
    }

    /** A whole day lasts days, an event at a time a duration, never both. */
    override fun refuseEntry(data: Map<String, Any?>, state: Map<String, Any?>, context: Context): String? {
        val allDay = data[ALL_DAY] == true
        if (allDay && data[DURATION] != null) return s(context).tool("error_all_day_duration")
        if (!allDay && data[DAYS] != null) return s(context).tool("error_days_without_all_day")
        return null
    }

    /**
     * An event: its name, required; its start, the entry's timestamp, required; its duration, or
     * whole days; its state, the status always there and the marks of its series and reminders.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.REQUIRED,
            data = listOf(
                FixedField(field(DURATION, s.tool("field_duration"), FieldType.DURATION, s.tool("schema_duration"), DURATION_UNITS)),
                FixedField(field(ALL_DAY, s.tool("field_all_day"), FieldType.BOOLEAN, s.tool("schema_all_day"))),
                FixedField(field(DAYS, s.tool("field_days"), FieldType.NUMERIC, s.tool("schema_days"), daysConfig))
            ),
            state = listOf(
                StateField(field(STATUS, s.tool("field_status"), FieldType.CHOICE, s.tool("schema_status"),
                    mapOf("options" to ChoiceSettings.storedOptions(Status.ALL, Status.ALL.associateWith { s.tool("status_$it") }, emptyMap()))), filterable = true),
                StateField(field(SERIES, s.tool("field_series"), FieldType.TEXT, s.tool("schema_series"), mapOf("length" to TextLength.SHORT.name)), filterable = true),
                StateField(field(SERIES_AT, s.tool("field_series_at"), FieldType.DATETIME, s.tool("schema_series_at")), filterable = false),
                StateField(field(REMINDED_UNTIL, s.tool("field_reminded_until"), FieldType.DATETIME, s.tool("schema_reminded_until")), filterable = false, bookkeeping = true),
                StateField(field(REMINDED_FOR, s.tool("field_reminded_for"), FieldType.DATETIME, s.tool("schema_reminded_for")), filterable = false, bookkeeping = true)
            ),
            start = START
        )
    }

    override fun getEntryMeanings(context: Context): app.treelune.core.fields.EntryMeanings {
        val s = Strings.`for`(tool = "agenda", context = context)
        return app.treelune.core.fields.EntryMeanings(
            name = s.tool("entry_name"),
            timestamp = s.tool("entry_timestamp"),
            extra = s.tool("entry_extra"),
            state = s.tool("entry_state")
        )
    }

    /** An event is created planned; cancelling, removing and restoring are the tool's operations. */
    val START = EntryStart(listOf(Status.PLANNED))

    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        val id = SettingNode.Field(field("id", s.tool("field_event"), FieldType.TEXT, Strings.`for`(context = context).shared("tool_operation_entry_id"), mapOf("length" to TextLength.SHORT.name)), required = true)
        return listOf(
            ToolOperation("cancel", s.tool("operation_cancel"), listOf(id)),
            ToolOperation("remove", s.tool("operation_remove"), listOf(id)),
            ToolOperation("restore", s.tool("operation_restore"), listOf(id))
        )
    }

    /** An occurrence of a series is removed, not deleted: its series would give it again. */
    override suspend fun refuseDelete(entry: ToolDataEntity, entries: suspend () -> List<ToolDataEntity>, context: Context): String? =
        if (AgendaEvent.of(entry).series != null) s(context).tool("error_delete_occurrence") else null

    /** The occurrences to come of a series whose times change, or which is gone. */
    override fun entriesRemovedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, context: Context): List<app.treelune.core.tools.RemovedByConfig> {
        val s = s(context)
        return AgendaRules.removedByConfig(previous, config, entries, System.currentTimeMillis()).map { removal ->
            app.treelune.core.tools.RemovedByConfig(removal.entries) {
                s.tool(if (removal.gone) "removed_series_gone" else "removed_series_moved").format(it, removal.series.name)
            }
        }
    }

    /** The occurrences to come taking their series' new name, duration or days, where they still had the former. */
    override fun entriesUpdatedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, context: Context): List<ToolDataEntity> =
        AgendaRules.updatedByConfig(previous, config, entries, System.currentTimeMillis())

    /** A start moved makes its reminders due anew. */
    override suspend fun settleEntries(entries: suspend () -> List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> {
        writtenId ?: return emptyList()
        val written = entries().find { it.id == writtenId } ?: return emptyList()
        // A whole day at the start of its day first, its reminders' mark then read against that start
        val placed = AgendaRules.settleAllDay(written, calendar(zone()))
        val settled = AgendaRules.settleReminded(placed ?: written) ?: placed
        return listOfNotNull(settled)
    }

    override fun getService(context: Context): ExecutableService = AgendaService(context)
    override fun getScheduler(): ToolScheduler = AgendaScheduler

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    /** The zone the calendar's days and reminders are read in. */
    fun zone() = AppConfigManager.getDateTimeConfig().getZoneId()

    /** The app's calendar read in [zone]: the hour its days start at, the first day of its weeks. */
    fun calendar(zone: java.time.ZoneId) = TimeResolver.at(System.currentTimeMillis(), zone)

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntry: app.treelune.core.tools.EntryToOpen?) {
        AgendaScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick,
            openEntryId = (openEntry as? app.treelune.core.tools.EntryToOpen.Existing)?.id)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (app.treelune.core.tools.EntryToOpen) -> Unit): app.treelune.core.tools.ToolTile =
        app.treelune.tools.agenda.ui.rememberAgendaTile(tool, open)
}
