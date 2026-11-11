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
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.SortKey
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.Size
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * The order of a selection's entries (EntrySort): one line per key, its field among [fields] that
 * have an order, its direction, and its removal while another key is left — a sort is never
 * empty, its default written by the picker that holds it (SelectionOrder) —, then the addition of
 * one more. A key on a field gone shows its path, for the service to name it.
 *
 * @param fields The fields of the selection's tool or series that may be keys, by path
 *   (rememberToolFields with its sort keys, a manual position included)
 */
@Composable
fun SortSetting(label: String, sort: List<SortKey>, fields: Map<String, FieldDefinition>, onChange: (List<SortKey>) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val sortable = EntrySort.sortableFields(fields)
    val directions = mapOf(true to s.shared("sort_direction_asc"), false to s.shared("sort_direction_desc"))

    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        UI.FieldLabel(label, true)
        sort.forEachIndexed { index, sortKey ->
            fun changed(next: SortKey) = onChange(sort.toMutableList().also { it[index] = next })
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                // Keyed by position and content: a removal above gives the line below its own key back
                key(index, sortKey.toString()) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (sortKey.field !in sortable) UI.Text(sortKey.field, TextType.BODY)
                        else FieldPicker(
                            label = "",
                            fields = sortable,
                            selected = FieldPick.Path(sortKey.field),
                            onSelect = { pick -> changed(sortKey.copy(field = (pick as FieldPick.Path).path)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        UI.FormSelection(
                            label = "",
                            options = directions.values.toList(),
                            selected = directions.getValue(sortKey.ascending),
                            onSelect = { text -> changed(sortKey.copy(ascending = directions.entries.first { it.value == text }.key)) },
                            required = true
                        )
                    }
                }
                if (sort.size > 1) UI.ActionButton(action = ButtonAction.DELETE, display = ButtonDisplay.ICON, size = Size.S,
                    onClick = { onChange(sort.filterIndexed { i, _ -> i != index }) })
            }
        }
        // A new key on the first field not used yet, smallest first
        val free = sortable.keys.firstOrNull { path -> sort.none { it.field == path } }
        if (free != null) UI.ActionButton(action = ButtonAction.ADD, display = ButtonDisplay.LABEL, onClick = {
            onChange(sort + SortKey(free, ascending = true))
        })
    }
}
