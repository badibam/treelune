package app.treelune.tools.path

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
import app.treelune.core.fields.settings.ElementKeys
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.themes.TagColor
import app.treelune.core.tools.OpenAt
import app.treelune.core.tools.ToolOperation
import app.treelune.core.tools.ToolTile
import app.treelune.core.tools.ToolTypeContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Path (docs/design/progression.md): where one stands in something that unfolds in pieces.
 * Its config is a grammar — kinds of stage, their fields, states, marks and effects, link types —;
 * its entries are its stages, each of a run, which its own operations alone write (PathService);
 * their states are computed by replaying their moves (PathReplay), never stored, and read by
 * everything that reads entries through its views `stages` and `moves` (PathViews).
 */
object PathToolType : ToolTypeContract {

    private fun s(context: Context) = Strings.`for`(tool = PathRows.TOOLTYPE, context = context)

    /** The units of the calendar a run may be opened at. */
    private val UNITS = listOf(app.treelune.core.selection.PeriodType.DAY, app.treelune.core.selection.PeriodType.WEEK,
        app.treelune.core.selection.PeriodType.MONTH, app.treelune.core.selection.PeriodType.YEAR)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getGuideConfig(context: Context): String = s(context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = s(context).tool("guide_screen")
    override fun getUsage(context: Context): String = s(context).tool("usage")
    override fun getAIDetail(context: Context): String = s(context).tool("ai_detail")
    override fun getFamily() = app.treelune.core.tools.ToolFamily.USE

    override fun getDefaultTileSize(): app.treelune.core.look.TileSize = app.treelune.core.look.TileSize.of(4, 4)
    override fun getItemSpec(): app.treelune.core.look.ItemSpec? = null
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "route"
    override fun getSuggestedIcons(): List<String> = listOf("route", "milestone", "footprints", "mountain", "trophy", "list-checks")

    /** Its fields are its kinds', declared in its grammar: none of the user's beside them. */
    override fun takesUserFields(): Boolean = false

    override fun views(): List<app.treelune.core.views.ToolView> = listOf(PathViews.Stages, PathViews.Moves)

    // -----------------------------------------------------------------------------------------
    // The grammar
    // -----------------------------------------------------------------------------------------

    private fun field(name: String, label: String, type: FieldType, description: String, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun short() = mapOf("length" to TextLength.SHORT.name)

    private fun choice(values: List<String>, labels: (String) -> String): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith(labels), emptyMap()))

    /** A key given from the name at the save, fixed from then on: what stages, rules and links name. */
    private fun key(context: Context, description: String) =
        SettingNode.Field(field("key", s(context).tool("field_key"), FieldType.TEXT, description, short()), systemWritten = true)

    private fun name(context: Context, description: String) =
        SettingNode.Field(field("name", s(context).tool("field_name"), FieldType.TEXT, description, short()), required = true)

    /** How a state or a mark shows on a box: a colour of the palette, full or outlined, faded (`paint`). */
    private fun paint(context: Context): SettingNode {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        val colors = TagColor.entries.map { it.name }
        return SettingNode.Group("paint", s.tool("field_paint"), listOf(
            SettingNode.Field(field("color", s.tool("field_color"), FieldType.CHOICE, s.tool("schema_color"), mapOf("options" to ChoiceSettings.storedOptions(
                colors, colors.associateWith { shared.shared("tag_color_${it.lowercase()}") }, TagColor.entries.associate { it.name to it })))),
            SettingNode.Field(field("fill", s.tool("field_fill"), FieldType.CHOICE, s.tool("schema_fill"), choice(listOf("outline", "full")) { s.tool("fill_$it") }), default = "outline"),
            SettingNode.Field(field("faded", s.tool("field_faded"), FieldType.BOOLEAN, s.tool("schema_faded")))
        ), description = s.tool("schema_paint"))
    }

    /** A rule judged on a stage, `self` reading it (Term.Self). */
    private fun rule(context: Context, name: String, label: String) =
        SettingNode.Condition(name, label, reference = s(context).tool("reference_judged"), emptyPeriod = null)

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        val states = SettingNode.ListOf("states", s.tool("field_states"), SettingNode.Item.Of(listOf(
            key(context, s.tool("schema_state_key")),
            name(context, s.tool("schema_state_name")),
            SettingNode.Field(field("manual", s.tool("field_manual"), FieldType.BOOLEAN, s.tool("schema_manual"))),
            rule(context, "when", s.tool("field_when")),
            SettingNode.Field(field("record", s.tool("field_record"), FieldType.BOOLEAN, s.tool("schema_record"))),
            rule(context, "note_if", s.tool("field_note_if")),
            SettingNode.Field(field("back_label", s.tool("field_back_label"), FieldType.TEXT, s.tool("schema_back_label"), short())),
            SettingNode.Field(field("closes_run", s.tool("field_closes_run"), FieldType.BOOLEAN, s.tool("schema_closes_run"))),
            paint(context),
            // What the app says when a stage enters it
            SettingNode.Field(field("notify", s.tool("field_notify"), FieldType.BOOLEAN, s.tool("schema_notify"))),
            app.treelune.core.events.EventPoints.node { shared.shared(it) }
        ), description = s.tool("schema_state")), required = true, minItems = 1, summary = listOf("name"), description = s.tool("schema_states"))
        val marks = SettingNode.ListOf("marks", s.tool("field_marks"), SettingNode.Item.Of(listOf(
            key(context, s.tool("schema_mark_key")),
            name(context, s.tool("schema_mark_name")),
            rule(context, "when", s.tool("field_when")),
            paint(context)
        ), description = s.tool("schema_mark")), summary = listOf("name"), description = s.tool("schema_marks"))
        val effects = SettingNode.ListOf("effects", s.tool("field_effects"), SettingNode.Item.Of(listOf(
            SettingNode.Field(field("label", s.tool("field_effect_label"), FieldType.TEXT, s.tool("schema_effect_label"), short()), required = true),
            rule(context, "on", s.tool("field_on")),
            SettingNode.Group("set", s.tool("field_set"), listOf(
                SettingNode.Selection("stages", s.tool("field_set_stages"), reference = s.tool("reference_judged"), required = true, ordered = false),
                SettingNode.Field(field("state", s.tool("field_set_state"), FieldType.TEXT, s.tool("schema_set_state"), short()), required = true)
            ), required = true, description = s.tool("schema_set"))
        ), description = s.tool("schema_effect")), summary = listOf("label"), description = s.tool("schema_effects"))
        val definitions = app.treelune.core.fields.settings.FieldTypeSettings.definitionNodes({ shared.shared(it) })
        return listOf(
            SettingNode.Section(s.tool("section_kinds"), listOf(
                SettingNode.ListOf(PathGrammar.KINDS, s.tool("field_kinds"), SettingNode.Item.Of(listOf(
                    key(context, s.tool("schema_kind_key")),
                    name(context, s.tool("schema_kind_name")),
                    SettingNode.Field(field("icon", s.tool("field_icon"), FieldType.TEXT, s.tool("schema_icon"), short()), icon = true),
                    SettingNode.ListOf("contains", s.tool("field_contains"), SettingNode.Item.Value(field("contains", s.tool("field_contains"), FieldType.TEXT, s.tool("schema_contains"), short())),
                        distinct = true, description = s.tool("schema_contains")),
                    SettingNode.ListOf("plan_fields", s.tool("field_plan_fields"), SettingNode.Item.Of(definitions), fieldDefinitions = true,
                        summary = listOf("display_name", "type"), description = s.tool("schema_plan_fields")),
                    SettingNode.ListOf("fill_fields", s.tool("field_fill_fields"), SettingNode.Item.Of(definitions), fieldDefinitions = true,
                        summary = listOf("display_name", "type"), description = s.tool("schema_fill_fields")),
                    states, marks, effects,
                    SettingNode.Field(field("layout", s.tool("field_layout"), FieldType.CHOICE, s.tool("schema_layout"), choice(listOf("graph", "parts")) { s.tool("layout_$it") }), default = "graph")
                ), description = s.tool("schema_kind")), required = true, minItems = 1, summary = listOf("name"), description = s.tool("schema_kinds")),
                SettingNode.Field(field(PathGrammar.ROOT_KIND, s.tool("field_root_kind"), FieldType.TEXT, s.tool("schema_root_kind"), short()), required = true)
            )),
            SettingNode.Section(s.tool("section_links"), listOf(
                SettingNode.ListOf(PathGrammar.LINK_TYPES, s.tool("field_link_types"), SettingNode.Item.Of(listOf(
                    key(context, s.tool("schema_link_type_key")),
                    name(context, s.tool("schema_link_type_name")),
                    SettingNode.Field(field("stroke", s.tool("field_stroke"), FieldType.CHOICE, s.tool("schema_stroke"), choice(listOf("solid", "dashed", "dotted")) { s.tool("stroke_$it") }), default = "solid")
                ), description = s.tool("schema_link_type")), summary = listOf("name"), description = s.tool("schema_link_types"))
            )),
            SettingNode.Section(s.tool("section_runs"), listOf(
                SettingNode.Field(field(PathGrammar.OPENS, s.tool("field_opens"), FieldType.CHOICE, s.tool("schema_opens"),
                    choice(Opens.entries.map { it.key }) { s.tool("opens_$it") }), default = Opens.MANUAL.key),
                SettingNode.Field(field(PathGrammar.LASTS, s.tool("field_lasts"), FieldType.DURATION, s.tool("schema_lasts"))),
                SettingNode.Field(field(PathGrammar.CONFIRM, s.tool("field_confirm"), FieldType.BOOLEAN, s.tool("schema_confirm")))
            )),
            SettingNode.Section(s.tool("section_scheduled"), listOf(
                SettingNode.Field(field(PathGrammar.UNIT, s.tool("field_unit"), FieldType.CHOICE, s.tool("schema_unit"),
                    choice(UNITS.map { it.name }) { shared.shared("period_type_${it.lowercase()}") }))
            ), shownWith = SettingNode.ShownWith(PathGrammar.OPENS, Opens.SCHEDULED.key))
        )
    }

    /** Kinds, states, marks and link types get their keys from their names, fixed from then on. */
    override fun completeConfig(config: JSONObject, previous: JSONObject?): JSONObject {
        val completed = JSONObject(config.toString())
        fun keys(array: JSONArray?) = ElementKeys.complete(ElementKeys.objects(array), "key") { ElementKeys.keyOf("", it.optString("name")).ifEmpty { "k" } }
        val kinds = completed.optJSONArray(PathGrammar.KINDS)
        keys(kinds)
        ElementKeys.objects(kinds).forEach { kind -> keys(kind.optJSONArray("states")); keys(kind.optJSONArray("marks")) }
        keys(completed.optJSONArray(PathGrammar.LINK_TYPES))
        return completed
    }

    override suspend fun refuseConfig(config: JSONObject, context: Context): String? = PathCheck.refuse(config) { s(context).tool(it) }

    override fun conditionSources(base: app.treelune.core.conditions.ConfigConditions.Sources, config: JSONObject, context: Context): app.treelune.core.conditions.ConfigConditions.Sources =
        PathConditionSources(base, completeConfig(config, null)) { s(context).tool(it) }

    // -----------------------------------------------------------------------------------------
    // The stages
    // -----------------------------------------------------------------------------------------

    /**
     * A stage: its name; its kind's fields in `data` (those of every kind declared, each stage
     * holding its own kind's alone); in `state`, what the Path's operations write.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = s(context)
        val grammar = PathGrammar.fromJson(config)
        val fields = grammar.kinds.flatMap { it.fields }.distinctBy { it.name }
        return EntryFields(
            name = CoreFieldUsage.REQUIRED,
            timestamp = CoreFieldUsage.ABSENT,
            data = fields.map { FixedField(it) },
            state = listOf(
                StateField(field(PathStages.KIND, s.tool("field_stage_kind"), FieldType.TEXT, s.tool("schema_stage_kind"), short()), filterable = true),
                StateField(field(PathStages.PARENT, s.tool("field_stage_parent"), FieldType.TEXT, s.tool("schema_stage_parent"), short()), filterable = true),
                StateField(field(PathStages.POSITION, s.tool("field_stage_position"), FieldType.NUMERIC, s.tool("schema_stage_position"), mapOf("decimals" to 0)), filterable = false, sortable = true),
                StateField(field(PathStages.LINKS, s.tool("field_stage_links"), FieldType.TEXT, s.tool("schema_stage_links")), filterable = false, schema = PathStages.LINKS_SCHEMA),
                StateField(field(PathStages.MOVES, s.tool("field_stage_moves"), FieldType.TEXT, s.tool("schema_stage_moves")), filterable = false, schema = PathStages.MOVES_SCHEMA),
                StateField(field(PathStages.FINAL_STATE, s.tool("field_stage_final_state"), FieldType.TEXT, s.tool("schema_stage_final_state"), short()), filterable = true),
                StateField(field(PathStages.PLANNED_END, s.tool("field_stage_planned_end"), FieldType.DATETIME, s.tool("schema_stage_planned_end")), filterable = true),
                StateField(field(PathStages.AWAITING, s.tool("field_stage_awaiting"), FieldType.DATETIME, s.tool("schema_stage_awaiting")), filterable = true),
                StateField(field(PathStages.TOLD, s.tool("field_stage_told"), FieldType.TEXT, s.tool("schema_stage_told"), short()), filterable = false, bookkeeping = true)
            ),
            // Written by the Path's operations alone, the plan holding together
            start = EntryStart(emptyList(), refusal = s.tool("start_refused"), withStatus = false)
        )
    }

    override fun getEntryMeanings(context: Context): app.treelune.core.fields.EntryMeanings {
        val s = s(context)
        return app.treelune.core.fields.EntryMeanings(name = s.tool("entry_name"), data = s.tool("entry_data"), state = s.tool("entry_state"))
    }

    /** A stage holding others is deleted with them, by the Path's delete. */
    override suspend fun refuseDelete(entry: ToolDataEntity, entries: suspend () -> List<ToolDataEntity>, context: Context): String? {
        val holds = entries().any { other -> other.state?.let { JSONObject(it).optString(PathStages.PARENT) } == entry.id }
        return if (holds) s(context).tool("error_delete_content").format(entry.name ?: "") else null
    }

    override fun getOperations(context: Context): List<ToolOperation> {
        val s = s(context)
        fun text(name: String, required: Boolean = false) = SettingNode.Field(field(name, s.tool("param_$name"), FieldType.TEXT, s.tool("schema_param_$name"), short()), required = required)
        val stage = text("stage", required = true)
        return listOf(
            ToolOperation("write_plan", s.tool("operation_write_plan"), listOf(text("parent"),
                SettingNode.Field(field("stages", s.tool("param_stages"), FieldType.TEXT, s.tool("schema_param_stages")), required = true))),
            ToolOperation("update_stage", s.tool("operation_update_stage"), listOf(stage, text("name"))),
            ToolOperation("move_stage", s.tool("operation_move_stage"), listOf(stage, text("parent"),
                SettingNode.Field(field("position", s.tool("param_position"), FieldType.NUMERIC, s.tool("schema_param_position"), mapOf("decimals" to 0))))),
            ToolOperation("delete_stage", s.tool("operation_delete_stage"), listOf(stage)),
            ToolOperation("note", s.tool("operation_note"), listOf(stage, text("state", required = true), text("note"))),
            ToolOperation("unnote", s.tool("operation_unnote"), listOf(stage)),
            ToolOperation("start", s.tool("operation_start"), listOf(text("name"),
                SettingNode.Field(field("plan", s.tool("param_plan"), FieldType.CHOICE, s.tool("schema_param_plan"), choice(listOf("copy", "empty")) { s.tool("plan_$it") })))),
            ToolOperation("stop", s.tool("operation_stop"), listOf(text("run"))),
            ToolOperation("validate", s.tool("operation_validate"), listOf(text("run", required = true))),
            ToolOperation("state", s.tool("operation_state"), emptyList())
        )
    }

    override fun getService(context: Context): ExecutableService = PathService(context)

    /** The judge passes at each tick, a date having perhaps come (PathJudge). */
    override fun getScheduler(): app.treelune.core.tools.ToolScheduler = PathJudge

    /** Its rules read other tools: the judge passes after every step that wrote one. */
    override val hearsWrites: Boolean get() = true

    override suspend fun heard(context: Context, written: Set<String>) = PathJudge.afterWrites(context, written)

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openAt: OpenAt?) {
        app.treelune.tools.path.ui.PathScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (OpenAt) -> Unit): ToolTile = app.treelune.tools.path.ui.rememberPathTile(tool)
}
