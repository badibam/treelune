package app.treelune.core.fields.settings

import app.treelune.core.fields.AI_READ_ONLY
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.DurationUnits
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ImageField
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.TextLength
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.themes.TagColor

/**
 * The settings of a field, declared with the fields themselves: what a field definition holds
 * (an element of a config's extra_fields), and what each field type's "config" holds.
 *
 * The schema of a field definition, its checking and what the AI reads of it are generated from
 * here (SettingsSchemaGenerator); no field type's settings are written by hand anywhere else.
 */
object FieldTypeSettings {

    /** The decimals a numeric setting takes: a bound or a step of a field. */
    private const val SETTING_DECIMALS = 2

    /**
     * A field definition: its common settings, then a variant on its type bringing that type's
     * "config".
     *
     * [types] narrows the variant to some types, for the schema of a definition of one type.
     * [labels] is how long the texts shown for the field may be (its name, an option's label, a
     * bound's): short under a tool's column, longer for a question the AI asks.
     */
    fun definitionNodes(text: (String) -> String, types: List<FieldType> = FieldType.entries, labels: TextLength = TextLength.SHORT): List<SettingNode> = listOf(
        // Made by the app from the label when the field is created, then sent back unchanged to
        // name the field: its format is checked by the service (FieldConfigValidator)
        field("name", "label_name", FieldType.TEXT, text, description = "field_type_schema_name_description",
            config = mapOf("length" to TextLength.SHORT.name)).copy(systemWritten = true),
        field("display_name", "custom_fields_display_name", FieldType.TEXT, text, required = true,
            description = "field_type_schema_display_name_description", config = mapOf("length" to labels.name)),
        field("description", "custom_fields_description", FieldType.TEXT, text, description = "field_type_schema_field_description",
            config = mapOf("length" to TextLength.MEDIUM.name)),
        field("always_visible", "custom_fields_always_visible", FieldType.BOOLEAN, text, default = false,
            description = "field_type_schema_always_visible_description"),
        aiReadOnly(text),
        SettingNode.Variant(
            selector = field("type", "custom_fields_type", FieldType.CHOICE, text, required = true,
                description = "field_type_schema_type_description",
                config = choice(types.map { it.name }, types.associate { it.name to text("field_type_${it.name.lowercase()}_display_name") })),
            cases = types.associate { type -> type.name to caseNodes(type, text, labels) },
            descriptions = typeMeanings(types, text)
        )
    )

    /**
     * What a field of [type] adds to its definition: its settings, if the type has any. A number
     * or a range the user adds measures in one unit, set with the field.
     */
    private fun caseNodes(type: FieldType, text: (String) -> String, labels: TextLength): List<SettingNode> {
        val config = configGroup(type, text, labels)
        // A fixed day or instant is hardly a suggestion for every new entry: dates have none, nor
        // a reference, one thing for all the entries to come, nor images, each owned by one entry
        val default = if (type in setOf(FieldType.DATE, FieldType.DATETIME, FieldType.REFERENCE, FieldType.IMAGE)) null
            else field("default_value", "field_default_value", type, text, description = "field_type_schema_default_value_description")
                .copy(valueOfDefined = true)
        return listOfNotNull(config, default)
    }

    /**
     * Whether the AI only reads the field (FieldDefinition.aiReadOnly), for a field a person
     * enters: the AI's writes that hold it are refused, the rest of the app's are not.
     */
    private fun aiReadOnly(text: (String) -> String) = field(AI_READ_ONLY, "field_ai_read_only", FieldType.BOOLEAN, text,
        default = false, description = "field_ai_read_only_description")

    /**
     * A value declared apart from a tool's fields, `{"type", "config"}`: its type among [types],
     * that type's settings, and whether the AI only reads it, once a type is chosen. A goal's
     * entered criterion is one.
     */
    fun valueNodes(text: (String) -> String, types: List<FieldType>): List<SettingNode> = listOf(
        SettingNode.Variant(
            selector = field("type", "custom_fields_type", FieldType.CHOICE, text, required = true,
                description = "field_type_schema_type_description",
                config = choice(types.map { it.name }, types.associate { it.name to text("field_type_${it.name.lowercase()}_display_name") })),
            cases = types.associate { type -> type.name to listOfNotNull(configGroup(type, text), aiReadOnly(text)) },
            descriptions = typeMeanings(types, text)
        )
    )

    /** What a value of each of [types] is, by type: the options of a field's type. */
    private fun typeMeanings(types: List<FieldType>, text: (String) -> String): Map<String, String> =
        types.associate { it.name to text("field_type_${it.name.lowercase()}_description") }

    /** The "config" of a value of [type], its unit first for a number or a range; null when the type has no settings. */
    private fun configGroup(type: FieldType, text: (String) -> String, labels: TextLength = TextLength.SHORT): SettingNode.Group? {
        val settings = listOfNotNull(if (type == FieldType.NUMERIC || type == FieldType.RANGE) unit(text) else null) +
            configNodes(type, text, labels)
        // Named by the type, whose settings they are: a choice's options, a number's unit. A number
        // or a range needs its decimals, a scale its bounds and a choice its options: their
        // config is required
        return if (settings.isEmpty()) null else SettingNode.Group("config", text("field_config_section_title").format(text("field_type_${type.name.lowercase()}_display_name")), settings,
            required = type in setOf(FieldType.NUMERIC, FieldType.RANGE, FieldType.SCALE, FieldType.CHOICE, FieldType.REFERENCE),
            description = text("field_type_schema_config_description"))
    }

    /**
     * The settings of a value of [type]: how it is made (a number's decimals, a scale's bounds, a
     * choice's options...). What it measures is not among them: see [unit].
     */
    fun configNodes(type: FieldType, text: (String) -> String): List<SettingNode> = configNodes(type, text, TextLength.SHORT)

    /** [configNodes] whose shown texts (an option's label, a bound's) may be [labels] long. */
    private fun configNodes(type: FieldType, text: (String) -> String, labels: TextLength): List<SettingNode> = when (type) {
        FieldType.TEXT -> listOf(
            field("length", "field_config_text_length", FieldType.CHOICE, text, default = TextLength.UNLIMITED.name,
                description = "field_type_text_length_description",
                config = choice(TextLength.entries.map { it.name },
                    TextLength.entries.associate { it.name to text("text_length_${it.name.lowercase()}_display_name") }))
        )
        FieldType.NUMERIC -> listOf(
            number("min", "field_config_min", text, description = "field_type_min_description"),
            number("max", "field_config_max", text, description = "field_type_max_description"),
            wholeNumber("decimals", "field_config_decimals", text, default = 0, required = true)
        )
        FieldType.SCALE -> listOf(
            number("min", "field_config_min", text, required = true, description = "field_type_scale_min_description"),
            number("max", "field_config_max", text, required = true, description = "field_type_scale_max_description"),
            label("min_label", "field_config_min_label", text, labels, "field_type_scale_min_label_description"),
            label("max_label", "field_config_max_label", text, labels, "field_type_scale_max_label_description"),
            number("step", "field_config_step", text, default = 1, description = "field_type_scale_step_description")
        )
        FieldType.CHOICE -> listOf(
            SettingNode.ListOf(
                name = "options",
                label = text("field_config_options"),
                item = SettingNode.Item.Of(listOf(
                    field("value", "field_config_option_value", FieldType.TEXT, text, required = true,
                        description = "field_type_option_value_description", config = mapOf("length" to TextLength.SHORT.name)),
                    label("label", "field_config_option_label", text, labels, "field_type_option_label_description"),
                    // Each color is its own swatch, wherever the value shows
                    field("color", "field_config_option_color", FieldType.CHOICE, text, description = "field_type_option_color_description",
                        config = choice(TagColor.entries.map { it.name },
                            labels = TagColor.entries.associate { it.name to text("tag_color_${it.name.lowercase()}") },
                            colors = TagColor.entries.associateBy { it.name }))
                ), description = text("field_type_option_description")),
                description = text("field_type_options_description"),
                required = true,
                minItems = 2,
                distinct = true,
                summary = listOf("value", "label", "color")
            ),
            flag("multiple", "field_config_multiple", "field_type_choice_multiple_description", text),
            flag("ordered", "field_config_ordered", "field_type_choice_ordered_description", text),
            flag("open", "field_config_open", "field_type_choice_open_description", text)
        )
        FieldType.BOOLEAN -> listOf(
            label("true_label", "field_config_true_label", text, labels, "field_type_true_label_description"),
            label("false_label", "field_config_false_label", text, labels, "field_type_false_label_description")
        )
        FieldType.RANGE -> listOf(
            number("min", "field_config_min", text, description = "field_type_range_min_description"),
            number("max", "field_config_max", text, description = "field_type_range_max_description"),
            wholeNumber("decimals", "field_config_decimals", text, default = 0, required = true)
        )
        FieldType.DATE -> emptyList()
        FieldType.TIME -> listOf(clockFormat("format", text))
        FieldType.DATETIME -> listOf(clockFormat("time_format", text))
        FieldType.DURATION -> listOf(
            field(DurationUnits.KEY, "field_config_duration_units", FieldType.CHOICE, text,
                default = DurationUnits.DEFAULT.name, description = "field_type_duration_units_description",
                config = choice(DurationUnits.entries.map { it.name }, DurationUnits.entries.associate { it.name to text("duration_units_${it.name.lowercase()}") }))
        )
        FieldType.REFERENCE -> listOf(
            SettingNode.Group(ReferenceTarget.TARGET, text("field_config_reference_target"), listOf(
                field(ReferenceTarget.KINDS, "field_config_reference_kinds", FieldType.CHOICE, text, required = true,
                    description = "field_type_reference_kinds_description",
                    config = choice(ReferenceKind.entries.map { it.name },
                        ReferenceKind.entries.associate { it.name to text("reference_kind_${it.name.lowercase()}") }) + ("multiple" to true)),
                // The tools whose entries it takes, each a reference to a tool instance
                SettingNode.ListOf(
                    name = ReferenceTarget.TOOL_INSTANCES,
                    label = text("field_config_reference_tool_instances"),
                    item = SettingNode.Item.Value(FieldDefinition(
                        name = "tool_instance", displayName = text("reference_kind_tool_instance"),
                        description = text("field_type_reference_tool_instances_description"),
                        type = FieldType.REFERENCE, alwaysVisible = false,
                        config = mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.TOOL_INSTANCE.name)))
                    )),
                    distinct = true,
                    shownWith = SettingNode.ShownWith(ReferenceTarget.KINDS, ReferenceKind.ENTRY.name),
                    description = text("field_type_reference_tool_instances_list_description")
                )
            ), required = true, description = text("field_type_reference_target_description"))
        )
        FieldType.IMAGE -> listOf(
            field(ImageField.MAX, "field_config_image_max", FieldType.NUMERIC, text,
                description = "field_type_image_max_description", config = mapOf("min" to 1, "decimals" to 0))
        )
    }

    private fun field(
        name: String,
        labelKey: String,
        type: FieldType,
        text: (String) -> String,
        required: Boolean = false,
        default: Any? = null,
        description: String? = null,
        config: Map<String, Any>? = null
    ) = SettingNode.Field(
        FieldDefinition(name = name, displayName = text(labelKey), description = description?.let(text),
            type = type, alwaysVisible = false, config = config),
        required = required,
        default = default
    )

    private fun choice(values: List<String>, labels: Map<String, String> = emptyMap(), colors: Map<String, TagColor> = emptyMap()): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, labels, colors))

    /** A setting that is a number, a bound or a step, with the decimals a setting may need. */
    private fun number(name: String, labelKey: String, text: (String) -> String, required: Boolean = false, default: Any? = null, description: String? = null) =
        field(name, labelKey, FieldType.NUMERIC, text, required = required, default = default, description = description, config = mapOf("decimals" to SETTING_DECIMALS))

    private fun wholeNumber(name: String, labelKey: String, text: (String) -> String, default: Int, required: Boolean = false) =
        field(name, labelKey, FieldType.NUMERIC, text, required = required, default = default,
            description = "field_type_decimals_description", config = mapOf("min" to 0, "decimals" to 0))

    private fun label(name: String, labelKey: String, text: (String) -> String, length: TextLength = TextLength.SHORT, description: String? = null) =
        field(name, labelKey, FieldType.TEXT, text, description = description, config = mapOf("length" to length.name))

    /**
     * The one unit a number or a range is in, set once for all its values ("km"), stored as "unit"
     * beside the value's settings. Whoever declares such a value adds it when its values share
     * a unit; a value whose unit changes from one entry to the next records it with the entry.
     */
    private fun unit(text: (String) -> String) = label("unit", "field_config_unit", text, description = "field_type_unit_description")

    private fun flag(name: String, labelKey: String, descriptionKey: String, text: (String) -> String) =
        field(name, labelKey, FieldType.BOOLEAN, text, default = false, description = descriptionKey)

    private fun clockFormat(name: String, text: (String) -> String) =
        field(name, "field_config_time_format", FieldType.CHOICE, text, default = "24h",
            description = "field_type_time_format_description", config = choice(listOf("24h", "12h")))
}
