package app.treelune.core.ui.components

import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.grid.SectionItem
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.UI
import app.treelune.core.ui.variables.VariableCard
import app.treelune.core.ui.variables.VariableRow
import org.json.JSONObject

/** One tile of a section as the screen holds it: a tool, a variable, a routine or a Task. */
sealed interface SectionTile {
    val id: String
    val tile: app.treelune.core.grid.Grid.Tile
    val mode: DisplayMode

    data class Tool(val tool: ToolInstance) : SectionTile {
        override val id get() = tool.id
        override val tile get() = SectionItem.of(tool).tile
        override val mode get() = DisplayMode.valueOf(JSONObject(tool.config_json).getString("display_mode"))
    }

    data class Variable(val variable: VariableRow) : SectionTile {
        override val id get() = variable.id
        override val tile get() = variable.tile
        override val mode get() = variable.look.displayMode
    }

    data class Routine(val automation: app.treelune.core.ai.data.Automation) : SectionTile {
        override val id get() = automation.id
        override val tile get() = automation.tile
        override val mode get() = automation.look.displayMode
    }

    data class TaskTile(val task: app.treelune.core.tasks.Task) : SectionTile {
        override val id get() = task.id
        override val tile get() = task.tile
        override val mode get() = task.look.displayMode
    }
}

/**
 * The tiles of one group section laid on their grid (GridLayout), tools, variables, routines and Tasks together,
 * each at its column and row as many cells as its mode takes, in edit mode when [edit] is given.
 * A tile of four cells' width with a body (EXTENDED, SQUARE, FULL) is as tall as its content
 * (GridLayout's grows), EXTENDED and SQUARE at most their cells (LookCard); in edit mode
 * EXTENDED and SQUARE show all their cells, which their place is chosen on.
 */
@Composable
fun SectionTiles(
    tiles: List<SectionTile>,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.EntryToOpen) -> Unit,
    onOpenVariable: (String) -> Unit,
    automationActions: app.treelune.core.ai.ui.automation.AutomationActions,
    onAutomationLongClick: (app.treelune.core.ai.data.Automation) -> Unit,
    edit: GridEdit? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val grows = tiles.map { it.mode == DisplayMode.FULL || (edit == null && (it.mode == DisplayMode.EXTENDED || it.mode == DisplayMode.SQUARE)) }
    GridLayout(tiles, tiles.map { it.tile }, grows, edit) { tile ->
        when (tile) {
            is SectionTile.Tool -> UI.ToolCard(
                tool = tile.tool,
                context = context,
                onClick = { onToolClick(tile.tool) },
                onLongClick = { onToolLongClick(tile.tool) },
                onOpenEntry = { entry -> onOpenEntry(tile.tool, entry) }
            )
            // A long touch opens it too: its settings are on its page
            is SectionTile.Variable -> VariableCard(tile.variable, onOpen = { onOpenVariable(tile.variable.id) }, onLongClick = { onOpenVariable(tile.variable.id) })
            is SectionTile.Routine -> app.treelune.core.ai.ui.automation.AutomationTile(tile.automation, automationActions) { onAutomationLongClick(tile.automation) }
            is SectionTile.TaskTile -> app.treelune.core.tasks.ui.TaskTile(tile.task)
        }
    }
}
