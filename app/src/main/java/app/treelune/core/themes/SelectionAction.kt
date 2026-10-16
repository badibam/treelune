package app.treelune.core.themes

/** An action of the bar over a selected text (ThemeContract.SelectionBar): its word, and what a touch on it does. */
data class SelectionAction(val label: String, val onClick: () -> Unit)
