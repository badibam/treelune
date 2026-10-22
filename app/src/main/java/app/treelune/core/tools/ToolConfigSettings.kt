package app.treelune.core.tools

import android.content.Context
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory

/**
 * The config of a tool instance, declared with the fields: the settings every tool has, then
 * the ones its tool type declares (ToolTypeContract.getConfigSettings), side by side in one
 * object. Its schema is generated from here, and the service checks every config write against it.
 */
object ToolConfigSettings {

    /**
     * A tool's look (Looks): its icon its type's when it has none of its own, its tile in its
     * zone from ICON to FULL.
     */
    fun look(toolType: ToolTypeContract): app.treelune.core.look.LookSpec = app.treelune.core.look.LookSpec(
        nameKey = "name",
        nameLabel = "tools_config_label_name",
        schemaPrefix = "tools_base_schema_config_",
        modes = app.treelune.core.ui.DisplayMode.entries,
        defaultMode = app.treelune.core.ui.DisplayMode.valueOf(toolType.getDefaultDisplayMode()),
        defaultIcon = toolType.getDefaultIconName(),
        suggestedIcons = toolType.getSuggestedIcons()
    )

    /** The general settings every tool has, whatever its type. */
    private fun generalNodes(toolType: ToolTypeContract, context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val text: (String) -> String = s::shared
        return listOf(
            SettingNode.Section(text("tools_config_section_general_params"), app.treelune.core.look.Looks.nodes(look(toolType), text) + listOf(
                field("group", text("label_group"), FieldType.TEXT, text("tools_base_schema_config_group"),
                    config = mapOf("length" to TextLength.SHORT.name)),
                // Whether the AI asks before writing the tool's entries; a person alone changes it
                // (Protections). Its config is guarded by its zone's protection
                field(VALIDATE_DATA, text("tools_config_label_data_validation"), FieldType.BOOLEAN, text("tools_base_schema_config_validate_data"), default = false),
                field("always_send", text("tools_config_label_always_send"), FieldType.BOOLEAN, text("tools_base_schema_config_always_send"), default = false)
            ))
        )
    }

    /**
     * The user's own fields, which every tool can add to its entries, and whether their names
     * show beside their values wherever they are shown (CustomFieldsDisplay).
     */
    private fun extraFieldsNodes(toolType: ToolTypeContract, context: Context): List<SettingNode> {
        val text: (String) -> String = Strings.`for`(context = context)::shared
        // One frame: the switch says how the fields of the list below it show
        return listOf(SettingNode.Section(text("custom_fields_section_title"), listOf(
            field(SHOW_FIELD_LABELS, text("tools_config_label_show_field_labels"), FieldType.BOOLEAN,
                text("tools_base_schema_config_show_field_labels"), default = toolType.getDefaultShowFieldLabels()),
            // What they are for in the type is the entry's first level (EntryDescription), which comes with this schema
            SettingNode.ListOf("extra_fields", text("custom_fields_list"),
                SettingNode.Item.Of(FieldTypeSettings.definitionNodes(text)), fieldDefinitions = true,
                summary = listOf("display_name", "type"), description = text("tools_base_schema_config_extra_fields"))
        )))
    }

    /** The setting that says whether the user's fields show their names. */
    const val SHOW_FIELD_LABELS = "show_field_labels"

    /** Whether the tool's data is protected: the AI asks before writing its entries (docs/AI.md, « Levels »). */
    const val VALIDATE_DATA = "validate_data"

    /**
     * The whole config of a tool of [toolType]: the general settings, its type's, then the user's
     * own fields, which come after the entries' main field they add to — for a tool that takes
     * them only (takesUserFields).
     */
    fun nodes(toolType: ToolTypeContract, context: Context): List<SettingNode> =
        // Built once per tool type and language: it depends on nothing else, and a tile reads
        // its name and icon through it on every drawing (a chart's takes some 150 ms to build)
        declarations.getOrPut(toolType to context.resources.configuration.locales[0]) {
            generalNodes(toolType, context) + typeNodes(toolType, context) +
                (if (toolType.takesUserFields()) extraFieldsNodes(toolType, context) else emptyList())
        }

    /**
     * The settings [toolType] declares, each in a frame like the rest of the screen: those it puts
     * in no section of its own go together in one, titled with the type's name, first; its own
     * sections stay as they are, a frame inside a frame saying nothing more.
     */
    private fun typeNodes(toolType: ToolTypeContract, context: Context): List<SettingNode> {
        val declared = toolType.getConfigSettings(context)
        val loose = declared.filter { it !is SettingNode.Section }
        val framed = if (loose.isEmpty()) emptyList() else listOf(SettingNode.Section(toolType.getDisplayName(context), loose))
        return framed + declared.filterIsInstance<SettingNode.Section>()
    }

    private val declarations = java.util.concurrent.ConcurrentHashMap<Pair<ToolTypeContract, java.util.Locale>, List<SettingNode>>()

    /** What a new tool of [toolType] starts from: its declared defaults (SettingDefaults). */
    fun defaults(toolType: ToolTypeContract, context: Context): org.json.JSONObject =
        app.treelune.core.fields.settings.SettingDefaults.of(nodes(toolType, context))

    /**
     * [config] with every setting of the first level it leaves out at its declared default: a
     * default is also what a setting's absence means. Settings under a variant are left as
     * they are, since which ones apply depends on the option the config chose.
     */
    fun withDefaults(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): org.json.JSONObject {
        val filled = org.json.JSONObject(config.toString())
        app.treelune.core.fields.settings.SettingsSchemaGenerator.flatten(nodes(toolType, context))
            .filterIsInstance<SettingNode.Field>()
            .forEach { node -> node.default?.let { if (!filled.has(node.definition.name)) filled.put(node.definition.name, it) } }
        return filled
    }

    /** [config] of a tool of [toolType], read through its declaration (SettingValues). */
    fun read(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): app.treelune.core.fields.settings.SettingValues =
        app.treelune.core.fields.settings.SettingValues(nodes(toolType, context), config)

    /** [config] of a tool of [tooltype], read through its declaration. */
    fun read(tooltype: String, config: org.json.JSONObject, context: Context): app.treelune.core.fields.settings.SettingValues =
        read(ToolTypeManager.getToolType(tooltype) ?: error("Unknown tooltype $tooltype"), config, context)

    /** The schema a config of [toolType] is held to, under [id]. */
    fun schema(toolType: ToolTypeContract, id: String, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = id,
            displayName = toolType.getDisplayName(context),
            description = toolType.getUsage(context),
            category = SchemaCategory.TOOL_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(toolType, context), s::shared).toString()
        )
    }

    private fun field(name: String, label: String, type: FieldType, description: String?, required: Boolean = false,
                      default: Any? = null, config: Map<String, Any>? = null) =
        SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required, default = default)

    private fun choice(values: List<String>, labels: Map<String, String>): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, labels))
}
