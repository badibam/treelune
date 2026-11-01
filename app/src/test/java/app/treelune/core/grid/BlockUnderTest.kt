package app.treelune.core.grid

import app.treelune.core.grid.Grid.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zones brought by an import set under their section as one block (Grid.blockUnder): side by side
 * they stay side by side, a row the part leaves empty closes, a tall one keeps the row it covers,
 * the zones already there do not move.
 */
class BlockUnderTest {

    private fun places(tiles: List<Tile>) = tiles.associate { it.id to (it.column to it.row) }

    private val there = listOf(Tile("mine", 0, 0, 2, 1), Tile("also", 0, 1, 4, 1))

    @Test
    fun `side by side they stay so, under the last row, a row left empty closed`() {
        // A at row 0 col 0, B beside it, C three rows down: the rows between held zones not brought
        val block = listOf(Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 2, 1), Tile("c", 2, 3, 2, 1))
        val placed = Grid.blockUnder(there, block)
        assertEquals(mapOf("mine" to (0 to 0), "also" to (0 to 1), "a" to (0 to 2), "b" to (2 to 2), "c" to (2 to 3)), places(placed))
        assertTrue(Grid.isLaidOut(placed))
    }

    @Test
    fun `a tall one keeps the row it covers, an empty section takes the block at its top`() {
        val block = listOf(Tile("tall", 0, 1, 2, 2), Tile("low", 2, 2, 2, 1))
        assertEquals(mapOf("tall" to (0 to 0), "low" to (2 to 1)), places(Grid.blockUnder(emptyList(), block)))
    }
}
