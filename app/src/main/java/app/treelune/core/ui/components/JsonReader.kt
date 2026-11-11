package app.treelune.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONArray
import org.json.JSONObject

/**
 * A JSON object or list read, never written (docs/UI.md): each key in bold before its value, an
 * object or a list folded behind a line that says how many it holds and opened by a touch, a text
 * with its line breaks. What a JSON field holds (TextLength.JSON), what a Task's step gave, what
 * an API was sent or answered. The text is said JSON by whoever holds it — a field's length, a
 * step's kind —, never guessed from how it looks; one that does not read shows as it is.
 *
 * @param json An object or a list, or its text
 * @param open Whether the first level shows open
 */
@Composable
fun JsonReader(json: Any, open: Boolean = true) {
    val value = remember(json) {
        when (json) {
            is JSONObject, is JSONArray -> json
            is String -> runCatching { org.json.JSONTokener(json).nextValue() }.getOrNull()?.takeIf { it is JSONObject || it is JSONArray }
            else -> null
        }
    }
    if (value == null) return UI.Text(json.toString(), TextType.BODY)
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.XS)) { Node(null, value, open, "") }
}

/** One key and its value, at [path] (what keeps its folding across a recomposition). */
@Composable
private fun Node(key: String?, value: Any?, open: Boolean, path: String) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    when (value) {
        is JSONObject, is JSONArray -> {
            val count = if (value is JSONObject) value.length() else (value as JSONArray).length()
            var shown by rememberSaveable(path) { mutableStateOf(open) }
            if (key != null) {
                Row(
                    modifier = Modifier.clickable { shown = !shown },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)
                ) {
                    UI.Icon(if (shown) "chevron-down" else "chevron-right", size = 16.dp)
                    UI.Text(key, TextType.STRONG)
                    UI.Text(s.shared(if (value is JSONObject) "json_reader_keys" else "json_reader_items").format(count), TextType.CAPTION)
                }
            }
            if (shown || key == null) Column(
                modifier = if (key != null) Modifier.padding(start = UI.Space.L) else Modifier,
                verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
            ) {
                if (value is JSONObject) value.keys().asSequence().toList().forEach { k -> Node(k, value.opt(k), false, "$path/$k") }
                else (value as JSONArray).let { list -> (0 until list.length()).forEach { i -> Node("${i + 1}", list.opt(i), false, "$path/$i") } }
            }
        }
        else -> {
            val text = if (value == null || value == JSONObject.NULL) "—" else value.toString()
            if (key == null) UI.Text(text, TextType.BODY)
            else Column {
                UI.Text(key, TextType.STRONG)
                UI.Text(text, TextType.BODY)
            }
        }
    }
}
