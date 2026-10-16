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
import app.treelune.core.guide.GuideBlock
import app.treelune.core.guide.GuideMarkup
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
                    UI.Text(block.text, TextType.STRONG)
                }
                is GuideBlock.Paragraph -> UI.Text(block.text, type)
                is GuideBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.Top) {
                    UI.Icon(block.icon ?: LIST_MARK)
                    Box(modifier = Modifier.weight(1f)) { UI.Text(block.text, type) }
                }
            }
        }
    } }
    if (selectable) UI.Selectable(body) else body()
}
