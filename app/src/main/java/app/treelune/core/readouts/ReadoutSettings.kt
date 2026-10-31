package app.treelune.core.readouts

import android.content.Context
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.look.LookSpec
import app.treelune.core.look.Looks
import app.treelune.core.strings.Strings
import app.treelune.core.themes.IconColor
import app.treelune.core.ui.DisplayMode
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory
import org.json.JSONObject

/**
 * A readout's settings, declared with the fields (docs/design/readouts.md): its look and its
 * group, as any tile's; its sentence, where `{name}` stands for one of its values; its values, each
 * a name and a term read when it is shown; the thing a touch opens; its colours, each a condition
 * on its values put as on a row (`{"field": "done"}`, ReadoutRows), a colour, and whether it
 * signals; the places it rises to besides its own tile. Not its zone (its place).
 */
object ReadoutSettings {

    const val SCHEMA_ID = "readout_settings"
    const val DEFAULT_ICON = "gauge"

    const val TEXT = "text"
    const val VALUES = "values"
    const val NAME = "name"
    const val VALUE = "value"
    const val TARGET = "target"
    const val COLORS = "colors"
    const val WHEN = "when"
    const val COLOR = "color"
    const val SIGNAL = "signal"
    const val PLACES = "places"

    /** Where a readout rises to, besides its own tile, in the order of the tree, from its group up. */
    enum class Place(val key: String) {
        GROUP_HEAD("group_head"),
        ZONE_HEAD("zone_head"),
        ZONE_TILE("zone_tile"),
        ZONE_GROUP_HEAD("zone_group_head"),
        HOME_HEAD("home_head");

        companion object {
            fun of(key: String): Place? = entries.firstOrNull { it.key == key }
        }
    }

    /** A readout's look: an icon always, its sentence from LINE on, as a variable shows its value. */
    val LOOK = LookSpec(
        nameKey = "name",
        nameLabel = "label_name",
        schemaPrefix = "readout_schema_",
        modes = listOf(DisplayMode.ICON, DisplayMode.MINIMAL, DisplayMode.LINE, DisplayMode.CONDENSED),
        defaultMode = DisplayMode.LINE,
        defaultIcon = DEFAULT_ICON,
        suggestedIcons = listOf("gauge", "eye", "bell", "flag", "list-checks", "circle-check", "target", "hourglass")
    )

    fun nodes(context: Context): List<SettingNode> = head(context) + body(context)

    /** Its look and its group. */
    fun head(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        return Looks.nodes(LOOK, s::shared) + SettingNode.Field(FieldDefinition("group", s.shared("label_group"), s.shared("readout_schema_group"), FieldType.TEXT, false,
            mapOf("length" to TextLength.SHORT.name)))
    }

    /** What it says, what it opens, how it colours itself and where it rises to. */
    fun body(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        return listOf(
            SettingNode.Field(FieldDefinition(TEXT, s.shared("readout_text"), s.shared("readout_schema_text"), FieldType.TEXT, false,
                mapOf("length" to TextLength.SHORT.name)), required = true),
            SettingNode.ListOf(VALUES, s.shared("readout_values"), SettingNode.Item.Of(listOf(
                SettingNode.Field(FieldDefinition(NAME, s.shared("readout_value_name"), s.shared("readout_schema_value_name"), FieldType.TEXT, false,
                    mapOf("length" to TextLength.SHORT.name)), required = true),
                SettingNode.Term(VALUE, s.shared("readout_value_term"), setOf(app.treelune.core.terms.Term.Kind.CONSTANT, app.treelune.core.terms.Term.Kind.VARIABLE, app.treelune.core.terms.Term.Kind.READING),
                    s.shared("readout_reference"), s.shared("readout_empty_period"), required = true)
            ), description = s.shared("readout_schema_value")), summary = listOf(NAME), description = s.shared("readout_schema_values")),
            SettingNode.Field(FieldDefinition(TARGET, s.shared("readout_target"), s.shared("readout_schema_target"), FieldType.REFERENCE, false,
                mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.APP.name, ReferenceKind.ZONE.name, ReferenceKind.TOOL_INSTANCE.name)))), required = true),
            SettingNode.ListOf(COLORS, s.shared("readout_colors"), SettingNode.Item.Of(listOf(
                SettingNode.Condition(WHEN, s.shared("readout_color_when"), reference = "", emptyPeriod = null, required = true, onRow = true),
                SettingNode.Field(FieldDefinition(COLOR, s.shared("readout_color"), s.shared("readout_schema_color"), FieldType.CHOICE, false,
                    mapOf("options" to IconColor.options(s::shared))), required = true),
                SettingNode.Field(FieldDefinition(SIGNAL, s.shared("readout_signal"), s.shared("readout_schema_signal"), FieldType.BOOLEAN, false, null), default = false)
            ), description = s.shared("readout_schema_color_line")), summary = listOf(COLOR), description = s.shared("readout_schema_colors")),
            SettingNode.Field(FieldDefinition(PLACES, s.shared("readout_places"), s.shared("readout_schema_places"), FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(Place.entries.map { it.key }, Place.entries.associate { it.key to s.shared("readout_place_${it.key}") })) + ("multiple" to true)))
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(SCHEMA_ID, s.shared("readout_display_name"), "", SchemaCategory.READOUT_CONFIG, SettingsSchemaGenerator.generate(nodes(context), s::shared).toString())
    }

    /** The settings [readout] stores, as their declaration describes them: the form create and update take. */
    fun of(readout: ReadoutEntity): JSONObject = JSONObject(readout.settingsJson).apply {
        put("name", readout.name)
        readout.description?.let { put(Looks.DESCRIPTION, it) }
        put(Looks.ICON, readout.iconName)
        readout.iconColor?.let { put(IconColor.KEY, it) }
        readout.texture?.let { put(app.treelune.core.themes.Texture.KEY, it) }
        put(Looks.DISPLAY_MODE, readout.displayMode)
        readout.group?.let { put("group", it) }
    }

    /** The keys of the body, which settings_json stores; the others are columns. */
    val BODY_KEYS = listOf(TEXT, VALUES, TARGET, COLORS, PLACES)

    /** The places [settings] names, in the order of the tree. */
    fun places(settings: JSONObject): Set<Place> {
        val stored = settings.optJSONArray(PLACES) ?: return emptySet()
        val keys = (0 until stored.length()).map { stored.optString(it) }.toSet()
        return Place.entries.filter { it.key in keys }.toSet()
    }
}
