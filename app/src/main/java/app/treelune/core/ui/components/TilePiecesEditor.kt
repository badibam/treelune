package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.fields.settings.SettingEditor
import app.treelune.core.look.TilePieces
import app.treelune.core.look.TileSize
import app.treelune.core.look.ZoneTileBody
import app.treelune.core.strings.StringsContext
import app.treelune.core.themes.IconColor
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONArray

/**
 * How many of [pieces] a zone's tile of [size] shows (docs/design/icon-tiles.md): in icons, as
 * many as hold after the zone's own icon (IconStrips.fitting, stripCells); with text, as many as
 * its body holds (ZoneTileBody), none without a body (4×1, 2×1, 2×2), all when it follows its content.
 */
fun shownPieces(size: TileSize, pieces: List<ListedPiece>): Int = when {
    size.inIcons -> (IconStrips.fitting(size.length, listOf(1) + stripCells(pieces)) - 1).coerceAtLeast(0)
    else -> size.body?.let { room -> ZoneTileBody.plan(room, pieces.map { it.summary }).placed.size } ?: 0
}

/**
 * The editor of a zone's tile pieces in its config (TilePieces): the pieces listed in their order,
 * moved by their grip, each with « With its summary » — which a piece without a second cell (a
 * variable, a Chart) shows with text alone, said under it — and its removal; a piece no longer in
 * the zone says so. Under them, the pieces of the zone not
 * listed yet, one to add; then those the tile does not show at [size], named. A zone being created
 * holds nothing yet: nothing is shown.
 */
fun tilePiecesEditor(zoneId: String?, size: TileSize, s: StringsContext): SettingEditor = object : SettingEditor {
    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        if (zoneId == null) return
        val context = LocalContext.current
        val coordinator = remember { app.treelune.core.coordinator.Coordinator(context) }
        var tiles by remember { mutableStateOf<List<SectionTile>?>(null) }
        LaunchedEffect(zoneId) {
            zonePieceTiles(coordinator, zoneId).onSuccess { tiles = it }.onFailure { UI.Toast(it.message ?: s.shared("error_operation_failed"), app.treelune.core.ui.Duration.LONG) }
        }
        val held = tiles ?: return
        val byId = held.associateBy { it.id }
        val pieces = TilePieces.of(value as? JSONArray)
        fun set(next: List<TilePieces.Piece>) = onChange(TilePieces.write(next)?.let { JSONArray(it) })

        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.M)) {
                UI.Text(s.shared("label_zone_tile_pieces"), TextType.SUBTITLE)
                UI.ReorderableColumn(
                    items = pieces,
                    key = { it.id },
                    spacing = UI.Space.S,
                    onMove = { from, to -> set(pieces.toMutableList().apply { add(to, removeAt(from)) }) }
                ) { _, piece ->
                    val tile = byId[piece.id]
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
                        DragHandle()
                        tile?.look?.iconName?.let { UI.ItemIcon(it, IconColor.of(tile.look.iconColor), 20.dp) }
                        Column(modifier = Modifier.weight(1f)) {
                            UI.Text(tile?.look?.name ?: piece.id, TextType.BODY, maxLines = 2)
                            when {
                                tile == null -> UI.Text(s.shared("zone_tile_pieces_gone"), TextType.CAPTION, maxLines = 1)
                                else -> {
                                    UI.Checkbox(checked = piece.summary, label = s.shared("label_zone_tile_piece_summary"),
                                        onCheckedChange = { checked -> set(pieces.map { if (it.id == piece.id) it.copy(summary = checked) else it }) })
                                    // Its summary shows with text alone: in icons it keeps one cell
                                    if (!hasSecond(tile)) UI.Text(s.shared("zone_tile_pieces_no_summary"), TextType.CAPTION, maxLines = 2)
                                }
                            }
                        }
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S,
                            onClick = { set(pieces.filter { it.id != piece.id }) })
                    }
                }
                // The zone's pieces not listed yet, one chosen to add at the end
                val free = held.filter { tile -> pieces.none { it.id == tile.id } }
                if (free.isNotEmpty()) {
                    val names = free.map { it.look.name }
                    UI.FormSelection(label = s.shared("zone_tile_pieces_add"), options = names, selected = "", required = false, onSelect = { chosen ->
                        free.getOrNull(names.indexOf(chosen))?.let { tile -> set(pieces + TilePieces.Piece(tile.id, summary = false)) }
                    })
                }
                // What the tile does not show at its size, named
                val listed = pieces.mapNotNull { piece -> byId[piece.id]?.let { ListedPiece(it, piece.summary) } }
                val cut = listed.drop(shownPieces(size, listed)).map { it.tile.look.name }
                if (cut.isNotEmpty()) Box { UI.Text(s.shared("zone_tile_pieces_cut").format(cut.joinToString(", ")), TextType.CAPTION) }
            }
        }
    }
}
