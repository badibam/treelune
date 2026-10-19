package app.treelune.core.markup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.treelune.core.markup.Markup
import app.treelune.core.markup.MarkupBlock
import app.treelune.core.markup.Run
import app.treelune.core.ui.Emphasis
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/** The icon before a line of a list that names no button. */
private const val LIST_MARK = "dot"

/**
 * A long text drawn from its markup (Markup): subtitles, paragraphs, lines of a list, each behind
 * the icon of the button it names or a dot, and lines of a numbered list behind their number.
 * [icons] for an app's text (the Guide, a tool type's usage), whose list lines may begin with
 * their icon; never for the user's. [selectable] but where a touch acts on it.
 */
@Composable
fun MarkupText(text: String, icons: Boolean, type: TextType = TextType.BODY, selectable: Boolean = true) {
    val blocks = remember(text, icons) { Markup.parse(text, icons) }
    // Read, so selectable, one selection across its paragraphs; not where a touch acts on it (the band)
    val body: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        blocks.forEachIndexed { i, block ->
            when (block) {
                // A subtitle stands apart from what comes before it. STRONG, ranked under HEADING in
                // every theme, so that it stays under the chapter's part titles (SUBTITLE has no rank)
                is MarkupBlock.Heading -> Box(modifier = if (i > 0) Modifier.padding(top = UI.Space.S) else Modifier) {
                    UI.Text(marked(block.runs), TextType.STRONG)
                }
                is MarkupBlock.Paragraph -> UI.Text(marked(block.runs), type)
                is MarkupBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.Top) {
                    UI.Icon(block.icon ?: LIST_MARK)
                    Box(modifier = Modifier.weight(1f)) { UI.Text(marked(block.runs), type) }
                }
                // Its number where a list line has its dot, in the text's own type
                is MarkupBlock.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.Top) {
                    UI.Text("${block.number}.", type)
                    Box(modifier = Modifier.weight(1f)) { UI.Text(marked(block.runs), type) }
                }
            }
        }
    } }
    if (selectable) UI.Selectable(body) else body()
}

/** [runs] as one text, each marked run in the theme's bold, italic, or both. */
@Composable
private fun marked(runs: List<Run>): AnnotatedString {
    val bold = UI.emphasis(Emphasis.BOLD)
    val italic = UI.emphasis(Emphasis.ITALIC)
    return remember(runs, bold, italic) {
        buildAnnotatedString {
            runs.forEach { run ->
                val style = when {
                    run.bold && run.italic -> bold.merge(italic)
                    run.bold -> bold
                    run.italic -> italic
                    else -> null
                }
                if (style == null) append(run.text) else withStyle(style) { append(run.text) }
            }
        }
    }
}
