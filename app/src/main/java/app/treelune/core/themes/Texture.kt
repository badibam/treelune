package app.treelune.core.themes

/**
 * A motif a zone's or a tool's tile and page header can carry (`texture`, docs/design/tool-texture.md):
 * a name the core fixes and each theme draws its own way, from its own cell of each
 * (TextureCell), at a tone of its own (TextureTones). ICON has no cell: the thing's icon is its cell.
 */
enum class Texture {
    LINES, DIAGONALS, GRID, ZIGZAG, WAVES,
    DOTS, CROSSES, STARS, RINGS, DIAMONDS, CONFETTI,
    CHECKER, BRICKS, SCALES,
    ICON;

    /** Whether a theme draws it from a cell file of its own: all but ICON. */
    val hasCell: Boolean get() = this != ICON

    companion object {

        /** The setting's key, in a zone's settings and in a tool's config alike. */
        const val KEY = "texture"

        /** The names a texture can take, in the order they are offered. */
        val NAMES: List<String> = entries.map { it.name }

        /** The texture of a stored name; null for none. A stored name is one of [NAMES], the services refusing any other. */
        fun of(stored: String?): Texture? = stored?.takeIf { it.isNotBlank() }?.let { valueOf(it) }

        /** The options of the setting, each its name and its label, as a CHOICE field stores them. */
        fun options(text: (String) -> String): List<Map<String, Any>> =
            app.treelune.core.fields.ChoiceSettings.storedOptions(NAMES, labels = NAMES.associateWith { text("texture_${it.lowercase()}") })

        /** Whether [given], a name about to be stored, is one of [NAMES] or none. */
        fun accepts(given: String?): Boolean = given.isNullOrBlank() || given in NAMES

        /**
         * Why [given] cannot be stored on a thing whose icon is [iconName], or null when it can:
         * a name that is not one of [NAMES], or ICON on a thing without an icon, which would have
         * nothing to draw. [text] gives the shared strings.
         */
        fun refusal(given: String?, iconName: String?, text: (String) -> String): String? = when {
            !accepts(given) -> text("service_error_texture_unknown").format(given, NAMES.joinToString(", "))
            given == ICON.name && iconName.isNullOrBlank() -> text("service_error_texture_icon_without_icon")
            else -> null
        }
    }
}

/** How strongly the motifs show, for the whole app (Appearance.textureIntensity): each theme has a tone of each motif for both. */
enum class TextureIntensity { SUBTLE, MARKED }

/** Where a motif is laid: a tile, or the header of the thing's page. */
enum class TextureSurface { TILE, HEADER }

/**
 * A thing's texture as a theme draws it: the motif, the icon an ICON motif repeats, and the
 * icon's colour, which the motif takes when there is one.
 */
data class ItemTexture(val texture: Texture, val iconName: String?, val color: TagColor?) {
    companion object {
        /** The texture stored as [stored] on a thing with [iconName] and [color]; null for none. */
        fun of(stored: String?, iconName: String?, color: TagColor?): ItemTexture? =
            Texture.of(stored)?.let { ItemTexture(it, iconName, color) }
    }
}
