package app.treelune.core.config

import android.content.Context
import app.treelune.core.database.entities.AppSettingCategories
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingValues
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * The app's settings, declared with the fields by category (docs/DATA.md): the
 * schema AppConfigService checks every write of a category against, the settings screen, and the
 * reading of a category all come from here.
 */
object AppSettings {

    /** The bounds of the relative label limits */
    val HOUR_LIMIT_RANGE = 1..24
    val DAY_LIMIT_RANGE = 1..30
    val WEEK_LIMIT_RANGE = 1..12
    val MONTH_LIMIT_RANGE = 1..24
    val YEAR_LIMIT_RANGE = 1..10

    /** The bounds of the AI limits: at least one call, and below a runaway, since each call is paid for */
    val AI_LIMITS_CHAT_RANGE = 1..50
    val AI_LIMITS_AUTOMATION_RANGE = 1..100
    val CHAIN_RANGE = IntProgression.fromClosedRange(10, 500, 10)

    /** The data size thresholds, in characters, and their step */
    val AI_DATA_CHAT_RANGE = IntProgression.fromClosedRange(5_000, 100_000, 5_000)
    val AI_DATA_AUTOMATION_RANGE = IntProgression.fromClosedRange(10_000, 500_000, 10_000)

    private val WEEK_DAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    /** The regions of the time zones offered, those of a place ("Europe/Lisbon"), not the old aliases ("US/Pacific", "EST"). */
    private val ZONE_REGIONS = Regex("^(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+")

    /**
     * Every time zone the phone's data knows by its place, and UTC, each shown with its offset now
     * ("Europe/Lisbon (UTC+01:00)"): the list follows the zone updates Android receives.
     */
    fun timezones(): Pair<List<String>, Map<String, String>> {
        val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        val ids = java.time.ZoneId.getAvailableZoneIds().filter { ZONE_REGIONS.matches(it) }.sorted() + "UTC"
        return ids to ids.associateWith { id ->
            val offset = java.time.ZoneId.of(id).rules.getOffset(now)
            "$id (UTC${if (offset == java.time.ZoneOffset.UTC) "" else offset.id})"
        }
    }

    /**
     * Every language of a country the phone knows ("pt-PT"), named in itself ("Português (Portugal)"),
     * by that name.
     */
    fun locales(): Pair<List<String>, Map<String, String>> {
        val locales = java.util.Locale.getAvailableLocales()
            .filter { it.language.isNotEmpty() && it.country.length == 2 && it.script.isEmpty() && it.variant.isEmpty() }
            .distinctBy { it.toLanguageTag() }
        val labels = locales.associate { locale ->
            locale.toLanguageTag() to "${locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }} — ${locale.toLanguageTag()}"
        }
        return labels.keys.sortedBy { labels.getValue(it).lowercase() } to labels
    }
    private val DATE_PATTERNS = listOf("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd")

    /** The categories a settings screen edits, each with its declaration. */
    val CATEGORIES = listOf(
        AppSettingCategories.FORMAT,
        AppSettingCategories.AI_LIMITS,
        AppSettingCategories.VALIDATION_CONFIG,
        AppSettingCategories.MAIN_SCREEN,
        AppSettingCategories.DEMO,
        AppSettingCategories.UI,
        AppSettingCategories.EXTERNAL_ACCESS,
        AppSettingCategories.GUIDE,
        AppSettingCategories.DATA
    )

    /**
     * The categories the AI reads and writes (APP_CONFIG, UPDATE_APP_CONFIG; docs/AI.md, « Levels »),
     * the app's level of validation guarding the writes: the home screen's groups alone. Never one
     * holding a secret, nor the look, which is the user's to see.
     */
    val OPEN_TO_AI = listOf(AppSettingCategories.MAIN_SCREEN)

    /** The Guide's progress (GuideProgress): the first-launch screen seen, the band hidden, the tutorial in progress, each chapter's step. */
    const val GUIDE_WELCOME_SEEN = "welcome_seen"
    const val GUIDE_BAND_HIDDEN = "band_hidden"
    const val GUIDE_CURRENT = "current"
    const val GUIDE_CHAPTERS = "chapters"

    /**
     * The folder of the phone chosen once (Android's document tree, its address), where the app
     * writes without asking; absent while none is chosen. Its right is this phone's: restored
     * elsewhere, the address stays and the folder is to choose again.
     */
    const val DATA_FOLDER = "folder"
    /** Whether the automatic backup writes into that folder (AutoBackup) */
    const val DATA_AUTO_BACKUP = "auto_backup"
    /** The case of the automatic backup last looked at in Settings › Data (AutoBackup.seen) */
    const val DATA_SEEN = "seen"
    /** How many days the history of writes keeps its steps (docs/DATA.md, « History of writes », HistoryDepth) */
    const val DATA_HISTORY_DAYS = "history_days"
    /** How many megabytes the history of writes takes at most, its latest step kept whatever its size */
    const val DATA_HISTORY_MEGABYTES = "history_megabytes"

    /** How an outside AI reaches the app: through its own Tailscale node, or through a relay (docs/EXTERNAL_ACCESS.md, « The Tailscale node »). */
    const val ACCESS_MODE = "access_mode"
    const val ACCESS_MODE_TAILSCALE = "TAILSCALE"
    const val ACCESS_MODE_RELAY = "RELAY"

    /** The relay's public address, under which an outside AI reaches the app (https). */
    const val RELAY_URL = "relay_url"

    /** The secret the relay knows the app by. */
    const val RELAY_SECRET = "relay_secret"

    /**
     * The connector's data threshold, in characters: its own, the size of a read weighing on the
     * outside AI's context and subscription, never on a key of the app's paid by the call.
     */
    const val MCP_MAX_DATA_CHARS = "max_data_chars"
    val MCP_DATA_RANGE = IntProgression.fromClosedRange(10_000, 500_000, 10_000)
    const val MCP_MAX_DATA_CHARS_DEFAULT = 200_000

    /** The setting under which the theme's interface sounds play. */
    const val UI_SOUNDS = "sounds"

    /** The interface's theme, by its id. */
    const val UI_THEME = "theme"

    /** Light, dark, or the phone's (AppearanceMode). */
    const val UI_MODE = "mode"

    /** Degrees the theme's colours are turned round the hue circle (Appearance.hueShift). */
    const val UI_HUE_SHIFT = "hue_shift"

    /** The hue shifts: a whole turn, by degree. */
    val HUE_SHIFT_RANGE = 0..359

    /** The history of writes' depth: days, and megabytes */
    val HISTORY_DAYS_RANGE = 1..365
    val HISTORY_MEGABYTES_RANGE = IntProgression.fromClosedRange(100, 5_000, 100)

    /** How many whole steps the interface's size is moved by. */
    const val UI_SIZE_STEP = "size_step"

    /** The size steps: the theme's own, then three larger. */
    val SIZE_STEP_RANGE = 0..3

    /** Whether the grids show in one column, for a narrow screen (Appearance.oneColumn). */
    const val UI_ONE_COLUMN = "one_column"

    /** How strongly the motifs of tiles and page headers show (Appearance.textureIntensity). */
    const val UI_TEXTURE_INTENSITY = "texture_intensity"

    /** Whether the home screen shows the app's name and mark under its buttons (Appearance.homeTitle). */
    const val UI_HOME_TITLE = "home_title"

    fun nodes(category: String, context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val text = s::shared
        return when (category) {
            AppSettingCategories.FORMAT -> listOf(
                // Absent, the time zone and the locale are the phone's
                SettingNode.Section(text("settings_format_timezone"), listOf(
                    timezones().let { (zones, labels) -> choice("timezone_override", text("app_config_format_timezone_override"), text("app_config_schema_format_timezone_override"), zones, labels) }
                )),
                // How numbers and dates are written: the convention first, then what it leaves to choose
                SettingNode.Section(text("settings_format_locale_section"), listOf(
                    locales().let { (locales, labels) -> choice("locale_override", text("app_config_format_locale_override"), text("app_config_schema_format_locale_override"), locales, labels) },
                    // Absent, the phone's habit
                    field("use_24_hour_format", text("settings_format_24h"), text("app_config_schema_format_use_24_hour_format"), FieldType.BOOLEAN),
                    choice("date_format_pattern", text("app_config_format_date_format_pattern"), text("app_config_schema_format_date_format_pattern"), DATE_PATTERNS),
                    choice("time_separator", text("app_config_format_time_separator"), text("app_config_schema_format_time_separator"), listOf(":", "h"), required = true)
                )),
                SettingNode.Section(text("settings_format_business_section"), listOf(
                    scale("day_start_hour", text("app_config_format_day_start_hour"), text("app_config_schema_format_day_start_hour"), 0..23),
                    choice("week_start_day", text("app_config_format_week_start_day"), text("app_config_schema_format_week_start_day"), WEEK_DAYS,
                        labels = WEEK_DAYS.mapIndexed { i, day -> day to text("day_of_week_${i + 1}") }.toMap(), required = true)
                )),
                SettingNode.Group("relative_label_limits", text("settings_format_relative_section"), required = true,
                    description = text("app_config_schema_relative_label_limits"), nodes = listOf(
                    scale("hour_limit", text("app_config_format_hour_limit"), text("app_config_schema_format_hour_limit"), HOUR_LIMIT_RANGE),
                    scale("day_limit", text("app_config_format_day_limit"), text("app_config_schema_format_day_limit"), DAY_LIMIT_RANGE),
                    scale("week_limit", text("app_config_format_week_limit"), text("app_config_schema_format_week_limit"), WEEK_LIMIT_RANGE),
                    scale("month_limit", text("app_config_format_month_limit"), text("app_config_schema_format_month_limit"), MONTH_LIMIT_RANGE),
                    scale("year_limit", text("app_config_format_year_limit"), text("app_config_schema_format_year_limit"), YEAR_LIMIT_RANGE)
                ))
            )
            AppSettingCategories.AI_LIMITS -> listOf(
                scale("chat_max_autonomous_roundtrips", text("app_config_ai_limits_chat"), text("settings_ai_limits_chat_help"), AI_LIMITS_CHAT_RANGE),
                scale("automation_max_autonomous_roundtrips", text("app_config_ai_limits_automation"), text("settings_ai_limits_automation_help"), AI_LIMITS_AUTOMATION_RANGE),
                scale("chat_max_data_chars", text("app_config_ai_data_chat"), text("settings_ai_data_chat_help"), AI_DATA_CHAT_RANGE),
                scale("automation_max_data_chars", text("app_config_ai_data_automation"), text("settings_ai_data_automation_help"), AI_DATA_AUTOMATION_RANGE),
                scale("always_send_max_chars", text("app_config_ai_always_send"), text("settings_ai_always_send_help"), AI_DATA_CHAT_RANGE),
                scale("chain_max_runs", text("app_config_chain_max_runs"), text("settings_chain_max_runs_help"), CHAIN_RANGE)
            )
            // The app's level alone: a zone and a tool carry theirs in their own settings
            AppSettingCategories.VALIDATION_CONFIG -> listOf(
                field(ValidationConfig.KEY_APP, text("app_config_validate_app"), text("app_config_validate_app_help"), FieldType.BOOLEAN, required = true),
                SettingNode.Field(FieldDefinition(ValidationConfig.KEY_PAST_DATA, text("app_config_validate_past_data"), text("app_config_validate_past_data_help"),
                    FieldType.BOOLEAN, false, null), default = ValidationConfig.PAST_DATA_DEFAULT)
            )
            AppSettingCategories.MAIN_SCREEN -> listOf(
                SettingNode.ListOf("zone_groups", text("label_zone_groups"),
                    SettingNode.Item.Value(FieldDefinition("zone_group", text("label_group"), text("message_zone_groups_description"),
                        FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name))),
                    required = true, distinct = true, description = text("app_config_schema_zone_groups"))
            )
            // The relay's address and secret are shown in the relay mode only, and kept under
            // Tailscale: going back to the relay finds them as they were
            AppSettingCategories.EXTERNAL_ACCESS -> listOf(
                choice(ACCESS_MODE, text("settings_external_access_mode"), text("settings_external_access_mode_help"),
                    listOf(ACCESS_MODE_TAILSCALE, ACCESS_MODE_RELAY),
                    labels = mapOf(ACCESS_MODE_TAILSCALE to text("settings_external_access_mode_tailscale"),
                        ACCESS_MODE_RELAY to text("settings_external_access_mode_relay")), required = true),
                SettingNode.Section(text("settings_external_access_relay_section"), listOf(
                    field(RELAY_URL, text("settings_external_access_relay_url"), text("settings_external_access_relay_url_help"),
                        FieldType.TEXT, config = mapOf("length" to TextLength.MEDIUM.name), address = true),
                    // MEDIUM: the relay's secret runs past SHORT's 60 characters, 64 in hexadecimal
                    field(RELAY_SECRET, text("settings_external_access_relay_secret"), text("settings_external_access_relay_secret_help"),
                        FieldType.TEXT, config = mapOf("length" to TextLength.MEDIUM.name), secret = true)
                ), shownWith = SettingNode.ShownWith(ACCESS_MODE, ACCESS_MODE_RELAY)),
                scale(MCP_MAX_DATA_CHARS, text("settings_external_access_max_data"), text("settings_external_access_max_data_help"), MCP_DATA_RANGE))
            AppSettingCategories.GUIDE -> listOf(
                field(GUIDE_WELCOME_SEEN, text("guide_setting_welcome_seen"), text("guide_schema_welcome_seen"), FieldType.BOOLEAN, required = true),
                field(GUIDE_BAND_HIDDEN, text("guide_setting_band_hidden"), text("guide_schema_band_hidden"), FieldType.BOOLEAN, required = true),
                field(GUIDE_CURRENT, text("guide_setting_current"), text("guide_schema_current"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)),
                SettingNode.ListOf(GUIDE_CHAPTERS, text("guide_setting_chapters"), SettingNode.Item.Of(listOf(
                    field("id", text("guide_setting_chapter"), text("guide_schema_chapter_id"), FieldType.TEXT, required = true, config = mapOf("length" to TextLength.SHORT.name)),
                    scale("step", text("guide_setting_step"), text("guide_setting_step_help"), 0..99),
                    field("done", text("guide_setting_done"), text("guide_schema_done"), FieldType.BOOLEAN, required = true),
                    SettingNode.ListOf("kept", text("guide_setting_kept"), SettingNode.Item.Of(listOf(
                        field("name", text("guide_setting_kept_name"), text("guide_schema_kept_name"), FieldType.TEXT, required = true, config = mapOf("length" to TextLength.SHORT.name)),
                        field("value", text("guide_setting_kept_value"), text("guide_schema_kept_value"), FieldType.TEXT, required = true, config = mapOf("length" to TextLength.SHORT.name))
                    ), description = text("guide_schema_kept_element")), required = true, summary = listOf("name"), description = text("guide_schema_kept"))
                ), description = text("guide_schema_chapter")), required = true, summary = listOf("id"), description = text("guide_schema_chapters"))
            )
            AppSettingCategories.DATA -> listOf(
                // Chosen in Android's folder picker, never typed
                field(DATA_FOLDER, text("settings_data_folder"), text("settings_data_schema_folder"), FieldType.TEXT, config = mapOf("length" to TextLength.UNLIMITED.name)),
                field(DATA_AUTO_BACKUP, text("settings_data_auto_backup"), text("settings_data_auto_backup_help"), FieldType.BOOLEAN, required = true),
                // Written by the screen that showed it, never entered
                field(DATA_SEEN, text("settings_data_seen"), text("settings_data_schema_seen"), FieldType.TEXT, config = mapOf("length" to TextLength.UNLIMITED.name), systemWritten = true),
                scale(DATA_HISTORY_DAYS, text("settings_data_history_days"), text("settings_data_history_days_help"), HISTORY_DAYS_RANGE),
                scale(DATA_HISTORY_MEGABYTES, text("settings_data_history_megabytes"), text("settings_data_history_megabytes_help"), HISTORY_MEGABYTES_RANGE)
            )
            AppSettingCategories.DEMO -> listOf(
                field(app.treelune.core.demo.DemoStartup.INSTALL_ON_UPDATE, text("settings_demo_install_on_update"),
                    text("settings_demo_install_on_update_help"), FieldType.BOOLEAN, required = true),
                // Absent from the settings stored before it: on
                SettingNode.Field(FieldDefinition(app.treelune.core.demo.DemoNotifications.SETTING, text("settings_demo_notifications"),
                    text("settings_demo_notifications_help"), FieldType.BOOLEAN, false, null), default = true)
            )
            AppSettingCategories.UI -> {
                val themes = app.treelune.core.themes.CurrentTheme.getAvailableThemes()
                val modes = app.treelune.core.themes.AppearanceMode.entries.map { it.name }
                listOf(
                    choice(UI_THEME, text("settings_ui_theme"), text("settings_ui_theme_help"), themes.keys.toList(),
                        labels = themes.mapValues { it.value.name(context) }, required = true),
                    choice(UI_MODE, text("settings_ui_mode"), text("settings_ui_mode_help"), modes,
                        labels = modes.associateWith { text("settings_ui_mode_${it.lowercase()}") }, required = true),
                    scale(UI_HUE_SHIFT, text("settings_ui_hue_shift"), text("settings_ui_hue_shift_help"), HUE_SHIFT_RANGE),
                    scale(UI_SIZE_STEP, text("settings_ui_size_step"), text("settings_ui_size_step_help"), SIZE_STEP_RANGE),
                    field(UI_ONE_COLUMN, text("settings_ui_one_column"), text("settings_ui_one_column_help"), FieldType.BOOLEAN, required = true),
                    field(UI_HOME_TITLE, text("settings_ui_home_title"), text("settings_ui_home_title_help"), FieldType.BOOLEAN, required = true),
                    app.treelune.core.themes.TextureIntensity.entries.map { it.name }.let { intensities ->
                        choice(UI_TEXTURE_INTENSITY, text("settings_ui_texture_intensity"), text("settings_ui_texture_intensity_help"), intensities,
                            labels = intensities.associateWith { text("settings_ui_texture_intensity_${it.lowercase()}") }, required = true)
                    },
                    field(UI_SOUNDS, text("settings_ui_sounds"), text("settings_ui_sounds_help"), FieldType.BOOLEAN, required = true)
                )
            }
            else -> throw IllegalArgumentException("No declaration for settings category '$category'")
        }
    }

    /** The schema a category's settings are held to. */
    fun schema(category: String, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "app_config_$category",
            displayName = title(category, context),
            description = "",
            category = SchemaCategory.APP_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(category, context), s::shared).toString()
        )
    }

    /** The name a category's screen is shown under. */
    fun title(category: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (category) {
            AppSettingCategories.FORMAT -> s.shared("settings_format_subtitle")
            AppSettingCategories.AI_LIMITS -> s.shared("settings_ai_limits")
            AppSettingCategories.VALIDATION_CONFIG -> s.shared("settings_validation")
            AppSettingCategories.MAIN_SCREEN -> s.shared("nav_home_config")
            AppSettingCategories.DEMO -> s.shared("settings_demo")
            AppSettingCategories.UI -> s.shared("settings_ui")
            AppSettingCategories.EXTERNAL_ACCESS -> s.shared("settings_external_access")
            AppSettingCategories.GUIDE -> s.shared("guide_title")
            AppSettingCategories.DATA -> s.shared("settings_data")
            else -> throw IllegalArgumentException("No declaration for settings category '$category'")
        }
    }

    /** What a category's screen is for, said under its title; null for the home screen's, opened from the home screen itself. */
    fun description(category: String, context: Context): String? {
        val s = Strings.`for`(context = context)
        return when (category) {
            AppSettingCategories.FORMAT -> s.shared("settings_format_description")
            AppSettingCategories.AI_LIMITS -> s.shared("settings_ai_limits_description")
            AppSettingCategories.VALIDATION_CONFIG -> s.shared("settings_validation_description")
            AppSettingCategories.DEMO -> s.shared("settings_demo_description")
            AppSettingCategories.UI -> s.shared("settings_ui_description")
            AppSettingCategories.EXTERNAL_ACCESS -> s.shared("settings_external_access_description")
            AppSettingCategories.GUIDE -> s.shared("guide_description")
            AppSettingCategories.DATA -> s.shared("settings_data_description")
            else -> null
        }
    }

    /** A category's stored [settings], read through its declaration. */
    fun read(category: String, settings: JSONObject, context: Context): SettingValues =
        SettingValues(nodes(category, context), settings)

    private fun field(name: String, label: String, description: String?, type: FieldType, required: Boolean = false,
                      config: Map<String, Any>? = null, secret: Boolean = false, address: Boolean = false,
                      systemWritten: Boolean = false) =
        SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required, secret = secret,
            address = address, systemWritten = systemWritten)

    private fun choice(name: String, label: String, description: String, values: List<String>,
                       labels: Map<String, String> = emptyMap(), required: Boolean = false) =
        field(name, label, description, FieldType.CHOICE, required, mapOf("options" to ChoiceSettings.storedOptions(values, labels)))

    /** A bounded whole number, set with a slider. */
    private fun scale(name: String, label: String, description: String, range: IntProgression) =
        field(name, label, description, FieldType.SCALE, required = true,
            config = mapOf("min" to range.first, "max" to range.last, "step" to range.step))
}
