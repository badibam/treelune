package app.treelune.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A variable of the core: a named value any reader reads (docs/DATA.md, « Variables »). It lives
 * in a zone, deleted with it, and reads any zone. Nothing of its value is stored: it is computed
 * each time it is read.
 *
 * @property name Unique in the app: a formula and the AI name it alone
 * @property label What its tile shows, free; never made from the name (VariableLook)
 * @property description What it is for, shown under its label on its page; null for none
 * @property group The zone's group it sits in, as a tool or a routine; null outside any
 * @property iconName A Lucide icon name, stored under its current name
 * @property iconColor Its icon's colour, a name of IconColor.NAMES; null for a neutral icon
 * @property texture The motif of its tile, a name of Texture.NAMES; null for none
 * @property displayMode How its tile shows: ICON, MINIMAL, LINE or CONDENSED (ZonePositions.MODES, as a zone)
 * @property gridX Its column in the grid of its group's section, among the tools (SectionPositions)
 * @property gridY Its row there
 * @property definitionJson `{"kind": "CONSTANT", "value", "field"}` or
 *   `{"kind": "FORMULA", "formula", "terms", "field"}`, checked by the variables service
 */
@Entity(
    tableName = "variables",
    foreignKeys = [
        ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zone_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["zone_id"]),
        Index(value = ["name"], unique = true)
    ]
)
data class VariableEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "group") val group: String?,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "icon_color") val iconColor: String?,
    @ColumnInfo(name = "texture") val texture: String?,
    @ColumnInfo(name = "display_mode") val displayMode: String,
    @ColumnInfo(name = "grid_x") val gridX: Int,
    @ColumnInfo(name = "grid_y") val gridY: Int,
    @ColumnInfo(name = "definition_json") val definitionJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
