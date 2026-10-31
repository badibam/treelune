package app.treelune.core.readouts.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.grid.Grid
import app.treelune.core.look.Look
import app.treelune.core.look.Looks
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.readouts.ReadoutSettings
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.services.VariableService
import app.treelune.core.strings.Strings
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.TagColor
import app.treelune.core.ui.CardType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DataChangeEvent
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/** A readout as readouts.list gives it: its look, its place among its section's tools, its settings, what it reads. */
data class ReadoutRow(
    val id: String,
    val zoneId: String,
    val group: String?,
    val look: Look,
    val gridX: Int,
    val gridY: Int,
    val settings: JSONObject,
    val toolsRead: Set<String>,
    /** The zone of the tool or the zone it opens; null for the app */
    val targetZoneId: String?
) {
    val tile: Grid.Tile get() = Grid.size(look.displayMode).let { Grid.Tile(id, gridX, gridY, it.width, it.height) }

    /** The places it rises to besides its own tile. */
    val places: Set<ReadoutSettings.Place> get() = ReadoutSettings.places(settings)

    companion object {
        fun of(map: Map<*, *>): ReadoutRow {
            val settings = JsonUtils.toJSONObject((map["settings"] as Map<*, *>).entries.associate { it.key.toString() to it.value })
            return ReadoutRow(
                id = map["id"] as String,
                zoneId = map["zone_id"] as String,
                group = settings.optString("group").takeIf { it.isNotEmpty() },
                look = Looks.read(ReadoutSettings.LOOK, settings),
                gridX = (map["grid_x"] as Number).toInt(),
                gridY = (map["grid_y"] as Number).toInt(),
                settings = settings,
                toolsRead = (map["tools_read"] as? List<*>)?.map { it.toString() }?.toSet() ?: emptySet(),
                targetZoneId = map["target_zone_id"] as? String
            )
        }

        /** Every readout of the app, or the error that kept them from being read. */
        suspend fun all(coordinator: Coordinator): Result<List<ReadoutRow>> {
            val result = coordinator.processUserAction("readouts.list_all", emptyMap())
            if (!result.isSuccess) return Result.failure(IllegalStateException(result.error ?: ""))
            return Result.success((result.data?.get("readouts") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { of(it) })
        }
    }
}

/** What a readout says now: its sentence, its colour, whether it signals. */
data class ReadoutShown(val text: String, val color: TagColor?, val signal: Boolean)

/**
 * What [readout] says now, null while it is first read; read again when an entry of a tool it
 * reads changes, or a variable, or the readouts themselves. A read that fails says why in its place.
 */
@Composable
fun rememberReadoutShown(readout: ReadoutRow): ReadoutShown? {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var shown by remember(readout.id) { mutableStateOf<ReadoutShown?>(null) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(readout.toolsRead) {
        DataChangeNotifier.changes.collect { event ->
            val read = event is DataChangeEvent.ToolDataChanged &&
                (event.toolInstanceId in readout.toolsRead || VariableService.ANY_TOOL in readout.toolsRead)
            if (read || event is DataChangeEvent.VariablesChanged) version++
        }
    }
    LaunchedEffect(readout, version) {
        val result = Coordinator(context).processUserAction("readouts.read", mapOf("readout_id" to readout.id))
        shown = if (!result.isSuccess) ReadoutShown(result.error ?: s.shared("field_reference_unreadable"), null, false)
        else ReadoutShown(result.data?.get("text") as? String ?: "", IconColor.of(result.data?.get("color") as? String), result.data?.get("signal") == true)
    }
    return shown
}

/** Opens what [readout] leads to: the home screen, a zone, a tool. */
fun openTarget(readout: ReadoutRow) {
    val target = ReferenceTarget.referenceOf(readout.settings.opt(ReadoutSettings.TARGET)) ?: return
    when (target.kind) {
        ReferenceKind.APP -> Navigator.popTo(0)
        ReferenceKind.ZONE -> target.id?.let { Navigator.push(Place.Zone(it)) }
        ReferenceKind.TOOL_INSTANCE -> {
            val zoneId = readout.targetZoneId ?: return
            target.id?.let { Navigator.push(Place.Tool(it, zoneId)) }
        }
        else -> {}
    }
}

/** Opens [readout]'s config. */
fun configure(readout: ReadoutRow) = Navigator.push(Place.ReadoutConfig(readout.zoneId, readout.id))

/** A readout's sentence in its colour (UI.Tag), or plain without one. */
@Composable
fun ReadoutSentence(shown: ReadoutShown?, maxLines: Int = 2) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    when {
        shown == null -> UI.Text(s.shared("tools_loading"), TextType.BODY, maxLines = maxLines)
        shown.color != null -> UI.Tag(shown.text, shown.color)
        else -> UI.Text(shown.text, TextType.BODY, maxLines = maxLines)
    }
}

/**
 * A readout's own tile among its section's tools (UI.LookCard): its look, its sentence beside its
 * header (LINE) or under it (CONDENSED). Touching it opens its target, a long touch its config.
 */
@Composable
fun ReadoutCard(readout: ReadoutRow) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val shown = rememberReadoutShown(readout)
    UI.LookCard(
        look = readout.look,
        subtitle = s.shared("readout_display_name"),
        seed = readout.id,
        // Its signal marks its icon, as something waiting marks a tool's
        waiting = shown?.signal == true,
        running = false,
        onClick = { openTarget(readout) },
        onLongClick = { configure(readout) },
        summary = { ReadoutSentence(shown) },
        glance = { ReadoutSentence(shown) },
        body = { }
    )
}

/**
 * The readouts that rise to one place, gathered in one card: a line each, its icon and its
 * sentence in its colour, in the order given (the tree's). A line touched opens its target, held
 * its config. Nothing when there are none.
 */
@Composable
fun ReadoutLines(readouts: List<ReadoutRow>, maxLines: Int? = null) {
    if (readouts.isEmpty()) return
    val shownOnes = if (maxLines != null) readouts.take(maxLines) else readouts
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            shownOnes.forEach { readout -> ReadoutLine(readout) }
        }
    }
}

/** One readout on one line of a gathered card. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReadoutLine(readout: ReadoutRow) {
    val shown = rememberReadoutShown(readout)
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { openTarget(readout) }, onLongClick = { configure(readout) }),
        horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        readout.look.iconName?.let { UI.ItemIcon(it, IconColor.of(readout.look.iconColor), 20.dp) }
        ReadoutSentence(shown, maxLines = 1)
    }
}

/**
 * The dots of the readouts that signal, for a tile that holds no line (a zone's in ICON or
 * MINIMAL): one per readout that signals, three at most, in the order given, each in its colour.
 */
@Composable
fun ReadoutDots(readouts: List<ReadoutRow>) {
    // Each read in its own place of the composition, keyed by its id: the list may change
    val shown = readouts.map { readout -> androidx.compose.runtime.key(readout.id) { rememberReadoutShown(readout) } }
    val signalling = shown.filterNotNull().filter { it.signal }.take(MAX_DOTS)
    if (signalling.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        signalling.forEach { UI.ItemIcon("circle", it.color, 8.dp) }
    }
}

/** The dots a tile shows at most. */
private const val MAX_DOTS = 3

/** The readouts among [readouts] that rise to [place], in the order of the tree given by [zoneOrder] (zone ids) and each zone's [groupsOf]. */
fun risingTo(readouts: List<ReadoutRow>, place: ReadoutSettings.Place, zoneOrder: List<String>, groupsOf: (String) -> List<String>): List<ReadoutRow> =
    readouts.filter { place in it.places }.sortedWith(compareBy<ReadoutRow>(
        { zoneOrder.indexOf(it.zoneId).let { i -> if (i < 0) Int.MAX_VALUE else i } },
        { groupsOf(it.zoneId).indexOf(it.group).let { i -> if (i < 0) Int.MAX_VALUE else i } },
        { it.gridY }, { it.gridX }
    ))
