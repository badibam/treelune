package app.treelune.core.events

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
 * An event of a zone (docs/CORE.md, « Events »): a name that a Task's step emits and that a Task
 * or a routine listens to, with the values it carries, declared (EventValues). It holds no data
 * of its own: it is wiring. Deleted with its zone; a write goes through the history's door (Writes).
 */
@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zone_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["zone_id"])]
)
data class EventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    /** Unique in its zone */
    val name: String,
    /** The values it carries, EventValues' stored form: `[]` when it carries none */
    @ColumnInfo(name = "values_json") val valuesJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** What it means, when it is emitted and what for: for the user and the AI alike; null for none */
    val description: String? = null
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events WHERE zone_id = :zoneId ORDER BY name COLLATE NOCASE")
    suspend fun getByZone(zoneId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    @Insert
    suspend fun insert(event: EventEntity)

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}
