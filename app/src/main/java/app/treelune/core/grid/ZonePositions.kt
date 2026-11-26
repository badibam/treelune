package app.treelune.core.grid

import app.treelune.core.database.entities.Zone

/**
 * Where the zones stand on the home screen: each one in the grid of the zone group section it is
 * shown in, at its grid_x and grid_y, as many cells as its size takes. Pure: the services
 * read the zones, ask here where they go, and write the positions that changed.
 */
object ZonePositions {

    /**
     * The zone group section a zone with [group] is shown in, among the home screen's
     * [zoneGroups]: its own, or the ungrouped one (null) when it has none. A group the home screen
     * does not have cannot be held (Groups): one that is anyway is a bug,
     * logged, and its zone shown among the ungrouped rather than lost from the screen.
     */
    fun section(group: String?, zoneGroups: List<String>): String? {
        if (group.isNullOrEmpty()) return null
        if (group in zoneGroups) return group
        app.treelune.core.utils.LogManager.ui("A zone holds the group '$group', which the home screen does not have", "ERROR")
        return null
    }

    fun size(zone: Zone): Grid.Size = requireNotNull(app.treelune.core.look.TileSize.parse(zone.tile_size)) { "Zone ${zone.id}: no tile size '${zone.tile_size}'" }.cells

    fun tile(zone: Zone): Grid.Tile = size(zone).let { Grid.Tile(zone.id, zone.grid_x, zone.grid_y, it.width, it.height) }

    /** The section, its visible or hidden part, [zone] stands in. */
    fun part(zone: Zone, zoneGroups: List<String>): Section = Section(section(zone.group, zoneGroups), zone.hidden)

    /** The tiles of the zones of [zones] standing in [part]. */
    fun tiles(zones: List<Zone>, zoneGroups: List<String>, part: Section): List<Grid.Tile> =
        zones.filter { part(it, zoneGroups) == part }.map { tile(it) }

    /** The zones of [zones] that [tiles] put elsewhere, at their new place. */
    fun moved(zones: List<Zone>, tiles: List<Grid.Tile>): List<Zone> {
        val byId = zones.associateBy { it.id }
        return tiles.mapNotNull { tile ->
            val zone = byId[tile.id] ?: return@mapNotNull null
            if (zone.grid_x == tile.column && zone.grid_y == tile.row) null
            else zone.copy(grid_x = tile.column, grid_y = tile.row)
        }
    }

    /** The home screen's groups going from [before] to [after]: the zones that change section move, returned at their new place. */
    fun regroup(zones: List<Zone>, before: List<String>, after: List<String>): List<Zone> =
        moved(zones, Grid.regroup(
            zones.map { tile(it) },
            zones.associate { it.id to Section(section(it.group, before), it.hidden) },
            zones.associate { it.id to Section(section(it.group, after), it.hidden) }
        ))
}
