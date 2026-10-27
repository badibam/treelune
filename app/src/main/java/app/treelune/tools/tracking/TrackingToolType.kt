package app.treelune.tools.tracking

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.SettingNode
import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.tools.ToolTypeContract
import app.treelune.core.services.ExecutableService
import app.treelune.core.validation.ValidationResult
import app.treelune.core.utils.NumberFormatting
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.strings.Strings
import app.treelune.core.utils.LogManager
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import app.treelune.core.validation.SchemaProvider
import app.treelune.core.validation.FieldLimits
import app.treelune.tools.tracking.ui.TrackingScreen
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import app.treelune.core.fields.toFieldConfig
import org.json.JSONObject

/**
 * Tracking Tool Type implementation
 * Provides static metadata for tracking tool instances
 */
object TrackingToolType : ToolTypeContract {
    
    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("display_name")
    }

    override fun getGuideConfig(context: Context): String = Strings.`for`(tool = "tracking", context = context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = Strings.`for`(tool = "tracking", context = context).tool("guide_screen")
    override fun getUsage(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("usage")
    }

    override fun getFamily() = app.treelune.core.tools.ToolFamily.NOTE

    override fun getDefaultDisplayMode(): String = "LINE"


    override fun getDefaultShowFieldLabels(): Boolean = true

    /**
     * A tracking tool's settings: what it follows ("type"), and for that type
     * - value: the settings of the entries' main field, those of its field type (FieldTypeSettings);
     * - units: the units a numeric value can be in;
     * - items: the shortcuts, each a name, and for a numeric tool the value it enters and its
     *   unit, one of the units, and values of the user's fields its entries take (extra).
     */
    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(tool = "tracking", context = context)
        val shared = Strings.`for`(context = context)
        fun field(name: String, label: String, description: String, type: FieldType, required: Boolean = false,
                  default: Any? = null, config: Map<String, Any>? = null) =
            SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required, default = default)
        val short = mapOf("length" to TextLength.SHORT.name)
        val kinds = TrackingKind.entries.map { it.key }

        return listOf(SettingNode.Variant(
            selector = field("type", s.tool("config_label_tracking_type"), s.tool("schema_config_type"), FieldType.CHOICE,
                default = TrackingKind.NUMERIC.key, config = mapOf("options" to ChoiceSettings.storedOptions(kinds, kinds.associateWith { s.tool("config_option_$it") }))),
            cases = TrackingKind.entries.associate { kind ->
                val item = listOfNotNull(
                    field("name", shared.shared("label_name"), s.tool("schema_config_item_name"), FieldType.TEXT, required = true, config = short),
                    if (kind == TrackingKind.NUMERIC)
                        field("value", s.tool("field_value"), s.tool("schema_config_item_value"), FieldType.NUMERIC, config = mapOf("decimals" to 2))
                    else null,
                    if (kind == TrackingKind.NUMERIC)
                        field("unit", s.tool("field_unit"), s.tool("schema_config_item_unit"), FieldType.TEXT, config = short)
                    else null,
                    SettingNode.FieldValues(TrackingConfig.EXTRA, s.tool("config_label_item_extra"), "extra_fields", s.tool("schema_config_item_extra"))
                )
                kind.key to listOfNotNull(
                    // A number needs its decimals, a scale its bounds and a choice its options:
                    // their value settings are required. A numeric entry takes its unit from "units".
                    kind.valueType?.let { valueType ->
                        SettingNode.Group("value", s.tool("field_value"),
                            FieldTypeSettings.configNodes(valueType, shared::shared),
                            required = kind == TrackingKind.NUMERIC || kind == TrackingKind.SCALE || kind == TrackingKind.CHOICE,
                            description = s.tool("schema_config_value"))
                    },
                    if (kind == TrackingKind.NUMERIC)
                        SettingNode.ListOf("units", s.tool("field_units"),
                            SettingNode.Item.Value(FieldDefinition("unit", s.tool("field_unit"), s.tool("schema_config_units"), FieldType.TEXT, false, short)),
                            distinct = true, description = s.tool("schema_config_units_list"))
                    else null,
                    SettingNode.ListOf("items", s.tool("field_items"), SettingNode.Item.Of(item, description = s.tool("schema_config_item")),
                        summary = item.filterIsInstance<SettingNode.Field>().map { it.definition.name }, description = s.tool("schema_config_items"))
                )
            },
            descriptions = TrackingKind.entries.associate { it.key to s.tool("schema_config_type_${it.key}") }
        ))
    }

    /**
     * A tracking entry: a name (the shortcut or the activity), a moment, and a main field,
     * "value", whose field type comes from the tool's type and whose settings come from the
     * config's "value". A numeric one also carries its unit, one of the units the config
     * declares or a new one that joins them, so an entry keeps saying what it measured when
     * shortcuts change. An occurrence
     * has no value: the entry is the fact that something happened.
     *
     * A timer's value is absent while it runs: its start is in the entry's state until it is
     * stopped.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "tracking", context = context)
        val kind = TrackingKind.of(config)
        val valueType = kind.valueType
            ?: return EntryFields(name = CoreFieldUsage.REQUIRED, timestamp = CoreFieldUsage.OPTIONAL)

        val valueConfig = config.optJSONObject("value")?.toFieldConfig() ?: emptyMap()
        val fields = mutableListOf(
            FixedField(
                FieldDefinition(
                    name = "value",
                    displayName = s.tool("field_value"),
                    description = s.tool("schema_data_value"),
                    type = valueType,
                    alwaysVisible = true,
                    config = valueConfig.ifEmpty { null }
                ),
                required = kind != TrackingKind.TIMER
            )
        )

        // Open: a unit given with an entry joins the units (configWithOptionsAdded), so a tool
        // without units yet takes its first one from an entry
        if (kind == TrackingKind.NUMERIC) {
            fields.add(
                FixedField(
                    FieldDefinition(
                        name = "unit",
                        displayName = s.tool("field_unit"),
                        description = s.tool("schema_data_unit"),
                        type = FieldType.CHOICE,
                        alwaysVisible = true,
                        config = mapOf("options" to ChoiceSettings.storedOptions(TrackingConfig.units(config)), "open" to true)
                    )
                )
            )
        }

        return EntryFields(name = CoreFieldUsage.REQUIRED, timestamp = CoreFieldUsage.OPTIONAL, data = fields)
    }

    override fun getAIDetail(context: Context): String = Strings.`for`(tool = "tracking", context = context).tool("ai_detail")

    override fun getEntryMeanings(context: Context): app.treelune.core.fields.EntryMeanings {
        val s = Strings.`for`(tool = "tracking", context = context)
        return app.treelune.core.fields.EntryMeanings(
            name = s.tool("entry_name"),
            timestamp = s.tool("entry_timestamp"),
            extra = s.tool("entry_extra")
        )
    }

    /** A new unit given with a numeric entry joins the end of the units. */
    override fun configWithOptionsAdded(config: JSONObject, field: String, added: List<String>): JSONObject {
        check(field == "unit") { "tracking declares no open choice \"$field\"" }
        return JSONObject(config.toString()).put("units", org.json.JSONArray(TrackingConfig.units(config) + added))
    }

    override fun getDefaultIconName(): String {
        return "activity"
    }
    
    override fun getSuggestedIcons(): List<String> {
        return listOf("activity", "trending-up", "scale", "heart-pulse", "dumbbell", "droplet", "moon", "timer")
    }
    
    
    override fun getService(context: Context): ExecutableService {
        return app.treelune.core.services.ToolDataService(context)
    }
    
    override fun getDatabaseEntities(): List<Class<*>> {
        return listOf(ToolDataEntity::class.java)
    }
    
    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openEntry: app.treelune.core.tools.EntryToOpen?
    ) {
        TrackingScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
        )
    }
    
    
    /**
     * The config keys that say how to read a tracking entry: the kind of value (type), its
     * settings (value: a scale's bounds, a choice's options...), the units a numeric value can
     * be in, the shortcuts, and the user's fields.
     */
    @Composable
    override fun rememberTile(tool: app.treelune.core.database.entities.ToolInstance, open: (app.treelune.core.tools.EntryToOpen) -> Unit): app.treelune.core.tools.ToolTile =
        app.treelune.tools.tracking.ui.rememberTrackingTile(tool)

    override suspend fun settleEntries(entries: suspend () -> List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> =
        TrackingStopwatch.settle(entries(), writtenId)
}
