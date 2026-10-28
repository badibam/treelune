package app.treelune.core.versioning

import app.treelune.core.grid.Grid
import app.treelune.core.ui.DisplayMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the routines to their v89 form (docs/AI.md, « Routines »): a routine is a tile of its
 * group's section, among the tools and the variables. It gains a look (Looks): a description (none),
 * an icon (`bot`), a colour and a texture (none), a display mode (EXTENDED, the card it was); and
 * its place in the section's grid, under the section's tools and variables, one under the other
 * in the order they were made.
 *
 * Shared by the database migration (STATEMENTS, [placed]) and the backup import ([backup]); a
 * history's copy without the columns puts back their defaults.
 */
object RoutinesAtV89 {

    const val ICON = "bot"
    const val MODE = "EXTENDED"

    /** The migration's statements, run on the v88 tables before the places are written. */
    val STATEMENTS = listOf(
        "ALTER TABLE automations ADD COLUMN description TEXT",
        "ALTER TABLE automations ADD COLUMN icon_name TEXT NOT NULL DEFAULT '$ICON'",
        "ALTER TABLE automations ADD COLUMN icon_color TEXT",
        "ALTER TABLE automations ADD COLUMN texture TEXT",
        "ALTER TABLE automations ADD COLUMN display_mode TEXT NOT NULL DEFAULT '$MODE'",
        "ALTER TABLE automations ADD COLUMN grid_x INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE automations ADD COLUMN grid_y INTEGER NOT NULL DEFAULT 0"
    )

    /** A tile already in a section, a tool or a variable: its zone, its group, the first row under it. */
    data class Below(val zoneId: String, val group: String?, val bottom: Int)

    /** A routine as v88 kept it. */
    data class Routine(val id: String, val zoneId: String, val group: String?, val createdAt: Long)

    /** The tile of zone [zoneId] in [group] at row [gridY], [mode] giving its height. */
    fun below(zoneId: String, group: String?, gridY: Int, mode: String): Below =
        Below(zoneId, group?.takeIf { it.isNotEmpty() }, gridY + Grid.size(DisplayMode.valueOf(mode)).height)

    /** The row of each routine of [routines], by id: one under the other in the order made, from under [tiles] of its section. */
    fun placed(routines: List<Routine>, tiles: List<Below>): Map<String, Int> {
        val height = Grid.size(DisplayMode.valueOf(MODE)).height
        return routines.groupBy { it.zoneId to it.group }.flatMap { (section, list) ->
            val start = tiles.filter { (it.zoneId to it.group) == section }.maxOfOrNull { it.bottom } ?: 0
            list.sortedBy { it.createdAt }.mapIndexed { i, routine -> routine.id to start + i * height }
        }.toMap()
    }

    /** The backup's [data] with its routines in their v89 form, placed under its tools and variables. */
    fun backup(data: JSONObject) {
        val routines = data.optJSONArray("automations") ?: return
        fun rows(key: String) = data.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val tiles = rows("tool_instances").map { tool ->
            val config = JSONObject(tool.getString("config_json"))
            below(tool.getString("zone_id"), config.optString("group"), tool.getInt("grid_y"), config.getString("display_mode"))
        } + rows("variables").map { below(it.getString("zone_id"), it.optString("group"), it.getInt("grid_y"), it.getString("display_mode")) }
        val list = (0 until routines.length()).map { routines.getJSONObject(it) }
        val placed = placed(list.map { Routine(it.getString("id"), it.getString("zone_id"), it.optString("group").takeIf { g -> g.isNotEmpty() }, it.optLong("created_at")) }, tiles)
        data.put("automations", JSONArray(list.map { routine ->
            JSONObject(routine.toString()).apply {
                if (!has("icon_name")) put("icon_name", ICON)
                if (!has("display_mode")) put("display_mode", MODE)
                if (!has("grid_x")) put("grid_x", 0)
                if (!has("grid_y")) put("grid_y", placed.getValue(routine.getString("id")))
            }
        }))
    }
}
