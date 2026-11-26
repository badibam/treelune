package app.treelune.tools.path.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.tools.path.PathGrammar
import app.treelune.tools.path.PathOrder
import app.treelune.tools.path.Stage

/**
 * The plan of a run in a window, indented as the plan editor shows it, to choose one stage
 * (« Déplacer vers… », a link's source): each line the stage's name, greyed with the reason when
 * [refusal] gives one, a touch on it saying that reason.
 *
 * @param stages The run's stages, root included
 */
@Composable
fun StagePickerDialog(title: String, stages: List<Stage>, refusal: (Stage) -> String?, onPick: (Stage) -> Unit, onCancel: () -> Unit) {
    val ordered = PathOrder.planOrder(stages)
    val byId = stages.associateBy { it.id }
    fun depth(stage: Stage): Int = generateSequence(stage.parent?.let { byId[it] }) { it.parent?.let { p -> byId[p] } }.count()
    UI.Dialog(type = DialogType.SELECTION, onConfirm = {}, onCancel = onCancel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(title, TextType.SUBTITLE)
            ordered.forEach { stage ->
                val refused = refusal(stage)
                // Indented by its depth, as in the plan
                Box(Modifier.fillMaxWidth().padding(start = UI.Space.L * depth(stage)).clickable {
                    if (refused != null) UI.Toast(refused) else onPick(stage)
                }) {
                    UI.Text(stage.name, if (refused != null) TextType.CAPTION else TextType.BODY)
                }
            }
        }
    }
}

/** Why the link of type [type] from [from] to [stage] may not be written: it would make the run's order loop. */
fun linkRefusal(grammar: PathGrammar, run: List<Stage>, stage: Stage, from: Stage, type: String, text: (String) -> String): String? {
    if (from.id == stage.id) return text("pick_self")
    val read = PathOrder.linkTypesRead(grammar)
    if (type !in read) return null
    val byId = run.associateBy { it.id }
    if (generateSequence(stage.parent?.let { byId[it] }) { it.parent?.let { p -> byId[p] } }.any { it.id == from.id }) return text("pick_ancestor")
    val linked = run.map { if (it.id == stage.id) it.copy(links = it.links + app.treelune.tools.path.Link(from.id, type)) else it }
    return if (PathOrder.of(linked, read) is PathOrder.Result.Loop) text("pick_loop") else null
}
