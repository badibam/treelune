package app.treelune.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.grid.Grid
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Size
import app.treelune.core.ui.UI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The edit mode of a screen's grids: the tools of a zone, the zones of the home screen. On for the
 * whole page, every group section at once. The tile being moved goes about its section by the
 * arrows; past its section's top or bottom it crosses into the section above or below, in the
 * page's order ([sectionOrder]), arriving by the side it crossed (Grid.enter), its group to change
 * with it. A section is named by its key (its group, "" for the ungrouped one).
 *
 * A move is written when it is validated, when another tile is touched or when the edit mode is
 * closed, in one write ([placeOperation], given [placeParams] and `sections`, each touched
 * section's group and places); "Cancel" puts every section it touched back as it was when the
 * tile was touched. What is being edited survives recreation.
 */
class GridEditor internal constructor(
    private val placeOperation: String,
    private val placeParams: Map<String, Any>,
    private val coordinator: Coordinator,
    private val scope: CoroutineScope,
    private val sectionTiles: (String) -> List<Grid.Tile>,
    private val sectionOrder: () -> List<String>,
    private val onError: (String) -> Unit,
    private val on: MutableState<Boolean>,
    private val section: MutableState<String?>,
    private val selected: MutableState<String?>,
    private val draft: MutableState<String?>,
    private val original: MutableState<String?>
) {
    val selectedId: String? get() = selected.value
    /** Whether the page is in edit mode. */
    val anyEditing: Boolean get() = on.value

    /** Whether the tile touched has moved since. */
    val moving: Boolean get() = draft.value != null && draft.value != original.value

    /** The places of the section [key] while the move in progress touches it, else null (the stored ones). */
    fun places(key: String): List<Grid.Tile>? = if (on.value) sections(draft.value)[key] else null

    /**
     * The section a tile stands in while the move in progress places it, or null when the move
     * does not touch it: what the page shows in each section follows it, the moved tile included.
     */
    fun sectionOf(id: String): String? =
        if (!on.value) null else sections(draft.value).entries.firstOrNull { (_, tiles) -> tiles.any { it.id == id } }?.key

    /** What the grid of section [key] shows in edit mode, or null outside it. */
    fun gridEdit(key: String): GridEdit? =
        if (!on.value) null
        else GridEdit(places(key) ?: sectionTiles(key), selected.value.takeIf { section.value == key }) { id -> select(key, id) }

    /** The page's edit button: opens its edit mode, or closes it, the move in progress written. */
    fun toggle() = afterCommit { on.value = !on.value }

    private fun select(key: String, id: String) {
        if (selected.value == id) return
        afterCommit {
            val tiles = encode(mapOf(key to sectionTiles(key)))
            section.value = key
            selected.value = id
            original.value = tiles
            draft.value = tiles
        }
    }

    /** The tile moved by an arrow, or nothing when it has nowhere to go that way. */
    fun move(direction: Grid.Direction) {
        val next = next(direction) ?: return
        section.value = next.first
        draft.value = encode(next.second)
    }

    fun canMove(direction: Grid.Direction): Boolean = next(direction) != null

    /** The section the tile ends in and every touched section's places, after [direction]; null when it cannot go. */
    private fun next(direction: Grid.Direction): Pair<String, Map<String, List<Grid.Tile>>>? {
        val key = section.value ?: return null
        val id = selected.value ?: return null
        val touched = sections(draft.value)
        val places = touched[key] ?: return null
        val oneColumn = app.treelune.core.themes.CurrentTheme.oneColumn
        // In one column the arrows move along the column (Grid.moveInOneColumn)
        val within = if (oneColumn) Grid.moveInOneColumn(places, id, direction) else Grid.move(places, id, direction)
        if (within != null) return key to touched + (key to within)
        // Past the section's top or bottom: into the section above or below, entering by the side it crossed
        if (direction != Grid.Direction.UP && direction != Grid.Direction.DOWN) return null
        val order = sectionOrder()
        val target = order.getOrNull(order.indexOf(key) + if (direction == Grid.Direction.UP) -1 else 1) ?: return null
        val tile = places.single { it.id == id }
        val entered = Grid.enter(touched[target] ?: sectionTiles(target), tile, fromBelow = direction == Grid.Direction.UP, oneColumn = oneColumn)
        return target to touched + mapOf(key to Grid.exit(places, id, oneColumn), target to entered)
    }

    /** The edit mode closed, the move in progress written. */
    fun close() = afterCommit { on.value = false }

    /** The move written, the tile unselected; the edit mode stays. */
    fun validate() = afterCommit {}

    /** The sections as they were when the tile was touched, the tile unselected. */
    fun cancel() {
        selected.value = null
        section.value = null
        draft.value = null
        original.value = null
    }

    /** Runs [then] once the move in progress is written; a write refused keeps it, its error said. */
    private fun afterCommit(then: () -> Unit) {
        val touched = sections(draft.value)
        if (touched.isEmpty() || !moving) {
            cancel()
            then()
            return
        }
        scope.launch {
            val result = coordinator.processUserAction(placeOperation, placeParams + mapOf(
                "sections" to touched.map { (key, places) ->
                    mapOf("group" to key, "places" to places.associate { it.id to mapOf("grid_x" to it.column, "grid_y" to it.row) })
                }
            ))
            if (!result.isSuccess) {
                onError(result.error ?: "")
                return@launch
            }
            cancel()
            then()
        }
    }

    /** Places by section kept as text, each tile with its size: "key|id,column,row,width,height;…" a section a line. */
    private fun encode(sections: Map<String, List<Grid.Tile>>): String = sections.entries.sortedBy { it.key }.joinToString("\n") { (key, tiles) ->
        key + "|" + tiles.sortedBy { it.id }.joinToString(";") { "${it.id},${it.column},${it.row},${it.width},${it.height}" }
    }

    private fun sections(text: String?): Map<String, List<Grid.Tile>> = text?.split("\n")?.filter { it.isNotEmpty() }?.associate { line ->
        val key = line.substringBefore("|")
        key to line.substringAfter("|").split(";").filter { it.isNotEmpty() }.map { part ->
            val (id, column, row, width, height) = part.split(",")
            Grid.Tile(id, column.toInt(), row.toInt(), width.toInt(), height.toInt())
        }
    } ?: emptyMap()
}

/**
 * The edit mode of a screen's grids, [sectionTiles] giving the stored tiles of a section by its
 * key and [sectionOrder] the page's sections in order, a move written by [placeOperation] with
 * [placeParams].
 */
@Composable
fun rememberGridEditor(placeOperation: String, placeParams: Map<String, Any>, sectionTiles: (String) -> List<Grid.Tile>, sectionOrder: () -> List<String>, onError: (String) -> Unit): GridEditor {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val on = rememberSaveable { mutableStateOf(false) }
    val section = rememberSaveable { mutableStateOf<String?>(null) }
    val selected = rememberSaveable { mutableStateOf<String?>(null) }
    val draft = rememberSaveable { mutableStateOf<String?>(null) }
    val original = rememberSaveable { mutableStateOf<String?>(null) }
    return GridEditor(placeOperation, placeParams, coordinator, scope, sectionTiles, sectionOrder, onError, on, section, selected, draft, original)
}

/**
 * Closes [editor]'s edit mode, its move written, when the screen that calls this leaves: another
 * screen opened over it, a zone over the home screen, a tool over its zone. A recreation of the
 * activity keeps it, as it keeps what is being edited.
 */
@Composable
fun CloseEditOnLeave(editor: GridEditor) {
    val current = androidx.compose.runtime.rememberUpdatedState(editor)
    val activity = LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (activity?.isChangingConfigurations != true) current.value.close() }
    }
}

/**
 * The bar of the edit mode, the full width under the screen for as long as the mode lasts: what
 * the mode is and how to move a tile, and « Done », which closes it, the move in progress written.
 * A tile touched, a cross of four arrows, each greyed where the tile has nowhere to go, the
 * validation in its middle, and "Cancel" beside. The phone's back key closes the mode as « Done ».
 */
@Composable
fun GridEditBar(editor: GridEditor) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    // Its arrows at the bottom of the screen, where the chat's button would float
    app.treelune.core.ui.HideChatButton()
    // Composed after the page's header, it answers the back key before it
    androidx.activity.compose.BackHandler { editor.close() }
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UI.Space.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    UI.Text(s.shared("grid_mode_title"), app.treelune.core.ui.TextType.STRONG)
                    if (editor.selectedId == null) UI.Text(s.shared("grid_mode_hint"), app.treelune.core.ui.TextType.CAPTION)
                }
                UI.Button(type = app.treelune.core.ui.ButtonType.PRIMARY, onClick = { editor.close() }) {
                    UI.Text(s.shared("grid_mode_done"), app.treelune.core.ui.TextType.BODY)
                }
            }
            if (editor.selectedId != null) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Composable
                fun Arrow(action: ButtonAction, direction: Grid.Direction) = UI.ActionButton(
                    action = action, display = ButtonDisplay.ICON, size = Size.M,
                    enabled = editor.canMove(direction), onClick = { editor.move(direction) }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) {
                    Arrow(ButtonAction.UP, Grid.Direction.UP)
                    Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.XS), verticalAlignment = Alignment.CenterVertically) {
                        Arrow(ButtonAction.LEFT, Grid.Direction.LEFT)
                        UI.ActionButton(action = ButtonAction.CONFIRM, display = ButtonDisplay.ICON, size = Size.M, onClick = { editor.validate() })
                        Arrow(ButtonAction.RIGHT, Grid.Direction.RIGHT)
                    }
                    Arrow(ButtonAction.DOWN, Grid.Direction.DOWN)
                }
                UI.ActionButton(action = ButtonAction.CANCEL, display = ButtonDisplay.LABEL, onClick = { editor.cancel() })
            }
        }
    }
}

/**
 * The buttons of a page of grids (a zone, the home screen), once for the whole page, in its header
 * before its gear (PageHeader's beforeRight): its edit mode, on while it lasts (shown when it has
 * tiles), and adding, off while it is in edit mode.
 */
@Composable
fun GridHeaderButtons(hasTiles: Boolean, editor: GridEditor, onAdd: () -> Unit) {
    if (hasTiles) UI.ActionButton(
        action = ButtonAction.ARRANGE,
        display = ButtonDisplay.ICON,
        active = editor.anyEditing,
        onClick = { editor.toggle() }
    )
    UI.ActionButton(
        action = ButtonAction.ADD,
        display = ButtonDisplay.ICON,
        enabled = !editor.anyEditing,
        onClick = onAdd
    )
}
