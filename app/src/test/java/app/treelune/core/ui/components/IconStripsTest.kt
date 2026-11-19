package app.treelune.core.ui.components

import app.treelune.core.grid.Grid
import app.treelune.core.look.TileSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the parts of a tile in icons go (docs/design/icon-tiles.md): what does not fit is not
 * shown, a pair is never cut, and a long row folds by two in one column.
 */
class IconStripsTest {

    @Test
    fun thePartsThatFit_inTheirOrder_aPairNeverCut() {
        // A zone's icon, then a pair, then a single: 3 cells hold the icon and the pair
        assertEquals(2, IconStrips.fitting(3, listOf(1, 2, 1)))
        assertEquals(3, IconStrips.fitting(4, listOf(1, 2, 1)))
        // A pair that does not fit ends the strip, the single after it left out too
        assertEquals(2, IconStrips.fitting(3, listOf(1, 1, 2, 1)))
        assertEquals(1, IconStrips.fitting(1, listOf(1, 2)))
    }

    @Test
    fun aLongRow_foldsByTwo_aPairPassingToTheNextRow() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), IconStrips.fold(listOf(1, 1, 1), 2))
        // The pair does not fit beside the icon: it starts the next row, the cell left empty
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), IconStrips.fold(listOf(1, 2, 1), 2))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), IconStrips.fold(listOf(1, 1, 1, 1), 2))
    }

    @Test
    fun inOneColumn_aColumnKeepsItsCells_aShortRowTakesTheWidth_aLongOneItsFoldedRows() {
        assertEquals(Grid.Size(2, 1), IconStrips.oneColumn(TileSize.Icon, listOf(1)))
        assertEquals(Grid.Size(2, 5), IconStrips.oneColumn(TileSize.icons(5, vertical = true), listOf(1, 2)))
        assertEquals(Grid.Size(4, 1), IconStrips.oneColumn(TileSize.icons(2, vertical = false), listOf(1, 1)))
        assertEquals(Grid.Size(4, 3), IconStrips.oneColumn(TileSize.icons(4, vertical = false), listOf(1, 2, 1)))
        assertEquals(Grid.Size(4, 2), IconStrips.oneColumn(TileSize.icons(4, vertical = false), listOf(1, 1, 1, 1)))
        // The zone's icon alone in a long row: one row
        assertEquals(Grid.Size(4, 1), IconStrips.oneColumn(TileSize.icons(3, vertical = false), listOf(1)))
    }
}
