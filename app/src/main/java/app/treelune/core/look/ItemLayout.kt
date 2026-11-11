package app.treelune.core.look

import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.settings.SettingNode
import org.json.JSONObject

/**
 * How a tile's body lays the elements it lists (docs/design/tile-sizes.md, « Le corps »): an
 * element's width, by halves (2 cells, two columns in a tile 4 wide, one in a tile 2 wide) or the
 * whole width; and its lines of text, 1 to [MAX_LINES], measured at display in the theme's text
 * and the size setting — never fractions of a cell. How many elements show follows from the
 * tile's size (TileGrid).
 */
data class ItemLayout(val width: ItemWidth, val lines: Int) {

    /** The columns a body [roomWidth] cells wide lays these elements on. */
    fun columns(roomWidth: Int): Int = if (width == ItemWidth.HALVES && roomWidth >= TileSize.FULL) 2 else 1

    companion object {
        const val WIDTH = "item_width"
        const val LINES = "item_lines"
        const val MAX_LINES = 10
    }
}

/** An element's width: two cells, or the whole width of the body. */
enum class ItemWidth(val stored: String) {
    HALVES("halves"),
    FULL("full");

    companion object {
        fun of(stored: String): ItemWidth? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * What a tool type's elements accept (ToolTypeContract.getItemSpec): the widths and the lines a
 * person may choose for its tile, and the ones a new tool takes. A setting with one value only is
 * not offered.
 */
data class ItemSpec(val widths: List<ItemWidth>, val defaultWidth: ItemWidth, val lines: IntRange, val defaultLines: Int) {

    init {
        require(defaultWidth in widths) { "Default width $defaultWidth not among $widths" }
        require(defaultLines in lines && lines.first >= 1 && lines.last <= ItemLayout.MAX_LINES) { "Lines $defaultLines out of $lines" }
    }

    /** The layout a tool's [config] chooses; what it leaves out is this spec's default. */
    fun read(config: JSONObject): ItemLayout = ItemLayout(
        width = config.optString(ItemLayout.WIDTH).takeIf { it.isNotEmpty() }?.let { requireNotNull(ItemWidth.of(it)) { "No element width '$it'" } } ?: defaultWidth,
        lines = if (config.has(ItemLayout.LINES)) (config.get(ItemLayout.LINES) as Number).toInt() else defaultLines
    )

    /** The settings a person chooses them with, each with its default present: only those with more than one value. */
    fun nodes(text: (String) -> String): List<SettingNode> = buildList {
        if (widths.size > 1) {
            val stored = widths.map { it.stored }
            add(SettingNode.Field(FieldDefinition(ItemLayout.WIDTH, text("tools_config_label_item_width"), text("tools_base_schema_config_item_width"), FieldType.CHOICE, false,
                mapOf("options" to ChoiceSettings.storedOptions(stored, widths.associate { it.stored to text("item_width_${it.stored}") }))), default = defaultWidth.stored))
        }
        if (lines.first != lines.last) {
            add(SettingNode.Field(FieldDefinition(ItemLayout.LINES, text("tools_config_label_item_lines"), text("tools_base_schema_config_item_lines"), FieldType.NUMERIC, false,
                mapOf("min" to lines.first, "max" to lines.last, "decimals" to 0)), default = defaultLines))
        }
    }

    companion object {
        /** Elements on whole lines, two lines each, every width and 1 to 10 lines open: a journal, messages, an agenda. */
        val LINES = ItemSpec(listOf(ItemWidth.FULL, ItemWidth.HALVES), ItemWidth.FULL, 1..ItemLayout.MAX_LINES, 2)

        /**
         * Elements that are one line each — a goal's criterion, a deck's group, a past session —, on
         * whole lines, one line each by default: given two, they would stand apart for nothing.
         */
        val ONE_LINE = ItemSpec(listOf(ItemWidth.FULL, ItemWidth.HALVES), ItemWidth.FULL, 1..ItemLayout.MAX_LINES, 1)
    }
}
