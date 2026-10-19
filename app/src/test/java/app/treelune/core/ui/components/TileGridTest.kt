package app.treelune.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers how many items a tile's body shows by its rows, two or four stacked in a row of cells. */
class TileGridTest {

    @Test
    fun aListShowsFourItemsPerRowOfCells_orEightWhenShort() {
        // EXTENDED gives one row of cells to the body, SQUARE three; two columns
        assertEquals(4, tileGridSlotRows(1, 30, 2, 2) * 2)
        assertEquals(8, tileGridSlotRows(1, 30, 2, 4) * 2)
        assertEquals(24, tileGridSlotRows(3, 30, 2, 4) * 2)
    }

    @Test
    fun fullShowsEveryItem() {
        assertEquals(15, tileGridSlotRows(null, 30, 2, 4))
        assertEquals(3, tileGridSlotRows(null, 5, 2, 2))
    }

    @Test
    fun aLimitedBodyTakesOnlyTheRowsItsItemsNeed() {
        // An empty tool in EXTENDED or SQUARE keeps no room for its body; three notes in two columns, two rows of slots
        assertEquals(0, tileGridSlotRows(1, 0, 2, 2))
        assertEquals(0, tileGridSlotRows(3, 0, 1, 1))
        assertEquals(2, tileGridSlotRows(3, 3, 2, 1))
    }
}
