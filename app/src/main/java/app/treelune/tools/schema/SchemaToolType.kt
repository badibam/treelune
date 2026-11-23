package app.treelune.tools.schema

import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.RowFields
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.themes.TagColor
import app.treelune.core.themes.Texture
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolTile
import app.treelune.core.tools.ToolTypeContract
import app.treelune.tools.schema.ui.SchemaScreen
import app.treelune.tools.schema.ui.rememberSchemaTile
import org.json.JSONObject

/**
 * A schema (docs/design/schema.md): boxes, arrows and nested groups, written by the person or by
 * the AI, drawn by the app. Its config is in the vocabulary of Mermaid's flowcharts, declared
 * setting by setting so that its form and schema are generated like any other. It keeps no entries.
 */
object SchemaToolType : ToolTypeContract {

    private fun s(context: Context) = Strings.`for`(tool = "schema", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getGuideConfig(context: Context): String = s(context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = s(context).tool("guide_screen")
    override fun getUsage(context: Context): String = s(context).tool("usage")
    override fun getAIDetail(context: Context): String = s(context).tool("ai_detail")
    override fun getFamily() = app.treelune.core.tools.ToolFamily.USE

    override fun getDefaultTileSize(): app.treelune.core.look.TileSize = app.treelune.core.look.TileSize.of(4, 4)
    override fun getItemSpec(): app.treelune.core.look.ItemSpec? = null
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "workflow"
    override fun getSuggestedIcons(): List<String> = listOf("workflow", "network", "waypoints", "git-fork", "share-2", "spline")

    override fun keepsEntries(): Boolean = false

    /** No entry: neither name nor date nor field. */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields =
        EntryFields(name = CoreFieldUsage.ABSENT, timestamp = CoreFieldUsage.ABSENT)

    private fun field(name: String, label: String, type: FieldType, description: String, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun short() = mapOf("length" to TextLength.SHORT.name)

    private fun choice(values: List<String>, labels: (String) -> String): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith(labels), emptyMap()))

    /** At most one color of the palette and one texture, by their names: the theme draws them. */
    private fun look(context: Context): List<SettingNode> {
        val s = s(context)
        val shared = Strings.`for`(context = context)
        val colors = TagColor.entries.map { it.name }
        // A box has no icon to make the ICON texture of
        val textures = Texture.entries.filter { it.hasCell }.map { it.name }
        return listOf(
            SettingNode.Field(field(SchemaKeys.COLOR, s.tool("field_color"), FieldType.CHOICE, s.tool("schema_color"), mapOf("options" to ChoiceSettings.storedOptions(
                colors, colors.associateWith { shared.shared("tag_color_${it.lowercase()}") }, TagColor.entries.associate { it.name to it })))),
            SettingNode.Field(field(Texture.KEY, s.tool("field_texture"), FieldType.CHOICE, s.tool("schema_texture"),
                choice(textures) { shared.shared("texture_${it.lowercase()}") }))
        )
    }

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        val things = listOf(ReferenceKind.ZONE, ReferenceKind.TOOL_INSTANCE, ReferenceKind.ENTRY, ReferenceKind.VARIABLE, ReferenceKind.EVENT).map { it.name }
        return listOf(
            SettingNode.Section(s.tool("section_schema"), listOf(
                SettingNode.Field(field(SchemaKeys.DIRECTION, s.tool("field_direction"), FieldType.CHOICE, s.tool("schema_direction"),
                    choice(SchemaDirection.entries.map { it.key }) { s.tool("direction_${it.lowercase()}") }), required = true, default = SchemaDirection.TOP_DOWN.key),
                SettingNode.ListOf(SchemaKeys.NODES, s.tool("field_nodes"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field(SchemaKeys.ID, s.tool("field_id"), FieldType.TEXT, s.tool("schema_node_id"), short()), required = true),
                    SettingNode.Field(field(SchemaKeys.LABEL, s.tool("field_label"), FieldType.TEXT, s.tool("schema_node_label"), short())),
                    SettingNode.Field(field(SchemaKeys.THING, s.tool("field_thing"), FieldType.REFERENCE, s.tool("schema_thing"),
                        mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to things)))),
                    SettingNode.Field(field(SchemaKeys.SHAPE, s.tool("field_shape"), FieldType.CHOICE, s.tool("schema_shape"),
                        choice(SchemaShape.entries.map { it.key }) { s.tool("shape_$it") }), default = SchemaShape.ROUNDED.key)
                ) + look(context), description = s.tool("schema_node")), summary = listOf(SchemaKeys.LABEL, SchemaKeys.ID), description = s.tool("schema_nodes")),
                SettingNode.ListOf(SchemaKeys.EDGES, s.tool("field_edges"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field(SchemaKeys.FROM, s.tool("field_from"), FieldType.TEXT, s.tool("schema_from"), short()), required = true, rowField = true),
                    SettingNode.Field(field(SchemaKeys.TO, s.tool("field_to"), FieldType.TEXT, s.tool("schema_to"), short()), required = true, rowField = true),
                    SettingNode.Field(field(SchemaKeys.LABEL, s.tool("field_edge_label"), FieldType.TEXT, s.tool("schema_edge_label"), short())),
                    SettingNode.Field(field(SchemaKeys.LINE, s.tool("field_line"), FieldType.CHOICE, s.tool("schema_line"),
                        choice(SchemaLine.entries.map { it.key }) { s.tool("line_$it") }), default = SchemaLine.NORMAL.key),
                    SettingNode.Field(field(SchemaKeys.HEAD, s.tool("field_head"), FieldType.BOOLEAN, s.tool("schema_head")), default = true)
                ), description = s.tool("schema_edge")), summary = listOf(SchemaKeys.FROM, SchemaKeys.TO, SchemaKeys.LABEL), description = s.tool("schema_edges")),
                SettingNode.ListOf(SchemaKeys.GROUPS, s.tool("field_groups"), SettingNode.Item.Of(listOf(
                    SettingNode.Field(field(SchemaKeys.ID, s.tool("field_id"), FieldType.TEXT, s.tool("schema_group_id"), short()), required = true),
                    SettingNode.Field(field(SchemaKeys.TITLE, s.tool("field_title"), FieldType.TEXT, s.tool("schema_title"), short()), required = true),
                    SettingNode.ListOf(SchemaKeys.MEMBERS, s.tool("field_members"),
                        SettingNode.Item.Value(field(SchemaKeys.MEMBERS, s.tool("field_member"), FieldType.TEXT, s.tool("schema_member"), short()), rowField = true),
                        description = s.tool("schema_members")),
                    SettingNode.Field(field(SchemaKeys.FOLDED, s.tool("field_folded"), FieldType.BOOLEAN, s.tool("schema_folded")))
                ) + look(context), description = s.tool("schema_group")), summary = listOf(SchemaKeys.TITLE), description = s.tool("schema_groups"))
            ))
        )
    }

    override fun getRowFields(): RowFields = SchemaRowFields

    override suspend fun refuseConfig(config: JSONObject, context: Context): String? = try {
        SchemaSpec.of(config) { s(context).tool(it) }
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    override fun getService(context: Context): ExecutableService? = null

    override fun getDatabaseEntities(): List<Class<*>> = emptyList()

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntry: EntryToOpen?) {
        SchemaScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile = rememberSchemaTile(tool)
}

/**
 * What an arrow's ends and a group's members choose among, offered by the form: the boxes, each by
 * its text, and in a group the other groups too. What no longer exists is named at the save.
 */
object SchemaRowFields : RowFields {

    override suspend fun at(root: JSONObject, path: List<Any>, context: Context): Map<String, FieldDefinition> {
        val boxes = items(root, SchemaKeys.NODES, SchemaKeys.LABEL)
        if (path.firstOrNull() != SchemaKeys.GROUPS) return boxes
        val self = (path.getOrNull(1) as? Int)?.let { root.optJSONArray(SchemaKeys.GROUPS)?.optJSONObject(it)?.optString(SchemaKeys.ID) }
        return boxes + items(root, SchemaKeys.GROUPS, SchemaKeys.TITLE).filterKeys { it != self }
    }

    override fun dependsOn(root: JSONObject, path: List<Any>): Any =
        "${root.optJSONArray(SchemaKeys.NODES)}|${root.optJSONArray(SchemaKeys.GROUPS)}|${path.take(2)}"

    private fun items(root: JSONObject, list: String, labelKey: String): Map<String, FieldDefinition> {
        val array = root.optJSONArray(list) ?: return emptyMap()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
            val id = item.optString(SchemaKeys.ID).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val label = item.optString(labelKey).trim().ifEmpty { id }
            id to FieldDefinition(id, label, null, FieldType.TEXT, false, null)
        }.toMap()
    }
}
