package app.treelune.core.fields.settings

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.utils.ScheduleConfig
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * A schedule, declared with the fields: a recurrence pattern and a variant on its type. The form ScheduleConfig reads and writes; a Messages tool holds one,
 * and so does a routine.
 */
object ScheduleSettings {

    /** The name a schedule is stored under in the object holding it, and by which SettingsForm knows it. */
    const val NAME = "schedule"

    /** A schedule labelled [label], as a config or a routine declares it. */
    fun group(label: String, text: (String) -> String) = SettingNode.Group(NAME, label, nodes(text), description = text("schedule_description"))

    /** The pattern types, as ScheduleConfig's SchedulePattern names them. */
    private val PATTERNS = listOf("DailyMultiple", "WeeklySimple", "MonthlyRecurrent", "WeeklyCustom", "YearlyRecurrent", "SpecificDates")

    /** The settings of a schedule, as the object stored under a config's "schedule". */
    fun nodes(text: (String) -> String): List<SettingNode> = listOf(
        SettingNode.Group(
            name = "pattern",
            label = text("schedule_pattern_label"),
            required = true,
            description = text("schedule_pattern_description"),
            nodes = listOf(SettingNode.Variant(
                selector = field("type", text("schedule_pattern_label"), FieldType.CHOICE, required = true, description = text("schedule_type_description"),
                    config = mapOf("options" to ChoiceSettings.storedOptions(PATTERNS, PATTERNS.associateWith { text("schedule_pattern_$it") }))),
                cases = mapOf(
                    "DailyMultiple" to listOf(
                        values("times", text("schedule_daily_times_label"), time(text), text("schedule_times_description"))
                    ) + cycle("days", text),
                    "WeeklySimple" to listOf(
                        values("days_of_week", text("schedule_weekly_days_label"), dayOfWeek(text), text("schedule_days_of_week_description")),
                        timeField(text)
                    ) + cycle("weeks", text),
                    "MonthlyRecurrent" to listOf(
                        values("months", text("schedule_month_label"), month(text), text("schedule_months_description")),
                        values("days_of_month", text("schedule_days_of_month_label"),
                            FieldDefinition("day_of_month", text("schedule_day_of_month_label"), text("schedule_day_of_month_description"), FieldType.NUMERIC, false, whole(1, 31)),
                            text("schedule_days_of_month_description")),
                        timeField(text)
                    ),
                    "WeeklyCustom" to listOf(
                        SettingNode.ListOf("moments", text("schedule_weekly_custom_label"), minItems = 1, required = true,
                            summary = listOf("day_of_week", "time"), description = text("schedule_moments_description"),
                            item = SettingNode.Item.Of(listOf(
                                SettingNode.Field(dayOfWeek(text), required = true),
                                timeField(text)
                            ), description = text("schedule_moment_description")))
                    ) + cycle("weeks", text),
                    "YearlyRecurrent" to listOf(
                        SettingNode.ListOf("dates", text("schedule_yearly_recurrent_label"), minItems = 1, required = true,
                            summary = listOf("day", "month", "time"), description = text("schedule_dates_description"),
                            item = SettingNode.Item.Of(listOf(
                                SettingNode.Field(month(text), required = true),
                                field("day", text("schedule_day_label"), FieldType.NUMERIC, required = true, config = whole(1, 31),
                                    description = text("schedule_day_of_month_description")),
                                timeField(text)
                            ), description = text("schedule_date_description")))
                    ),
                    "SpecificDates" to listOf(
                        values("timestamps", text("schedule_specific_dates_label"),
                            FieldDefinition("timestamp", text("schedule_specific_dates_label"), text("schedule_timestamp_description"), FieldType.DATETIME, false, null),
                            text("schedule_timestamps_description"))
                    )
                ),
                descriptions = PATTERNS.associateWith { text("schedule_pattern_${it}_description") }
            ))
        )
    )

    /**
     * One day or one week in "every", placed by "start_date" (Cycle): a schedule without them runs
     * every day or week. That they go together is ScheduleConfig's to refuse ([refusal]).
     */
    private fun cycle(unit: String, text: (String) -> String): List<SettingNode> = listOf(
        SettingNode.Field(FieldDefinition("every", text("schedule_every_${unit}_label"), text("schedule_every_${unit}_description"),
            FieldType.NUMERIC, false, mapOf("min" to 1, "decimals" to 0)), default = 1),
        SettingNode.Field(FieldDefinition("start_date", text("schedule_start_date_label"), text("schedule_start_date_description"),
            FieldType.DATE, false, null))
    )

    /**
     * Why a schedule [config] holds, as [nodes] declare them (a group named [NAME], or in the
     * elements of a list, an Agenda's series), does not read as ScheduleConfig; null when each
     * reads. The schema checks each value alone; the reading, what holds between them (a cycle
     * without its start date).
     */
    fun refusal(nodes: List<SettingNode>, config: JSONObject): String? {
        for (node in nodes) {
            val refusal = when (node) {
                is SettingNode.Group -> config.optJSONObject(node.name)?.let { value ->
                    if (node.name == NAME) unreadable(value) else refusal(node.nodes, value)
                }
                is SettingNode.ListOf -> (node.item as? SettingNode.Item.Of)?.let { shape ->
                    val list = config.optJSONArray(node.name) ?: JSONArray()
                    (0 until list.length()).firstNotNullOfOrNull { i -> list.optJSONObject(i)?.let { refusal(shape.nodes, it) } }
                }
                is SettingNode.Variant -> node.cases[config.optString(node.selector.definition.name)]?.let { refusal(it, config) }
                is SettingNode.Section -> refusal(node.nodes, config)
                else -> null
            }
            if (refusal != null) return refusal
        }
        return null
    }

    private fun unreadable(schedule: JSONObject): String? = try {
        Json.decodeFromString(ScheduleConfig.serializer(), schedule.toString())
        null
    } catch (e: Exception) {
        "Invalid schedule: ${e.message}"
    }

    private fun field(name: String, label: String, type: FieldType, required: Boolean = false, config: Map<String, Any>? = null, description: String? = null) =
        SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required)

    /** A required list of at least one value. */
    private fun values(name: String, label: String, value: FieldDefinition, description: String) =
        SettingNode.ListOf(name, label, SettingNode.Item.Value(value), required = true, minItems = 1, description = description)

    private fun whole(min: Int, max: Int): Map<String, Any> = mapOf("min" to min, "max" to max, "decimals" to 0)

    private fun time(text: (String) -> String) = FieldDefinition("time", text("schedule_time_label_single"), text("schedule_time_description"), FieldType.TIME, false, null)
    private fun timeField(text: (String) -> String) = SettingNode.Field(time(text), required = true)
    private fun dayOfWeek(text: (String) -> String) = FieldDefinition("day_of_week", text("schedule_day_label"), text("schedule_day_of_week_description"), FieldType.NUMERIC, false, whole(1, 7))
    private fun month(text: (String) -> String) = FieldDefinition("month", text("schedule_month_label"), text("schedule_month_description"), FieldType.NUMERIC, false, whole(1, 12))
}
