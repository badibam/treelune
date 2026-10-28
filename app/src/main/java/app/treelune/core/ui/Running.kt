package app.treelune.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager

/** What runs on the screen shown, which the tiles mark; nothing where no screen provides it. */
val LocalRunning = androidx.compose.runtime.staticCompositionLocalOf { Running() }

/**
 * The tools with a stopwatch running on one of their entries (tools.running), the routine whose
 * run the AI is doing now, and the zones holding one or the other.
 */
data class Running(val tools: Set<String> = emptySet(), val zones: Set<String> = emptySet(), val routines: Set<String> = emptySet()) {
    fun tool(id: String): Boolean = id in tools
    fun zone(id: String): Boolean = id in zones
    fun routine(id: String): Boolean = id in routines
}

/**
 * The stopwatches running in every tool, read again whenever tools or their entries change, and
 * the routine whose run is the AI's session now, read again when that session changes; none while
 * they are read, and none when a read fails, which is logged.
 */
@Composable
fun rememberRunning(): Running {
    val context = LocalContext.current
    var running by remember { mutableStateOf(Running()) }
    var version by remember { mutableIntStateOf(0) }
    // The routine running and its zone: the AI's session, when it is a routine's run
    val ai by app.treelune.core.ai.orchestration.AIOrchestrator.currentState.collectAsState()
    var routine by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(ai.sessionId, ai.sessionType) {
        val sessionId = ai.sessionId
        routine = if (sessionId == null || ai.sessionType != app.treelune.core.ai.data.SessionType.ROUTINE_RUN) null else {
            val coordinator = Coordinator(context)
            val routineId = coordinator.processUserAction("ai_sessions.get", mapOf("id" to sessionId)).data?.get("routine_id") as? String
            val found = routineId?.let { coordinator.processUserAction("routines.get", mapOf("routine_id" to it)).data?.get("routine") as? Map<*, *> }
            if (routineId == null || found == null) {
                LogManager.ui("rememberRunning: the routine of run $sessionId not read", "ERROR")
                null
            } else routineId to (found["zone_id"] as String)
        }
    }
    LaunchedEffect(Unit) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged || event is DataChangeEvent.ToolsChanged) version++
        }
    }
    LaunchedEffect(version) {
        val result = Coordinator(context).processUserAction("tools.running", emptyMap())
        if (!result.isSuccess) {
            LogManager.ui("rememberRunning: not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        fun ids(key: String) = (result.data?.get(key) as? List<*>).orEmpty().map { it.toString() }.toSet()
        running = Running(ids("tools"), ids("zones"))
    }
    val now = routine ?: return running
    return running.copy(zones = running.zones + now.second, routines = setOf(now.first))
}
