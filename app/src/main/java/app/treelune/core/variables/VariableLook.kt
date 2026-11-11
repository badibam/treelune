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

    val SPEC = LookSpec(
        nameKey = "label",
        nameLabel = "variable_label_label",
        schemaPrefix = "variable_schema_",
        defaultSize = app.treelune.core.look.TileSize.of(4, 1),
        defaultIcon = DEFAULT_ICON,
        suggestedIcons = SUGGESTED_ICONS
    )
}
