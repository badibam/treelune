package app.treelune.core.guide.ui

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
import app.treelune.core.guide.GuideBlock
import app.treelune.core.guide.GuideMarkup
import app.treelune.core.guide.Run
import app.treelune.core.ui.Emphasis
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/** The icon before a line of a list that names no button. */
private const val LIST_MARK = "dot"

/**
 * A Guide text drawn from its markup (GuideMarkup): subtitles, paragraphs, and lines of a list,
 * each behind the icon of the button it names, or a dot. [selectable] but where a touch acts on it.
 */
@Composable
fun GuideText(text: String, type: TextType = TextType.BODY, selectable: Boolean = true) {
    val blocks = remember(text) { GuideMarkup.parse(text) }
    // Read, so selectable, one selection across its paragraphs; not where a touch acts on it (the band)
    val body: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        blocks.forEachIndexed { i, block ->
            when (block) {
                // A subtitle stands apart from what comes before it. STRONG, ranked under HEADING in
                // every theme, so that it stays under the chapter's part titles (SUBTITLE has no rank)
                is GuideBlock.Heading -> Box(modifier = if (i > 0) Modifier.padding(top = UI.Space.S) else Modifier) {
                    UI.Text(marked(block.runs), TextType.STRONG)
                }
                is GuideBlock.Paragraph -> UI.Text(marked(block.runs), type)
                is GuideBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.Top) {
                    UI.Icon(block.icon ?: LIST_MARK)
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
