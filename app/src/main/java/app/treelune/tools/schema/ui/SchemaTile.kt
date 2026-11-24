package app.treelune.tools.schema.ui

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
 * A schema's tile (docs/design/schema.md, « La tuile »). The summary counts its boxes and arrows;
 * the body is the schema, scaled down to a fixed room, whole at its reading size in a tile that
 * follows its content. Where the tile has no body (4×1, 2×2), the schema stands in the summary's
 * place as in a body of 2×1, the summary staying when not even its dots hold there. A touch opens
 * the tool.
 */
@Composable
fun rememberSchemaTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "schema", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }

    var reading by app.treelune.core.ui.rememberShown<SchemaReading?>("tile:${tool.id}:reading") { null }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool.id, config.toString(), version) { reading = SchemaReading.of(config, context) }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ }
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val text = when (val read = reading ?: return) {
                    is SchemaReading.Problem -> s.tool("tile_problem")
                    is SchemaReading.Drawn -> {
                        val boxes = read.spec.nodes.size
                        val arrows = read.spec.edges.size
                        s.tool("tile_counts").format(
                            if (boxes == 1) s.tool("count_box_one") else s.tool("count_boxes").format(boxes),
                            if (arrows == 1) s.tool("count_arrow_one") else s.tool("count_arrows").format(arrows)
                        )
                    }
                }
                UI.Text(text, TextType.CAPTION, maxLines = 2)
            }

            @Composable
            override fun Body(room: app.treelune.core.look.TileSize.Room) = Drawn(room) {}

            @Composable
            override fun Glance() {
                val read = reading as? SchemaReading.Drawn
                if (read == null || (read.spec.nodes.isEmpty() && read.spec.groups.isEmpty())) Summary()
                else Drawn(app.treelune.core.look.TileSize.Room(2, 1)) { Summary() }
            }

            /** The schema in [room]; [orElse] where nothing of it holds there, even in dots. */
            @Composable
            private fun Drawn(room: app.treelune.core.look.TileSize.Room, orElse: @Composable () -> Unit) {
                val read = reading as? SchemaReading.Drawn ?: return
                if (read.spec.nodes.isEmpty() && read.spec.groups.isEmpty()) return
                val folded = read.spec.groups.filter { it.folded }.map { it.id }.toSet()
                // Sideways when the body has more columns than rows, a square one staying upright
                // (docs/design/progression.md): read on the cells, never by placing both ways
                if (room.rows == null) SchemaView(read, folded, onTouch = null)
                else SchemaView(read, folded, onTouch = null, modifier = Modifier.fillMaxSize(), reduced = true, across = room.width > room.rows, orElse = orElse)
            }
        }
    }
}
