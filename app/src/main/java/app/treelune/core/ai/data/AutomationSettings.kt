package app.treelune.core.ai.data

import android.content.Context
import app.treelune.core.ai.database.AutomationEntity
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * An automation's settings, declared with the fields (docs/DATA.md): its look (its name, a
 * description, an icon, a colour, a texture, a display mode; Looks), the provider it runs with, its group, whether it is on, which of its runs wait for the user,
 * its schedule, and what it does about the runs it missed. Not its zone (its place), its seed session (its message) nor its history.
 *
 * AutomationService checks every write against the schema generated from here, on the settings
 * as they are about to be stored.
 */
object AutomationSettings {

    /** Catch-up choices: missed runs are caught up to a delay, or whatever their delay. */
    const val LIMITED = "limited"
    const val UNLIMITED = "unlimited"

    /**
     * When a run waits for the user to read it (docs/AI.md, « Summary of the run »), stored under
     * "notify_end": every run, a run with a problem (NOTIFY_ON_PROBLEM, RunEnd.problem), none. Its
     * summary is kept whatever the choice.
     */
    const val NOTIFY_ALWAYS = "always"
    const val NOTIFY_ON_PROBLEM = "on_problem"
    const val NOTIFY_NEVER = "never"

    const val SCHEMA_ID = "automation_settings"

    const val DEFAULT_ICON = "bot"
    const val DEFAULT_MODE = "EXTENDED"

    /** Where the icon picker starts for a routine. */
    val SUGGESTED_ICONS = listOf("bot", "sparkles", "repeat", "alarm-clock", "calendar-clock", "sunrise", "moon", "newspaper", "clipboard-check", "brain")

    /**
     * A routine's look (Looks): an icon always, `bot` until one is chosen; its tile among its
     * section's tools from ICON to SQUARE, its state, its switch and its buttons from LINE on,
     * its last runs in SQUARE (AutomationTile).
     */
    val LOOK = app.treelune.core.look.LookSpec(
        nameKey = "name",
        nameLabel = "label_name",
        schemaPrefix = "automation_schema_",
        modes = listOf(app.treelune.core.ui.DisplayMode.ICON, app.treelune.core.ui.DisplayMode.MINIMAL, app.treelune.core.ui.DisplayMode.LINE,
            app.treelune.core.ui.DisplayMode.CONDENSED, app.treelune.core.ui.DisplayMode.EXTENDED, app.treelune.core.ui.DisplayMode.SQUARE),
        defaultMode = app.treelune.core.ui.DisplayMode.valueOf(DEFAULT_MODE),
        defaultIcon = DEFAULT_ICON,
        suggestedIcons = SUGGESTED_ICONS
    )

    /** The provider its runs call (AIProviderConfigEntity.id). */
    const val PROVIDER_ID = "provider_id"

    /**
     * The identity setting's key. A creation gives it, null for the app's own AI: the screen
     * shows it preselected with the zone's, and an AI, which sees no preselection, says it.
     */
    const val IDENTITY_ID = "identity_id"

    /**
     * What a scheduled automation does about the runs it missed, stored under "catch_up": the
     * choice is required, with no default -- an hour and several weeks are equally common --, and a
     * limited catch-up says its delay.
     */
    fun catchUpNodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val limits = listOf(LIMITED, UNLIMITED)
        return listOf(
            SettingNode.Variant(
                selector = SettingNode.Field(FieldDefinition("limit", s.shared("automation_catch_up_window"), s.shared("automation_schema_catch_up_limit"), FieldType.CHOICE, false,
                    mapOf("options" to ChoiceSettings.storedOptions(limits, mapOf(
                        LIMITED to s.shared("automation_catch_up_limited"),
                        UNLIMITED to s.shared("automation_catch_up_unlimited"))))), required = true),
                cases = mapOf(
                    LIMITED to listOf(SettingNode.Field(FieldDefinition("window", s.shared("automation_catch_up_delay"), s.shared("automation_schema_catch_up_window"), FieldType.DURATION, false,
                        app.treelune.core.fields.DurationUnits.DAY_HOUR_MINUTE.config()), required = true)),
                    UNLIMITED to emptyList()
                ),
                descriptions = mapOf(LIMITED to s.shared("automation_schema_catch_up_limited"), UNLIMITED to s.shared("automation_schema_catch_up_unlimited"))
            ),
            SettingNode.Field(FieldDefinition("dismiss_older_instances", s.shared("automation_catch_up_run_every"), s.shared("automation_schema_dismiss_older"), FieldType.BOOLEAN, false,
                mapOf("true_label" to s.shared("automation_catch_up_latest_only"), "false_label" to s.shared("automation_catch_up_every_occurrence"))),
                default = false)
        )
    }

    fun nodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        return app.treelune.core.look.Looks.nodes(LOOK, s::shared) + listOf(
            // One of the providers the user created, absent for none: the routine is then neither
            // on nor run; checked to exist by the service, as the identity
            SettingNode.Field(FieldDefinition(PROVIDER_ID, s.shared("label_ai_provider"), s.shared("automation_schema_provider_id"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name))),
            // The identity its runs speak as, absent for the app's own AI; checked to exist by the service
            SettingNode.Field(FieldDefinition(IDENTITY_ID, s.shared("label_identity"), s.shared("automation_schema_identity_id"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name))),
            // One of the tool groups of the automation's zone
            SettingNode.Field(FieldDefinition("group", s.shared("label_group"), s.shared("automation_schema_group"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name))),
            SettingNode.Field(FieldDefinition("is_enabled", s.shared("automation_is_enabled"), s.shared("automation_schema_is_enabled"), FieldType.BOOLEAN, false, null), default = true),
            // A new routine's runs all wait: one wants to see what a routine just made does
            SettingNode.Field(FieldDefinition("notify_end", s.shared("automation_notify_end"), s.shared("automation_schema_notify_end"), FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(listOf(NOTIFY_ALWAYS, NOTIFY_ON_PROBLEM, NOTIFY_NEVER), mapOf(
                    NOTIFY_ALWAYS to s.shared("automation_notify_end_always"),
                    NOTIFY_ON_PROBLEM to s.shared("automation_notify_end_on_problem"),
                    NOTIFY_NEVER to s.shared("automation_notify_end_never"))))), default = NOTIFY_ALWAYS),
            // Absent: the automation runs on demand only
            ScheduleSettings.group(s.shared("automation_schedule"), s::shared),
            // Present with the schedule, and only with it (AutomationService)
            SettingNode.Group("catch_up", s.shared("automation_catch_up_title"), catchUpNodes(context), description = s.shared("automation_schema_catch_up")),
            // What starts it beside its schedule: an entry written
            app.treelune.core.triggers.Triggers.node(s::shared)
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("automation_display_name"),
            description = "",
            category = SchemaCategory.AUTOMATION_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(context), s::shared).toString()
        )
    }

    /** The settings [entity] stores, as their declaration describes them: the form create and update take. */
    fun of(entity: AutomationEntity): JSONObject = JSONObject().apply {
        put("name", entity.name)
        entity.description?.let { put(app.treelune.core.look.Looks.DESCRIPTION, it) }
        put(app.treelune.core.look.Looks.ICON, entity.iconName)
        entity.iconColor?.let { put(app.treelune.core.themes.IconColor.KEY, it) }
        entity.texture?.let { put(app.treelune.core.themes.Texture.KEY, it) }
        put(app.treelune.core.look.Looks.DISPLAY_MODE, entity.displayMode)
        entity.providerId?.let { put(PROVIDER_ID, it) }
        entity.identityId?.let { put(IDENTITY_ID, it) }
        entity.group?.let { put("group", it) }
        put("is_enabled", entity.isEnabled)
        put("notify_end", entity.notifyEnd)
        entity.scheduleJson?.let { put("schedule", JSONObject(it)) }
        org.json.JSONArray(entity.triggersJson).takeIf { it.length() > 0 }?.let { put(app.treelune.core.triggers.Triggers.KEY, it) }
        entity.catchUp?.let { limit ->
            put("catch_up", JSONObject().apply {
                put("limit", limit)
                entity.catchUpWindow?.let { put("window", it) }
                put("dismiss_older_instances", entity.dismissOlderInstances)
            })
        }
    }
}
