package app.treelune.tools.sequence.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldInput
import app.treelune.core.fields.FieldType
import app.treelune.core.strings.StringsContext
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.DateTimeFormatter
import app.treelune.core.utils.LogManager
import app.treelune.tools.sequence.SequenceToolType
import app.treelune.tools.sequence.SequenceVariants
import org.json.JSONObject

/**
 * What a session's variant asks before it starts (SequenceVariants), as the params of the
 * operation it opens for: a timer's `duration`, a medium's `entry`; nothing for steps, which
 * starts at once. [onAnswer] gets those params, [onCancel] closes without starting.
 *
 * @param history The tool's sessions, which tell the entry played last
 */
@Composable
internal fun LaunchDialog(config: JSONObject, history: List<SequenceEntry>, s: StringsContext, onAnswer: (Map<String, Any?>) -> Unit, onCancel: () -> Unit) {
    when (SequenceVariants.of(config)) {
        SequenceVariants.TIMER -> TimerDialog(config, s, onAnswer, onCancel)
        SequenceVariants.MEDIA -> MediaDialog(config, history, s, onAnswer, onCancel)
        else -> LaunchedEffect(Unit) { onAnswer(emptyMap()) }
    }
}

/** How long: the config's length proposed. */
@Composable
private fun TimerDialog(config: JSONObject, s: StringsContext, onAnswer: (Map<String, Any?>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var length by rememberSaveable { mutableStateOf<Any?>(config.optLong(SequenceVariants.TIMER_DURATION, SequenceVariants.DEFAULT_TIMER)) }
    val field = FieldDefinition(SequenceToolType.DURATION, s.tool("launch_timer"), null, FieldType.DURATION, false, SequenceToolType.SESSION_UNITS)
    UI.Dialog(type = DialogType.CONFIRM, onConfirm = { onAnswer(mapOf(SequenceToolType.DURATION to length)) }, onCancel = onCancel,
        confirmEnabled = ((length as? Number)?.toLong() ?: 0L) > 0L) {
        FieldInput(field, length, { length = it }, context, required = true)
    }
}

/**
 * Which entry: those of the selection that hold a file in the field, in the sort's order, each
 * with when this tool last played it; the one it played last marked so, and the list opened at it.
 * A touch answers.
 */
@Composable
private fun MediaDialog(config: JSONObject, history: List<SequenceEntry>, s: StringsContext, onAnswer: (Map<String, Any?>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var offered by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(config.toString()) {
        offered = try {
            SequenceVariants.offered(context, config)
        } catch (e: IllegalStateException) {
            LogManager.ui("Sequence: the entries to play not read: ${e.message}", "ERROR")
            error = e.message
            emptyList()
        }
    }
    // When each entry was played last, by its id: the latest session that read its file
    val playedAt = remember(history) {
        history.sortedByDescending { it.timestamp }.flatMap { session ->
            session.steps.orEmpty().mapNotNull { it.sourceEntry?.id }.distinct().map { it to session.timestamp }
        }.distinctBy { it.first }.toMap()
    }
    // The entry played last of all
    val lastPlayed = remember(playedAt) { playedAt.maxByOrNull { it.value }?.key }

    UI.Dialog(type = DialogType.SELECTION, onConfirm = {}, onCancel = onCancel) {
        Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text(s.tool("launch_media"), TextType.SUBTITLE)
            val entries = offered ?: return@Column UI.LoadingIndicator()
            error?.let { UI.Text(it, TextType.ERROR) }
            if (entries.isEmpty() && error == null) UI.Text(s.tool("launch_media_none"), TextType.CAPTION)
            val start = entries.indexOfFirst { it["id"] == lastPlayed }.coerceAtLeast(0)
            val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
            LazyColumn(state = list) {
                itemsIndexed(entries, key = { _, e -> e["id"] as String }) { _, entry ->
                    val id = entry["id"] as String
                    Column(modifier = Modifier.fillMaxWidth().clickable { onAnswer(mapOf("entry" to id)) }.padding(vertical = UI.Space.S)) {
                        // An entry without a name says its date
                        UI.Text((entry["name"] as? String)?.takeIf { it.isNotBlank() }
                            ?: DateTimeFormatter.formatForDisplay((entry["timestamp"] as Number).toLong(), context), TextType.BODY)
                        playedAt[id]?.let { at ->
                            val played = DateTimeFormatter.formatForDisplay(at, context)
                            UI.Text(s.tool(if (id == lastPlayed) "launch_media_last" else "launch_media_played").format(played), TextType.CAPTION)
                        }
                    }
                }
            }
        }
    }
}
