package app.treelune.core.look.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.treelune.core.look.TileSize
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.UI

/**
 * A tile's size chosen in lists side by side (docs/design/tile-sizes.md and icon-tiles.md, « Le
 * choix »): its width — icons, half or the whole width — and, with text, its height, 1 to 10
 * rows or as tall as its content; in icons, its length, and its direction (a column or a row)
 * once it is longer than one cell. [sizes] are the ones the owner takes (LookSpec.sizes): a piece
 * goes to 2 cells in icons, a zone to 10 in a column and 4 in a row. One value comes out
 * (TileSize). A height kept across a change of width when the new width takes it; « as tall as
 * its content » puts the tile on the whole width, the only one that takes it, and the width
 * shows it. A length kept across a change of direction as far as the new direction goes.
 */
@Composable
fun TileSizeSelector(size: TileSize, sizes: List<TileSize>, s: StringsContext, onChange: (TileSize) -> Unit) {
    val widths = listOf(s.shared("tile_width_icon"), s.shared("tile_width_half"), s.shared("tile_width_full"))
    val width = when {
        size.inIcons -> widths[0]
        size.width == TileSize.HALF -> widths[1]
        else -> widths[2]
    }
    // A fixed height to keep when the width changes: the one shown, or two rows
    val kept = if (size.inIcons) 2 else size.height ?: 2
    // The longest strips the owner takes, in a column and in a row
    val longestColumn = sizes.filter { it.vertical }.maxOfOrNull { it.length } ?: 1
    val longestRow = sizes.filter { it.inIcons && !it.vertical }.maxOfOrNull { it.length } ?: 1
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            Box(modifier = Modifier.weight(1f)) {
                UI.FormSelection(label = s.shared("tile_width_label"), options = widths, selected = width, required = true, onSelect = { chosen ->
                    onChange(when (chosen) {
                        widths[0] -> if (size.inIcons) size else TileSize.Icon
                        widths[1] -> TileSize.of(TileSize.HALF, if (size.inIcons) 1 else kept)
                        else -> if (size.inIcons) TileSize.of(TileSize.FULL, 1) else if (size.followsContent) size else TileSize.of(TileSize.FULL, kept)
                    })
                })
            }
            if (size.inIcons) Box(modifier = Modifier.weight(1f)) {
                val longest = if (size.vertical || size.isIcon) maxOf(longestColumn, longestRow) else longestRow
                val lengths = (1..longest).map { it.toString() }
                UI.FormSelection(label = s.shared("tile_length_label"), options = lengths, selected = size.length.toString(), required = true, onSelect = { chosen ->
                    val length = chosen.toInt()
                    // From the icon alone, a column unless only a row goes that far
                    val vertical = if (size.isIcon) length <= longestColumn else size.vertical
                    onChange(TileSize.icons(length.coerceAtMost(if (vertical) longestColumn else longestRow), vertical))
                })
            } else Box(modifier = Modifier.weight(1f)) {
                val heights = (1..TileSize.MAX_HEIGHT).map { it.toString() } + s.shared("tile_height_content")
                val height = size.height?.toString() ?: heights.last()
                UI.FormSelection(label = s.shared("tile_height_label"), options = heights, selected = height, required = true, onSelect = { chosen ->
                    onChange(chosen.toIntOrNull()?.let { TileSize.of(if (size.followsContent) TileSize.FULL else size.width, it) } ?: TileSize.Content)
                })
            }
        }
        // The direction once the strip is longer than one cell
        if (size.inIcons && !size.isIcon) {
            val directions = listOf(s.shared("tile_direction_column"), s.shared("tile_direction_row"))
            UI.FormSelection(label = s.shared("tile_direction_label"), options = directions, selected = directions[if (size.vertical) 0 else 1], required = true, onSelect = { chosen ->
                val vertical = chosen == directions[0]
                if (vertical != size.vertical) onChange(TileSize.icons(size.length.coerceAtMost(if (vertical) longestColumn else longestRow), vertical))
            })
        }
    }
}
