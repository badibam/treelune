package app.treelune.core.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.treelune.core.strings.StringsContext
import app.treelune.core.tasks.StepObject
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONObject

/**
 * The form of an `assemble` step (StepObject): the keys of the object, each with what it holds —
 * a text, a pointer (Readable or Technical), an entry, a value of the run, or more keys —; an
 * object opens in place of its parent, under the line of the keys that lead to it, and « Back »
 * goes up one level. A key renamed keeps its place.
 */
@Composable
internal fun AssembleEditor(step: JSONObject, earlier: Earlier, s: StringsContext, with: (JSONObject.() -> Unit) -> Unit) {
    // The keys from the step's object down to the one shown
    var path by rememberSaveable { mutableStateOf(listOf<String>()) }
    val root = step.optJSONObject(StepObject.OBJECT) ?: JSONObject()
    // A path to a key renamed or removed meanwhile comes back up to where it still leads
    val reached = path.runningFold(root as JSONObject?) { here, key -> here?.optJSONObject(key)?.optJSONObject(StepObject.OBJECT) }
    val depth = reached.indexOfFirst { it == null }.let { if (it < 0) path.size else it - 1 }
    val shown = reached[depth]!!
    val at = path.take(depth)

    /** The step's object with the one at [at] replaced by [next]. */
    fun write(next: JSONObject) = with {
        fun into(holder: JSONObject, keys: List<String>): JSONObject =
            if (keys.isEmpty()) next
            else JSONObject(holder.toString()).put(keys.first(), JSONObject().put(StepObject.OBJECT,
                into(holder.optJSONObject(keys.first())?.optJSONObject(StepObject.OBJECT) ?: JSONObject(), keys.drop(1))))
        put(StepObject.OBJECT, into(root, at))
    }

    /** [shown] with [key]'s part changed, renamed, or removed (null), the order of the keys kept. */
    fun change(key: String, newKey: String = key, part: JSONObject?) = write(JSONObject().apply {
        shown.keys().asSequence().toList().forEach { k ->
            if (k != key) put(k, shown.get(k)) else if (part != null) put(newKey, part)
        }
    })

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.FieldLabel(s.shared("task_object_label"), true)
        if (at.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.ActionButton(action = ButtonAction.BACK, display = ButtonDisplay.ICON, size = Size.S, onClick = { path = at.dropLast(1) })
            UI.Text((listOf(s.shared("task_object_label")) + at).joinToString(" › "), TextType.CAPTION)
        }
        UI.Text(s.shared("task_object_help"), TextType.CAPTION)

        shown.keys().asSequence().toList().forEach { key ->
            val part = shown.optJSONObject(key) ?: JSONObject()
            UI.Card(type = CardType.DEFAULT, size = Size.S) {
                Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.M), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                        Box(modifier = Modifier.weight(1f)) {
                            UI.FormField(label = s.shared("task_object_key"), value = key, required = true, onChange = { name ->
                                // Two keys of one name would be one: the rename waits for a name of its own
                                if (name.isNotBlank() && (name == key || !shown.has(name))) change(key, name, part)
                            })
                        }
                        UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S, onClick = { change(key, part = null) })
                    }
                    Part(key, part, earlier, s, onChange = { change(key, part = it) }, onOpen = { path = at + key })
                }
            }
        }

        // What a key can hold, each added at the end under a name of its own
        fun added(base: String, part: JSONObject) {
            val name = generateSequence(1) { it + 1 }.map { "$base$it" }.first { !shown.has(it) }
            write(JSONObject(shown.toString()).put(name, part))
        }
        var pointing by rememberSaveable { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            UI.Button(type = ButtonType.DEFAULT, onClick = { added(s.shared("task_object_new_text"), JSONObject().put(StepObject.TEXT, "")) }) { UI.Text(s.shared("task_block_text"), TextType.LABEL) }
            UI.Button(type = ButtonType.DEFAULT, onClick = { pointing = true }) { UI.Text(s.shared("task_block_pointer"), TextType.LABEL) }
            UI.Button(type = ButtonType.DEFAULT, onClick = {
                val received = JSONObject().put(app.treelune.core.tasks.TaskSteps.RECEIVED, JSONObject())
                added(s.shared("task_object_new_entry"), JSONObject().put(StepObject.ENTRY, received))
            }) { UI.Text(s.shared("task_block_entry"), TextType.LABEL) }
            UI.Button(type = ButtonType.DEFAULT, onClick = { added(s.shared("task_object_new_value"), JSONObject().put(StepObject.VALUE, JSONObject())) }) { UI.Text(s.shared("task_block_value"), TextType.LABEL) }
            UI.Button(type = ButtonType.DEFAULT, onClick = { added(s.shared("task_object_new_object"), JSONObject().put(StepObject.OBJECT, JSONObject())) }) { UI.Text(s.shared("task_object_kind_object"), TextType.LABEL) }
        }
        if (pointing) app.treelune.core.ui.selectors.PointerSelector(
            reference = s.shared("task_reference_run"),
            onDismiss = { pointing = false },
            onConfirm = { config ->
                pointing = false
                added(s.shared("task_object_new_pointer"), JSONObject().put(StepObject.POINTER, JSONObject(config).put(StepObject.RENDERING, StepObject.READABLE)))
            }
        )
    }
}

/** What one key holds, edited by its kind; an object opened by [onOpen]. */
@Composable
private fun Part(key: String, part: JSONObject, earlier: Earlier, s: StringsContext, onChange: (JSONObject) -> Unit, onOpen: () -> Unit) {
    var pointing by rememberSaveable(key) { mutableStateOf(false) }
    when {
        part.has(StepObject.TEXT) -> UI.FormField(label = s.shared("task_block_text"), value = part.optString(StepObject.TEXT), required = false,
            onChange = { onChange(JSONObject().put(StepObject.TEXT, it)) }, fieldType = app.treelune.core.ui.FieldType.TEXT_UNLIMITED)
        part.has(StepObject.POINTER) -> {
            val pointer = part.optJSONObject(StepObject.POINTER) ?: JSONObject()
            // A pointer is chosen anew to be changed, as in a routine's message; its rendering kept
            app.treelune.core.ai.ui.components.EnrichmentBlockContent(
                app.treelune.core.ai.data.MessageSegment.EnrichmentBlock(app.treelune.core.ai.data.EnrichmentType.POINTER, pointer.toString()),
                onEdit = { pointing = true }
            )
            val renderings = listOf(StepObject.READABLE to s.shared("task_object_readable"), StepObject.TECHNICAL to s.shared("task_object_technical"))
            UI.FormSelection(
                label = s.shared("task_object_rendering"),
                options = renderings.map { it.second },
                selected = renderings.firstOrNull { it.first == pointer.optString(StepObject.RENDERING, StepObject.READABLE) }?.second ?: "",
                onSelect = { text -> onChange(JSONObject().put(StepObject.POINTER, JSONObject(pointer.toString()).put(StepObject.RENDERING, renderings.first { it.second == text }.first))) },
                required = true
            )
            UI.Text(s.shared(if (pointer.optString(StepObject.RENDERING, StepObject.READABLE) == StepObject.TECHNICAL) "task_object_technical_help" else "task_object_readable_help"), TextType.CAPTION)
            if (pointing) app.treelune.core.ui.selectors.PointerSelector(
                reference = s.shared("task_reference_run"),
                onDismiss = { pointing = false },
                onConfirm = { config ->
                    pointing = false
                    onChange(JSONObject().put(StepObject.POINTER, JSONObject(config).put(StepObject.RENDERING, pointer.optString(StepObject.RENDERING, StepObject.READABLE))))
                }
            )
        }
        part.has(StepObject.ENTRY) -> StepInput(part.optJSONObject(StepObject.ENTRY)?.takeIf { it.length() > 0 }, blockEntryField(s), earlier, s, optional = false) { input ->
            onChange(JSONObject().put(StepObject.ENTRY, input ?: JSONObject()))
        }
        part.has(StepObject.VALUE) -> StepInput(part.optJSONObject(StepObject.VALUE)?.takeIf { it.length() > 0 }, blockValueField(s), earlier, s, optional = false) { input ->
            onChange(JSONObject().put(StepObject.VALUE, input ?: JSONObject()))
        }
        part.has(StepObject.OBJECT) -> UI.Button(type = ButtonType.DEFAULT, onClick = onOpen) {
            UI.Text(s.shared("task_object_open").format(part.optJSONObject(StepObject.OBJECT)?.length() ?: 0), TextType.BODY)
        }
    }
}
