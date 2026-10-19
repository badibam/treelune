package app.treelune.tools.notes.ui.components

import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.treelune.core.ui.*
import app.treelune.core.strings.Strings
import app.treelune.core.fields.CustomFieldsDisplay
import app.treelune.tools.notes.ui.NoteEntry

/**
 * A note's card, opened in its dialog to be edited
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry,
    toolInstanceId: String,
    config: org.json.JSONObject, // The tool's config, for how its fields show
    titles: Boolean = false, // Whether the tool's notes take a title, shown above the text
    showContextMenu: Boolean = false,
    contextMenuNoteId: String? = null,
    onNoteClick: () -> Unit = {}, // Opens edit dialog
    onContextMenuChanged: (Boolean) -> Unit = {},
    dragHandle: (@Composable () -> Unit)? = null, // The band across its top it is reordered by
    onAddAbove: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "notes", context = context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val title = note.title?.takeIf { titles }

    UI.Card(type = CardType.DEFAULT) {
        Box {
            // The grip across the top, the text under it taking the whole width
            Column {
                dragHandle?.invoke()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (!showContextMenu) {
                                    onNoteClick() // Open edit dialog
                                }
                            },
                            onLongClick = { onContextMenuChanged(true) }
                        )
                        // The band stands in the place of the top margin
                        .padding(start = UI.Space.L, end = UI.Space.L, bottom = UI.Space.L, top = if (dragHandle != null) 0.dp else UI.Space.L)
                ) {
                    title?.let {
                        UI.Text(text = it, type = TextType.SUBTITLE)
                        Spacer(modifier = Modifier.height(UI.Space.XS))
                    }

                    // Note content display
                    val displayContent = note.content.trim()

                    if (displayContent.isBlank()) {
                        // Empty note
                        Box(
                            modifier = Modifier.height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UI.Text(
                                text = s.tool("content_empty"),
                                type = TextType.CAPTION
                            )
                        }
                    } else {
                        // Note with content
                        UI.Text(
                            text = displayContent,
                            type = TextType.BODY
                        )

                        // Custom fields display (always shown for alwaysVisible fields)
                        Spacer(modifier = Modifier.height(UI.Space.S))
                        CustomFieldsDisplay(
                            toolType = app.treelune.tools.notes.NotesToolType,
                            config = config,
                            values = note.extra,
                            layout = app.treelune.core.fields.FieldsLayout.COMPACT,
                            context = context
                        )
                    }
                }
            }

            // Context menu overlay
            if (showContextMenu) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UI.Space.S),
                    horizontalArrangement = Arrangement.spacedBy(UI.Space.XS)
                ) {
                    UI.ActionButton(
                        action = ButtonAction.ADD,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            onContextMenuChanged(false)
                            onAddAbove()
                        }
                    )

                    // The note as text: its title, when titles are shown, its content, then the user's fields
                    UI.ActionButton(
                        action = ButtonAction.COPY,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            onContextMenuChanged(false)
                            scope.launch { UI.copy(context, app.treelune.core.fields.EntryText.of(listOf(title, note.content), config, note.extra, context)) }
                        }
                    )

                    UI.ActionButton(
                        action = ButtonAction.DELETE,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        requireConfirmation = true,
                        confirmMessage = s.tool("delete_confirm_template").format(
                            title ?: (note.content.take(30) + if (note.content.length > 30) "..." else "")
                        ),
                        onClick = {
                            onContextMenuChanged(false)
                            onDelete()
                        }
                    )

                    UI.ActionButton(
                        action = ButtonAction.CONFIRM,
                        display = ButtonDisplay.ICON,
                        size = Size.S,
                        onClick = {
                            onContextMenuChanged(false)
                        }
                    )
                }
            }
        }
    }
}