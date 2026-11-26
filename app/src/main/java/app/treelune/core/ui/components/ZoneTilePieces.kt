package app.treelune.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import app.treelune.core.look.TilePieces
import app.treelune.core.look.TileSize
import app.treelune.core.look.ZoneTileBody
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/** A piece listed on a zone's tile, found among the zone's pieces, with its summary or without. */
data class ListedPiece(val tile: SectionTile, val summary: Boolean)

/**
 * The pieces [zoneTilePieces] lists, found among [held] in its order; a piece no longer in the
 * zone is left out (TilePieces).
 */
fun listedPieces(zoneTilePieces: String?, held: List<SectionTile>): List<ListedPiece> {
    val byId = held.associateBy { it.id }
    return TilePieces.read(zoneTilePieces).mapNotNull { piece -> byId[piece.id]?.let { ListedPiece(it, piece.summary) } }
}

/** The cells each of [pieces] takes in a strip of icons: two with its summary and a second cell, one otherwise. */
fun stripCells(pieces: List<ListedPiece>): List<Int> = pieces.map { if (it.summary && hasSecond(it.tile)) 2 else 1 }

/**
 * [zone]'s tile on the home screen's grid, with the cells it takes in one column when its row of
 * icons folds by its [pieces] (IconStrips.oneColumn).
 */
fun zoneGridTile(zone: app.treelune.core.database.entities.Zone, pieces: List<ListedPiece>): app.treelune.core.grid.Grid.Tile {
    val tile = app.treelune.core.grid.ZonePositions.tile(zone)
    val size = TileSize.ofStored(zone.tile_size)
    return if (size.inIcons && !size.vertical && size.length > IconStrips.ONE_COLUMN_ROW)
        tile.copy(inOneColumn = IconStrips.oneColumn(size, listOf(1) + stripCells(pieces))) else tile
}

/** Whether what is drawn here is a piece of a zone's tile: its icon is then the small one (UI.TileIcon), the zone's standing out. */
val LocalZonePiece = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The size of the zone's own icon on the zone's tile drawn here, null outside one: the largest the
 * theme draws in its place (UI.ZoneIconSized), its pieces taking two thirds of it (UI.TileIcon).
 */
val LocalZoneIcon = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.unit.Dp?> { null }

/** What a piece of a zone's tile does when touched, from the home screen: what its own tile does in its zone. */
@Composable
fun PieceContent(tile: SectionTile, zoneId: String) {
    val context = LocalContext.current
    val routineActions = app.treelune.core.ai.ui.routine.rememberRoutineActions { UI.Toast(it, app.treelune.core.ui.Duration.LONG) }
    CompositionLocalProvider(LocalZonePiece provides true) {
        SectionTileContent(
            tile = tile,
            context = context,
            onToolClick = { Navigator.push(Place.Tool(it.id, zoneId)) },
            onToolLongClick = { Navigator.push(Place.ToolConfig(zoneId, it.tooltype, it.id)) },
            onOpenEntry = { tool, entry -> Navigator.push(Place.Tool(tool.id, zoneId), entry) },
            onOpenVariable = { Navigator.push(Place.Variable(zoneId, it)) },
            routineActions = routineActions
        )
    }
}

/**
 * [pieces] as the parts of a zone's strip of icons [size] (docs/design/icon-tiles.md): each its own
 * tile in icons, without its frame — its icon, which opens it, and with its summary its second
 * cell, under it in a column, beside it in a row (a variable or a tool without a second cell
 * keeping one cell).
 */
fun stripPieces(size: TileSize, pieces: List<ListedPiece>, zoneId: String): List<IconPiece> = pieces.map { piece ->
    val pair = stripCells(listOf(piece)).single() == 2
    val inner = if (pair) TileSize.icons(2, vertical = size.vertical) else TileSize.Icon
    IconPiece(piece.tile.id, if (pair) 2 else 1) {
        CompositionLocalProvider(LocalInnerTile provides inner) { PieceContent(piece.tile, zoneId) }
    }
}

/**
 * The body of a zone's tile with text, in [room] (ZoneTileBody): each piece as its own tile without
 * its frame — in text a line, a condensed tile or a minimal one; in [icons] its icon, or its icon
 * and its second cell side by side, around the header's place when [besideHeader] —, on the body's grid of cells, each row a
 * share of the body (or a row of the grid and the gap after it when the tile follows its content, as a
 * fixed body's share holds the gaps it spans), each row under the
 * theme's separator, the first parting it from the zone's header; « +N » at the last place when some do not fit.
 */
@Composable
fun ZonePiecesBody(room: TileSize.Room, pieces: List<ListedPiece>, zoneId: String, icons: Boolean, besideHeader: Boolean) {
    if (pieces.isEmpty()) return
    val pairs = pieces.map { it.summary && hasSecond(it.tile) }
    val plan = remember(room, pieces.map { it.summary }, icons, besideHeader) {
        if (icons) ZoneTileBody.icons(room, pairs, besideHeader) else ZoneTileBody.plan(room, pieces.map { it.summary })
    }
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val gridRow = with(LocalDensity.current) { LocalGridRow.current.roundToPx() } + app.treelune.core.themes.CurrentTheme.current.gridRowGapPx()
    Layout(
        content = {
            plan.placed.forEach { place ->
                val piece = pieces[place.id.toInt()]
                key(piece.tile.id) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Each row under the theme's divider, the first parting it from the zone's
                        // header; none on the header's own row
                        if (place.row > 0 || plan.reserved == null) UI.Divider()
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val inner = when {
                                !icons -> ZoneTileBody.size(room.width, piece.summary)
                                pairs[place.id.toInt()] -> TileSize.icons(2, vertical = false)
                                else -> TileSize.Icon
                            }
                            CompositionLocalProvider(LocalInnerTile provides inner) { PieceContent(piece.tile, zoneId) }
                        }
                    }
                }
            }
            plan.morePlace?.let {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    UI.Text(s.shared("tile_more").format(plan.more), TextType.CAPTION, maxLines = 1)
                }
            }
        }
    ) { measurables, constraints ->
        val rows = room.rows
        val rowPx = if (rows != null && constraints.hasBoundedHeight) constraints.maxHeight / rows else gridRow
        val cellPx = constraints.maxWidth / room.width
        val places = plan.placed + listOfNotNull(plan.morePlace)
        val placeables = measurables.mapIndexed { i, measurable ->
            val place = places[i]
            measurable.measure(Constraints.fixed(place.width * cellPx, place.height * rowPx))
        }
        val height = if (rows != null && constraints.hasBoundedHeight) constraints.maxHeight else plan.rows * rowPx
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, placeable -> placeable.place(places[i].column * cellPx, places[i].row * rowPx) }
        }
    }
}
