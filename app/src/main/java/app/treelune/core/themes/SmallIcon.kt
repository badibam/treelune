package app.treelune.core.themes

/** An item's icon drawn smaller than a tile's (ThemeContract.SmallItemIcon): what it stands for sets how small. */
enum class SmallIcon {
    /** A piece on its zone's tile: two thirds of the zone's icon, which it must not be taken for. */
    PIECE,

    /** A readout's line, beside its text. */
    READOUT
}
