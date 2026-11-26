package app.treelune.core.variables

import app.treelune.core.look.LookSpec

/**
 * A variable's look (Looks): its label, the name its tile shows (`name` being the one formulas
 * write), an icon always, `variable` until one is chosen, its tile among its section's tools
 * from ICON to CONDENSED, its value shown in LINE and CONDENSED.
 */
object VariableLook {

    const val DEFAULT_ICON = "variable"

    /** Where the icon picker starts for a variable: what one counts or aims at. */
    val SUGGESTED_ICONS = listOf("variable", "target", "gauge", "calculator", "sigma", "trending-up", "scale", "flame", "clock", "wallet", "percent", "hash")

    /**
     * How a variable of several values lays them on its tile (ItemLayout): by halves, its label
     * over its value, or on the whole width, side by side, the default; one line each, never offered.
     */
    val ITEMS = app.treelune.core.look.ItemSpec(listOf(app.treelune.core.look.ItemWidth.FULL, app.treelune.core.look.ItemWidth.HALVES),
        app.treelune.core.look.ItemWidth.FULL, 1..1, 1)

    /**
     * The layout of [stored], a variable's `item_width`; the default without one. By halves, a
     * value takes two lines, its label over it; on the whole width one, side by side.
     */
    fun items(stored: String?): app.treelune.core.look.ItemLayout {
        val read = ITEMS.read(org.json.JSONObject().apply { stored?.let { put(app.treelune.core.look.ItemLayout.WIDTH, it) } })
        return if (read.width == app.treelune.core.look.ItemWidth.HALVES) read.copy(lines = 2) else read
    }

    val SPEC = LookSpec(
        nameKey = "label",
        nameLabel = "variable_label_label",
        schemaPrefix = "variable_schema_",
        defaultSize = app.treelune.core.look.TileSize.of(4, 1),
        defaultIcon = DEFAULT_ICON,
        suggestedIcons = SUGGESTED_ICONS
    )
}
