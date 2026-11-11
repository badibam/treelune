package app.treelune.tools.structured.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.strings.Strings
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileLine
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager

/** How many sheets a FULL tile shows. */
private const val FULL_SHEETS = 10

/** A sheet as the tile shows it: its name and its user's fields. */
private data class TileSheet(val id: String, val name: String, val updatedAt: Long, val extra: Map<String, Any?>)

/**
 * A Records tool's tile. The summary is how many records it holds and a button that opens the
 * tool on a new sheet; the body the sheets last changed, each its name, then its user's fields on
 * the lines its tile gives it (TileItem), ten when it follows its content. Touching a sheet opens it.
 */
@Composable
fun rememberStructuredTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "structured", context = context) }
    var sheets by app.treelune.core.ui.rememberShown<List<TileSheet>?>("tile:${tool.id}:sheets") { null }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id, "fields" to listOf("id", "name", "updated_at") + app.treelune.core.ui.components.tileItemFieldPaths(org.json.JSONObject(tool.config_json))))
        if (!result.isSuccess) {
            LogManager.ui("Structured tile ${tool.id}: sheets not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        sheets = (result.data?.get("entries") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            .map { TileSheet(it["id"] as String, it["name"] as? String ?: "", (it["updated_at"] as Number).toLong(),
                (it["extra"] as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value }.orEmpty()) }
            .sortedByDescending { it.updatedAt }
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++
        }
    }

    return remember(tool.id) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = sheets ?: return
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    UI.Text(
                        if (loaded.size == 1) s.tool("tile_count_one") else s.tool("tile_count_many").format(loaded.size.toString()),
                        TextType.BODY, maxLines = 1
                    )
                    UI.Button(type = ButtonType.PRIMARY, size = Size.S, onClick = { open(EntryToOpen.New) }) {
                        UI.Text(s.tool("new_sheet"), TextType.LABEL, maxLines = 1)
                    }
                }
            }

            @Composable
            override fun Body(room: app.treelune.core.look.TileSize.Room) {
                val loaded = sheets ?: return
                TileGrid(room, if (room.rows == null) loaded.take(FULL_SHEETS) else loaded) { sheet ->
                    app.treelune.core.ui.components.TileItem(sheet.name, modifier = Modifier.clickable { open(EntryToOpen.Existing(sheet.id)) },
                        fields = app.treelune.core.ui.components.tileItemFields(app.treelune.tools.structured.StructuredToolType, org.json.JSONObject(tool.config_json), sheet.extra, context))
                }
            }
        }
    }
}
