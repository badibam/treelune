package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.treelune.core.fields.settings.SettingPageEditor
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.ItemTexture
import app.treelune.core.themes.TagColor
import app.treelune.core.themes.Texture
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/** The side of a choice's tile: a small tile of the grid, its icon in the middle. */
private val SWATCH = 64.dp

/** The side of the tile on a choice's line in the form. */
private val SUMMARY_SWATCH = 40.dp

/** The icon a texture or a colour is shown with while the thing has none. */
private const val SAMPLE_ICON = "circle"

/**
 * A small tile of a thing as the theme draws it (its ToolCardContainer), [iconName] in [color]
 * in its middle, [texture] on it; a blank [iconName] shows a sample icon.
 */
@Composable
private fun ThingTile(iconName: String, color: TagColor?, texture: Texture?, side: Dp, onClick: () -> Unit) {
    val shown = iconName.ifBlank { SAMPLE_ICON }
    Box(modifier = Modifier.widthIn(min = side).fillMaxWidth().height(side)) {
        CurrentTheme.current.ToolCardContainer(
            displayMode = DisplayMode.ICON,
            texture = texture?.let { ItemTexture(it, shown, color) },
            color = color,
            onClick = onClick,
            onLongClick = { }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                UI.ItemIcon(iconName = shown, color = color, size = 20.dp)
            }
        }
    }
}

/**
 * One choice of an appearance page (docs/design/tool-texture.md): the thing's tile as it would
 * look with it, its [name] under it, after a check when [chosen], so the choice reads without
 * its colour.
 */
@Composable
internal fun AppearanceChoice(iconName: String, color: TagColor?, texture: Texture?, name: String, chosen: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        ThingTile(iconName, color, texture, SWATCH, onClick)
        Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
            if (chosen) UI.Icon(iconName = "check", size = 14.dp)
            UI.Text(name, if (chosen) TextType.LABEL else TextType.CAPTION, textAlign = TextAlign.Center)
        }
    }
}

/** The line of an appearance choice in the form: the thing's small tile as chosen, then the choice's [name]. */
@Composable
internal fun AppearanceSummary(iconName: String, color: TagColor?, texture: Texture?, name: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(SUMMARY_SWATCH)) { ThingTile(iconName, color, texture, SUMMARY_SWATCH, onClick = { }) }
        UI.Text(name, TextType.BODY)
    }
}

/** The name of a texture as the screen says it; null for none. */
private fun textureName(texture: Texture?, s: app.treelune.core.strings.StringsContext): String =
    s.shared(texture?.let { "texture_${it.name.lowercase()}" } ?: "texture_none")

/**
 * A zone's or a tool's texture (docs/design/tool-texture.md): none, then each of Texture.NAMES,
 * every choice a small tile of the thing as the theme draws it with that motif, in its icon's
 * colour and at the intensity set, its name under it, the chosen one marked. ICON is offered only
 * to a thing with an icon, which it repeats.
 *
 * @param current The stored texture name; null for none
 * @param iconName The thing's icon; blank while it has none
 * @param iconColor The stored name of its icon's colour; null for none
 * @param onChange The name chosen, null for none
 */
@Composable
fun TextureSelector(current: String?, iconName: String, iconColor: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val color = IconColor.of(iconColor)
    val choices = listOf<Texture?>(null) + Texture.entries.filter { it != Texture.ICON || iconName.isNotBlank() }
    ChoiceGrid(choices, sample = null) { texture ->
        AppearanceChoice(iconName, color, texture, textureName(texture, s), current == texture?.name) { onChange(texture?.name) }
    }
}

/**
 * The texture of a zone or a tool chosen on a page of its config form (SettingPageEditor): its
 * line shows the thing's tile with it, the page the TextureSelector.
 *
 * @param iconName The thing's icon; blank while it has none
 * @param iconColor The stored name of its icon's colour; null for none
 */
fun texturePage(label: String, iconName: String, iconColor: String?) = object : SettingPageEditor {
    override val label = label

    @Composable
    override fun Summary(value: Any?) {
        val context = LocalContext.current
        val texture = Texture.of(value as? String)
        AppearanceSummary(iconName, IconColor.of(iconColor), texture, textureName(texture, remember { Strings.`for`(context = context) }))
    }

    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        TextureSelector(current = value as? String, iconName = iconName, iconColor = iconColor, onChange = { onChange(it) })
    }
}
