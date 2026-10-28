package app.treelune.core.grid

import app.treelune.core.database.entities.ToolInstance

import org.json.JSONArray
import org.json.JSONObject

/**
 * The group section a tool, a variable or a routine of a zone is shown in, and the zone's
 * groups. Pure; where their tiles stand is SectionGrid's.
 */
object ToolPositions {

    /**
     * The group section a tool with [group] is shown in, among the zone's [zoneGroups]: its own,
     * or the ungrouped one (null) when it has none. A group the zone does not have cannot be held
     * (Groups): one that is anyway is a bug, logged, and its tool shown
     * among the ungrouped rather than lost from the screen.
     */
    fun section(group: String?, zoneGroups: List<String>): String? {
        if (group.isNullOrEmpty()) return null
        if (group in zoneGroups) return group
        app.treelune.core.utils.LogManager.ui("A tool holds the group '$group', which its zone does not have", "ERROR")
        return null
    }

    fun section(tool: ToolInstance, zoneGroups: List<String>): String? =
        section(JSONObject(tool.config_json).optString("group").takeIf { it.isNotEmpty() }, zoneGroups)

    /** A zone's tool_groups column as the list of its groups. */
    fun zoneGroups(toolGroups: String?): List<String> =
        toolGroups?.let { json -> JSONArray(json).let { array -> (0 until array.length()).map { array.getString(it) } } } ?: emptyList()
}
