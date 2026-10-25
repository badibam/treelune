package app.treelune.core.tasks

import app.treelune.core.grid.Grid
import app.treelune.core.look.Look
import app.treelune.core.look.Looks
import app.treelune.core.utils.JsonUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Task as the tasks service gives it in a result (TaskService.describe): what its tile and its
 * page show. Its settings as they are written, its steps and its access in their stored forms.
 */
data class Task(
    val id: String,
    val zoneId: String,
    val look: Look,
    val group: String?,
    val isEnabled: Boolean,
    val settings: JSONObject,
    val steps: JSONArray,
    val access: JSONArray,
    val gridX: Int,
    val gridY: Int,
    val updatedAt: Long
) {
    val name: String get() = look.name

    val tile: Grid.Tile get() = Grid.size(look.displayMode).let { Grid.Tile(id, gridX, gridY, it.width, it.height) }

    companion object {
        /**
         * A Task of a list as the AI reads it (TASKS): its id, name, zone and group, whether its
         * schedule runs it, and its steps by their kind.
         */
        @Suppress("UNCHECKED_CAST")
        fun listed(map: Map<*, *>): Map<String, Any?> {
            val settings = map["settings"] as Map<String, Any?>
            return buildMap {
                put("id", map["id"])
                put("name", settings["name"])
                put("zone_id", map["zone_id"])
                settings["group"]?.let { put("group", it) }
                put("is_enabled", settings["is_enabled"])
                put("scheduled", settings.containsKey("schedule"))
                put("steps", (map["steps"] as List<*>).map { (it as Map<*, *>)["kind"] })
            }
        }

        /** The single reader of a Task in a result; fails loudly on one that does not hold it. */
        @Suppress("UNCHECKED_CAST")
        fun fromResult(map: Map<*, *>): Task {
            val settings = JsonUtils.toJSONObject(map["settings"] as Map<String, Any?>)
            return Task(
                id = map["id"] as String,
                zoneId = map["zone_id"] as String,
                look = Looks.read(TaskSettings.LOOK, settings),
                group = settings.optString("group").ifEmpty { null },
                isEnabled = settings.getBoolean("is_enabled"),
                settings = settings,
                steps = JsonUtils.toJSONArray(map["steps"] as List<*>),
                access = JsonUtils.toJSONArray(map["access"] as List<*>),
                gridX = (map["grid_x"] as Number).toInt(),
                gridY = (map["grid_y"] as Number).toInt(),
                updatedAt = (map["updated_at"] as Number).toLong()
            )
        }
    }
}
