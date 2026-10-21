package app.treelune.core.ui.components

import androidx.compose.runtime.Composable
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.grid.ToolPositions
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.UI
import org.json.JSONObject

/**
 * The tools of one group section laid on their grid (GridLayout), each tool at its grid_x and
 * grid_y as many cells as its mode takes, in edit mode when [edit] is given. A tile of four
 * cells' width with a body (EXTENDED, SQUARE, FULL) is as tall as its content (GridLayout's
 * grows), EXTENDED and SQUARE at most their cells (ToolCard); in edit mode EXTENDED and SQUARE
 * show all their cells, which their place is chosen on.
 */
@Composable
fun ToolGrid(
    tools: List<ToolInstance>,
    onToolClick: (ToolInstance) -> Unit,
    onToolLongClick: (ToolInstance) -> Unit,
    onOpenEntry: (ToolInstance, app.treelune.core.tools.EntryToOpen) -> Unit,
    edit: GridEdit? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun mode(tool: ToolInstance) = DisplayMode.valueOf(JSONObject(tool.config_json).getString("display_mode"))
    val modes = tools.map(::mode)
    GridLayout(tools, tools.map { ToolPositions.tile(it) }, modes.map { it == DisplayMode.FULL || (edit == null && (it == DisplayMode.EXTENDED || it == DisplayMode.SQUARE)) }, edit) { tool ->
        UI.ToolCard(
            tool = tool,
            displayMode = mode(tool),
            context = context,
            onClick = { onToolClick(tool) },
            onLongClick = { onToolLongClick(tool) },
            onOpenEntry = { entry -> onOpenEntry(tool, entry) }
        )
    }
}

