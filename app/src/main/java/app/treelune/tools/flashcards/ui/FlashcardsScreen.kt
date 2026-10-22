package app.treelune.tools.flashcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.CardType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.chatButtonSpace
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.flashcards.FlashcardsToolType
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A deck's page (docs/TOOLS.md, « Flashcards »): a line « Everything », then a line per
 * group, each with its due and new cards. Touching one opens the launch window, a line per direction
 * and « Both », with their numbers; touching one starts the session. A deck of one direction starts
 * at once. The session takes the page's place until it ends or is left.
 *
 * Read again when any tool's entries change, the source's among them, and when the tools do.
 */
@Composable
fun FlashcardsScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val s = remember { Strings.`for`(tool = "flashcards", context = context) }

    var config by remember { mutableStateOf<JSONObject?>(null) }
    var counts by remember { mutableStateOf<DeckCounts?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    // The group whose launch window is open: "" for the whole deck; null when none is
    var launching by rememberSaveable { mutableStateOf<String?>(null) }
    // The session under way, saved across a rotation (SessionState); null when none
    var session by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (config == null) { problem = tool.error ?: s.shared("tools_loading_config"); return@LaunchedEffect }
        val result = coordinator.processUserAction("flashcards.counts", mapOf("tool_instance_id" to toolInstanceId))
        if (result.isSuccess) { counts = DeckCounts.of(result.data); problem = null } else problem = result.error
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ }
    }

    /** A session over [group] (null for the whole deck) and [direction] (null for every one), its cards read now. */
    fun start(group: String?, direction: String?) {
        launching = null
        scope.launch {
            val result = coordinator.processUserAction("flashcards.due", buildMap {
                put("tool_instance_id", toolInstanceId)
                group?.let { put("group", it) }
                direction?.let { put("direction", it) }
            })
            if (!result.isSuccess) { UI.Toast(result.error ?: s.shared("message_error_simple"), Duration.LONG); return@launch }
            val started = SessionState.started(result.data)
            if (started.session.over) UI.Toast(s.tool("session_nothing"), Duration.SHORT)
            else session = started.toJson().toString()
        }
    }

    val loaded = config
    val settings = loaded?.let { ToolConfigSettings.read(FlashcardsToolType, it, context) }
    val name = settings?.string("name") ?: FlashcardsToolType.getDisplayName(context)

    val running = session
    if (running != null && loaded != null) {
        FlashcardsSessionView(
            toolInstanceId = toolInstanceId,
            deckName = name,
            config = loaded,
            state = SessionState.fromJson(JSONObject(running)),
            onChange = { session = it.toJson().toString() },
            onLeave = { session = null }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.L).verticalScroll(rememberScrollState()).chatButtonSpace(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = name,
            subtitle = settings?.string("description")?.takeIf { it.isNotBlank() },
            icon = settings?.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings?.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )
        problem?.let { UI.Text(it, TextType.ERROR) }
        val deck = counts ?: return@Column run { if (problem == null) UI.LoadingIndicator() }

        fun open(group: DeckCounts.Counts) {
            // One direction: no window to choose in
            if (group.directions.size == 1) start(group.group, group.directions.single().key)
            else launching = group.group ?: ""
        }
        GroupLine(s.tool("group_all"), deck.all, s, strong = true) { open(deck.all) }
        deck.groups.forEach { group -> GroupLine(group.label ?: "", group, s) { open(group) } }
        if (deck.groups.isNotEmpty()) UI.Text(s.tool("groups_note"), TextType.CAPTION)
    }

    val window = launching
    val deck = counts
    if (window != null && deck != null) {
        val group = if (window.isEmpty()) deck.all else deck.groups.firstOrNull { it.group == window }
        // The group gone since its window opened: the window closes
        if (group == null) LaunchedEffect(window) { launching = null }
        else UI.Dialog(type = DialogType.SELECTION, onConfirm = {}, onCancel = { launching = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(group.label ?: s.tool("group_all"), TextType.TITLE)
                group.directions.forEach { d ->
                    LaunchLine(d.name, countsText(DeckCounts.Counts(null, null, d.due, d.new, emptyList()), s), !d.empty) { start(group.group, d.key) }
                }
                LaunchLine(s.tool("direction_all"), countsText(group, s), !group.empty) { start(group.group, null) }
            }
        }
    }
}

/** A line of the page: a group, or the whole deck, and what it has for today. */
@Composable
private fun GroupLine(label: String, counts: DeckCounts.Counts, s: app.treelune.core.strings.StringsContext, strong: Boolean = false, onClick: () -> Unit) {
    UI.Card(type = CardType.DEFAULT) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(UI.Space.M),
            horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) { UI.Text(label, if (strong) TextType.STRONG else TextType.BODY) }
            UI.Text(countsText(counts, s), TextType.CAPTION)
        }
    }
}

/** A line of the launch window: a direction, or every one, its numbers; one with nothing for today does nothing. */
@Composable
private fun LaunchLine(label: String, numbers: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = UI.Space.S),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) { UI.Text(label, if (enabled) TextType.BODY else TextType.CAPTION) }
        UI.Text(numbers, TextType.CAPTION)
    }
}
