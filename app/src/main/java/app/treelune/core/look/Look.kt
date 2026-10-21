package app.treelune.core.look

import android.content.Context
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.icons.Icons
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.Texture
import app.treelune.core.ui.DisplayMode
import org.json.JSONObject

/**
 * How a thing of the app shows itself on its tile and its page (docs/BRICKS.md, « Look »): a
 * zone, a tool, a variable. Its name, a description, an icon, the icon's colour, a texture, and
 * the display mode its tile takes in its grid. Each owner keeps it where it keeps the rest — a
 * zone and a variable in their columns, a tool in its config — under the same keys, read and
 * checked here.
 */
data class Look(
    val name: String,
    val description: String?,
    val iconName: String?,
    val iconColor: String?,
    val texture: String?,
    val displayMode: DisplayMode
)

/**
 * What an owner's look allows.
 *
 * @property nameKey Where its name is kept: `name`, or `label` for a variable, whose `name` is the
 *   one formulas write
 * @property nameLabel The shared string that names the name in a form
 * @property schemaPrefix The shared strings that tell the AI each setting: `<prefix>name`,
 *   `<prefix>description`, `<prefix>icon_name`, `<prefix>icon_color`, `<prefix>texture`,
 *   `<prefix>display_mode`
 * @property modes The display modes its tile may take, from the smallest
 * @property defaultMode The mode a new one takes
 * @property defaultIcon The icon a new one takes, and the one it shows without its own; null when
 *   an icon is optional, the tile its name alone without one
 * @property suggestedIcons Where the icon picker starts
 */
data class LookSpec(
    val nameKey: String,
    val nameLabel: String,
    val schemaPrefix: String,
    val modes: List<DisplayMode>,
    val defaultMode: DisplayMode,
    val defaultIcon: String?,
    val suggestedIcons: List<String>
)

/** The look's settings, read and checked the same way for every owner. */
object Looks {

    const val DESCRIPTION = "description"
    const val ICON = "icon_name"
    const val DISPLAY_MODE = "display_mode"

    /** The keys a look of [spec] is kept under, beside the owner's other settings. */
    fun keys(spec: LookSpec): List<String> = listOf(spec.nameKey, DESCRIPTION, ICON, IconColor.KEY, Texture.KEY, DISPLAY_MODE)

    /**
     * The look's settings, declared with the fields: the form and the AI's schema are generated
     * from them. The icon is checked against the icon index, the colour and the texture with
     * their names, by [check].
     */
    fun nodes(spec: LookSpec, text: (String) -> String): List<SettingNode> {
        fun field(name: String, label: String, type: FieldType, config: Map<String, Any>, required: Boolean = false, default: Any? = null) =
            SettingNode.Field(FieldDefinition(name, label, text("${spec.schemaPrefix}$name"), type, false, config), required = required, default = default)
        val modes = spec.modes.map { it.name }
        return listOf(
            field(spec.nameKey, text(spec.nameLabel), FieldType.TEXT, mapOf("length" to TextLength.SHORT.name), required = true),
            field(DESCRIPTION, text("label_description"), FieldType.TEXT, mapOf("length" to TextLength.MEDIUM.name)),
            field(ICON, text("label_icon"), FieldType.TEXT, mapOf("length" to TextLength.SHORT.name), default = spec.defaultIcon),
            field(IconColor.KEY, text("label_icon_color"), FieldType.CHOICE, mapOf("options" to IconColor.options(text))),
            field(Texture.KEY, text("label_texture"), FieldType.CHOICE, mapOf("options" to Texture.options(text))),
            field(DISPLAY_MODE, text("tools_config_label_display_mode"), FieldType.CHOICE,
                mapOf("options" to ChoiceSettings.storedOptions(modes, modes.associateWith { text("tools_config_display_${it.lowercase()}") })),
                required = true, default = spec.defaultMode.name)
        )
    }

    /** The look kept in [values], a missing icon read as [LookSpec.defaultIcon]. */
    fun read(spec: LookSpec, values: JSONObject): Look = Look(
        name = values.optString(spec.nameKey),
        description = values.text(DESCRIPTION),
        iconName = values.text(ICON) ?: spec.defaultIcon,
        iconColor = values.text(IconColor.KEY),
        texture = values.text(Texture.KEY),
        displayMode = values.text(DISPLAY_MODE)?.let { DisplayMode.valueOf(it) } ?: spec.defaultMode
    )

    /** What checking a look comes to: kept, its icon maybe stored under its current name, or refused. */
    sealed interface Checked {
        data class Kept(val renamedFrom: String? = null, val renamedTo: String? = null) : Checked {
            /** Said in a write's result when a former icon name was stored under the current one. */
            fun report(): Map<String, Any> =
                if (renamedFrom != null && renamedTo != null) mapOf("icon_renamed" to mapOf("from" to renamedFrom, "to" to renamedTo)) else emptyMap()
        }
        data class Refused(val message: String) : Checked
    }

    /**
     * The look in [values] checked and brought to its stored form, in place: a description, an
     * icon, a colour or a texture given as null or blank is taken out (how a write empties it); an
     * icon is a Lucide name, a former one stored under the name it became, one that names no
     * icon refused; a colour and a texture are of their names; a texture ICON needs an icon to
     * repeat, the owner's or its default; the mode is one [spec] allows.
     *
     * Only an icon that changes from [before]'s is checked: a thing saved again with the icon it
     * already had stays savable.
     */
    fun check(spec: LookSpec, values: JSONObject, before: JSONObject?, context: Context): Checked {
        val s = app.treelune.core.strings.Strings.`for`(context = context)
        listOf(DESCRIPTION, ICON, IconColor.KEY, Texture.KEY).forEach { key -> if (values.has(key) && values.text(key) == null) values.remove(key) }
        var kept = Checked.Kept()
        values.text(ICON)?.takeIf { it != before?.text(ICON) }?.let { given ->
            val stored = Icons.storedName(context, given) ?: return Checked.Refused(s.shared("service_error_icon_unknown").format(given))
            if (stored != given) {
                values.put(ICON, stored)
                kept = Checked.Kept(renamedFrom = given, renamedTo = stored)
            }
        }
        val color = values.text(IconColor.KEY)
        if (!IconColor.accepts(color)) return Checked.Refused(s.shared("service_error_icon_color_unknown").format(color, IconColor.NAMES.joinToString(", ")))
        Texture.refusal(values.text(Texture.KEY), values.text(ICON) ?: spec.defaultIcon.orEmpty(), s::shared)?.let { return Checked.Refused(it) }
        values.text(DISPLAY_MODE)?.let { mode ->
            if (spec.modes.none { it.name == mode }) {
                return Checked.Refused(s.shared("look_error_display_mode").format(mode, spec.modes.joinToString(", ") { it.name }))
            }
        }
        return kept
    }

    /** A text setting as kept: null when absent, JSON null or blank. */
    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
