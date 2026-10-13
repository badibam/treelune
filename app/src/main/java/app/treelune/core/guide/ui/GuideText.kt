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
 * each behind the icon of the button it names, or a dot.
 */
@Composable
fun GuideText(text: String, type: TextType = TextType.BODY) {
    val blocks = remember(text) { GuideMarkup.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
        blocks.forEachIndexed { i, block ->
            when (block) {
                // A subtitle stands apart from what comes before it
                is GuideBlock.Heading -> Box(modifier = if (i > 0) Modifier.padding(top = UI.Space.S) else Modifier) {
                    UI.Text(block.text, TextType.SUBTITLE)
                }
                is GuideBlock.Paragraph -> UI.Text(block.text, type)
                is GuideBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(UI.Space.S), verticalAlignment = Alignment.Top) {
                    UI.Icon(block.icon ?: LIST_MARK)
                    Box(modifier = Modifier.weight(1f)) { UI.Text(block.text, type) }
                }
            }
        }
    }
}
