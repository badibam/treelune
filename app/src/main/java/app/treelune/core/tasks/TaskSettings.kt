package app.treelune.core.tasks

import android.content.Context
import app.treelune.core.ai.data.AutomationSettings
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.look.LookSpec
import app.treelune.core.look.Looks
import app.treelune.core.strings.Strings
import app.treelune.core.ui.DisplayMode
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * A Task's settings, declared with the fields: its look, its group, whether its schedule runs it,
 * which of its runs wait for the user, its schedule and what it does about the runs it missed —
 * the routine's envelope (AutomationSettings), without a provider nor an identity, a Task calling
 * no AI, nor an access, its steps saying what they reach. Not its zone (its place) nor its steps (TaskSteps).
 */
object TaskSettings {

    const val SCHEMA_ID = "task_settings"
    const val DEFAULT_ICON = "list-ordered"
    const val DEFAULT_MODE = "EXTENDED"

    /** A Task's look: an icon always; its state from LINE on, its buttons from EXTENDED, its last runs in SQUARE (TaskTile). */
    val LOOK = LookSpec(
        nameKey = "name",
        nameLabel = "label_name",
        schemaPrefix = "task_schema_",
        modes = listOf(DisplayMode.ICON, DisplayMode.MINIMAL, DisplayMode.LINE, DisplayMode.CONDENSED, DisplayMode.EXTENDED, DisplayMode.SQUARE),
        defaultMode = DisplayMode.valueOf(DEFAULT_MODE),
        defaultIcon = DEFAULT_ICON,
        suggestedIcons = listOf("list-ordered", "workflow", "webhook", "cloud-download", "calendar-clock", "moon", "repeat", "plug")
    )

    fun nodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        return Looks.nodes(LOOK, s::shared) + listOf(
            SettingNode.Field(FieldDefinition("group", s.shared("label_group"), s.shared("task_schema_group"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name))),
            SettingNode.Field(FieldDefinition("is_enabled", s.shared("automation_is_enabled"), s.shared("task_schema_is_enabled"), FieldType.BOOLEAN, false, null), default = true),
            // A run ended waits to be read when it failed, by default: one that went well says nothing
            SettingNode.Field(FieldDefinition("notify_end", s.shared("automation_notify_end"), s.shared("task_schema_notify_end"), FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(listOf(AutomationSettings.NOTIFY_ALWAYS, AutomationSettings.NOTIFY_ON_PROBLEM, AutomationSettings.NOTIFY_NEVER), mapOf(
                    AutomationSettings.NOTIFY_ALWAYS to s.shared("automation_notify_end_always"),
                    AutomationSettings.NOTIFY_ON_PROBLEM to s.shared("automation_notify_end_on_problem"),
                    AutomationSettings.NOTIFY_NEVER to s.shared("automation_notify_end_never"))))), default = AutomationSettings.NOTIFY_ON_PROBLEM),
            // Absent: run on demand only
            ScheduleSettings.group(s.shared("automation_schedule"), s::shared),
            // Present with the schedule, and only with it (TaskService)
            SettingNode.Group("catch_up", s.shared("automation_catch_up_title"), AutomationSettings.catchUpNodes(context), description = s.shared("automation_schema_catch_up"))
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(SCHEMA_ID, s.shared("task_display_name"), "", SchemaCategory.TASK_CONFIG, SettingsSchemaGenerator.generate(nodes(context), s::shared).toString())
    }

    /** The settings [task] stores, as their declaration describes them: the form create and update take. */
    fun of(task: TaskEntity): JSONObject = JSONObject().apply {
        put("name", task.name)
        task.description?.let { put(Looks.DESCRIPTION, it) }
        put(Looks.ICON, task.iconName)
        task.iconColor?.let { put(app.treelune.core.themes.IconColor.KEY, it) }
        task.texture?.let { put(app.treelune.core.themes.Texture.KEY, it) }
        put(Looks.DISPLAY_MODE, task.displayMode)
        task.group?.let { put("group", it) }
        put("is_enabled", task.isEnabled)
        put("notify_end", task.notifyEnd)
        task.scheduleJson?.let { put("schedule", JSONObject(it)) }
        task.catchUp?.let { limit ->
            put("catch_up", JSONObject().apply {
                put("limit", limit)
                task.catchUpWindow?.let { put("window", it) }
                put("dismiss_older_instances", task.dismissOlderInstances)
            })
        }
    }
}
