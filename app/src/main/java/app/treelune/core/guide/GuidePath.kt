package app.treelune.core.guide

import app.treelune.core.navigation.Place
import app.treelune.core.navigation.PlaceStack

/**
 * The way to a step's place, as gestures to make: from the places open (the band), or from the
 * home screen (the Guide's page). Up first, to the place both ways share (« come back to Home »),
 * then down, one gesture per place. Pure: the names of places and the texts are handed in.
 */
object GuidePath {

    /**
     * The gestures from [open] (the stack, chat excluded) to [target]; empty once there.
     *
     * @param chatOpen Whether the chat is open over [open]
     * @param name A place's name, null when not known
     * @param text A string by key, formatted with its arguments
     */
    fun gestures(open: List<Place>, chatOpen: Boolean, target: Place, name: (Place) -> String?, text: (String, Array<out Any>) -> String): List<String> {
        if (target == Place.Chat) return if (chatOpen) emptyList() else listOf(text("guide_path_chat", emptyArray()))
        val way = PlaceStack.chain(target)
        val shared = open.zip(way).takeWhile { (a, b) -> a == b }.size
        if (shared == open.size && shared == way.size) return emptyList()
        val up = if (shared < open.size) listOf(text("guide_path_back_to", arrayOf(name(open[shared - 1]).orEmpty()))) else emptyList()
        return up + way.drop(shared).map { gesture(it, name, text) }
    }

    /** The gestures as one sentence: « Open the zone Running, then touch Runs ». */
    fun sentence(gestures: List<String>, then: String): String =
        gestures.joinToString(then).replaceFirstChar { it.uppercase() }

    private fun gesture(place: Place, name: (Place) -> String?, text: (String, Array<out Any>) -> String): String {
        fun t(key: String, vararg args: Any) = text(key, args)
        val n = name(place).orEmpty()
        return when (place) {
            is Place.Zone -> t("guide_path_zone", n)
            is Place.ZoneConfig -> t("guide_path_zone_config")
            is Place.CreateZone -> t("guide_path_zone_new")
            is Place.Tool -> t("guide_path_tool", n)
            is Place.ToolConfig -> t(if (place.toolId != null) "guide_path_tool_config" else "guide_path_tool_new")
            is Place.Variable -> t("guide_path_variable")
            is Place.Automation -> t("guide_path_automation")
            is Place.Execution -> t("guide_path_execution")
            is Place.Seed -> t("guide_path_seed")
            is Place.Task -> t("guide_path_task", n)
            is Place.TaskRun -> t("guide_path_task_run")
            Place.HomeConfig -> t("guide_path_home_config")
            Place.Settings -> t("guide_path_settings")
            is Place.SettingsPage -> t("guide_path_settings_page", n)
            // An identity's tile touched on its settings page, as a page's on the settings
            is Place.Identity -> t("guide_path_settings_page", n)
            Place.AIProviderKind -> t("guide_path_settings_page", t("ai_provider_add"))
            // The export's button touched on the data's settings page
            Place.Export -> t("guide_path_settings_page", t("share_export_open"))
            is Place.Import -> t("guide_path_settings_page", t("share_import_open"))
            is Place.AIProvider -> t("guide_path_settings_page", n)
            is Place.Api -> t("guide_path_settings_page", n)
            Place.Guide -> t("guide_path_guide")
            is Place.Chapter -> t("guide_path_chapter", n)
            Place.Chat -> t("guide_path_chat")
            Place.Home -> throw IllegalStateException("The home screen is never on the way down")
        }
    }
}
