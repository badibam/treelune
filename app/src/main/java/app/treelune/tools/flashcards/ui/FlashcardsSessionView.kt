package app.treelune.tools.flashcards.ui

import app.treelune.core.ui.chatButtonSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldValue
import app.treelune.core.fields.ToolFields
import app.treelune.core.fields.formatValue
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Duration
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.ui.components.ButtonRow
import app.treelune.tools.flashcards.FlashcardsDeck
import app.treelune.tools.flashcards.Fsrs
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * A session under way (docs/TOOLS.md, « Flashcards », « La séance », « La face d'une carte »): the card's
 * front, marked « New » for a card never reviewed before; touched, its back under a line, the front kept, and the four ratings under it. Each rating
 * is written at once (flashcards.review); undoing goes back to the card before, its back hidden, its
 * rating deleted. Leaving stops the session where it stands: a failed card not passed again stays due.
 *
 * @param onChange The session's next state, which the page keeps across a rotation
 */
@Composable
fun FlashcardsSessionView(
    toolInstanceId: String,
    deckName: String,
    config: JSONObject,
    state: SessionState,
    onChange: (SessionState) -> Unit,
    onLeave: () -> Unit
) {
    val context = LocalContext.current
    val coordinator = remember { Coordinator(context) }
    val scope = rememberCoroutineScope()
    val s = remember { Strings.`for`(tool = "flashcards", context = context) }
    // A rating or an undoing on its way: the buttons wait for it
    var busy by remember { mutableStateOf(false) }

    // The source's fields, which each value is shown by
    val sourceId = config.optJSONObject(FlashcardsDeck.SOURCE)?.optJSONObject("target")?.optString("id")
    var fields by remember { mutableStateOf<Map<String, FieldDefinition>?>(null) }
    LaunchedEffect(sourceId) {
        fields = try { ToolFields.filterable(sourceId ?: return@LaunchedEffect, context, s) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: IllegalStateException) {
            UI.Toast(e.message ?: s.shared("message_error_simple"), Duration.LONG)
            null
        }
    }

    fun rate(rating: Fsrs.Rating) {
        val card = state.shown ?: return
        busy = true
        scope.launch {
            val result = coordinator.processUserAction("flashcards.review", mapOf(
                "tool_instance_id" to toolInstanceId, "source_entry" to card.line, "direction" to card.direction, "rating" to rating.name
            ))
            busy = false
            if (!result.isSuccess) { UI.Toast(result.error ?: s.shared("message_error_simple"), Duration.LONG); return@launch }
            val now = System.currentTimeMillis()
            val next = state.session.rated(card, rating, now, result.data?.get("id") as String)
            onChange(state.copy(session = next, shown = next.next(now), turned = false))
        }
    }

    fun undo() {
        val undone = state.session.undone() ?: return
        busy = true
        scope.launch {
            val result = coordinator.processUserAction("tool_data.delete", mapOf("id" to undone.reviewId))
            busy = false
            if (!result.isSuccess) { UI.Toast(result.error ?: s.shared("message_error_simple"), Duration.LONG); return@launch }
            onChange(state.copy(session = undone.session, shown = undone.card, turned = false))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = UI.Space.L).verticalScroll(rememberScrollState()).chatButtonSpace(),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(
            title = deckName,
            subtitle = s.tool("session_progress").format(state.session.done, state.total),
            leftButton = ButtonAction.BACK,
            onLeftClick = onLeave
        )
        val shown = state.shown
        val faces = shown?.let { state.faces[it] }
        val known = fields
        when {
            state.session.over || shown == null -> {
                UI.Text(overText(state, s), TextType.BODY)
                ButtonRow {
                    UI.Button(type = ButtonType.PRIMARY, onClick = onLeave) { UI.Text(s.tool("session_back"), TextType.LABEL) }
                    if (state.session.history.isNotEmpty()) UI.Button(type = ButtonType.DEFAULT, onClick = { if (!busy) undo() }) { UI.Text(s.tool("action_undo"), TextType.LABEL) }
                }
            }
            faces == null || known == null -> UI.LoadingIndicator()
            else -> {
                UI.Card(type = CardType.DEFAULT) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.turned) { onChange(state.copy(turned = true)) }.padding(UI.Space.L),
                        verticalArrangement = Arrangement.spacedBy(UI.Space.M),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // A card never reviewed before this session says so, failed and back again included
                        if (shown in state.fresh) UI.Tag(s.tool("session_new"), app.treelune.core.themes.TagColor.BLUE)
                        Face(faces.front, known, s)
                        if (state.turned) {
                            UI.Divider()
                            Face(faces.back, known, s)
                        } else UI.Text(s.tool("session_turn"), TextType.CAPTION)
                    }
                }
                if (state.turned) ButtonRow {
                    UI.Button(type = ButtonType.DANGER, onClick = { if (!busy) rate(Fsrs.Rating.AGAIN) }) { UI.Text(s.tool("rating_again"), TextType.LABEL) }
                    UI.Button(type = ButtonType.SECONDARY, onClick = { if (!busy) rate(Fsrs.Rating.HARD) }) { UI.Text(s.tool("rating_hard"), TextType.LABEL) }
                    UI.Button(type = ButtonType.PRIMARY, onClick = { if (!busy) rate(Fsrs.Rating.GOOD) }) { UI.Text(s.tool("rating_good"), TextType.LABEL) }
                    UI.Button(type = ButtonType.TERTIARY, onClick = { if (!busy) rate(Fsrs.Rating.EASY) }) { UI.Text(s.tool("rating_easy"), TextType.LABEL) }
                }
                if (state.session.history.isNotEmpty()) ButtonRow {
                    UI.Button(type = ButtonType.DEFAULT, onClick = { if (!busy) undo() }) { UI.Text(s.tool("action_undo"), TextType.LABEL) }
                }
            }
        }
    }
}

/**
 * What the session's end says: the cards passed that had been seen before, then the new ones, each
 * count left out when it is zero.
 */
private fun overText(state: SessionState, s: app.treelune.core.strings.StringsContext): String {
    val passed = state.passed
    val new = passed.count { it in state.fresh }
    val reviewed = passed.size - new
    val reviewedText = s.tool(if (reviewed == 1) "session_reviewed_singular" else "session_reviewed_plural").format(reviewed)
    val counts = when {
        new == 0 -> reviewedText
        reviewed == 0 -> s.tool(if (new == 1) "session_new_only_singular" else "session_new_only_plural").format(new)
        else -> s.tool(if (new == 1) "session_and_new_singular" else "session_and_new_plural").format(reviewedText, new)
    }
    return s.tool("session_over").format(counts)
}

/**
 * One face of a card: its first field large, the question or the answer; the others smaller, as
 * details, without their labels. Each value shows as its field type shows it; an empty one does not
 * show — the first field of a front is never empty, a line without it making no card. A field gone
 * from the source since the session started is an error, never a value shown blind.
 */
@Composable
private fun Face(values: List<Pair<String, Any?>>, fields: Map<String, FieldDefinition>, s: app.treelune.core.strings.StringsContext) {
    val context = LocalContext.current
    values.forEachIndexed { index, (path, value) ->
        val field = fields[path] ?: return@forEachIndexed UI.Text(s.tool("error_field_gone_session"), TextType.ERROR)
        if (value == null) return@forEachIndexed
        if (index == 0) UI.Text(field.formatValue(value, context), TextType.TITLE, textAlign = TextAlign.Center)
        else FieldValue(field, value, context)
    }
}
