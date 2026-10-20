package app.treelune.core.ai.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import app.treelune.core.ui.Size
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * Shared card for user interactions (validation, communication modules)
 *
 * Reusable pattern for every interaction that needs an answer from the user.
 * Used for AI action validation, communication modules, and the like.
 *
 * Design: a primary-bordered card, so the interaction being asked for stands out.
 * Appearance is delegated to the active theme through UI.kt.
 */
@Composable
fun InteractionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    actions: (@Composable () -> Unit)?
) {
    // Appearance delegated to the active theme through UI.kt
    UI.InteractionCard(
        title = title,
        content = content,
        actions = actions
    )
}

/**
 * Standard action buttons for user interactions: CANCEL, then CONFIRM, the main one, last; the
 * theme lays them out side by side while they hold on one line, else one under the other with
 * CONFIRM on top (InteractionCard).
 */
@Composable
fun InteractionActions(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmLabel: String,
    cancelLabel: String,
    confirmEnabled: Boolean = true
) {
    UI.Button(
        onClick = onCancel,
        type = ButtonType.DEFAULT,
        size = Size.M
    ) {
        UI.Text(text = cancelLabel, type = TextType.BODY)
    }

    UI.Button(
        onClick = onConfirm,
        type = ButtonType.PRIMARY,
        size = Size.M,
        state = if (confirmEnabled) ComponentState.NORMAL else ComponentState.DISABLED
    ) {
        UI.Text(text = confirmLabel, type = TextType.BODY)
    }
}
