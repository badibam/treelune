package app.treelune.core.look.ui

import androidx.compose.runtime.Composable
import app.treelune.core.fields.settings.SettingEditor
import app.treelune.core.look.LookSpec
import app.treelune.core.look.Looks
import app.treelune.core.strings.StringsContext
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.Texture
import app.treelune.core.ui.components.IconSelector
import org.json.JSONObject

/**
 * The editors of a look's icon, colour and texture in a settings form (SettingsForm), the same
 * for every owner (Looks): the icon picker, the owner's suggestions first; the colour and the
 * texture each on a page of the form, shown on the icon the owner will have. [values] holds the
 * form's settings, [onValues] replaces them.
 *
 * An owner with a default icon shows it while it has none of its own, and an icon chosen can be
 * taken away, bringing the default back. An owner whose icon is optional loses it, its tile its
 * name alone, and an ICON texture with it, having nothing left to repeat.
 */
fun lookEditors(spec: LookSpec, values: JSONObject, s: StringsContext, onValues: (JSONObject) -> Unit): Map<String, SettingEditor> {
    val shown = values.optString(Looks.ICON).ifBlank { spec.defaultIcon.orEmpty() }
    val color = values.optString(IconColor.KEY).ifBlank { null }
    val texture = values.optString(Texture.KEY).ifBlank { null }
    return mapOf(
        Looks.ICON to object : SettingEditor {
            @Composable
            override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
                val own = (value as? String)?.takeIf { it.isNotBlank() && it != spec.defaultIcon }
                IconSelector(current = shown, color = IconColor.of(color), suggested = spec.suggestedIcons, onChange = { onChange(it) },
                    onClear = own?.let { {
                        if (spec.defaultIcon != null) onChange(null)
                        else onValues(JSONObject(values.toString()).apply {
                            remove(Looks.ICON)
                            if (Texture.of(optString(Texture.KEY)) == Texture.ICON) remove(Texture.KEY)
                        })
                    } })
            }
        },
        IconColor.KEY to app.treelune.core.ui.components.iconColorPage(s.shared("label_icon_color"), shown, texture),
        Texture.KEY to app.treelune.core.ui.components.texturePage(s.shared("label_texture"), shown, color)
    )
}
