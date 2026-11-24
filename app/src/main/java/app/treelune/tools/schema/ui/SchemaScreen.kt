package app.treelune.tools.schema.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolConfigSettings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.pageScroll
import app.treelune.core.utils.DataChangeNotifier
import app.treelune.core.utils.JsonUtils
import app.treelune.tools.schema.SchemaToolType
import app.treelune.tools.schema.SchemaTouch
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A schema's page, for reading (docs/design/schema.md, « L'écran »): a linked box opens its thing,
 * a box without a link shows its whole text if it was cut, a group folds and unfolds for the visit
 * without writing anything. What changes the schema goes through its config.
 *
 * Read again when its config changes and when any data does: a box shows the current name of its thing.
 */
@Composable
fun SchemaScreen(toolInstanceId: String, onNavigateBack: () -> Unit, onConfigureClick: () -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val s = remember { Strings.`for`(tool = "schema", context = context) }

    var config by app.treelune.core.ui.rememberShown<JSONObject?>("page:$toolInstanceId:config") { null }
    var reading by app.treelune.core.ui.rememberShown<SchemaReading?>("page:$toolInstanceId:reading") { null }
    var version by remember { mutableIntStateOf(0) }
    // Groups folded for this visit, starting from what the config says; a config changed starts over
    var folded by remember { mutableStateOf<Set<String>?>(null) }
    // The groups the person opened, which the app never folds to make room
    var keptOpen by remember { mutableStateOf<Set<String>>(emptySet()) }
    // The box whose whole text is open in a window
    var opened by remember { mutableStateOf<SchemaTouch.Box?>(null) }
    val pageScrollState = rememberScrollState()

    LaunchedEffect(toolInstanceId, version) {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        val loaded = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (loaded == null) {
            reading = SchemaReading.Problem(tool.error ?: s.shared("tools_loading_config"))
            return@LaunchedEffect
        }
        val changed = loaded.toString() != config?.toString()
        val read = SchemaReading.of(loaded, context)
        config = loaded
        reading = read
        if (changed || folded == null) {
            folded = (read as? SchemaReading.Drawn)?.spec?.let { spec -> spec.nodes.filter { it.folded && it.id in spec.holders }.map { it.id }.toSet() }.orEmpty()
            keptOpen = emptySet()
            opened = null
        }
    }
    LaunchedEffect(toolInstanceId) {
        DataChangeNotifier.changes.collect { event -> if (event.mayChangeReadings()) version++ }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.L).pageScroll(pageScrollState),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        val settings = config?.let { ToolConfigSettings.read(SchemaToolType, it, context) }
        UI.PageHeader(
            title = settings?.string("name") ?: SchemaToolType.getDisplayName(context),
            subtitle = SchemaToolType.pageSubtitle(context, settings?.string("description")),
            icon = settings?.string("icon_name"),
            iconColor = app.treelune.core.themes.IconColor.of(settings?.string(app.treelune.core.themes.IconColor.KEY)),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onNavigateBack,
            onRightClick = onConfigureClick
        )
        when (val read = reading) {
            null -> UI.LoadingIndicator()
            is SchemaReading.Problem -> UI.Text(s.tool("screen_problem").format(read.message), TextType.ERROR)
            is SchemaReading.Drawn -> if (read.spec.nodes.isEmpty()) UI.Text(s.tool("screen_empty"), TextType.CAPTION) else {
                val shownFolded = folded.orEmpty()
                SchemaView(read, shownFolded, onTouch = { touch ->
                    when (touch) {
                        is SchemaTouch.Group -> if (touch.folded) {
                            folded = shownFolded - touch.id
                            keptOpen = keptOpen + touch.id
                        } else {
                            folded = shownFolded + touch.id
                            keptOpen = keptOpen - touch.id
                        }
                        is SchemaTouch.Box -> {
                            val thing = touch.open
                            when {
                                touch.cut -> opened = touch
                                thing != null -> scope.launch { openThing(thing, context) }
                            }
                        }
                    }
                }, pageScroll = pageScrollState, keptOpen = keptOpen)
            }
        }
    }

    opened?.let { box ->
        UI.Dialog(type = DialogType.INFO, onConfirm = { opened = null }, onCancel = { opened = null }) {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                UI.Text(box.text, TextType.BODY)
                box.open?.let { thing ->
                    UI.Button(type = ButtonType.DEFAULT, onClick = {
                        opened = null
                        scope.launch { openThing(thing, context) }
                    }) { UI.Text(s.tool("open_thing"), TextType.LABEL) }
                }
            }
        }
    }
}
