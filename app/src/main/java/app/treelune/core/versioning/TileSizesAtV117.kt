package app.treelune.core.versioning

import app.treelune.core.look.TileSize
import org.json.JSONObject

/**
 * Brings a tile's size to its v117 form (docs/design/tile-sizes.md): the display mode
 * (`display_mode`, ICON … FULL) becomes the tile size (`tile_size`, `icon`, `2x1` … `4xcontent`),
 * each mode the size it had (TileSize.FORMER_MODES). A zone, a variable, a routine, a Task and a
 * readout keep it in a column, a tool in its config. A List's short items become the lines of its
 * tile's elements.
 *
 * Shared by the database migration, the backup import and the history's copies.
 */
object TileSizesAtV117 {

    const val FORMER = "display_mode"
    const val KEY = "tile_size"

    /** The tables that keep it in a column. */
    val TABLES = listOf("zones", "variables", "routines", "tasks", "readouts")

    /** The size [mode] became; a name that was never a mode is a corrupt row, and fails. */
    fun size(mode: String): String = requireNotNull(TileSize.FORMER_MODES[mode]) { "No display mode '$mode'" }

    /**
     * [values] (a row, column -> value, or a tool's config) at v117, in place: its mode under the
     * size's key, as the size it had. One already at v117 is left as it is.
     */
    fun values(values: JSONObject): JSONObject {
        if (!values.has(FORMER)) return values
        values.put(KEY, size(values.getString(FORMER)))
        values.remove(FORMER)
        return values
    }

    /** A List's former setting: its tile gave each item one line rather than two. */
    const val SHORT_ITEMS = "short_items"

    /** The lines an element of a tile takes (ItemLayout.LINES). */
    const val ITEM_LINES = "item_lines"

    /**
     * A tool's config (its JSON) at v117, null when it holds neither a display mode nor a List's
     * short items: the mode as its size, short items as one line per element, two otherwise.
     */
    fun config(json: String): String? {
        val config = JSONObject(json)
        if (!config.has(FORMER) && !config.has(SHORT_ITEMS)) return null
        values(config)
        if (config.has(SHORT_ITEMS)) {
            config.put(ITEM_LINES, if (config.getBoolean(SHORT_ITEMS)) 1 else 2)
            config.remove(SHORT_ITEMS)
        }
        return config.toString()
    }

    /** A tool's row (column -> value) at v117: its config's mode. */
    fun tool(row: JSONObject): JSONObject {
        row.optString("config_json").takeIf { it.isNotEmpty() && !row.isNull("config_json") }
            ?.let { json -> config(json)?.let { row.put("config_json", it) } }
        return row
    }

    /** Rewrites the backup document's tiles in place: its zones', variables', routines', Tasks' and readouts' columns, its tools' configs. */
    fun backup(data: JSONObject) {
        TABLES.forEach { table ->
            val rows = data.optJSONArray(table) ?: return@forEach
            for (i in 0 until rows.length()) values(rows.getJSONObject(i))
        }
        val tools = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until tools.length()) tool(tools.getJSONObject(i))
    }
}
