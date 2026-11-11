package app.treelune.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a tile's body puts its elements (tileGridPlan, docs/design/tile-sizes.md): a fixed body
 * holds as many as fit, their places never depending on how many there are, the last place saying
 * how many are left; a body that follows its content shows them all.
 */
class TileGridTest {

    @Test
    fun aFixedBody_holdsAsManyAsFit_onPlacesSharedOutOfItsHeight() {
        // 200 px for elements of 45: four places of 50, two columns
        assertEquals(TileGridPlan(shown = 8, more = 0, rowsUsed = 4, slotPx = 50), tileGridPlan(45, 200, 8, 2))
        // Fewer elements: the same places, rows left empty below them
        assertEquals(TileGridPlan(shown = 3, more = 0, rowsUsed = 2, slotPx = 50), tileGridPlan(45, 200, 3, 2))
    }

    @Test
    fun whatDoesNotFit_isSaid_inTheLastPlace() {
        // Eight places for thirty elements: seven shown, « +23 » in the eighth
        assertEquals(TileGridPlan(shown = 7, more = 23, rowsUsed = 4, slotPx = 50), tileGridPlan(45, 200, 30, 2))
        // One column of four places
        assertEquals(TileGridPlan(shown = 3, more = 2, rowsUsed = 4, slotPx = 50), tileGridPlan(45, 200, 5, 1))
    }

    @Test
    fun aBodyTooShortForOne_stillHasOnePlace() {
        assertEquals(TileGridPlan(shown = 1, more = 0, rowsUsed = 1, slotPx = 30), tileGridPlan(45, 30, 1, 1))
        assertEquals(TileGridPlan(shown = 0, more = 2, rowsUsed = 1, slotPx = 30), tileGridPlan(45, 30, 2, 1))
    }

    @Test
    fun aBodyThatFollowsItsContent_showsThemAll_emptyTakingNoRow() {
        assertEquals(TileGridPlan(shown = 30, more = 0, rowsUsed = 15, slotPx = 45), tileGridPlan(45, null, 30, 2))
        assertEquals(TileGridPlan(shown = 0, more = 0, rowsUsed = 0, slotPx = 45), tileGridPlan(45, null, 0, 1))
    }
}
