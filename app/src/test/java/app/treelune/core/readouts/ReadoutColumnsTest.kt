package app.treelune.core.readouts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The columns of a card gathering readouts: a zone's own setting, else the home screen's, else
 * one; one in the app's one column whatever is set; "home" stored as nothing, as the zone follows.
 */
class ReadoutColumnsTest {

    @Test
    fun aZone_followsTheHomeScreen_unlessItSetsItsOwn() {
        assertEquals(2, ReadoutColumns.of(zone = null, home = "two", oneColumn = false))
        assertEquals(2, ReadoutColumns.of(zone = "home", home = "two", oneColumn = false))
        assertEquals(1, ReadoutColumns.of(zone = "one", home = "two", oneColumn = false))
        assertEquals(2, ReadoutColumns.of(zone = "two", home = "one", oneColumn = false))
    }

    @Test
    fun nothingSet_isOneColumn_andTheAppsOneColumn_alwaysIs() {
        assertEquals(1, ReadoutColumns.of(zone = null, home = null, oneColumn = false))
        assertEquals(1, ReadoutColumns.of(zone = "two", home = "two", oneColumn = true))
    }

    @Test
    fun followingTheHomeScreen_isStoredAsNothing() {
        assertEquals(null, ReadoutColumns.stored("home"))
        assertEquals(null, ReadoutColumns.stored(null))
        assertEquals("two", ReadoutColumns.stored("two"))
    }
}
