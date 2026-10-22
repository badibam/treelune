package app.treelune.tools.goal

import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.settings.FieldTypeSettings
import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.StateField
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.ScheduleSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.reading.Reduction
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolOperation
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.tools.ToolTypeContract
import app.treelune.tools.goal.ui.GoalScreen
import org.json.JSONObject

/**
 * A goal (docs/TOOLS.md, « Goal »): an entry is an attempt over a period, opened
 * active, filled in along the period, validated as succeeded or failed — or expired, left
 * unvalidated. A one-off goal has one attempt; a recurring one an attempt per period, opened by
 * its schedule.
 *
 * The definition lives in the config (criteria, sub-goals, at least N), and each attempt keeps a
 * copy: changing the goal does not change how past attempts were judged. An entered criterion is
 * a field of the attempt, written as any field; a read one is read over the attempt's period. A
 * validated or expired attempt is locked: only reopening it changes it.
 */
object GoalToolType : ToolTypeContract {

    /** An attempt's life. */
    object Status {
        const val ACTIVE = "active"
        const val TO_VALIDATE = "to_validate"
        const val SUCCEEDED = "succeeded"
        const val FAILED = "failed"
        const val EXPIRED = "expired"
        val ALL = listOf(ACTIVE, TO_VALIDATE, SUCCEEDED, FAILED, EXPIRED)
        val LOCKED = setOf(SUCCEEDED, FAILED, EXPIRED)
    }

    // State keys of an attempt
    const val STATUS = "status"
    const val PERIOD_END = "period_end"
    const val JUDGEMENT = "judgement"
    const val VALIDATED_BY = "validated_by"
    const val VALIDATED_AT = "validated_at"
    const val REOPENED_AT = "reopened_at"
    const val NOTIFIED = "notified"

    /** The data key of the definition's copy, a JSON text. */
    const val DEFINITION = "definition"

    /** The setting that closes validating and reopening an attempt to the AI. */
    const val AI_NO_VALIDATION = "ai_no_validation"

    /** Whether a goal has one attempt or one per occurrence of its schedule, chosen first in its config. */
    const val RECURRENCE = "recurrence"

    object Recurrence {
        const val ONCE = "once"
        const val RECURRING = "recurring"
    }

    /** The types a value entered in an attempt takes: those a condition compares. */
    private val ENTERED_TYPES = listOf(FieldType.BOOLEAN, FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION,
        FieldType.CHOICE, FieldType.TEXT, FieldType.DATE, FieldType.TIME)

    /** Seven days, the default delay before an attempt left to validate expires. */
    const val DEFAULT_EXPIRY = 7L * 86_400_000

    /**
     * The units of an attempt's duration and of the delay before it expires, days on the
     * calendar: GoalScheduler adds the days at the same time of the clock (TimeResolver.after).
     */
    val DURATION_UNITS = app.treelune.core.fields.DurationUnits.DAY_HOUR.config()

    private fun s(context: Context) = Strings.`for`(tool = "goal", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getGuideConfig(context: Context): String = Strings.`for`(tool = "goal", context = context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = Strings.`for`(tool = "goal", context = context).tool("guide_screen")
    override fun getUsage(context: Context): String = s(context).tool("usage")
    override fun getFamily() = app.treelune.core.tools.ToolFamily.USE

    override fun getDefaultDisplayMode(): String = "LINE"
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "target"
    override fun getSuggestedIcons(): List<String> = listOf("target", "trophy", "flag", "medal")

    private fun field(name: String, label: String, type: FieldType, description: String? = null, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun choice(values: List<String>, labels: (String) -> String): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith(labels), emptyMap()))

    /**
     * The settings of one criterion: its name, whether it is essential, the value it declares
     * when that value is entered in each attempt, and its condition (Criterion).
     */
    private fun criterionNodes(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        return listOf(
            // Written by the app when the criterion is created (completeConfig), sent back unchanged
            SettingNode.Field(field("key", "key", FieldType.TEXT, s.tool("schema_key"), mapOf("length" to TextLength.SHORT.name)), systemWritten = true),
            SettingNode.Field(field("name", shared.shared("label_name"), FieldType.TEXT, s.tool("schema_criterion_name"), mapOf("length" to TextLength.SHORT.name)), required = true),
            SettingNode.Field(field("essential", s.tool("field_essential"), FieldType.BOOLEAN, s.tool("schema_essential")), default = false),
            SettingNode.Group(Criterion.ENTERED, s.tool("field_field"), FieldTypeSettings.valueNodes(shared::shared, ENTERED_TYPES), description = s.tool("schema_entered")),
            SettingNode.Field(field("instructions", s.tool("field_instructions"), FieldType.TEXT, s.tool("schema_instructions"), mapOf("length" to TextLength.MEDIUM.name))),
            SettingNode.Condition(Criterion.CONDITION, s.tool("field_condition"), reference = s.tool("reference_attempt_end"),
                emptyPeriod = s.tool("period_of_attempt"), required = true, enteredField = Criterion.ENTERED)
        )
    }

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        val criteria = SettingNode.ListOf("criteria", s.tool("field_criteria"), SettingNode.Item.Of(criterionNodes(context), description = s.tool("schema_criterion")),
            summary = listOf("name"), description = s.tool("schema_criteria"))
        return listOf(
            SettingNode.Section(s.tool("section_definition"), listOf(
                criteria,
                SettingNode.ListOf("sub_goals", s.tool("field_sub_goals"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field("name", shared.shared("label_name"), FieldType.TEXT, s.tool("schema_sub_goal_name"), mapOf("length" to TextLength.SHORT.name)), required = true),
                    SettingNode.Field(field("at_least", s.tool("field_at_least"), FieldType.NUMERIC, s.tool("schema_at_least"), mapOf("min" to 0, "decimals" to 0))),
                    SettingNode.Field(field("essential", s.tool("field_essential"), FieldType.BOOLEAN, s.tool("schema_essential")), default = false),
                    criteria
                ), description = s.tool("schema_sub_goal")), summary = listOf("name"), description = s.tool("schema_sub_goals")),
                SettingNode.Field(field("at_least", s.tool("field_at_least"), FieldType.NUMERIC, s.tool("schema_at_least"), mapOf("min" to 0, "decimals" to 0)))
            )),
            SettingNode.Section(s.tool("section_time"), listOf(
                // Chosen first, with no default: each option brings only the settings GoalScheduler reads for it
                SettingNode.Variant(
                    selector = SettingNode.Field(field(RECURRENCE, s.tool("field_$RECURRENCE"), FieldType.CHOICE, s.tool("schema_$RECURRENCE"),
                        choice(listOf(Recurrence.ONCE, Recurrence.RECURRING)) { s.tool("${RECURRENCE}_$it") }), required = true),
                    cases = mapOf(
                        Recurrence.ONCE to listOf(
                            SettingNode.Field(field("start", s.tool("field_start"), FieldType.DATETIME, s.tool("schema_start"))),
                            SettingNode.Field(field("deadline", s.tool("field_deadline"), FieldType.DATETIME, s.tool("schema_deadline")))
                        ),
                        Recurrence.RECURRING to listOf(
                            ScheduleSettings.group(s.tool("field_schedule"), shared::shared).copy(required = true),
                            SettingNode.Field(field("duration", s.tool("field_duration"), FieldType.DURATION, s.tool("schema_duration"), DURATION_UNITS))
                        )
                    ),
                    descriptions = listOf(Recurrence.ONCE, Recurrence.RECURRING).associateWith { s.tool("schema_${RECURRENCE}_$it") }
                ),
                SettingNode.Field(field("enabled", s.tool("field_enabled"), FieldType.BOOLEAN, s.tool("schema_enabled")), required = true, default = true),
                SettingNode.Field(field("expiry_delay", s.tool("field_expiry_delay"), FieldType.DURATION, s.tool("schema_expiry_delay"), DURATION_UNITS), required = true, default = DEFAULT_EXPIRY),
                SettingNode.Field(field("notify", s.tool("field_notify"), FieldType.BOOLEAN, s.tool("schema_notify")), required = true, default = true),
                SettingNode.Field(field(AI_NO_VALIDATION, s.tool("field_$AI_NO_VALIDATION"), FieldType.BOOLEAN, s.tool("schema_$AI_NO_VALIDATION")), default = false)
            ))
        )
    }

    /**
     * An attempt: the goal's name, the start of its period; in data the definition's copy and a
     * field per entered criterion of the current definition; in state its life, the end of its
     * period, and once validated its judgement, frozen.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        val entered = GoalDefinition.of(config).allCriteria.mapNotNull { it.enteredField() }
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.REQUIRED,
            data = listOf(FixedField(field(DEFINITION, s.tool("field_definition"), FieldType.TEXT, s.tool("schema_definition"),
                mapOf("length" to TextLength.UNLIMITED.name)), systemWritten = true)) + entered.map { FixedField(it) },
            state = listOf(
                StateField(field(STATUS, s.tool("field_status"), FieldType.CHOICE, s.tool("schema_status"),
                    choice(Status.ALL) { s.tool("status_$it") }), filterable = true),
                StateField(field(PERIOD_END, s.tool("field_period_end"), FieldType.DATETIME, s.tool("schema_period_end")), filterable = true),
                StateField(field(JUDGEMENT, s.tool("field_judgement"), FieldType.TEXT, s.tool("schema_judgement"), mapOf("length" to TextLength.UNLIMITED.name)), filterable = false),
                StateField(field(VALIDATED_BY, s.tool("field_validated_by"), FieldType.TEXT, config = mapOf("length" to TextLength.SHORT.name)), filterable = false),
                StateField(field(VALIDATED_AT, s.tool("field_validated_at"), FieldType.DATETIME), filterable = false),
                StateField(field(REOPENED_AT, s.tool("field_reopened_at"), FieldType.DATETIME), filterable = false),
                StateField(field(NOTIFIED, s.tool("field_notified"), FieldType.BOOLEAN), filterable = false)
            ),
            // An attempt is opened by the goal itself, which sets its period and copies its definition
            start = app.treelune.core.fields.EntryStart(emptyList(), refusal = s.tool("start_refused"))
        )
    }

    override fun getEntryMeanings(context: Context): app.treelune.core.fields.EntryMeanings {
        val s = Strings.`for`(tool = "goal", context = context)
        return app.treelune.core.fields.EntryMeanings(
            name = s.tool("entry_name"),
            timestamp = s.tool("entry_timestamp"),
            data = s.tool("entry_data"),
            state = s.tool("entry_state")
        )
    }

    /**
     * Validating and reopening an attempt, for the screen and the AI alike. Who validated is the
     * call's origin (currentOrigin): a person or the AI, never a scheduler.
     */
    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        val id = SettingNode.Field(field("id", s.tool("field_attempt"), FieldType.TEXT, Strings.`for`(context = context).shared("tool_operation_entry_id"), mapOf("length" to TextLength.SHORT.name)), required = true)
        return listOf(
            ToolOperation("validate", s.tool("operation_validate"), listOf(id)),
            ToolOperation("reopen", s.tool("operation_reopen"), listOf(id))
        )
    }

    /**
     * Each criterion without a key gets one, from its name and apart from the keys already taken;
     * a key given is kept, so renaming a criterion keeps its values.
     */
    override fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject {
        val completed = JSONObject(config.toString())
        val criteria = listOfNotNull(completed.optJSONArray("criteria")) +
            (completed.optJSONArray("sub_goals")?.let { subs -> (0 until subs.length()).mapNotNull { subs.getJSONObject(it).optJSONArray("criteria") } } ?: emptyList())
        val all = criteria.flatMap { array -> (0 until array.length()).map { array.getJSONObject(it) } }
        val taken = all.mapNotNull { it.optString("key").takeIf { k -> k.isNotEmpty() } }.toMutableSet()
        all.filter { it.optString("key").isEmpty() }.forEach { criterion ->
            val base = Criterion.keyOf(criterion.optString("name"))
            var key = base
            var n = 2
            while (key in taken) key = "${base}_${n++}"
            taken.add(key)
            criterion.put("key", key)
        }
        // An entered criterion's condition is put on its own value: its left side is written here
        all.filter { it.optJSONObject(Criterion.ENTERED)?.optString("type").isNullOrEmpty().not() }.forEach { criterion ->
            criterion.optJSONObject(Criterion.CONDITION)?.put(Conditions.LEFT, JSONObject().put(Conditions.FIELD, Criterion.enteredPath(criterion.getString("key"))))
        }
        return completed
    }

    /**
     * Whether validating or reopening an attempt of the goal of [config] is refused to a call of
     * [origin]: the AI, a routine or an AI come through the MCP server, when the goal closes its
     * validation to the AI. Reading where an attempt stands (evaluate) stays open.
     */
    fun closedToAi(config: JSONObject, origin: app.treelune.core.coordinator.Source): Boolean =
        config.optBoolean(AI_NO_VALIDATION, false) && app.treelune.core.fields.AiReadOnly.refuses(origin)

    /** Whether [after] opens to the AI the validation [before] closes to it. */
    fun validationOpened(before: JSONObject, after: JSONObject): Boolean =
        before.optBoolean(AI_NO_VALIDATION, false) && !after.optBoolean(AI_NO_VALIDATION, false)

    /**
     * The validation closed to the AI, and each entered criterion read-only for it, kept where
     * [after] leaves the setting out; a criterion is the same by its key, in a sub-goal or not.
     */
    override fun keepClosedToAi(before: JSONObject, after: JSONObject): JSONObject {
        val kept = JSONObject(after.toString())
        if (!kept.has(AI_NO_VALIDATION) && before.optBoolean(AI_NO_VALIDATION, false)) kept.put(AI_NO_VALIDATION, true)
        val objects = app.treelune.core.fields.AiReadOnly::objects
        fun criteria(config: JSONObject) = objects(config.optJSONArray("criteria")) +
            objects(config.optJSONArray("sub_goals")).flatMap { objects(it.optJSONArray("criteria")) }
        val entered = { criterion: JSONObject -> criterion.optJSONObject(Criterion.ENTERED) }
        val marked = app.treelune.core.fields.AiReadOnly.markedIds(criteria(before), id = "key", locate = entered)
        app.treelune.core.fields.AiReadOnly.keepMarks(criteria(kept), marked, id = "key", locate = entered)
        return kept
    }

    /** Closing the validation to the AI is anyone's, opening it again a person's. */
    override fun refuseConfigChange(before: JSONObject, after: JSONObject, byAPerson: Boolean, context: Context): String? =
        if (byAPerson || !validationOpened(before, after)) null
        else s(context).shared("service_error_protection_user_only").format(s(context).tool("field_$AI_NO_VALIDATION"))

    /** A validated or expired attempt is locked: only its own operation, reopening it, changes it. */
    override fun refuseChange(entry: ToolDataEntity, context: Context): String? =
        s(context).tool("error_locked").takeIf { statusOf(entry) in Status.LOCKED }

    /** A locked attempt, and a reopened one, judged by its copy: a change of the goal leaves their values. */
    override fun keepsItsPast(entry: ToolDataEntity, context: Context): Boolean =
        statusOf(entry) in Status.LOCKED || entry.state?.takeIf { it.isNotBlank() }?.let { JSONObject(it).has(REOPENED_AT) } == true

    fun statusOf(entry: ToolDataEntity): String? = entry.state?.takeIf { it.isNotBlank() }?.let { JSONObject(it).optString(STATUS) }

    /** An attempt to validate waits. */
    override fun getWaiting(config: JSONObject): List<JSONObject> =
        listOf(Conditions.onField("state.$STATUS", "in", listOf(Status.TO_VALIDATE)))

    override fun getService(context: Context): ExecutableService = GoalService(context)

    override fun getScheduler(): ToolScheduler = GoalScheduler

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntry: app.treelune.core.tools.EntryToOpen?) {
        GoalScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick, openEntryId = (openEntry as? app.treelune.core.tools.EntryToOpen.Existing)?.id)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (app.treelune.core.tools.EntryToOpen) -> Unit): app.treelune.core.tools.ToolTile =
        app.treelune.tools.goal.ui.rememberGoalTile(tool)
}
