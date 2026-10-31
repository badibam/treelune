package app.treelune.core.grid

import android.content.Context
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.Zone
import app.treelune.core.themes.IconColor
import app.treelune.core.themes.Texture
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * What a zone holds, as the AI reads it to understand and extend an organisation (ZONE_CONTENTS):
 * its sections in the zone's order, the ungrouped one last, each with every tile of its grid —
 * tools, variables, routines, Tasks, readouts — in reading order (row by row, left to right),
 * each with its kind, its name, its size and its look; then the zone's events. The zones in the
 * home screen's order: its groups', the ungrouped last, each in reading order. A tool also gives
 * its type, its description and its extra fields, which writing its entries needs.
 */
class ZoneContents(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)

    /** The zones in the home screen's order, [zoneGroups] its groups; [only] one zone alone. */
    suspend fun read(zoneGroups: List<String>, only: String?): List<Map<String, Any?>> {
        val zones = database.zoneDao().getAllZones().filter { only == null || it.id == only }
        return ordered(zones, zoneGroups).map { zone(it) }
    }

    private suspend fun zone(zone: Zone): Map<String, Any?> {
        val groups = ToolPositions.zoneGroups(zone.tool_groups)
        val tiles = tiles(zone.id)
        val sections = (groups.map { it as String? } + null).mapNotNull { section ->
            val held = tiles.filter { ToolPositions.section(it.group, groups) == section }.sortedWith(compareBy({ it.row }, { it.column }))
            if (held.isEmpty() && section == null) null
            else mapOf<String, Any?>("group" to section, "tiles" to held.map { it.described })
        }
        return buildMap<String, Any?> {
            put("id", zone.id)
            put("name", zone.name)
            put("description", zone.description)
            put("group", zone.group)
            put("display_mode", zone.display_mode)
            put("icon_name", zone.icon_name)
            put(IconColor.KEY, zone.icon_color)
            put(Texture.KEY, zone.texture)
            put("sections", sections)
            put("events", database.eventDao().getByZone(zone.id).map { mapOf("id" to it.id, "name" to it.name, "description" to it.description) })
        }
    }

    /** One tile: its section's group, its place, and what is said of it. */
    private class Tile(val group: String?, val column: Int, val row: Int, val described: Map<String, Any?>)

    private fun look(kind: String, id: String, name: String, mode: String, icon: String?, color: String?, texture: String?, more: Map<String, Any?> = emptyMap()) =
        linkedMapOf<String, Any?>("kind" to kind, "id" to id, "name" to name) + more +
            mapOf("display_mode" to mode, "icon_name" to icon, IconColor.KEY to color, Texture.KEY to texture)

    private suspend fun tiles(zoneId: String): List<Tile> {
        val tools = database.toolInstanceDao().getToolInstancesByZone(zoneId).map { tool ->
            val config = JSONObject(tool.config_json)
            val extra = config.optJSONArray("extra_fields")?.let { fields ->
                (0 until fields.length()).map { fields.getJSONObject(it) }.map { mapOf("name" to it.optString("name"), "display_name" to it.optString("display_name"), "type" to it.optString("type")) }
            }.orEmpty()
            Tile(config.optString("group").takeIf { it.isNotEmpty() }, tool.grid_x, tool.grid_y, look("tool", tool.id, config.optString("name"), config.optString("display_mode"),
                config.optString("icon_name").takeIf { it.isNotEmpty() }, config.optString(IconColor.KEY).takeIf { it.isNotEmpty() }, config.optString(Texture.KEY).takeIf { it.isNotEmpty() },
                buildMap<String, Any?> {
                    put("tooltype", tool.tooltype)
                    config.optString("description").takeIf { it.isNotEmpty() }?.let { put("description", it) }
                    if (extra.isNotEmpty()) put("extra_fields", extra)
                }))
        }
        val variables = database.variableDao().getByZone(zoneId).map {
            Tile(it.group, it.gridX, it.gridY, look("variable", it.id, it.label, it.displayMode, it.iconName, it.iconColor, it.texture, mapOf("variable_name" to it.name)))
        }
        val routines = database.aiDao().getRoutinesByZone(zoneId).map {
            Tile(it.group, it.gridX, it.gridY, look("routine", it.id, it.name, it.displayMode, it.iconName, it.iconColor, it.texture, mapOf("is_enabled" to it.isEnabled)))
        }
        val tasks = database.taskDao().getByZone(zoneId).map {
            Tile(it.group, it.gridX, it.gridY, look("task", it.id, it.name, it.displayMode, it.iconName, it.iconColor, it.texture, mapOf("is_enabled" to it.isEnabled)))
        }
        val readouts = database.readoutDao().getByZone(zoneId).map {
            Tile(it.group, it.gridX, it.gridY, look("readout", it.id, it.name, it.displayMode, it.iconName, it.iconColor, it.texture))
        }
        return tools + variables + routines + tasks + readouts
    }

    companion object {
        /** [zones] in the home screen's order: by section, its groups' then the ungrouped, each in reading order. */
        fun ordered(zones: List<Zone>, zoneGroups: List<String>): List<Zone> {
            val sections = zoneGroups.map { it as String? } + null
            return zones.sortedWith(compareBy<Zone>({ sections.indexOf(ZonePositions.section(it.group, zoneGroups)) }, { it.grid_y }, { it.grid_x }))
        }
    }
}
