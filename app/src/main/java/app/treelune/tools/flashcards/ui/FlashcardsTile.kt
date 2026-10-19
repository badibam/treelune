package app.treelune.tools.flashcards.ui

import androidx.compose.foundation.layout.Box
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
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.ui.components.TileLine
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.LogManager

/**
 * A deck's tile. The summary is what is left for today, every direction together: « 14 to review ·
 * 6 new »; the body a line per group with its own, as the page shows them. A touch opens the deck.
 * Read again when any tool's entries change, the source's among them: a word added is a card more.
 */
@Composable
fun rememberFlashcardsTile(tool: ToolInstance): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "flashcards", context = context) }
    var counts by remember { mutableStateOf<DeckCounts?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, tool.config_json, version) {
        val result = coordinator.processUserAction("flashcards.counts", mapOf("tool_instance_id" to tool.id))
        if (!result.isSuccess) {
            LogManager.ui("Flashcards tile ${tool.id}: counts not read: ${result.error}", "ERROR")
            problem = result.error
            return@LaunchedEffect
        }
        counts = DeckCounts.of(result.data)
        problem = null
    }
    LaunchedEffect(tool.id) {
        DataChangeNotifier.changes.collect { event ->
            if (event is DataChangeEvent.ToolDataChanged || event is DataChangeEvent.ToolsChanged) version++
        }
    }

    return remember(tool.id) {
        object : ToolTile {
            @Composable
            override fun Summary() {
                problem?.let { return UI.Text(it, TextType.CAPTION, maxLines = 2) }
                val loaded = counts ?: return
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    UI.Text(countsText(loaded.all, s), TextType.BODY, maxLines = 2)
                }
            }

            @Composable
            override fun Body(rows: Int?) {
                val loaded = counts ?: return
                TileGrid(rows, loaded.groups, columns = 1) { group ->
                    TileLine(group.label ?: "", trailing = { UI.Text(countsText(group, s), TextType.CAPTION, maxLines = 1) })
                }
            }
        }
    }
}

/** « 14 to review · 6 new », what is left alone when the other is none, « Nothing for today » when both are. */
internal fun countsText(counts: DeckCounts.Counts, s: app.treelune.core.strings.StringsContext): String = listOfNotNull(
    s.tool("count_due").format(counts.due).takeIf { counts.due > 0 },
    s.tool("count_new").format(counts.new).takeIf { counts.new > 0 }
).joinToString(" · ").ifEmpty { s.tool("count_nothing") }
