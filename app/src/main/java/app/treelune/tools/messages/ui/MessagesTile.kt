package app.treelune.tools.messages.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.database.entities.ToolInstance
import app.treelune.core.strings.Strings
import app.treelune.core.tools.EntryToOpen
import app.treelune.core.tools.ToolTile
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.TileLine
import app.treelune.core.ui.components.TileGrid
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.DateTimeFormatter
import app.treelune.core.utils.FormatUtils
import app.treelune.core.utils.LogManager

/** How many messages a FULL tile shows, unread and read together. */
private const val FULL_MESSAGES = 10

/** What the tile shows: the messages received, unread first, and the next send. */
private data class Inbox(val unread: List<Occurrence>, val read: List<Occurrence>, val nextSend: Long?)

/**
 * A messages tool's tile. The summary counts the unread messages and names the oldest, which a
 * touch on the tile opens; with none, it says all are read and when the next one goes out. The
 * body lists the unread messages from the oldest, then the last read ones, each its title (the
 * unread ones stronger) and how long ago, then on the lines its tile gives it its text and its
 * user's fields (TileItem), ten when it follows its content. Opening a message reads it.
 */
@Composable
fun rememberMessagesTile(tool: ToolInstance, open: (EntryToOpen) -> Unit): ToolTile {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val s = remember { Strings.`for`(tool = "messages", context = context) }
    var inbox by app.treelune.core.ui.rememberShown<Inbox?>("tile:${tool.id}:inbox") { null }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(tool.id, version) {
        val onError: (String) -> Unit = { LogManager.ui("Messages tile ${tool.id}: $it", "ERROR") }
        val sent = loadByStatus(context, coordinator, tool.id, "sent", onError) ?: return@LaunchedEffect
        val pending = loadByStatus(context, coordinator, tool.id, "pending", onError) ?: return@LaunchedEffect
        val received = sent.filterNot { it.archived }
        inbox = Inbox(
            unread = received.filterNot { it.read }.sortedBy { it.dueAt },
            read = received.filter { it.read }.sortedByDescending { it.dueAt },
            nextSend = pending.minOfOrNull { it.dueAt }
        )
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
                val loaded = inbox ?: return
                val oldest = loaded.unread.firstOrNull()
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    if (oldest != null) {
                        UI.Text(if (loaded.unread.size == 1) s.tool("tile_unread_one") else s.tool("tile_unread").format(loaded.unread.size.toString()), TextType.BODY, maxLines = 1)
                        UI.Text(oldest.displayTitle, TextType.CAPTION, maxLines = 1)
                    } else {
                        UI.Text(s.tool("tile_all_read"), TextType.BODY, maxLines = 1)
                        UI.Text(
                            loaded.nextSend?.let { s.tool("tile_next").format(DateTimeFormatter.formatForDisplay(it, context)) } ?: s.tool("tile_no_send"),
                            TextType.CAPTION, maxLines = 1
                        )
                    }
                }
            }

            @Composable
            override fun Body(room: app.treelune.core.look.TileSize.Room) {
                val loaded = inbox ?: return
                val shown = (loaded.unread + loaded.read).let { if (room.rows == null) it.take(FULL_MESSAGES) else it }
                val config = org.json.JSONObject(tool.config_json)
                TileGrid(room, shown) { message ->
                    app.treelune.core.ui.components.TileItem(
                        message.displayTitle,
                        modifier = Modifier.clickable { open(EntryToOpen.Existing(message.id)) },
                        text = message.displayContent?.let { app.treelune.core.markup.Markup.plain(it) },
                        fields = app.treelune.core.ui.components.tileItemFields(app.treelune.tools.messages.MessageToolType, config, message.extra, context),
                        mainType = if (message.read) TextType.CAPTION else TextType.STRONG,
                        trailing = { UI.Text(FormatUtils.formatRelativeTimeShort(message.dueAt, context), TextType.CAPTION, maxLines = 1) }
                    )
                }
            }
        }
    }
}
