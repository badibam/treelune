package app.treelune.core.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A zone's pieces in the body of its tile with text (docs/design/icon-tiles.md): each as its own
 * tile on the body's cells, in order; what does not fit counted at the last place.
 */
class ZoneTileBodyTest {

    private fun places(plan: ZoneTileBody.Plan) = plan.placed.map { Triple(it.id.toInt(), it.column, it.row) }

    @Test
    fun fourWide_aSummaryTakesALine_twoWithoutSideBySide() {
        val plan = ZoneTileBody.plan(TileSize.Room(4, 3), listOf(true, false, false, true))
        assertEquals(listOf(Triple(0, 0, 0), Triple(1, 0, 1), Triple(2, 2, 1), Triple(3, 0, 2)), places(plan))
        assertEquals(0, plan.more)
        assertNull(plan.morePlace)
    }

    @Test
    fun twoWide_aSummaryTakesACondensedTile_twoRows() {
        val plan = ZoneTileBody.plan(TileSize.Room(2, 3), listOf(true, false))
        assertEquals(listOf(Triple(0, 0, 0), Triple(1, 0, 2)), places(plan))
        assertEquals(ZoneTileBody.size(2, true), TileSize.of(2, 2))
    }

    @Test
    fun whatDoesNotFit_isCountedAtTheLastPlace() {
        // Two rows 4 wide: three minimal tiles fit beside the place of « +2 »
        val plan = ZoneTileBody.plan(TileSize.Room(4, 2), List(5) { false })
        assertEquals(3, plan.placed.size)
        assertEquals(2, plan.more)
        assertEquals(2, plan.morePlace!!.column)
        assertEquals(1, plan.morePlace!!.row)
    }

    @Test
    fun aBodyThatFollowsItsContent_showsThemAll() {
        val plan = ZoneTileBody.plan(TileSize.Room(4, null), List(9) { it % 2 == 0 })
        assertEquals(9, plan.placed.size)
        assertEquals(0, plan.more)
    }

    @Test
    fun inIconsFourWide_thePiecesStartBesideTheHeader() {
        // A pair, then three icons: the pair beside the header, the icons on the next row
        val plan = ZoneTileBody.icons(TileSize.Room(4, 2), listOf(true, false, false, false), besideHeader = true)
        assertEquals(listOf(Triple(0, 2, 0), Triple(1, 0, 1), Triple(2, 1, 1), Triple(3, 2, 1)), places(plan))
        assertEquals(0, plan.more)
    }

    @Test
    fun inIconsFourWideOnOneRow_whatDoesNotFitBesideTheHeaderIsCounted() {
        val plan = ZoneTileBody.icons(TileSize.Room(4, 1), List(3) { false }, besideHeader = true)
        assertEquals(listOf(Triple(0, 2, 0)), places(plan))
        assertEquals(2, plan.more)
        assertEquals(3, plan.morePlace!!.column)
    }

    @Test
    fun inIconsTwoWide_twoIconsARowUnderTheHeader() {
        val plan = ZoneTileBody.icons(TileSize.Room(2, 2), List(4) { false }, besideHeader = false)
        assertEquals(listOf(Triple(0, 0, 0), Triple(1, 1, 0), Triple(2, 0, 1), Triple(3, 1, 1)), places(plan))
    }

    @Test
    fun theRoomOfTheTile_inTextUnderTheHeader_inIconsFourWideTheWholeTile() {
        assertEquals(TileSize.Room(4, 2), ZoneTileBody.room(TileSize.of(4, 3), icons = false))
        assertEquals(TileSize.Room(4, 3), ZoneTileBody.room(TileSize.of(4, 3), icons = true))
        assertEquals(TileSize.Room(2, 2), ZoneTileBody.room(TileSize.of(2, 3), icons = true))
        assertNull(ZoneTileBody.room(TileSize.of(2, 1), icons = true))
        assertNull(ZoneTileBody.room(TileSize.of(4, 1), icons = false))
    }
}
