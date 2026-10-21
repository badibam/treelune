package app.treelune.core.schemas

import android.content.Context
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory

/**
 * A zone's settings, declared with the fields (docs/DATA.md): what a zone stores
 * beside its identity and its place in the list. Its schema, "zone_config", is generated from here,
 * and ZoneService checks every write against it.
 */
object ZoneSettings {

    const val SCHEMA_ID = "zone_config"

    /** Where the icon picker starts for a zone: the themes zones are usually made of. */
    val SUGGESTED_ICONS = listOf(
        "heart", "dumbbell", "briefcase", "house", "wallet", "graduation-cap",
        "users", "leaf", "book-open", "utensils", "plane", "music", "palette", "folder"
    )

    /** A zone's look (Looks): its icon optional, its tile on the home screen from ICON to CONDENSED. */
    val LOOK = app.treelune.core.look.LookSpec(
        nameKey = "name",
        nameLabel = "label_zone_name",
        schemaPrefix = "zone_schema_",
        modes = app.treelune.core.grid.ZonePositions.MODES,
        defaultMode = app.treelune.core.ui.DisplayMode.LINE,
        defaultIcon = null,
        suggestedIcons = SUGGESTED_ICONS
    )

    fun nodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        fun field(name: String, labelKey: String, type: FieldType, length: TextLength, required: Boolean = false) =
            SettingNode.Field(
                FieldDefinition(name, s.shared(labelKey), s.shared("zone_schema_$name"), type, false, mapOf("length" to length.name)),
                required = required
            )
        return listOf(
            // Its name, description, icon, colour, texture and mode, checked by Looks
            *app.treelune.core.look.Looks.nodes(LOOK, s::shared).toTypedArray(),
            // One of the zone groups of the main screen
            field("group", "label_zone_group", FieldType.TEXT, TextLength.SHORT),
            // The identity its tile opens a conversation with; checked to exist by the service
            field("identity_id", "label_zone_identity", FieldType.TEXT, TextLength.SHORT),
            // The groups the zone's tools are sorted into
            SettingNode.ListOf("tool_groups", s.shared("label_tool_groups"),
                SettingNode.Item.Value(FieldDefinition("tool_group", s.shared("label_tool_group"), s.shared("zone_schema_tool_groups"),
                    FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name))),
                distinct = true),
            // Whether the AI asks before changing what the zone holds; a person alone changes it
            SettingNode.Field(
                FieldDefinition("validate", s.shared("label_zone_validate"), s.shared("zone_schema_validate"), FieldType.BOOLEAN, false, null),
                required = true, default = false
            )
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("zone_config_schema_display_name"),
            description = s.shared("zone_config_schema_description"),
            category = SchemaCategory.ZONE_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(context), s::shared).toString()
        )
    }
}
