package app.treelune.core.grid

/**
 * A section of a page's grid: a group's tiles ([group], null for the ungrouped ones), or, when
 * [hidden], that group's hidden part, a grid of its own just after it, where its hidden tiles stand
 * among themselves (docs/design/phone-inbox.md, « Cacher une tuile »). A hidden tile's place counts
 * in its hidden part, never in the visible grid, which changes without it.
 */
data class Section(val group: String?, val hidden: Boolean = false) {

    /** The section's key in the grid editor: its group, "" for the ungrouped one, marked for a hidden part. */
    val key: String get() = (if (hidden) HIDDEN_MARK else "") + (group ?: "")

    /** The visible part of the same group. */
    val shown: Section get() = copy(hidden = false)

    /** The hidden part of the same group. */
    val hiding: Section get() = copy(hidden = true)

    companion object {
        /** A character no group's name holds, the first of a hidden part's key. */
        private const val HIDDEN_MARK = "\u0001"

        fun ofKey(key: String): Section {
            val hidden = key.startsWith(HIDDEN_MARK)
            return Section(key.removePrefix(HIDDEN_MARK).ifEmpty { null }, hidden)
        }

        /** A page's sections in order, [groups] then the ungrouped one, each followed by its hidden part. */
        fun ordered(groups: List<String>): List<Section> =
            (groups.map { it as String? } + null).flatMap { listOf(Section(it), Section(it, hidden = true)) }
    }
}
