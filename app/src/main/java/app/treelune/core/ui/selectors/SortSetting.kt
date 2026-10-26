package app.treelune.core.ui.selectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.fields.EntrySort
import app.treelune.core.fields.ReferenceTarget
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONArray
import org.json.JSONObject

/**
 * The order of a selection's entries as a setting (SettingNode.Sort, EntrySort): one line per
 * key, its field among those of the selection's tool that have an order, its direction and its
 * removal, then the addition of one more. Nothing to choose before the selection has its tool;
 * without a key, it says the order that holds then. A key on a field gone shows its path, for the
 * service to name it.
 *
 * @param selection The stored selection beside it, whose tool the fields are read from
 */
@Composable
fun SortSetting(label: String, sort: JSONArray, selection: JSONObject?, onChange: (JSONArray) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val toolId = ReferenceTarget.referenceOf(selection?.opt("target"))?.id
    val fields = EntrySort.sortableFields(rememberToolFields(toolId))
    val keys = (0 until sort.length()).mapNotNull { sort.optJSONObject(it) }
    fun publish(next: List<JSONObject>) = onChange(JSONArray(next))
    val directions = EntrySort.DIRECTIONS.associateWith { s.shared("sort_direction_$it") }

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.FieldLabel(label, false)
        if (toolId == null) return@Column UI.Text(s.shared("setting_field_of_tool_first"), TextType.CAPTION)
        if (keys.isEmpty()) UI.Text(s.shared("sort_none"), TextType.CAPTION)
        keys.forEachIndexed { index, sortKey ->
            fun changed(field: String, value: String) = publish(keys.toMutableList().also { it[index] = JSONObject(sortKey.toString()).put(field, value) })
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                // Keyed by position and content: a removal above gives the line below its own key back
                key(index, sortKey.toString()) {
                    Box(modifier = Modifier.weight(1f)) {
                        val path = sortKey.optString(EntrySort.FIELD)
                        if (path.isNotEmpty() && path !in fields) UI.Text(path, TextType.BODY)
                        else FieldPicker(
                            label = "",
                            fields = fields,
                            selected = path.takeIf { it.isNotEmpty() }?.let { FieldPick.Path(it) },
                            onSelect = { pick -> changed(EntrySort.FIELD, (pick as FieldPick.Path).path) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormSelection(
                            label = "",
                            options = directions.values.toList(),
                            selected = directions[sortKey.optString(EntrySort.DIRECTION)] ?: "",
                            onSelect = { text -> changed(EntrySort.DIRECTION, directions.entries.first { it.value == text }.key) },
                            required = true
                        )
                    }
                }
                UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S,
                    onClick = { publish(keys.filterIndexed { i, _ -> i != index }) })
            }
        }
        // A new key on the first field not used yet, smallest first
        val free = fields.keys.firstOrNull { path -> keys.none { it.optString(EntrySort.FIELD) == path } }
        if (free != null) UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.LABEL, onClick = {
            publish(keys + JSONObject().put(EntrySort.FIELD, free).put(EntrySort.DIRECTION, EntrySort.ASC))
        })
    }
}
