package app.treelune.core.look.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.treelune.core.look.TileSize
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.UI

/**
 * A tile's size chosen in two lists side by side (docs/design/tile-sizes.md, « Le choix »): its
 * width — the icon, half or the whole width — and, but for the icon, its height, 1 to 10 rows or
 * as tall as its content. One value comes out (TileSize). A height kept across a change of width
 * when the new width takes it; « as tall as its content » puts the tile on the whole width, the
 * only one that takes it, and the width shows it.
 */
@Composable
fun TileSizeSelector(size: TileSize, s: StringsContext, onChange: (TileSize) -> Unit) {
    val widths = listOf(s.shared("tile_width_icon"), s.shared("tile_width_half"), s.shared("tile_width_full"))
    val width = when {
        size.isIcon -> widths[0]
        size.width == TileSize.HALF -> widths[1]
        else -> widths[2]
    }
    val heights = (1..TileSize.MAX_HEIGHT).map { it.toString() } + s.shared("tile_height_content")
    val height = size.height?.toString() ?: heights.last()
    // A fixed height to keep when the width changes: the one shown, or two rows
    val kept = size.height ?: 2
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("tile_width_label"), options = widths, selected = width, required = true, onSelect = { chosen ->
                onChange(when (chosen) {
                    widths[0] -> TileSize.Icon
                    widths[1] -> TileSize.of(TileSize.HALF, if (size.isIcon) 1 else kept)
                    else -> if (size.isIcon) TileSize.of(TileSize.FULL, 1) else if (size.followsContent) size else TileSize.of(TileSize.FULL, kept)
                })
            })
        }
        if (!size.isIcon) Box(modifier = Modifier.weight(1f)) {
            UI.FormSelection(label = s.shared("tile_height_label"), options = heights, selected = height, required = true, onSelect = { chosen ->
                onChange(chosen.toIntOrNull()?.let { TileSize.of(if (size.followsContent) TileSize.FULL else size.width, it) } ?: TileSize.Content)
            })
        }
    }
}
