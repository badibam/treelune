package app.treelune.core.ui.components

import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.grid.SectionItem
import app.treelune.core.ui.UI
import app.treelune.core.ui.variables.VariableCard
import app.treelune.core.ui.variables.VariableRow
import org.json.JSONObject

/** One tile of a section as the screen holds it: a tool, a variable, a routine, a Task or a readout. */
sealed interface SectionTile {
    val id: String
    val tile: app.treelune.core.grid.Grid.Tile
    val size: app.treelune.core.look.TileSize
    /** Its look: its name, its icon, its size. */
    val look: app.treelune.core.look.Look

    data class Tool(val tool: ToolInstance) : SectionTile {
        override val id get() = tool.id
        override val tile get() = SectionItem.of(tool).tile
        override val size get() = app.treelune.core.look.TileSize.ofStored(JSONObject(tool.config_json).getString("tile_size"))
        override val look get() = app.treelune.core.look.Looks.read(
            app.treelune.core.tools.ToolConfigSettings.look(requireNotNull(app.treelune.core.tools.ToolTypeManager.getToolType(tool.tooltype)) { "No tool type '${tool.tooltype}'" }),
            JSONObject(tool.config_json))
    }

    data class Variable(val variable: VariableRow) : SectionTile {
        override val id get() = variable.id
        override val tile get() = variable.tile
        override val size get() = variable.look.tileSize
        override val look get() = variable.look
    }

    data class Routine(val routine: app.treelune.core.ai.data.Routine) : SectionTile {
        override val id get() = routine.id
        override val tile get() = routine.tile
        override val size get() = routine.look.tileSize
        override val look get() = routine.look
    }

    data class TaskTile(val task: app.treelune.core.tasks.Task) : SectionTile {
        override val id get() = task.id
        override val tile get() = task.tile
        override val size get() = task.look.tileSize
        override val look get() = task.look
    }

    data class Readout(val readout: app.treelune.core.readouts.ui.ReadoutRow) : SectionTile {
        override val id get() = readout.id
        override val tile get() = readout.tile
        override val size get() = readout.look.tileSize
        override val look get() = readout.look
    }
}

/** A tool of a tools.list result asked with its config and its place. */
@Suppress("UNCHECKED_CAST")
fun toolInstanceOf(map: Map<String, Any?>) = ToolInstance(
    id = map["id"] as String,
    zone_id = map["zone_id"] as String,
    tooltype = map["tooltype"] as String,
    config_json = app.treelune.core.utils.JsonUtils.toJSONObject(map["config"] as Map<String, Any?>).toString(),
    grid_x = (map["grid_x"] as Number).toInt(),
    grid_y = (map["grid_y"] as Number).toInt(),
    created_at = (map["created_at"] as Number).toLong(),
    updated_at = (map["updated_at"] as Number).toLong(),
    hidden = map["hidden"] as Boolean
)

/**
 * Every piece of [zoneId] as a tile — its tools, variables, routines, Tasks and readouts —, read
 * through the dispatcher as the zone's screen reads them; the first refusal fails it, with its reason.
 */
suspend fun zonePieceTiles(coordinator: app.treelune.core.coordinator.Coordinator, zoneId: String): Result<List<SectionTile>> {
    suspend fun read(operation: String, params: Map<String, Any?>, key: String): List<Map<*, *>> {
        val result = coordinator.processUserAction(operation, params)
        if (result.status != app.treelune.core.commands.CommandStatus.SUCCESS) throw IllegalStateException(result.error ?: operation)
        return (result.data?.get(key) as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    }
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        read("tools.list", mapOf("zone_id" to zoneId, "include_config" to true, "include_position" to true), "tool_instances").map { SectionTile.Tool(toolInstanceOf(it as Map<String, Any?>)) } +
            read("variables.list", mapOf("zone_id" to zoneId), "variables").map { SectionTile.Variable(app.treelune.core.ui.variables.VariableRow.of(it)) } +
            read("routines.list", mapOf("zone_id" to zoneId), "routines").map { SectionTile.Routine(app.treelune.core.ai.data.Routine.fromResult(it)) } +
            read("tasks.list", mapOf("zone_id" to zoneId), "tasks").map { SectionTile.TaskTile(app.treelune.core.tasks.Task.fromResult(it)) } +
            read("readouts.list", mapOf("zone_id" to zoneId), "readouts").map { SectionTile.Readout(app.treelune.core.readouts.ui.ReadoutRow.of(it)) }
    }
}

/**
 * The tiles of one group section laid on their grid (GridLayout), tools, variables, routines, Tasks and readouts together,
 * each at its column and row as many cells as its size takes, in edit mode when [edit] is given.
 * A tile of a fixed height fills its cells whatever it shows; one that follows its content is
 * as tall as what it shows (GridLayout's grows).
 */
@Composable
fun SectionTiles(
    tiles: List<SectionTile>,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.OpenAt) -> Unit,
    onOpenVariable: (String) -> Unit,
    routineActions: app.treelune.core.ai.ui.routine.RoutineActions,
    edit: GridEdit? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Only a tile that follows its content grows with it: a fixed height is a fixed box
    val grows = tiles.map { it.size.followsContent }
    GridLayout(tiles, tiles.map { it.tile }, grows, edit) { tile ->
        SectionTileContent(tile, context, onToolClick, onToolLongClick, onOpenEntry, onOpenVariable, routineActions)
    }
}

/** One tile of a section drawn as its kind draws it, at its size, or at the one LocalInnerTile gives. */
@Composable
fun SectionTileContent(
    tile: SectionTile,
    context: android.content.Context,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.OpenAt) -> Unit,
    onOpenVariable: (String) -> Unit,
    routineActions: app.treelune.core.ai.ui.routine.RoutineActions
) = when (tile) {
    is SectionTile.Tool -> UI.ToolCard(
        tool = tile.tool,
        context = context,
        onClick = { onToolClick(tile.tool) },
        onLongClick = { onToolLongClick(tile.tool) },
        onOpenEntry = { entry -> onOpenEntry(tile.tool, entry) }
    )
    // A long touch opens it too: its settings are on its page
    is SectionTile.Variable -> VariableCard(tile.variable, onOpen = { onOpenVariable(tile.variable.id) }, onLongClick = { onOpenVariable(tile.variable.id) })
    is SectionTile.Routine -> app.treelune.core.ai.ui.routine.RoutineTile(tile.routine, routineActions)
    is SectionTile.TaskTile -> app.treelune.core.tasks.ui.TaskTile(tile.task)
    is SectionTile.Readout -> app.treelune.core.readouts.ui.ReadoutCard(tile.readout)
}

/**
 * Whether [tile] has a second cell in icons (docs/design/icon-tiles.md): a tool as its type
 * declares (ToolTypeContract.tileSecond); a variable, a routine, a Task and a readout always — a
 * variable's first value shown on its tile (VariableCard).
 */
fun hasSecond(tile: SectionTile): Boolean = when (tile) {
    is SectionTile.Tool -> app.treelune.core.tools.ToolTypeManager.getToolType(tile.tool.tooltype)?.tileSecond != app.treelune.core.tools.IconSecond.NONE
    else -> true
}
