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
 * tool group; [left] the links from what is ticked to what is not, each said under its element.
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
                        onCheckedChange = { on -> onSelection(Ticks.zone(selection, zone, on)) },
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

            if (zone.id in selection.zones) zoneExtra(zone)

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
}

/** An element's box, a tool's switch for its entries, and what its links left say. */
@Composable
private fun ElementChoice(
    tree: PartTree,
    zone: PartTree.Zone,
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
            onCheckedChange = { on -> onSelection(Ticks.element(selection, zone, element, on)) },
            label = when (element.kind) {
                PartElement.Kind.TOOL -> s.shared("share_element_tool").format(element.name,
                    element.tooltype?.let { ToolTypeManager.getToolType(it)?.getDisplayName(context) } ?: element.tooltype.orEmpty())
                PartElement.Kind.VARIABLE -> s.shared("share_element_variable").format(element.name)
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
        // What its links to what is not ticked mean, a routine's provider and identity said once
        left.filter { it.from.id == element.id && (it.from.kind == element.kind || (element.kind == PartElement.Kind.TOOL && it.from.kind == PartElement.Kind.ENTRIES)) }
            .map { link -> link.blocking to PartLinks.text(link, tree::name, s::shared) }
            .distinct()
            .forEach { (blocks, text) -> UI.Text(text, if (blocks) TextType.WARNING else TextType.CAPTION) }
        if (ticked) extra(element)
    }
}

/** An element of the tree as the links name it. */
private fun PartTree.Element.part() = PartElement(kind, id)

/**
 * What a tick changes: a zone ticked takes all it holds, its tools without their entries; a zone
 * unticked lets all of it go. An element ticked takes its zone; unticked, its zone stays.
 */
internal object Ticks {

    fun zone(selection: PartSelection, zone: PartTree.Zone, on: Boolean): PartSelection {
        val ids = zone.elements.groupBy({ it.kind }, { it.id })
        val tools = ids[PartElement.Kind.TOOL].orEmpty()
        val variables = ids[PartElement.Kind.VARIABLE].orEmpty()
        val routines = ids[PartElement.Kind.ROUTINE].orEmpty()
        return if (on) PartSelection(
            selection.zones + zone.id,
            selection.tools + tools.filter { it !in selection.tools }.associateWith { false },
            selection.variables + variables,
            selection.automations + routines
        ) else PartSelection(
            selection.zones - zone.id,
            selection.tools - tools.toSet(),
            selection.variables - variables.toSet(),
            selection.automations - routines.toSet()
        )
    }

    fun element(selection: PartSelection, zone: PartTree.Zone, element: PartTree.Element, on: Boolean): PartSelection {
        val zones = if (on) selection.zones + zone.id else selection.zones
        return when (element.kind) {
            PartElement.Kind.TOOL -> selection.copy(zones = zones, tools = if (on) selection.tools + (element.id to false) else selection.tools - element.id)
            PartElement.Kind.VARIABLE -> selection.copy(zones = zones, variables = if (on) selection.variables + element.id else selection.variables - element.id)
            else -> selection.copy(zones = zones, automations = if (on) selection.automations + element.id else selection.automations - element.id)
        }
    }
}
