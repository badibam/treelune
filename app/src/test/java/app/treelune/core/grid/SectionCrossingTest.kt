package app.treelune.core.grid

import app.treelune.core.grid.Grid.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tile crossing into the next section in edit mode (Grid.enter, Grid.exit): up from the top of
 * its section, it arrives at the bottom of the one above, in its column, nothing there moving;
 * down from the bottom, at the top of the one below, the others moving down by its height; its
 * own section closes the rows it leaves empty; in one column, it goes last or first in order.
 */
class SectionCrossingTest {

    private fun places(tiles: List<Tile>) = tiles.associate { it.id to (it.column to it.row) }

    // Above: a and b on row 0, c on row 1; the tile: two columns wide, one high
    private val above = listOf(Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 2, 1), Tile("c", 0, 1, 1, 1))
    private val moving = Tile("t", 2, 0, 2, 1)

    @Test
    fun `entering from below, it takes a new row at the bottom, in its column`() {
        val entered = Grid.enter(above, moving, fromBelow = true)
        assertEquals(mapOf("a" to (0 to 0), "b" to (2 to 0), "c" to (0 to 1), "t" to (2 to 2)), places(entered))
        assertTrue(Grid.isLaidOut(entered))
    }

    @Test
    fun `entering from above, it takes the top, the others moving down by its height`() {
        val entered = Grid.enter(above, moving.copy(height = 2), fromBelow = false)
        assertEquals(mapOf("a" to (0 to 2), "b" to (2 to 2), "c" to (0 to 3), "t" to (2 to 0)), places(entered))
        assertTrue(Grid.isLaidOut(entered))
    }

    @Test
    fun `a column past the edge is brought left, an empty section takes it at its top`() {
        val wide = Tile("w", 3, 0, 2, 1)
        assertEquals(2, Grid.enter(above, wide, fromBelow = true).single { it.id == "w" }.column)
        assertEquals(mapOf("t" to (2 to 0)), places(Grid.enter(emptyList(), moving, fromBelow = false)))
    }

    @Test
    fun `its own section closes what it leaves empty`() {
        val section = listOf(Tile("t", 0, 0, 4, 1), Tile("d", 0, 1, 1, 1))
        assertEquals(mapOf("d" to (0 to 0)), places(Grid.exit(section, "t")))
    }

    @Test
    fun `in one column it goes last from below, first from above`() {
        assertEquals("t", Grid.readingOrder(Grid.enter(above, moving, fromBelow = true, oneColumn = true)).last().id)
        assertEquals("t", Grid.readingOrder(Grid.enter(above, moving, fromBelow = false, oneColumn = true)).first().id)
    }
}
