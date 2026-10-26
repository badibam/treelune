package app.treelune.core.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.commands.CommandStatus
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.navigation.Navigator
import app.treelune.core.navigation.Place
import app.treelune.core.strings.Strings
import app.treelune.core.tasks.Task
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.chatButtonSpace
import app.treelune.core.ui.Duration
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * A Task's page (Place.Task), what one looks at: its state, its switch and its button to run it,
 * then its last runs, each opening. Its steps and its settings are its config (TaskConfigScreen),
 * opened by the header's button.
 */
@Composable
fun TaskScreen(place: Place.Task, onName: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val coordinator = remember { Coordinator(context) }
    val actions = rememberTaskActions()

    var task by remember { mutableStateOf<Task?>(null) }

    suspend fun load() {
        val result = coordinator.processUserAction("tasks.get", mapOf("task_id" to place.id))
        if (result.status != CommandStatus.SUCCESS) {
            UI.Toast(result.error ?: s.shared("error_unknown"), Duration.LONG)
            Navigator.drop(place)
            return
        }
        task = Task.fromResult(result.data?.get("task") as Map<*, *>)
    }
    LaunchedEffect(place.id) {
        load()
        app.treelune.core.utils.DataChangeNotifier.changes.collect { if (it is app.treelune.core.utils.DataChangeEvent.ZonesChanged) load() }
    }

    val shown = task ?: run {
        UI.Text(s.shared("message_loading"), TextType.BODY, fillMaxWidth = true, textAlign = TextAlign.Center)
        return
    }
    onName(shown.name)

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).chatButtonSpace().padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = shown.name,
            subtitle = s.shared("task_display_name"),
            icon = shown.look.iconName,
            iconColor = app.treelune.core.themes.IconColor.of(shown.look.iconColor),
            leftButton = ButtonAction.BACK,
            rightButton = ButtonAction.CONFIGURE,
            onLeftClick = onBack,
            onRightClick = { Navigator.push(Place.TaskConfig(shown.zoneId, shown.id, null)) }
        )

        val runs = rememberRuns(shown.id, 20)
        UI.Card(type = CardType.DEFAULT, size = Size.M) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                TaskStateLine(shown, runs.firstOrNull()) { actions.toggle(shown, it) }
                app.treelune.core.triggers.ui.ConditionJudgementLines(shown.conditionJudgements)
                UI.Button(type = ButtonType.PRIMARY, onClick = { actions.run(shown) }) {
                    UI.Text(s.shared("task_run_now"), TextType.BODY)
                }
            }
        }

        UI.Text(s.shared("task_runs"), TextType.HEADING)
        if (runs.isEmpty()) UI.Text(s.shared("task_runs_empty"), TextType.CAPTION)
        runs.forEach { RunRow(it, shown) }
    }
}
