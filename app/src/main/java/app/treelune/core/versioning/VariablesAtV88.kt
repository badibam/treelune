package app.treelune.core.versioning

import app.treelune.core.grid.Grid
import app.treelune.core.ui.DisplayMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the variables to their v88 form (docs/DATA.md, « Variables »): a variable is a tile of
 * its group's section, among the tools, as a tool is. It gains a look (Looks): a label, the text
 * its tile shows (its name copied, which the card showed until then), a description, a colour
 * and a texture (none), an icon (`variable`), a display mode (LINE); and its place in the section's grid, where its line in
 * the group's card (`order_index`, which goes) becomes a row: the variables one under the other,
 * under the section's tools, in their order.
 *
 * Shared by the database migration (STATEMENTS, [placed]), the backup import ([backup]) and the
 * history's copies ([copy]).
 */
object VariablesAtV88 {

    const val ICON = "variable"
    const val MODE = "LINE"

    /** The migration's statements around the rows written in between: the new table, then the old one replaced. */
    val CREATE = listOf(
        """
        CREATE TABLE IF NOT EXISTS variables_new (
            id TEXT NOT NULL,
            zone_id TEXT NOT NULL,
            name TEXT NOT NULL,
            label TEXT NOT NULL,
            description TEXT,
            `group` TEXT,
            icon_name TEXT NOT NULL,
            icon_color TEXT,
            texture TEXT,
            display_mode TEXT NOT NULL,
            grid_x INTEGER NOT NULL,
            grid_y INTEGER NOT NULL,
            definition_json TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(id),
            FOREIGN KEY(zone_id) REFERENCES zones(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()
    )

    val REPLACE = listOf(
        "DROP TABLE variables",
        "ALTER TABLE variables_new RENAME TO variables",
        "CREATE INDEX IF NOT EXISTS index_variables_zone_id ON variables(zone_id)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_variables_name ON variables(name)"
    )

    /** A tool as the placing reads it: its zone, its group, the first row under it. */
    data class ToolRow(val zoneId: String, val group: String?, val bottom: Int)

    /** A variable as v87 kept it, its line in its group's card. */
    data class Line(val id: String, val zoneId: String, val group: String?, val orderIndex: Int)

    /** The tool of [zoneId] whose config is [configJson], at row [gridY]. */
    fun toolRow(zoneId: String, configJson: String, gridY: Int): ToolRow {
        val config = JSONObject(configJson)
        val height = Grid.size(DisplayMode.valueOf(config.getString("display_mode"))).height
        return ToolRow(zoneId, config.optString("group").takeIf { it.isNotEmpty() }, gridY + height)
    }

    /**
     * The row of each variable of [lines], by id: one per row in their order, from the first row
     * under the tools of their section among [tools], column 0.
     */
    fun placed(lines: List<Line>, tools: List<ToolRow>): Map<String, Int> =
        lines.groupBy { it.zoneId to it.group }.flatMap { (section, variables) ->
            val start = tools.filter { (it.zoneId to it.group) == section }.maxOfOrNull { it.bottom } ?: 0
            variables.sortedBy { it.orderIndex }.mapIndexed { i, variable -> variable.id to start + i }
        }.toMap()

    /** A variable's row (column -> value) in its v88 form, at [row] of its grid. */
    fun copy(variable: JSONObject, row: Int): JSONObject = JSONObject(variable.toString()).apply {
        remove("order_index")
        if (!has("label")) put("label", getString("name"))
        if (!has("icon_name")) put("icon_name", ICON)
        if (!has("display_mode")) put("display_mode", MODE)
        if (!has("grid_x")) put("grid_x", 0)
        if (!has("grid_y")) put("grid_y", row)
    }

    /** The backup's [data] with its variables in their v88 form, placed under its tools. */
    fun backup(data: JSONObject) {
        val variables = data.optJSONArray("variables") ?: return
        val tools = data.optJSONArray("tool_instances") ?: JSONArray()
        val rows = (0 until tools.length()).map { tools.getJSONObject(it) }
            .map { toolRow(it.getString("zone_id"), it.getString("config_json"), it.getInt("grid_y")) }
        val list = (0 until variables.length()).map { variables.getJSONObject(it) }
        val placed = placed(list.map { Line(it.getString("id"), it.getString("zone_id"), it.optString("group").takeIf { g -> g.isNotEmpty() }, it.optInt("order_index")) }, rows)
        data.put("variables", JSONArray(list.map { copy(it, placed.getValue(it.getString("id"))) }))
    }
}
