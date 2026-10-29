package app.treelune.core.guide

import app.treelune.core.strings.StringsContext

/**
 * The app's pieces beyond the tool types, which a setup combines: what each is for (its usage)
 * and what the user does with it on screen (its screen: a list whose line may begin with its
 * button's icon, `{name}`, as a tool type's guide_screen). A tool type says the same of itself in
 * its own texts; the core names no tool type here, a tool that notifies saying so in its usage.
 *
 * Read by the AI (PromptChunks, « The pieces »), so that it proposes what the screen offers, such
 * as a routine's conversation button; written for the user too, in the app's language.
 */
object Pieces {

    /** One piece: the keys of its name, its usage and its screen, written whole for check_string_keys.py. */
    data class Piece(val nameKey: String, val usageKey: String, val screenKey: String)

    val ALL = listOf(
        Piece("piece_zone_name", "piece_zone_usage", "piece_zone_screen"),
        Piece("piece_tool_name", "piece_tool_usage", "piece_tool_screen"),
        Piece("piece_routine_name", "piece_routine_usage", "piece_routine_screen"),
        Piece("piece_task_name", "piece_task_usage", "piece_task_screen"),
        Piece("piece_variable_name", "piece_variable_usage", "piece_variable_screen"),
        Piece("piece_alert_name", "piece_alert_usage", "piece_alert_screen"),
        Piece("piece_event_name", "piece_event_usage", "piece_event_screen"),
        Piece("piece_api_name", "piece_api_usage", "piece_api_screen"),
        Piece("piece_pointer_name", "piece_pointer_usage", "piece_pointer_screen"),
        Piece("piece_identity_name", "piece_identity_usage", "piece_identity_screen"),
        Piece("piece_conversation_name", "piece_conversation_usage", "piece_conversation_screen"),
        Piece("piece_notification_name", "piece_notification_usage", "piece_notification_screen")
    )

    /**
     * A screen text for a reader who sees no icon: a list line's `{name}` becomes the button it
     * draws, « The “name” button: », the Lucide name the AI already knows from choosing icons.
     */
    fun withoutIcons(text: String, s: StringsContext): String =
        ICON.replace(text) { s.shared("piece_button").format(it.groupValues[1]) + " " }

    private val ICON = Regex("""(?m)^- \{([a-z0-9-]+)\}\s*""")
}
