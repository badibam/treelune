package app.treelune.core.ui.exports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.backup.PartElement
import app.treelune.core.backup.PartLink
import app.treelune.core.backup.PartLinks
import app.treelune.core.backup.PartSelection
import app.treelune.core.backup.PartTree
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * The boxes of [tree]: its zones under their home group, each opened on its elements under their
 * tool group, then its APIs; [left] the links from what is ticked to what is not, each said under
 * its element, a zone's under the zone, an event's under the zone it arrives in, with the event
 * itself when it comes from another zone (PartTree.eventArrivals).
 * Under a zone ticked, [zoneExtra]; under an element ticked, [elementExtra]: what the screen adds
 * (where a zone arrives, how an element will).
 */
@Composable
fun PartTreeChoice(
    tree: PartTree,
    selection: PartSelection,
    onSelection: (PartSelection) -> Unit,
    opened: List<String>,
    onOpened: (List<String>) -> Unit,
    left: Set<PartLink>,
    zoneExtra: @Composable (PartTree.Zone) -> Unit = {},
    elementExtra: @Composable (PartTree.Element) -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val arrivals = tree.eventArrivals(selection)
    var group: String? = null
    tree.zones.forEachIndexed { index, zone ->
        // A home group's title above its first zone
        if (zone.group != null && (index == 0 || zone.group != group)) {
            UI.Card(type = CardType.SECTION_HEADER) {
                Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.M)) { UI.Text(zone.group, TextType.HEADING) }
            }
        }
        group = zone.group

        Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            val kept = zone.elements.count { PartLinks.kept(it.part(), selection) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                Box(modifier = Modifier.weight(1f)) {
                    UI.Checkbox(
                        checked = zone.id in selection.zones,
                        onCheckedChange = { on -> onSelection(Ticks.zone(selection, tree, zone, on)) },
                        label = s.shared(when (kept) { 0 -> "share_zone_count_zero"; 1 -> "share_zone_count_one"; else -> "share_zone_count_other" })
                            .format(zone.name, kept, zone.elements.size)
                    )
                }
                if (zone.elements.isNotEmpty()) {
                    val open = zone.id in opened
                    UI.ActionButton(action = if (open) ButtonAction.UP else ButtonAction.DOWN, display = ButtonDisplay.ICON, size = Size.S, onClick = {
                        onOpened(if (open) opened - zone.id else opened + zone.id)
                    })
                }
            }

            if (zone.id in selection.zones) {
                // The identity it speaks as, left out
                left.filter { it.from.kind == PartElement.Kind.ZONE && it.from.id == zone.id }
                    .forEach { link -> UI.Text(PartLinks.text(link, tree::name, s::shared), if (link.blocking) TextType.WARNING else TextType.CAPTION) }
                // The events arriving here: one from another zone said, and what each lacks
                arrivals.filterValues { it == zone.id }.keys.forEach { eventId ->
                    val event = tree.events.first { it.id == eventId }
                    if (event.zoneId != zone.id) tree.zones.firstOrNull { it.id == event.zoneId }?.let { from ->
                        UI.Text(s.shared("share_event_arrives").format(event.name, from.name), TextType.CAPTION)
                    }
                    left.filter { it.from == PartElement(PartElement.Kind.EVENT, eventId) }
                        .forEach { link -> UI.Text(PartLinks.text(link, tree::name, s::shared), TextType.WARNING) }
                }
                zoneExtra(zone)
            }

            if (zone.id in opened) {
                Column(modifier = Modifier.padding(start = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    var toolGroup: String? = null
                    zone.elements.forEachIndexed { i, element ->
                        if (element.group != null && (i == 0 || element.group != toolGroup)) UI.Text(element.group, TextType.LABEL)
                        toolGroup = element.group
                        ElementChoice(tree, zone, element, selection, onSelection, left, elementExtra)
                    }
                }
            }
        }
    }

    // The APIs, beside the zones: each goes whatever is ticked, its key never
    if (tree.apis.isNotEmpty()) {
        UI.Card(type = CardType.SECTION_HEADER) {
            Box(modifier = Modifier.fillMaxWidth().padding(UI.Space.M)) { UI.Text(s.shared("share_apis_title"), TextType.HEADING) }
        }
        Column(modifier = Modifier.padding(horizontal = UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(s.shared("share_apis_help"), TextType.CAPTION)
            tree.apis.forEach { api -> ElementChoice(tree, null, api, selection, onSelection, left, elementExtra) }
        }
    }
}

/** An element's box, a tool's switch for its entries, and what its links left say; a Task ticked, how it arrives. */
@Composable
private fun ElementChoice(
    tree: PartTree,
    zone: PartTree.Zone?,
    element: PartTree.Element,
    selection: PartSelection,
    onSelection: (PartSelection) -> Unit,
    left: Set<PartLink>,
    extra: @Composable (PartTree.Element) -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val ticked = PartLinks.kept(element.part(), selection)
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
        UI.Checkbox(
            checked = ticked,
            onCheckedChange = { on -> onSelection(Ticks.element(selection, tree, zone, element, on)) },
            label = when (element.kind) {
                PartElement.Kind.TOOL -> s.shared("share_element_tool").format(element.name,
                    element.tooltype?.let { ToolTypeManager.getToolType(it)?.getDisplayName(context) } ?: element.tooltype.orEmpty())
                PartElement.Kind.VARIABLE -> s.shared("share_element_variable").format(element.name)
                PartElement.Kind.IDENTITY -> s.shared("share_element_identity").format(element.name)
                PartElement.Kind.TASK -> s.shared("share_element_task").format(element.name)
                PartElement.Kind.API -> s.shared("share_element_api").format(element.name)
                else -> s.shared("share_element_routine").format(element.name)
            }
        )
        if (ticked && element.entries > 0) {
            Box(modifier = Modifier.padding(start = UI.Space.L)) {
                UI.Switch(
                    checked = selection.tools[element.id] == true,
                    onCheckedChange = { on -> onSelection(selection.copy(tools = selection.tools + (element.id to on))) },
                    label = if (element.entries == 1) s.shared("share_entries_one") else s.shared("share_entries_other").format(element.entries)
                )
            }
        }
        // What its links to what is not ticked mean
        left.filter { it.from.id == element.id && (it.from.kind == element.kind || (element.kind == PartElement.Kind.TOOL && it.from.kind == PartElement.Kind.ENTRIES)) }
            .map { link -> link.blocking to PartLinks.text(link, tree::name, s::shared) }
            .distinct()
            .forEach { (blocks, text) -> UI.Text(text, if (blocks) TextType.WARNING else TextType.CAPTION) }
        if (ticked && element.kind == PartElement.Kind.TASK) UI.Text(s.shared("share_task_off"), TextType.CAPTION)
        if (ticked) extra(element)
    }
}

/** An element of the tree as the links name it. */
private fun PartTree.Element.part() = PartElement(kind, id)

/**
 * What a tick changes: a zone ticked takes all it holds, its tools without their entries, the
 * identities it and its routines speak as and the APIs its Tasks call; a zone unticked lets all of
 * it go, an identity another zone ticked speaks as and an API another ticked Task calls staying. An element ticked takes its zone, a routine its identity too;
 * unticked, its zone stays. An API, in no zone, goes or not on its own.
 */
internal object Ticks {

    fun zone(selection: PartSelection, tree: PartTree, zone: PartTree.Zone, on: Boolean): PartSelection {
        val ids = zone.elements.groupBy({ it.kind }, { it.id })
        val tools = ids[PartElement.Kind.TOOL].orEmpty()
        val variables = ids[PartElement.Kind.VARIABLE].orEmpty()
        val routines = ids[PartElement.Kind.ROUTINE].orEmpty()
        val identities = ids[PartElement.Kind.IDENTITY].orEmpty()
        val tasks = ids[PartElement.Kind.TASK].orEmpty()
        // The APIs its Tasks call go with it: no API ticked, its Tasks would block the file
        val apis = tree.links.filter { it.from.kind == PartElement.Kind.TASK && it.from.id in tasks && it.to.kind == PartElement.Kind.API }.map { it.to.id }
        return if (on) PartSelection(
            selection.zones + zone.id,
            selection.tools + tools.filter { it !in selection.tools }.associateWith { false },
            selection.variables + variables,
            selection.routines + routines,
            selection.identities + identities,
            selection.tasks + tasks,
            selection.apis + apis
        ) else PartSelection(
            selection.zones - zone.id,
            selection.tools - tools.toSet(),
            selection.variables - variables.toSet(),
            selection.routines - routines.toSet(),
            selection.identities - identities.filter { identity ->
                tree.zones.none { other -> other.id != zone.id && other.id in selection.zones && other.elements.any { it.kind == PartElement.Kind.IDENTITY && it.id == identity } }
            }.toSet(),
            selection.tasks - tasks.toSet(),
            // An API another ticked Task still calls stays
            selection.apis - apis.filter { api ->
                tree.links.none { it.to.kind == PartElement.Kind.API && it.to.id == api && it.from.kind == PartElement.Kind.TASK && it.from.id !in tasks && it.from.id in selection.tasks }
            }.toSet()
        )
    }

    fun element(selection: PartSelection, tree: PartTree, zone: PartTree.Zone?, element: PartTree.Element, on: Boolean): PartSelection {
        val zones = if (on && zone != null) selection.zones + zone.id else selection.zones
        return when (element.kind) {
            PartElement.Kind.TOOL -> selection.copy(zones = zones, tools = if (on) selection.tools + (element.id to false) else selection.tools - element.id)
            PartElement.Kind.VARIABLE -> selection.copy(zones = zones, variables = if (on) selection.variables + element.id else selection.variables - element.id)
            PartElement.Kind.IDENTITY -> selection.copy(zones = zones, identities = if (on) selection.identities + element.id else selection.identities - element.id)
            PartElement.Kind.TASK -> selection.copy(zones = zones, tasks = if (on) selection.tasks + element.id else selection.tasks - element.id)
            PartElement.Kind.API -> selection.copy(apis = if (on) selection.apis + element.id else selection.apis - element.id)
            else -> {
                // A routine ticked takes the identity it speaks as, which can be unticked on its own
                val identity = tree.links.firstOrNull { it.from == PartElement(PartElement.Kind.ROUTINE, element.id) && it.to.kind == PartElement.Kind.IDENTITY }?.to?.id
                selection.copy(zones = zones, routines = if (on) selection.routines + element.id else selection.routines - element.id,
                    identities = if (on && identity != null) selection.identities + identity else selection.identities)
            }
        }
    }
}

/**
 * The links that keep the file from being made, said in full at the bottom of the screen: each
 * is said under its element too, but that one may be folded in its zone, out of sight.
 */
@Composable
internal fun BlockingLinks(tree: PartTree, left: Set<PartLink>, one: String, other: String) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val blocking = left.filter { it.blocking }.map { PartLinks.text(it, tree::name, s::shared) }.distinct()
    if (blocking.isEmpty()) return
    UI.Text(if (blocking.size == 1) s.shared(one) else s.shared(other).format(blocking.size), TextType.WARNING)
    blocking.forEach { UI.Text(it, TextType.CAPTION) }
}
