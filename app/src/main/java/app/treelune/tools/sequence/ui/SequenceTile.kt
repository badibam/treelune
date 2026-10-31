package app.treelune.tools.sequence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.ui.components.TileLine
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateTimeFormatter
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.StoredSchedule
import app.treelune.tools.sequence.SequenceToolType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.treelune.tools.sequence.SequenceVariants
import org.json.JSONObject

/**
 * A session's tile (docs/TOOLS.md, « Session (Sequence) »). During a session, the summary is
 * its step and its clock; a session over waiting to be finished; otherwise the planned session waiting and since when, or the next one
 * planned, or the last one done. Its first button starts a session, asking first what the
 * variant asks as the page does (LaunchDialog), then opens the page on it; during a session or
 * one waiting to be finished, it opens the page instead. The body lists the last sessions, one
 * per line: when, state, length, how far.
 */
@Composable
fun rememberSequenceTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "sequence", context = context) }
    val config = remember(tool.config_json) { JSONObject(tool.config_json) }

    var entries by remember { mutableStateOf<List<SequenceEntry>?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // The launch's question open, from the tile's button
    var asking by remember { mutableStateOf(false) }
    val open = { app.treelune.core.navigation.Navigator.push(app.treelune.core.navigation.Place.Tool(tool.id, tool.zone_id)) }
    /** A session started with what the question answered, then its page opened on it. */
    fun start(answer: Map<String, Any?>) = scope.launch {
        val result = coordinator.processUserAction("sequence.${app.treelune.tools.sequence.SequenceService.START}", mapOf("tool_instance_id" to tool.id) + answer)
        if (result.isSuccess) open() else UI.Toast(result.error ?: s.shared("message_error_simple"), app.treelune.core.ui.Duration.LONG)
    }

    LaunchedEffect(tool.id, version) {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to tool.id))
        if (!result.isSuccess) {
            LogManager.ui("Session tile ${tool.id}: entries not read: ${result.error}", "ERROR")
            return@LaunchedEffect
        }
        entries = sequenceEntries(result.data)
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event -> if (event is DataChangeEvent.ToolDataChanged && event.toolInstanceId == tool.id) version++ }
    }

    return remember(tool.id, config.toString()) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                val loaded = entries ?: return
                val running = loaded.firstOrNull { it.run != null }?.run
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                if (running != null) LaunchedEffect(running) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
                val toFinish = loaded.firstOrNull { it.status == SequenceToolType.Status.TO_FINISH }
                val planned = loaded.filter { it.status == SequenceToolType.Status.PLANNED }.maxByOrNull { it.timestamp }
                val last = loaded.filter { it.status == SequenceToolType.Status.DONE || it.status == SequenceToolType.Status.STOPPED }.maxByOrNull { it.timestamp }
                val (first, second) = when {
                    running != null -> running.current.name to stepClock(running, now)
                    toFinish != null -> s.tool("tile_to_finish") to FormatUtils.formatRelativeTimeShort(toFinish.timestamp, context)
                    planned != null -> s.tool("tile_planned") to FormatUtils.formatRelativeTimeShort(planned.timestamp, context)
                    else -> {
                        val next = (StoredSchedule.of(config) as? StoredSchedule.Readable)
                            ?.takeIf { config.optBoolean(SequenceToolType.ENABLED, true) }
                            ?.let { ScheduleCalculator.calculateNextExecution(it.schedule.pattern, System.currentTimeMillis()) }
                        when {
                            next != null -> s.tool("tile_next") to DateTimeFormatter.formatForDisplay(next, context)
                            last != null -> s.tool("tile_last") to FormatUtils.formatRelativeTimeShort(last.timestamp, context)
                            else -> s.tool("history_empty") to ""
                        }
                    }
                }
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    // Its button first: a session started, or the one going on, or waiting to be finished, opened
                    if (running != null || toFinish != null) UI.ActionButton(action = app.treelune.core.ui.ButtonAction.VIEW,
                        display = app.treelune.core.ui.ButtonDisplay.ICON, size = app.treelune.core.ui.Size.S, onClick = open)
                    else UI.ActionButton(action = app.treelune.core.ui.ButtonAction.START, display = app.treelune.core.ui.ButtonDisplay.ICON,
                        size = app.treelune.core.ui.Size.S, onClick = {
                            if (SequenceVariants.of(config) == SequenceVariants.STEPS) start(emptyMap()) else asking = true
                        })
                    Column(modifier = Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                        UI.Text(first, TextType.BODY, maxLines = 1)
                        if (second.isNotEmpty()) UI.Text(second, TextType.CAPTION, maxLines = 1)
                    }
                }
                if (asking) LaunchDialog(config, loaded.filter { it.status != SequenceToolType.Status.PLANNED && it.status != SequenceToolType.Status.TO_FINISH }, s,
                    onAnswer = { answer -> asking = false; start(answer) }, onCancel = { asking = false })
            }

            @Composable
            override fun Body(rows: Int?) {
                val past = entries?.filter { it.status != SequenceToolType.Status.PLANNED && it.run == null }?.sortedByDescending { it.timestamp } ?: return
                TileGrid(rows, past, columns = 1) { entry -> TileLine(historyLine(entry, s, context), maxLines = 1) }
            }
        }
    }
}
