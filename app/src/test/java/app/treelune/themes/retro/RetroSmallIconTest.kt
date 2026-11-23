package app.treelune.themes.retro

import app.treelune.core.themes.SmallIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroSmallIconTest {

    private fun piece(zone: Int, scale: Int) = RetroGrid.smallIconUnit(SmallIcon.PIECE, zone, scale)
    private fun readout(zone: Int, scale: Int) = RetroGrid.smallIconUnit(SmallIcon.READOUT, zone, scale)

    @Test
    fun `a piece is two thirds of its zone's icon, a readout the text's pixel under it`() {
        assertEquals(4, piece(6, 3))
        assertEquals(3, readout(6, 3))
        assertEquals(5, piece(8, 4))
        assertEquals(4, readout(8, 4))
    }

    @Test
    fun `where the zone's icon is at the text's pixel, a readout stays one under it and a piece not under a readout`() {
        assertEquals(4, readout(5, 5))
        assertEquals(4, piece(5, 5))
        assertEquals(5, readout(6, 6))
        assertEquals(5, piece(6, 6))
    }

    @Test
    fun `a small icon is never larger than the zone's, and smaller whenever a pixel is left`() {
        for (scale in 1..8) for (zone in listOf(scale, scale * 2)) {
            assertTrue(piece(zone, scale) <= zone)
            assertTrue(readout(zone, scale) <= piece(zone, scale))
            if (zone > 1) assertTrue(piece(zone, scale) < zone)
        }
    }
}
