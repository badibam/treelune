package app.treelune.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How an element shares its lines under its main line (tileItemLines, docs/design/tile-sizes.md):
 * its text first, its fields one per line, each showing when both are there, nothing cut mid-line.
 */
class TileItemTest {

    @Test
    fun oneLine_isTheMainLineAlone() {
        assertEquals(TileItemLines(0, 0), tileItemLines(1, hasText = true, fieldCount = 3))
    }

    @Test
    fun aText_takesEveryLineLeft_whenThereAreNoFields() {
        assertEquals(TileItemLines(3, 0), tileItemLines(4, hasText = true, fieldCount = 0))
    }

    @Test
    fun fieldsWithoutText_takeOneLineEach_asManyAsFit() {
        assertEquals(TileItemLines(0, 2), tileItemLines(3, hasText = false, fieldCount = 5))
        assertEquals(TileItemLines(0, 1), tileItemLines(4, hasText = false, fieldCount = 1))
    }

    @Test
    fun aTextAndFields_theTextKeepsOneLine_theFieldsTheRest() {
        assertEquals(TileItemLines(1, 0), tileItemLines(2, hasText = true, fieldCount = 2))
        assertEquals(TileItemLines(1, 2), tileItemLines(4, hasText = true, fieldCount = 2))
        assertEquals(TileItemLines(4, 2), tileItemLines(7, hasText = true, fieldCount = 2))
        assertEquals(TileItemLines(1, 3), tileItemLines(5, hasText = true, fieldCount = 6))
    }
}
