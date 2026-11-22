package app.treelune.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "zones")
data class Zone(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    /** A Lucide icon name, stored under its current name. Null until one is chosen. */
    val icon_name: String? = null,
    /** Its icon's colour, a name of IconColor.NAMES; null for a neutral icon. */
    val icon_color: String? = null,
    /** The motif of its tile and its page's header, a name of Texture.NAMES; null for none (docs/THEMES.md, « What a theme is in the app »). */
    val texture: String? = null,
    val active: Boolean = true,
    /** The size of its tile on the home screen (TileSize, ZoneSettings.LOOK). */
    val tile_size: String = "4x1",
    /** Its column and row in the grid of its zone group on the home screen, placed by ZonePositions. */
    val grid_x: Int,
    val grid_y: Int,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis(),

    /**
     * Tool groups defined at zone level (JSON array of group names)
     * Example: ["Santé", "Productivité", "Loisirs"]
     * Tool instances and routines can be assigned to these groups
     */
    val tool_groups: String? = null,

    /**
     * Zone group assignment (links to zone_groups in app_config)
     * Allows organizing zones into groups on MainScreen
     * Example: "Santé", "Productivité", null (ungrouped)
     */
    val group: String? = null,

    /**
     * Whether the zone's content is protected (docs/AI.md, « Levels »): the AI asks before it
     * creates, changes or deletes a tool of it, its tool groups, its routines or its variables.
     * Changed by a person alone (Protections).
     */
    val validate: Boolean = false,

    /**
     * The columns of the readouts gathered at its head and at its tool groups' heads
     * (ReadoutColumns): "one" or "two"; null follows the home screen's setting.
     */
    val readout_columns: String? = null,

    /**
     * The pieces its tile shows, in order, each with its summary or without (TilePieces,
     * docs/design/icon-tiles.md): a JSON array of `{"id", "summary"}`; null for none.
     */
    val tile_pieces: String? = null,
    /** Whether its tile draws its pieces in icons rather than in text (TilePieces.ICONS, ZoneTileBody). */
    val tile_pieces_icons: Boolean = false
)