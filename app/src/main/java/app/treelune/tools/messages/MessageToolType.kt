package app.treelune.tools.messages

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.fields.TextLength
import org.json.JSONObject
import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.tools.ToolTypeContract
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.services.ExecutableService
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import app.treelune.core.validation.FieldLimits
import app.treelune.tools.messages.scheduler.MessageScheduler
// import app.treelune.tools.messages.ui.MessagesScreen

/**
 * Messages Tool Type implementation
 *
 * One instance = one notification template. Its successive sends are its occurrences,
 * stored as ordinary tool_data entries — there is no separate execution plane.
 *
 * Architecture:
 * - Config = the template: the invariant part of every send (common_title, priority),
 *   the recurrence that spawns occurrences, and the channel settings.
 * - Data = the occurrences: one entry per send, carrying the part written for that day
 *   (title, content, custom field values) plus, once sent, the invariant part copied in.
 *
 * The two parts compose, they never override each other, so no "which one wins" rule
 * exists anywhere. The invariant part is copied AT SEND TIME and never at creation:
 * a pending occurrence is an intention, not an event, so two competing copies of the
 * template never coexist, and editing the template applies to everything not yet sent.
 *
 * Occurrence lifecycle (data.status):
 * - pending: created ahead of time by the scheduler, holds only its own part
 * - sent: went out, holds the copied invariant part and the send result
 * - expired: its time passed beyond validity_window while the app was off
 * - cancelled: its time arrived while the template was disabled — a decision, not a miss
 */
object MessageToolType : ToolTypeContract {

    // ========================================
    // Metadata
    // ========================================

    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "messages", context = context)
        return s.tool("display_name")
    }

    override fun getGuideConfig(context: Context): String = Strings.`for`(tool = "messages", context = context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = Strings.`for`(tool = "messages", context = context).tool("guide_screen")
    override fun getUsage(context: Context): String {
        val s = Strings.`for`(tool = "messages", context = context)
        return s.tool("usage")
    }

    override fun getDefaultIconName(): String {
        return "bell"
    }

    override fun getSuggestedIcons(): List<String> {
        return listOf("bell", "bell-ring", "message-circle", "alarm-clock", "calendar-clock")
    }

    override fun getFamily() = app.treelune.core.tools.ToolFamily.PLAN

    override fun getDefaultDisplayMode(): String = "LINE"


    override fun getDefaultShowFieldLabels(): Boolean = true

    // ========================================
    // Schemas (SchemaProvider interface)
    // ========================================

    /**
     * Creates messages configuration schema — the template itself.
     *
     * Holds the invariant part of every send (common_title, priority), the recurrence
     * that spawns occurrences, and the two knobs that govern their lifecycle.
     *
     * common_title is deliberately distinct from the base "name": name is how you find
     * the instance in its zone, common_title is what shows up on the lock screen. Nothing
     * forces them to match, and forcing it would be paid for later. Optional — when absent
     * it contributes nothing and the notification carries only the occurrence's own title.
     *
     * priority lives here and ONLY here. It describes the channel — how insistently this
     * stream is allowed to interrupt — not the individual send. Keeping a copy on the
     * occurrence too would recreate the default/override pair this design rules out.
     *
     * schedule is optional: without it nothing fires on its own and the instance is a pure
     * notification channel, fed on demand by the user or the AI.
     */
    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(tool = "messages", context = context)
        val shared = Strings.`for`(context = context)
        fun field(name: String, type: FieldType, required: Boolean = false, default: Any? = null, config: Map<String, Any>? = null) =
            SettingNode.Field(FieldDefinition(name, s.tool("field_$name"), s.tool("schema_config_$name"), type, false, config),
                required = required, default = default)
        val priorities = listOf("default", "high", "low")
        return listOf(
            // What every send carries: a reminder whose text never varies needs nothing more
            SettingNode.Section(s.tool("section_common"), listOf(
                field("common_title", FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)),
                field("common_content", FieldType.TEXT, config = mapOf("length" to TextLength.LONG.name))
            )),
            // How the stream is allowed to reach the user
            SettingNode.Section(s.tool("section_channel"), listOf(
                field("enabled", FieldType.BOOLEAN, default = true),
                field("priority", FieldType.CHOICE, default = "default",
                    config = mapOf("options" to ChoiceSettings.storedOptions(priorities, priorities.associateWith { s.tool("priority_$it") }))),
                field("external_notifications", FieldType.BOOLEAN, default = true)
            )),
            // The recurrence and the life of the occurrences it creates
            SettingNode.Section(s.tool("section_schedule"), listOf(
                // Absent: nothing fires on its own, the tool is a channel fed on demand
                ScheduleSettings.group(s.tool("field_schedule"), shared::shared),
                // How far ahead occurrences are created, and how long a missed one may still go out
                field("creation_horizon", FieldType.DURATION, default = 2 * 86_400_000L,
                    config = app.treelune.core.fields.DurationUnits.DAY.config()),
                field("validity_window", FieldType.DURATION, default = 3_600_000L,
                    config = app.treelune.core.fields.DurationUnits.DAY_HOUR_MINUTE.config())
            ))
        )
    }

    /** Send now: an occurrence due at once, which the scheduler sends as it sends the others. */
    override fun getOperations(context: Context): List<app.treelune.core.tools.ToolOperation> {
        val s = Strings.`for`(tool = "messages", context = context)
        fun param(name: String, length: TextLength) = SettingNode.Field(FieldDefinition(
            name, s.tool("field_$name"), s.tool("schema_data_$name"), FieldType.TEXT, false,
            mapOf("length" to length.name)))
        return listOf(app.treelune.core.tools.ToolOperation(
            name = "execute",
            description = s.tool("operation_execute"),
            params = listOf(param("title", TextLength.SHORT), param("content", TextLength.LONG))
        ))
    }

    /**
     * One occurrence of a message: one send.
     *
     * Its timestamp is the due time, not its creation time: time is the only ordering axis
     * tool_data has, and the moment the entry was written stays in updated_at.
     *
     * Data holds what the occurrence says: its own title and content, written beforehand, and
     * the template's common part and priority, copied when it goes out. The copy is a fact,
     * since what went out does not change when the template does.
     *
     * State holds what the app and the user's actions produce on it: its status, whether the
     * notification went out, read, archived, and what created it.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "messages", context = context)
        fun field(name: String, type: FieldType, config: Map<String, Any>? = null) = FieldDefinition(
            name = name,
            displayName = s.tool("field_$name"),
            description = s.tool("schema_data_$name"),
            type = type,
            alwaysVisible = false,
            config = config
        )
        fun choice(vararg options: Pair<String, String>) = mapOf(
            "options" to app.treelune.core.fields.ChoiceSettings.storedOptions(options.map { it.first }, labels = options.toMap())
        )
        fun labels(trueLabel: String, falseLabel: String) = mapOf("true_label" to trueLabel, "false_label" to falseLabel)

        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.OPTIONAL,
            data = listOf(
                FixedField(field("title", FieldType.TEXT, mapOf("length" to TextLength.SHORT.name))),
                FixedField(field("content", FieldType.TEXT, mapOf("length" to TextLength.LONG.name))),
                FixedField(field("common_title", FieldType.TEXT, mapOf("length" to TextLength.SHORT.name))),
                FixedField(field("common_content", FieldType.TEXT, mapOf("length" to TextLength.LONG.name))),
                FixedField(field("priority", FieldType.CHOICE, choice(
                    "default" to s.tool("priority_default"),
                    "high" to s.tool("priority_high"),
                    "low" to s.tool("priority_low")
                )))
            ),
            state = listOf(
                StateField(field("status", FieldType.CHOICE, choice(
                    "pending" to s.tool("status_pending"),
                    "sent" to s.tool("status_sent"),
                    "expired" to s.tool("status_expired"),
                    "cancelled" to s.tool("status_cancelled")
                )), filterable = true),
                StateField(field("notification_sent", FieldType.BOOLEAN, labels(s.tool("notification_sent_true"), s.tool("status_notification_failed"))), filterable = true),
                StateField(field("read", FieldType.BOOLEAN, labels(s.tool("filter_read"), s.tool("filter_unread"))), filterable = true),
                StateField(field("archived", FieldType.BOOLEAN, labels(s.tool("filter_archived"), s.tool("not_archived"))), filterable = true),
                StateField(field("triggered_by", FieldType.CHOICE, choice(
                    "SCHEDULE" to s.tool("triggered_by_schedule"),
                    "MANUAL" to s.tool("triggered_by_manual")
                )), filterable = true)
            ),
            start = START
        )
    }

    override fun getEntryMeanings(context: Context): app.treelune.core.fields.EntryMeanings {
        val s = Strings.`for`(tool = "messages", context = context)
        return app.treelune.core.fields.EntryMeanings(
            name = s.tool("entry_name"),
            timestamp = s.tool("entry_timestamp"),
            data = s.tool("entry_data"),
            extra = s.tool("entry_extra"),
            state = s.tool("entry_state")
        )
    }

    /**
     * A message written by someone, the AI or a screen, is one to send at its time, the same as
     * one the recurrence creates but placed by hand: the scheduler sends it when it is due.
     */
    val START = app.treelune.core.fields.EntryStart(listOf("pending")) { status, _ ->
        JSONObject().put(app.treelune.core.fields.EntryStart.STATUS, status).put("triggered_by", "MANUAL")
    }

    // ========================================
    // UI
    // ========================================

    @Composable
    override fun rememberTile(tool: app.treelune.core.database.entities.ToolInstance, open: (app.treelune.core.tools.EntryToOpen) -> Unit): app.treelune.core.tools.ToolTile =
        app.treelune.tools.messages.ui.rememberMessagesTile(tool, open)

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: app.treelune.core.tools.EntryToOpen?
    ) {
        app.treelune.tools.messages.ui.MessagesScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick,
            openEntryId = (openEntry as? app.treelune.core.tools.EntryToOpen.Existing)?.id
        )
    }

    // ========================================
    // Discovery pattern
    // ========================================

    /** A message sent and not read yet waits. */
    override fun getWaiting(config: JSONObject): List<JSONObject> = listOf(
        app.treelune.core.conditions.Conditions.onField("state.status", "in", listOf("sent")),
        app.treelune.core.conditions.Conditions.onField("state.read", "=", false)
    )

    /**
     * The sends prepared by hand that a change of the recurrence deletes: a pending occurrence
     * the recurrence made, still to come, holding what was written for it, and no longer at a time
     * the new recurrence gives within its horizon. The scheduler's next pass would delete it with
     * what it holds (MessageScheduler.reconcilePending); said here, the change waits for the
     * user's agreement, told the times and how many prepared sends are kept. An empty one goes
     * unsaid: the recurrence makes it again, nothing of it is lost. A suspended template or an
     * unreadable recurrence deletes nothing, so neither does this.
     */
    override fun entriesRemovedByConfig(previous: JSONObject, config: JSONObject, entries: List<ToolDataEntity>, context: Context): List<app.treelune.core.tools.RemovedByConfig> {
        val settings = app.treelune.core.tools.ToolConfigSettings.read(this, config, context)
        if (!settings.boolean("enabled")) return emptyList()
        val schedule = when (val stored = app.treelune.core.utils.StoredSchedule.of(config)) {
            is app.treelune.core.utils.StoredSchedule.Unreadable -> return emptyList()
            is app.treelune.core.utils.StoredSchedule.Readable -> stored.schedule
            app.treelune.core.utils.StoredSchedule.None -> null
        }
        val horizon = settings.number("creation_horizon")?.toLong() ?: 0L
        if (horizon <= 0) return emptyList()
        val now = System.currentTimeMillis()
        val expected = schedule?.let { MessageScheduler.expectedOccurrences(it, now, now + horizon, entries.firstOrNull()?.toolInstanceId.orEmpty()) }.orEmpty().toSet()

        val prepared = entries.filter { entry ->
            val state = entry.state?.let { JSONObject(it) } ?: return@filter false
            val due = entry.timestamp ?: return@filter false
            state.optString("status") == "pending" && state.optString("triggered_by") == "SCHEDULE" && due > now &&
                (JSONObject(entry.data).length() > 0 || !entry.extra.isNullOrEmpty() && JSONObject(entry.extra).length() > 0)
        }
        val (kept, removed) = prepared.partition { it.timestamp in expected }
        if (removed.isEmpty()) return emptyList()
        val s = Strings.`for`(tool = "messages", context = context)
        val times = removed.sortedBy { it.timestamp }.joinToString(", ") { app.treelune.core.utils.DateUtils.formatFullDateTime(it.timestamp!!) }
        return listOf(app.treelune.core.tools.RemovedByConfig(removed) { count ->
            s.tool("removed_prepared").format(count, times, kept.size)
        })
    }

    override fun getService(context: Context): ExecutableService {
        return MessageService(context)
    }

    override fun getDatabaseEntities(): List<Class<*>> {
        // Messages uses unified ToolDataEntity (no custom entity)
        return listOf(ToolDataEntity::class.java)
    }

    // ========================================
    // Scheduling
    // ========================================

    override fun getScheduler(): ToolScheduler {
        return MessageScheduler
    }
}
