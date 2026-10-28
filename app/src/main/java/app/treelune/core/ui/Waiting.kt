package app.treelune.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.LogManager

/** What waits on the screen shown, which the tiles read; nothing where no screen provides it. */
val LocalWaiting = androidx.compose.runtime.staticCompositionLocalOf { Waiting() }

/**
 * What waits for the user in the zones (docs/BRICKS.md, Attente, its first form): the entries of
 * each tool and each tool's oldest (tools.waiting), the runs of each routine (routines.waiting),
 * and both summed by zone.
 */
data class Waiting(
    val tools: Map<String, Int> = emptyMap(),
    val zones: Map<String, Int> = emptyMap(),
    val oldest: Map<String, String> = emptyMap(),
    val routines: Map<String, Int> = emptyMap()
) {
    fun tool(id: String): Boolean = (tools[id] ?: 0) > 0
    fun zone(id: String): Boolean = (zones[id] ?: 0) > 0
    fun routine(id: String): Boolean = (routines[id] ?: 0) > 0
}

/**
 * What waits in the tools and routines of [zoneId], or of every zone when null, read again
 * whenever tools, their entries, the zones or the sessions change; nothing while it is read, and
 * nothing when a read fails, which is logged.
 */
@Composable
fun rememberWaiting(zoneId: String?): Waiting {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf(Waiting()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged || event is DataChangeEvent.ToolsChanged ||
                event is DataChangeEvent.AISessionsChanged || event is DataChangeEvent.ZonesChanged) version++
        }
    }
    LaunchedEffect(zoneId, version) {
        val params = zoneId?.let { mapOf("zone_id" to it) } ?: emptyMap()
        val coordinator = Coordinator(context)
        val tools = coordinator.processUserAction("tools.waiting", params)
        val runs = coordinator.processUserAction("automations.waiting", params)
        if (!tools.isSuccess || !runs.isSuccess) {
            LogManager.ui("rememberWaiting: not read: ${tools.error ?: runs.error}", "ERROR")
            return@LaunchedEffect
        }
        fun counts(result: app.treelune.core.commands.CommandResult, key: String) =
            (result.data?.get(key) as? Map<*, *>)?.entries?.associate { it.key.toString() to (it.value as Number).toInt() } ?: emptyMap()
        val oldest = (tools.data?.get("oldest") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
        val zones = (counts(tools, "zones").keys + counts(runs, "zones").keys)
            .associateWith { (counts(tools, "zones")[it] ?: 0) + (counts(runs, "zones")[it] ?: 0) }
        waiting = Waiting(counts(tools, "tools"), zones, oldest, counts(runs, "automations"))
    }
    return waiting
}
