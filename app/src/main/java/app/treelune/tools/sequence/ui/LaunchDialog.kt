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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import app.treelune.core.fields.FieldValue
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * A touch never starts: it opens the entry's window ([MediaPreview]), whose « Start » answers.
 */
@Composable
private fun MediaDialog(config: JSONObject, history: List<SequenceEntry>, s: StringsContext, onAnswer: (Map<String, Any?>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var offered by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(config.toString()) {
        offered = try {
            SequenceVariants.offered(context, config)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
    // How many sessions played each entry
    val playCounts = remember(history) {
        history.flatMap { session -> session.steps.orEmpty().mapNotNull { it.sourceEntry?.id }.distinct() }.groupingBy { it }.eachCount()
    }
    // The entry touched, shown before it starts
    var previewed by remember { mutableStateOf<Map<String, Any?>?>(null) }

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
                    Column(modifier = Modifier.fillMaxWidth().clickable { previewed = entry }.padding(vertical = UI.Space.S)) {
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

    previewed?.let { entry ->
        MediaPreview(config, entry, playedAt[entry["id"]], playCounts[entry["id"]] ?: 0, s,
            onStart = { onAnswer(mapOf("entry" to entry["id"] as String)) }, onBack = { previewed = null })
    }
}

/**
 * The entry [entry] touched in « What to play? », before it starts: its name, its medium's
 * length, when it was played last and how many times, then the fields the config shows
 * (SequenceVariants.MEDIA_SHOWN), each with its label. « Start » answers; back returns to the list.
 * A linked medium the phone no longer has is said, and nothing starts.
 */
@Composable
private fun MediaPreview(config: JSONObject, entry: Map<String, Any?>, lastAt: Long?, count: Int, s: StringsContext, onStart: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val toolId = config.optJSONObject(SequenceVariants.MEDIA_SOURCE)?.optJSONObject("target")?.optString("id").orEmpty()
    val shown = config.optJSONArray(SequenceVariants.MEDIA_SHOWN)?.let { list -> (0 until list.length()).mapNotNull { list.optString(it).takeIf { p -> p.isNotEmpty() } } }.orEmpty()
    // The fields of the source, to show each value as its field says
    val fields by produceState<Map<String, FieldDefinition>?>(initialValue = null, toolId) {
        value = try {
            app.treelune.core.fields.ToolFields.filterable(toolId, context, s)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            LogManager.ui("Sequence: the fields of $toolId not read: ${e.message}", "WARN")
            emptyMap()
        }
    }
    // Its medium: its length, or that the phone no longer has it
    val mediumId = app.treelune.core.fields.FileField.ids(app.treelune.core.reading.FieldReading.valueAt(entry, config.optString(SequenceVariants.MEDIA_FIELD))).firstOrNull()
    val medium by produceState<Pair<Long?, Boolean>?>(initialValue = null, mediumId) {
        value = withContext(Dispatchers.IO) {
            val found = mediumId?.let { app.treelune.core.files.Files.inspect(context, it) }
            found?.description?.durationMs to (found == null && mediumId != null && app.treelune.core.files.Files.isLink(context, mediumId))
        }
    }
    val unreachable = medium?.second == true

    UI.Dialog(type = DialogType.SELECTION, onConfirm = onBack, onCancel = onBack) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Text((entry["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: DateTimeFormatter.formatForDisplay((entry["timestamp"] as Number).toLong(), context), TextType.TITLE)
            medium?.first?.let { UI.Text(app.treelune.core.files.clock(it), TextType.CAPTION) }
            UI.Text(if (lastAt == null) s.tool("launch_media_never")
                else s.tool("launch_media_last").format(DateTimeFormatter.formatForDisplay(lastAt, context)) + " · " + s.tool("launch_media_count").format(count), TextType.CAPTION)
            if (unreachable) UI.Text(s.shared("file_link_unreachable"), TextType.ERROR)
            val known = fields
            if (known == null) UI.LoadingIndicator()
            else shown.forEach { path ->
                val definition = known[path] ?: return@forEach
                val value = app.treelune.core.reading.FieldReading.valueAt(entry, path)
                if (value != null) {
                    UI.Text(definition.displayName, TextType.LABEL)
                    FieldValue(definition, value, context)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.CenterVertically) {
                UI.ActionButton(action = ButtonAction.BACK, onClick = onBack)
                UI.Button(type = ButtonType.PRIMARY, size = Size.M,
                    state = if (unreachable || medium == null) ComponentState.DISABLED else ComponentState.NORMAL, onClick = onStart) {
                    UI.Text(s.tool("launch_media_start"), TextType.BODY)
                }
            }
        }
    }
}
