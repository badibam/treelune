package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.ItemTexture
import app.treelune.core.themes.Texture
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/** The side of a choice's tile: a small tile of the grid, its icon in the middle. */
private val SWATCH = 64.dp

/** The icon a texture is shown with while the thing has none. */
private const val SAMPLE_ICON = "circle"

/**
 * A zone's or a tool's texture (docs/design/tool-texture.md): none, then each of Texture.NAMES,
 * every choice a small tile of the thing as the theme draws it with that motif, in its icon's
 * colour and at the intensity set, its name under it, the chosen one marked. ICON is offered only
 * to a thing with an icon, which it repeats. It sits under the IconColorSelector of the same thing.
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
    val shown = iconName.ifBlank { SAMPLE_ICON }
    val choices = listOf<Texture?>(null) + Texture.entries.filter { it != Texture.ICON || iconName.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.Text(s.shared("label_texture"), TextType.LABEL)
        ChoiceGrid(choices, sample = null) { texture ->
            val chosen = current == texture?.name
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                Box(modifier = Modifier.widthIn(min = SWATCH).fillMaxWidth().height(SWATCH)) {
                    CurrentTheme.current.ToolCardContainer(
                        displayMode = DisplayMode.ICON,
                        texture = texture?.let { ItemTexture(it, shown, color) },
                        onClick = { onChange(texture?.name) },
                        onLongClick = { }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            UI.ItemIcon(iconName = shown, color = color, size = 20.dp)
                        }
                    }
                }
                // The chosen one's name after a check, so the choice reads without its colour
                Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
                    if (chosen) UI.Icon(iconName = "check", size = 14.dp)
                    UI.Text(s.shared(texture?.let { "texture_${it.name.lowercase()}" } ?: "texture_none"),
                        if (chosen) TextType.LABEL else TextType.CAPTION, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
