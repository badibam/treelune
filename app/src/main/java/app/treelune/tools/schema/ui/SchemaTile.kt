package app.treelune.tools.schema.ui

import app.treelune.core.drawing.diagram.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeNotifier
import org.json.JSONObject

/**
 * A schema's tile (docs/design/schema.md, « La tuile »), without a summary
 * (ToolTypeContract.tileSummary): the body is the schema, scaled down to a fixed room under the
 * header, whole at its reading size in a tile that follows its content; 4×1, it stands beside the
 * header as in a body of 2×1. A schema that cannot be drawn says so in its place. A touch opens the
 * tool.
 */
@Composable
fun rememberSchemaTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "schema", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }

    var reading by app.treelune.core.ui.rememberShown<DiagramReading?>("tile:${tool.id}:reading") { null }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool.id, config.toString(), version) { reading = schemaReading(config, context) }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ }
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            /** Never laid by the tile, which has none: what stands where the schema cannot be drawn. */
            @Composable
            override fun Summary() {
                if (reading is DiagramReading.Problem) UI.Text(s.tool("tile_problem"), TextType.CAPTION, maxLines = 2)
            }

            @Composable
            override fun Body(room: app.treelune.core.look.TileSize.Room) = Drawn(room)

            @Composable
            override fun Glance() = Drawn(app.treelune.core.look.TileSize.Room(2, 1))

            /** The schema in [room], nothing where not even its dots hold; why it cannot be drawn, in its place. */
            @Composable
            private fun Drawn(room: app.treelune.core.look.TileSize.Room) {
                val read = reading as? DiagramReading.Drawn ?: return Summary()
                if (read.spec.nodes.isEmpty()) return
                val folded = read.spec.nodes.filter { it.folded && it.id in read.spec.holders }.map { it.id }.toSet()
                // Sideways when the body has more columns than rows, a square one staying upright
                // (docs/design/progression.md): read on the cells, never by placing both ways
                if (room.rows == null) DiagramView(read, folded, onTouch = null)
                else DiagramView(read, folded, onTouch = null, modifier = Modifier.fillMaxSize(), reduced = true,
                    // A top level in parts fills the tile as a grid of them, each running down
                    across = room.width > room.rows && !read.spec.parts)
            }
        }
    }
}
