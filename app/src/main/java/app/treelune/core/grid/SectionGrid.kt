package app.treelune.core.grid

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.database.entities.VariableEntity
import org.json.JSONObject

/**
 * One tile of a zone's section: a tool or a variable, laid on the same grid (docs/UI.md,
 * « ToolGrid »). Its id names it among both, ids being UUIDs.
 */
data class SectionItem(
    val id: String,
    val kind: Kind,
    val zoneId: String,
    val group: String?,
    val column: Int,
    val row: Int,
    val size: Grid.Size,
    val hidden: Boolean = false
) {
    /** The section, its visible or hidden part, it stands in among a zone's [zoneGroups]. */
    fun part(zoneGroups: List<String>): Section = ToolPositions.part(group, hidden, zoneGroups)

    enum class Kind { TOOL, VARIABLE, ROUTINE, TASK, READOUT }

    val tile: Grid.Tile get() = Grid.Tile(id, column, row, size.width, size.height)

    companion object {
        fun of(tool: ToolInstance): SectionItem {
            val config = JSONObject(tool.config_json)
            return SectionItem(tool.id, Kind.TOOL, tool.zone_id, config.optString("group").takeIf { it.isNotEmpty() },
                tool.grid_x, tool.grid_y, app.treelune.core.look.TileSize.ofStored(config.getString("tile_size")).cells, tool.hidden)
        }

        fun of(variable: VariableEntity): SectionItem = SectionItem(variable.id, Kind.VARIABLE, variable.zoneId, variable.group,
            variable.gridX, variable.gridY, app.treelune.core.look.TileSize.ofStored(variable.tileSize).cells, variable.hidden)

        fun of(routine: app.treelune.core.ai.database.RoutineEntity): SectionItem = SectionItem(routine.id, Kind.ROUTINE, routine.zoneId,
            routine.group, routine.gridX, routine.gridY, app.treelune.core.look.TileSize.ofStored(routine.tileSize).cells, routine.hidden)

        fun of(task: app.treelune.core.tasks.TaskEntity): SectionItem = SectionItem(task.id, Kind.TASK, task.zoneId,
            task.group, task.gridX, task.gridY, app.treelune.core.look.TileSize.ofStored(task.tileSize).cells, task.hidden)

        fun of(readout: app.treelune.core.readouts.ReadoutEntity): SectionItem = SectionItem(readout.id, Kind.READOUT, readout.zoneId,
            readout.group, readout.gridX, readout.gridY, app.treelune.core.look.TileSize.ofStored(readout.tileSize).cells, readout.hidden)
    }
}

/** Where the tiles of a section go, decided without the database. */
object SectionPlaces {

    /**
     * The zone's groups going from [before] to [after], [items] holding the groups they hold once
     * renamed: each tile whose section changes leaves its grid and arrives in the other one, in
     * their order on the screen. The tiles that move, at their new place.
     */
    fun regroup(items: List<SectionItem>, before: List<String>, after: List<String>): List<SectionItem> = moved(items, Grid.regroup(
        items.map { it.tile },
        items.associate { it.id to it.part(before) },
        items.associate { it.id to it.part(after) }
    ))

    /** The items of [items] that [tiles] put elsewhere, at their new place. */
    fun moved(items: List<SectionItem>, tiles: List<Grid.Tile>): List<SectionItem> {
        val byId = items.associateBy { it.id }
        return tiles.mapNotNull { tile ->
            val item = byId[tile.id] ?: return@mapNotNull null
            if (item.column == tile.column && item.row == tile.row) null else item.copy(column = tile.column, row = tile.row)
        }
    }
}

/**
 * Where the tiles of a zone's sections stand, tools and variables together: each in the grid of
 * the section it stands in, its group's visible or hidden part (SectionItem.part), at its column
 * and row. The services
 * that write a tool or a variable ask here where it goes, and the others that move are written
 * here, inside the caller's transaction.
 */
class SectionGrid(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val writes = app.treelune.core.history.Writes(context)

    /** Every tile of zone [zoneId], tools, variables, routines, Tasks and readouts. */
    suspend fun items(zoneId: String): List<SectionItem> =
        database.toolInstanceDao().getToolInstancesByZone(zoneId).map { SectionItem.of(it) } +
            database.variableDao().getByZone(zoneId).map { SectionItem.of(it) } +
            database.aiDao().getRoutinesByZone(zoneId).map { SectionItem.of(it) } +
            database.taskDao().getByZone(zoneId).map { SectionItem.of(it) } +
            database.readoutDao().getByZone(zoneId).map { SectionItem.of(it) }

    /** The tiles of [items] standing in [part] of a zone whose groups are [zoneGroups]. */
    fun tiles(items: List<SectionItem>, zoneGroups: List<String>, part: Section): List<Grid.Tile> =
        items.filter { it.part(zoneGroups) == part }.map { it.tile }

    /** [item] arriving at the end of its section's grid, in a zone whose groups are [toolGroups]: its place. */
    suspend fun arrive(item: SectionItem, toolGroups: String?): Grid.Tile {
        val groups = ToolPositions.zoneGroups(toolGroups)
        val others = items(item.zoneId).filter { it.id != item.id }
        return Grid.arrive(tiles(others, groups, item.part(groups)), item.id, item.size).last()
    }

    /** [item] leaving its section's grid: the tiles after the rows it leaves empty move up. */
    suspend fun leave(item: SectionItem, toolGroups: String?) {
        val groups = ToolPositions.zoneGroups(toolGroups)
        val items = items(item.zoneId)
        write(items, Grid.leave(tiles(items, groups, item.part(groups)), item.id))
    }

    /**
     * [before] becoming [after]: a tile that changes zone or section leaves its grid and arrives
     * in the other one; one that changes size in its grid takes it where it is, the tiles it grows
     * over moving down. The other tiles that move are written here; [after]'s place is returned
     * for the caller to write.
     */
    suspend fun move(before: SectionItem, after: SectionItem, oldGroups: String?, newGroups: String?): Grid.Tile {
        val fromSection = before.part(ToolPositions.zoneGroups(oldGroups))
        val toSection = after.part(ToolPositions.zoneGroups(newGroups))
        if (before.zoneId != after.zoneId || fromSection != toSection) {
            leave(before, oldGroups)
            return arrive(after, newGroups)
        }
        if (after.size == before.size) return after.tile
        val groups = ToolPositions.zoneGroups(newGroups)
        val items = items(after.zoneId)
        val tiles = Grid.resize(tiles(items, groups, toSection), after.id, after.size)
        write(items.filter { it.id != after.id }, tiles)
        return tiles.single { it.id == after.id }
    }

    /** The zone's groups going from [before] to [after] (SectionPlaces.regroup): the tiles that moved written; their ids. */
    suspend fun regroup(items: List<SectionItem>, before: List<String>, after: List<String>): List<String> =
        save(SectionPlaces.regroup(items, before, after))

    /**
     * The tiles of [section] in a zone whose groups are [zoneGroups] put at [places] (id -> column
     * and row): true once written, false when [places] does not name each of them once, or lays
     * them over one another or out of the grid.
     */
    suspend fun place(zoneId: String, zoneGroups: List<String>, section: Section, places: Map<String, Pair<Int, Int>>): Boolean {
        val items = items(zoneId).filter { it.part(zoneGroups) == section }
        if (places.keys != items.map { it.id }.toSet()) return false
        val placed = items.map { item -> places.getValue(item.id).let { (column, row) -> item.tile.copy(column = column, row = row) } }
        if (!Grid.isLaidOut(placed)) return false
        write(items, placed)
        return true
    }

    /**
     * The tiles of several sections put where the grid editor leaves them ([sections], each a
     * section — a group, null the ungrouped one, or its hidden part — and its places): a tile named
     * in a section it does not stand in crosses into it, its group and whether it is hidden becoming
     * that section's, then each section is placed as [place] does. Throws when a section's places
     * do not name each of its tiles once or do not lay them out, for the caller's transaction to
     * undo it all.
     */
    suspend fun placeSections(zoneId: String, zoneGroups: List<String>, sections: List<Pair<Section, Map<String, Pair<Int, Int>>>>) {
        val byId = items(zoneId).associateBy { it.id }
        for ((section, places) in sections) for (id in places.keys) {
            val item = byId[id] ?: throw IllegalStateException("tile $id is not of zone $zoneId")
            if (item.part(zoneGroups) != section) regroup(item, section)
        }
        for ((section, places) in sections) {
            if (!place(zoneId, zoneGroups, section, places)) throw IllegalStateException("section ${section.group ?: "(ungrouped)"}${if (section.hidden) " (hidden)" else ""} not laid out")
        }
    }

    /** [item] given the group of [section] (null for none) and whether it is hidden, as its own kind keeps them. */
    private suspend fun regroup(item: SectionItem, section: Section) {
        val now = System.currentTimeMillis()
        val group = section.group
        val hidden = section.hidden
        when (item.kind) {
            SectionItem.Kind.TOOL -> database.toolInstanceDao().getToolInstanceById(item.id)?.let { tool ->
                val config = org.json.JSONObject(tool.config_json).apply { if (group == null) remove("group") else put("group", group) }
                writes.updateTool(tool.copy(config_json = config.toString(), hidden = hidden, updated_at = now))
            }
            SectionItem.Kind.VARIABLE -> database.variableDao().getById(item.id)?.let { writes.updateVariable(it.copy(group = group, hidden = hidden, updatedAt = now)) }
            SectionItem.Kind.ROUTINE -> database.aiDao().getRoutineById(item.id)?.let { writes.updateRoutine(it.copy(group = group, hidden = hidden, updatedAt = now)) }
            SectionItem.Kind.TASK -> database.taskDao().get(item.id)?.let { writes.updateTask(it.copy(group = group, hidden = hidden, updatedAt = now)) }
            SectionItem.Kind.READOUT -> database.readoutDao().get(item.id)?.let { writes.updateReadout(it.copy(group = group, hidden = hidden, updatedAt = now)) }
        }
    }

    /** The items of [items] that [tiles] put elsewhere, written at their new place; their ids. */
    private suspend fun write(items: List<SectionItem>, tiles: List<Grid.Tile>): List<String> = save(SectionPlaces.moved(items, tiles))

    private suspend fun save(moved: List<SectionItem>): List<String> = moved.map { item ->
        when (item.kind) {
            SectionItem.Kind.TOOL -> writes.moveTool(item.id, item.column, item.row)
            SectionItem.Kind.VARIABLE -> writes.moveVariable(item.id, item.column, item.row)
            SectionItem.Kind.ROUTINE -> writes.moveRoutine(item.id, item.column, item.row)
            SectionItem.Kind.TASK -> writes.moveTask(item.id, item.column, item.row)
            SectionItem.Kind.READOUT -> writes.moveReadout(item.id, item.column, item.row)
        }
        item.id
    }
}
