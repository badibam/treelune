package app.treelune.core.look

import app.treelune.core.grid.Grid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A tile's size (docs/design/tile-sizes.md): its closed list, what it gives its body, one column, the former modes. */
class TileSizeTest {

    @Test
    fun theSizes_areTheStripsOfIcons_twoWidthsOfOneToTen_andTheContentsOnFullWidthOnly() {
        // A zone's: the icon, columns 2 to 10, rows 2 to 4, then the sizes with text
        assertEquals(1 + 9 + 3 + 10 + 10 + 1, TileSize.ALL.size)
        assertEquals(listOf("icon", "icons_1x2", "icons_1x10", "icons_2x1", "icons_4x1", "2x1", "2x10", "4x1", "4x10", "4xcontent"),
            listOf(0, 1, 9, 10, 12, 13, 22, 23, 32, 33).map { TileSize.ALL[it].stored })
        TileSize.ALL.forEach { assertEquals(it, TileSize.parse(it.stored)) }
        listOf("2xcontent", "3x2", "4x11", "2x0", "icons_1x1", "icons_5x1", "icons_2x2", "icons_1x11", "SQUARE", "", null).forEach { assertNull("$it", TileSize.parse(it)) }
    }

    @Test
    fun aPiece_takesTheIconAndThePair_aZoneEveryStrip() {
        assertEquals(listOf("icon", "icons_1x2", "icons_2x1"), TileSize.PIECE_ICONS.map { it.stored })
        assertTrue(TileSize.PIECE.none { it.inIcons && it.length > 2 })
        assertTrue(TileSize.parse("icons_1x7")!! in TileSize.ZONE)
        assertFalse(TileSize.parse("icons_1x7")!! in TileSize.PIECE)
    }

    @Test
    fun aStripOfIcons_saysItsDirectionAndLength_andHasNoBody() {
        val column = TileSize.parse("icons_1x3")!!
        assertTrue(column.vertical); assertEquals(3, column.length); assertEquals(Grid.Size(1, 3), column.cells)
        val row = TileSize.parse("icons_3x1")!!
        assertFalse(row.vertical); assertEquals(3, row.length); assertEquals(Grid.Size(3, 1), row.cells)
        assertEquals(TileSize.Icon, TileSize.icons(1, vertical = true))
        listOf(TileSize.Icon, column, row).forEach { assertNull(it.body); assertFalse(it.summaryBeside); assertEquals(it, it.inOneColumn()) }
    }

    @Test
    fun theBody_isWhatTheHeaderAndTheSummaryLeave_besideThemOnFullWidth_underThemOnHalf() {
        // Without a body: the summary stands with the header
        listOf("icon", "2x1", "2x2", "4x1").forEach { assertNull(it, TileSize.parse(it)!!.body) }
        assertEquals(TileSize.Room(4, 1), TileSize.parse("4x2")!!.body)
        assertEquals(TileSize.Room(4, 3), TileSize.parse("4x4")!!.body)
        assertEquals(TileSize.Room(2, 1), TileSize.parse("2x3")!!.body)
        assertEquals(TileSize.Room(2, 8), TileSize.parse("2x10")!!.body)
        assertEquals(TileSize.Room(4, null), TileSize.Content.body)
    }

    @Test
    fun inOneColumn_everyTileIsLaidAsTwoWide_aFullOneARowTaller() {
        assertEquals(TileSize.parse("2x3"), TileSize.parse("2x3")!!.inOneColumn())
        assertEquals(TileSize.of(2, 5), TileSize.parse("4x4")!!.inOneColumn())
        assertEquals(TileSize.Icon, TileSize.Icon.inOneColumn())
        // Its body two cells wide, as tall as its content
        val content = TileSize.Content.inOneColumn()
        assertTrue(content.followsContent)
        assertEquals(TileSize.Room(2, null), content.body)
        assertEquals(TileSize.Room(2, 3), TileSize.parse("4x4")!!.inOneColumn().body)
    }

    @Test
    fun theFormerModes_becomeTheSizesTheyHad() {
        assertEquals(
            mapOf("ICON" to "icon", "MINIMAL" to "2x1", "LINE" to "4x1", "CONDENSED" to "2x2", "EXTENDED" to "4x2", "SQUARE" to "4x4", "FULL" to "4xcontent"),
            TileSize.FORMER_MODES
        )
        TileSize.FORMER_MODES.values.forEach { assertTrue(it, TileSize.parse(it) != null) }
    }

    @Test
    fun tallHalfTiles_ofDifferentHeights_standSideBySide_andAreMovedAndResizedAroundEachOther() {
        fun tile(id: String, column: Int, row: Int, size: String) = TileSize.parse(size)!!.cells.let { Grid.Tile(id, column, row, it.width, it.height) }
        val tall = tile("tall", 0, 0, "2x4")
        val short = tile("short", 2, 0, "2x2")
        val under = tile("under", 2, 2, "2x1")
        val grid = listOf(tall, short, under)
        assertFalse(grid.any { a -> grid.any { b -> a !== b && a.overlaps(b) } })
        assertEquals(4, Grid.rowCount(grid))

        // The short one grown to 2x3 pushes the one under it down to the first row the tall one
        // does not cross, the tall one untouched
        val grown = Grid.resize(grid, "short", Grid.Size(2, 3)).associateBy { it.id }
        assertEquals(0 to 0, grown.getValue("tall").let { it.column to it.row })
        assertEquals(2 to 4, grown.getValue("under").let { it.column to it.row })

        // Packed in reading order, they find the same places
        assertEquals(grid.associate { it.id to (it.column to it.row) }, Grid.pack(Grid.readingOrder(grid)).associate { it.id to (it.column to it.row) })
    }
}
