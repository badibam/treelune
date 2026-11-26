package app.treelune.tools.structured

import android.content.Context
import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.services.ExecutableService
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeContract
import app.treelune.tools.structured.ui.StructuredScreen
import org.json.JSONObject

/**
 * Records: records of what the user declares (foods and their calories, books, contacts),
 * each found by its name (docs/TOOLS.md, « Records »).
 *
 * Its columns are the user's fields (extra), declared in the instance's config; the tool type
 * declares none, and an entry has no date. The name is required and unique in the instance, the
 * case and the spaces around not counted: it is what the AI, a REFERENCE and an import find a
 * sheet by. What makes it a tool is its screen: a table, a sheet per entry, a shared filter header.
 */
object StructuredToolType : ToolTypeContract {

    /** How many of the user's fields the table shows as columns, after the name. */
    const val TABLE_COLUMNS = "table_columns"

    /** How long a sheet's name may be: one of [NAME_LENGTHS], a few words by default. */
    const val NAME_LENGTH = "name_length"

    /** The order its table opens in (the Sort brick): its keys, the name A to Z without them. */
    const val DEFAULT_SORT = "default_sort"

    /** The lengths a name may take: a text field's, but those a name found by can hold. */
    private val NAME_LENGTHS = listOf(app.treelune.core.fields.TextLength.SHORT, app.treelune.core.fields.TextLength.MEDIUM, app.treelune.core.fields.TextLength.LONG)

    /** The length of a sheet's name in [config], its default when the config says none. */
    fun nameLength(config: JSONObject, context: Context): app.treelune.core.fields.TextLength =
        app.treelune.core.fields.TextLength.valueOf(app.treelune.core.tools.ToolConfigSettings.read(this, config, context).string(NAME_LENGTH)!!)

    override fun getDisplayName(context: Context): String =
        Strings.`for`(tool = "structured", context = context).tool("display_name")

    override fun getGuideConfig(context: Context): String = Strings.`for`(tool = "structured", context = context).tool("guide_config")
    override fun getGuideScreen(context: Context): String = Strings.`for`(tool = "structured", context = context).tool("guide_screen")
    override fun getUsage(context: Context): String =
        Strings.`for`(tool = "structured", context = context).tool("usage")

    override fun getFamily() = app.treelune.core.tools.ToolFamily.NOTE

    override fun getDefaultTileSize(): app.treelune.core.look.TileSize = app.treelune.core.look.TileSize.of(4, 1)
    override fun getItemSpec(): app.treelune.core.look.ItemSpec? = app.treelune.core.look.ItemSpec.LINES

    override fun getDefaultShowFieldLabels(): Boolean = true

    override fun getConfigSettings(context: Context): List<app.treelune.core.fields.settings.SettingNode> {
        val s = Strings.`for`(tool = "structured", context = context)
        return listOf(app.treelune.core.fields.settings.SettingNode.Field(
            FieldDefinition(TABLE_COLUMNS, s.tool("field_table_columns"), s.tool("schema_config_table_columns"),
                FieldType.NUMERIC, false, mapOf("min" to 0, "decimals" to 0)),
            required = true,
            default = 2
        ), app.treelune.core.fields.settings.SettingNode.Field(
            FieldDefinition(NAME_LENGTH, s.tool("field_name_length"), s.tool("schema_config_name_length"),
                FieldType.CHOICE, false, mapOf("options" to app.treelune.core.fields.ChoiceSettings.storedOptions(
                    NAME_LENGTHS.map { it.name },
                    NAME_LENGTHS.associate { it.name to s.shared("text_length_${it.name.lowercase()}_display_name") }))),
            required = true,
            default = app.treelune.core.fields.TextLength.SHORT.name
        ), app.treelune.core.fields.settings.SettingNode.Sort(DEFAULT_SORT, s.tool("field_default_sort"), s.tool("schema_config_default_sort")))
    }

    /** Its table's order when it opens: the config's default sort, the name A to Z without one. */
    fun defaultSort(config: JSONObject, text: (String) -> String): List<app.treelune.core.fields.SortKey> =
        config.optJSONArray(DEFAULT_SORT)?.takeIf { it.length() > 0 }?.let { app.treelune.core.fields.EntrySort.read(it, text) }
            ?: listOf(app.treelune.core.fields.SortKey("name", ascending = true))

    /** The default sort names fields of the sheets with an order, each once (EntrySort.parse): a field removed since is named. */
    override suspend fun refuseConfig(config: JSONObject, toolId: String?, context: Context): String? {
        val sort = config.optJSONArray(DEFAULT_SORT)?.takeIf { it.length() > 0 } ?: return null
        val s = Strings.`for`(tool = "structured", context = context)
        val fields = app.treelune.core.fields.EntryFilters.sortableFields(getEntryFields(config, context),
            config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()) { s.shared(it) }
        return (app.treelune.core.fields.EntrySort.parse(sort, fields) { s.shared(it) } as? app.treelune.core.fields.EntrySort.Parsed.Refused)
            ?.let { s.shared("settings_count").format(s.tool("field_default_sort"), it.error) }
    }

    /** A sheet: its name, required, unique, as long as the config says; no date; nothing of the tool type in data. */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields = EntryFields(
        name = CoreFieldUsage.REQUIRED,
        nameUnique = true,
        nameLength = nameLength(config, context),
        timestamp = CoreFieldUsage.ABSENT
    )

    override fun getEntryMeanings(context: Context): app.treelune.core.fields.EntryMeanings {
        val s = Strings.`for`(tool = "structured", context = context)
        return app.treelune.core.fields.EntryMeanings(
            name = s.tool("entry_name"),
            extra = s.tool("entry_extra")
        )
    }

    override fun getDefaultIconName(): String = "table"

    override fun getSuggestedIcons(): List<String> = listOf("table", "database", "book-open", "apple", "contact")

    override fun getService(context: Context): ExecutableService = app.treelune.core.services.ToolDataService(context)

    override fun getDatabaseEntities(): List<Class<*>> = listOf(ToolDataEntity::class.java)

    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit,
        openAt: app.treelune.core.tools.OpenAt?
    ) {
        StructuredScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick, openAt = openAt)
    }

    override val tileSecond = app.treelune.core.tools.IconSecond.GESTURE

    @Composable
    override fun rememberTile(tool: ToolInstance, open: (app.treelune.core.tools.OpenAt) -> Unit): app.treelune.core.tools.ToolTile =
        app.treelune.tools.structured.ui.rememberStructuredTile(tool, open)
}
