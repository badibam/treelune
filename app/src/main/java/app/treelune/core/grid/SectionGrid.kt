package app.treelune.core.grid

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.database.entities.VariableEntity
import app.treelune.core.ui.DisplayMode
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
    val size: Grid.Size
) {
    enum class Kind { TOOL, VARIABLE, AUTOMATION, TASK }

    val tile: Grid.Tile get() = Grid.Tile(id, column, row, size.width, size.height)

    companion object {
        fun of(tool: ToolInstance): SectionItem {
            val config = JSONObject(tool.config_json)
            return SectionItem(tool.id, Kind.TOOL, tool.zone_id, config.optString("group").takeIf { it.isNotEmpty() },
                tool.grid_x, tool.grid_y, Grid.size(DisplayMode.valueOf(config.getString("display_mode"))))
        }

        fun of(variable: VariableEntity): SectionItem = SectionItem(variable.id, Kind.VARIABLE, variable.zoneId, variable.group,
            variable.gridX, variable.gridY, Grid.size(DisplayMode.valueOf(variable.displayMode)))

        fun of(routine: app.treelune.core.ai.database.RoutineEntity): SectionItem = SectionItem(routine.id, Kind.AUTOMATION, routine.zoneId,
            routine.group, routine.gridX, routine.gridY, Grid.size(DisplayMode.valueOf(routine.displayMode)))

        fun of(task: app.treelune.core.tasks.TaskEntity): SectionItem = SectionItem(task.id, Kind.TASK, task.zoneId,
            task.group, task.gridX, task.gridY, Grid.size(DisplayMode.valueOf(task.displayMode)))
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
        items.associate { it.id to ToolPositions.section(it.group, before) },
        items.associate { it.id to ToolPositions.section(it.group, after) }
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
 * the group section it is shown in (ToolPositions.section), at its column and row. The services
 * that write a tool or a variable ask here where it goes, and the others that move are written
 * here, inside the caller's transaction.
 */
class SectionGrid(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val writes = app.treelune.core.history.Writes(context)

    /** Every tile of zone [zoneId], tools, variables, routines and Tasks. */
    suspend fun items(zoneId: String): List<SectionItem> =
        database.toolInstanceDao().getToolInstancesByZone(zoneId).map { SectionItem.of(it) } +
            database.variableDao().getByZone(zoneId).map { SectionItem.of(it) } +
            database.aiDao().getRoutinesByZone(zoneId).map { SectionItem.of(it) } +
            database.taskDao().getByZone(zoneId).map { SectionItem.of(it) }

    /** The tiles of [items] shown in [section] of a zone whose groups are [zoneGroups]. */
    fun tiles(items: List<SectionItem>, zoneGroups: List<String>, section: String?): List<Grid.Tile> =
        items.filter { ToolPositions.section(it.group, zoneGroups) == section }.map { it.tile }

    /** [item] arriving at the end of its section's grid, in a zone whose groups are [toolGroups]: its place. */
    suspend fun arrive(item: SectionItem, toolGroups: String?): Grid.Tile {
        val groups = ToolPositions.zoneGroups(toolGroups)
        val others = items(item.zoneId).filter { it.id != item.id }
        return Grid.arrive(tiles(others, groups, ToolPositions.section(item.group, groups)), item.id, item.size).last()
    }

    /** [item] leaving its section's grid: the tiles after the rows it leaves empty move up. */
    suspend fun leave(item: SectionItem, toolGroups: String?) {
        val groups = ToolPositions.zoneGroups(toolGroups)
        val items = items(item.zoneId)
        write(items, Grid.leave(tiles(items, groups, ToolPositions.section(item.group, groups)), item.id))
    }

    /**
     * [before] becoming [after]: a tile that changes zone or section leaves its grid and arrives
     * in the other one; one that changes size in its grid takes it where it is, the tiles it grows
     * over moving down. The other tiles that move are written here; [after]'s place is returned
     * for the caller to write.
     */
    suspend fun move(before: SectionItem, after: SectionItem, oldGroups: String?, newGroups: String?): Grid.Tile {
        val fromSection = ToolPositions.section(before.group, ToolPositions.zoneGroups(oldGroups))
        val toSection = ToolPositions.section(after.group, ToolPositions.zoneGroups(newGroups))
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
    suspend fun place(zoneId: String, zoneGroups: List<String>, section: String?, places: Map<String, Pair<Int, Int>>): Boolean {
        val items = items(zoneId).filter { ToolPositions.section(it.group, zoneGroups) == section }
        if (places.keys != items.map { it.id }.toSet()) return false
        val placed = items.map { item -> places.getValue(item.id).let { (column, row) -> item.tile.copy(column = column, row = row) } }
        if (!Grid.isLaidOut(placed)) return false
        write(items, placed)
        return true
    }

    /** The items of [items] that [tiles] put elsewhere, written at their new place; their ids. */
    private suspend fun write(items: List<SectionItem>, tiles: List<Grid.Tile>): List<String> = save(SectionPlaces.moved(items, tiles))

    private suspend fun save(moved: List<SectionItem>): List<String> = moved.map { item ->
        when (item.kind) {
            SectionItem.Kind.TOOL -> writes.moveTool(item.id, item.column, item.row)
            SectionItem.Kind.VARIABLE -> writes.moveVariable(item.id, item.column, item.row)
            SectionItem.Kind.AUTOMATION -> writes.moveRoutine(item.id, item.column, item.row)
            SectionItem.Kind.TASK -> writes.moveTask(item.id, item.column, item.row)
        }
        item.id
    }
}
