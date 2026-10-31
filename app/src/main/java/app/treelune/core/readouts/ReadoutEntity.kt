package app.treelune.core.readouts

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import app.treelune.core.database.entities.Zone

/**
 * A readout (docs/design/readouts.md): a sentence whose named values are read each time it is
 * shown, coloured by the first of its conditions that holds, in a zone's group, as a tile of its
 * section among the tools. Its sentence, its values, its target, its colours and the places it
 * rises to are its settings (ReadoutSettings), stored together; its look is in its own columns,
 * as a Task's, for the grid to read without them. Deleted with its zone, which the history reads
 * before (CoveredTable.ZONES).
 */
@Entity(
    tableName = "readouts",
    foreignKeys = [ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zone_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["zone_id"])]
)
data class ReadoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "icon_color") val iconColor: String?,
    val texture: String?,
    @ColumnInfo(name = "display_mode") val displayMode: String,
    val group: String?,
    @ColumnInfo(name = "grid_x") val gridX: Int,
    @ColumnInfo(name = "grid_y") val gridY: Int,
    /** Its sentence, values, target, colours and places, ReadoutSettings' stored form */
    @ColumnInfo(name = "settings_json") val settingsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface ReadoutDao {
    @Query("SELECT * FROM readouts WHERE zone_id = :zoneId ORDER BY grid_y ASC, grid_x ASC")
    suspend fun getByZone(zoneId: String): List<ReadoutEntity>

    @Query("SELECT * FROM readouts ORDER BY zone_id ASC, grid_y ASC, grid_x ASC")
    suspend fun getAll(): List<ReadoutEntity>

    @Query("SELECT * FROM readouts WHERE id = :id")
    suspend fun get(id: String): ReadoutEntity?

    @Insert
    suspend fun insert(readout: ReadoutEntity)

    @Update
    suspend fun update(readout: ReadoutEntity)

    @Query("UPDATE readouts SET grid_x = :gridX, grid_y = :gridY WHERE id = :id")
    suspend fun updatePosition(id: String, gridX: Int, gridY: Int)

    @Query("DELETE FROM readouts WHERE id = :id")
    suspend fun delete(id: String)
}
