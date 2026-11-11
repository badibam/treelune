package app.treelune.core.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.formatValue
import app.treelune.core.fields.shownCustomFields
import app.treelune.core.fields.toFieldDefinitions
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import org.json.JSONObject

/**
 * An element of a tile's body that is an entry (docs/design/tile-sizes.md, « Le corps »), filled
 * in this order on the lines its tile gives it (LocalItemLines): its [main] line; its [text], for
 * a tool that has one (a note's, a journal entry's, a message's start); then its user's [fields],
 * one per line (tileItemFields). When both cannot show whole, the text keeps one line at least and
 * the fields take the lines it leaves, so that each shows; nothing is ever cut mid-line. With
 * nothing under it, [main] has every line.
 */
@Composable
fun TileItem(
    main: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    fields: List<String> = emptyList(),
    mainType: TextType = TextType.BODY,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) = TileLine(modifier, leading, trailing) {
    val lines = LocalItemLines.current
    val shownText = text?.takeIf { it.isNotBlank() }
    val split = tileItemLines(lines, shownText != null, fields.size)
    if (split.text == 0 && split.fields == 0) UI.Text(main, mainType, maxLines = lines)
    else Column {
        UI.Text(main, mainType, maxLines = 1)
        if (split.text > 0) UI.Text(shownText!!, TextType.CAPTION, maxLines = split.text)
        fields.take(split.fields).forEach { UI.Text(it, TextType.CAPTION, maxLines = 1) }
    }
}

/** The lines an element's text and its fields take under its main line. */
data class TileItemLines(val text: Int, val fields: Int)

/**
 * How [lines] are shared under the main line between a text ([hasText]) and [fieldCount] fields
 * of one line each: the text keeps one line at least when there are fields, every line left when
 * there are none; the fields take what the text leaves.
 */
fun tileItemLines(lines: Int, hasText: Boolean, fieldCount: Int): TileItemLines {
    val under = lines - 1
    if (under <= 0) return TileItemLines(0, 0)
    val text = when {
        !hasText -> 0
        fieldCount == 0 -> under
        else -> maxOf(1, under - fieldCount)
    }
    return TileItemLines(text, minOf(fieldCount, under - text))
}

/**
 * The paths of a tool's user's fields a tile asks tool_data.get for (`extra.<name>`), to show
 * them (tileItemFields): each named, a container being refused whole; a file left out.
 */
fun tileItemFieldPaths(config: JSONObject): List<String> =
    (config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList())
        .filter { it.type != FieldType.FILE }.map { "extra.${it.name}" }

/**
 * An entry's user's fields as its tile shows them, one line each: those that hold a value or show
 * always (shownCustomFields), in the config's order, with their names when the tool shows them
 * (show_field_labels); a file left out, a tile showing it apart.
 */
fun tileItemFields(toolType: app.treelune.core.tools.ToolTypeContract, config: JSONObject, extra: Map<String, Any?>, context: Context): List<String> {
    val showLabels = app.treelune.core.tools.ToolConfigSettings.read(toolType, config, context)
        .boolean(app.treelune.core.tools.ToolConfigSettings.SHOW_FIELD_LABELS)
    val s = Strings.`for`(context = context)
    return shownCustomFields(config, extra).filter { it.type != FieldType.FILE }.map { field ->
        val value = field.formatValue(extra[field.name], context)
        if (showLabels) s.shared("field_label_inline").format(field.displayName) + " " + value else value
    }
}
