package app.treelune.core.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tile's grid is the group section the zone screen shows it in, a tool's and a variable's
 * alike: one naming a group the zone does not have is shown ungrouped, so it is placed there, and
 * moves when the zone's groups change. Otherwise two grids would lay tiles over each other in the
 * same section.
 */
class SectionPlacesTest {

    private fun item(id: String, group: String?, x: Int, y: Int, size: Grid.Size = Grid.Size(4, 1), kind: SectionItem.Kind = SectionItem.Kind.TOOL) =
        SectionItem(id, kind, "z", group, x, y, size)

    private val half = Grid.Size(2, 1)

    @Test
    fun aTileIsInTheSectionItIsShownIn() {
        assertEquals("Health", ToolPositions.section("Health", listOf("Health")))
        assertNull(ToolPositions.section("Gone", listOf("Health")))
        assertNull(ToolPositions.section(null, listOf("Health")))
        assertNull(ToolPositions.section("", listOf("Health")))
    }

    @Test
    fun aGroupTheZoneLoses_sendsItsToolsAndVariablesToTheBottomOfTheUngroupedGrid() {
        val items = listOf(
            item("u", null, 0, 0),
            item("h1", "Health", 0, 0, half), item("h2", "Health", 2, 0, half, SectionItem.Kind.VARIABLE),
            item("w", "Work", 0, 0)
        )
        val moved = SectionPlaces.regroup(items, listOf("Health", "Work"), listOf("Work")).associateBy { it.id }
        assertEquals(setOf("h1", "h2"), moved.keys)
        // In their order on the screen, each at the bottom on a row of its own
        assertEquals(0 to 1, moved.getValue("h1").let { it.column to it.row })
        assertEquals(0 to 2, moved.getValue("h2").let { it.column to it.row })
        assertEquals(SectionItem.Kind.VARIABLE, moved.getValue("h2").kind)
    }

    @Test
    fun aGroupTheZoneGains_takesBackItsTiles_andTheirRowsClose() {
        val items = listOf(item("u", null, 0, 0), item("o", "Later", 0, 1, kind = SectionItem.Kind.VARIABLE), item("v", null, 0, 2))
        val moved = SectionPlaces.regroup(items, emptyList(), listOf("Later")).associateBy { it.id }
        assertEquals(0 to 0, moved.getValue("o").let { it.column to it.row })
        assertEquals(0 to 1, moved.getValue("v").let { it.column to it.row })
    }

    @Test
    fun groupsThatChangeNoSection_moveNothing() {
        val items = listOf(item("h", "Health", 0, 0))
        assertEquals(emptyList<SectionItem>(), SectionPlaces.regroup(items, listOf("Health"), listOf("Health", "Work")))
    }
}
