package app.treelune.core.apis

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * An API the user declared (docs/CORE.md, « APIs »): a name, unique, and its config (ApiSettings),
 * its key sealed (SecretSettings).
 */
@Entity(tableName = "apis", indices = [Index(value = ["name"], unique = true)])
data class ApiEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "config_json") val configJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface ApiDao {
    @Query("SELECT * FROM apis ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<ApiEntity>

    @Query("SELECT * FROM apis WHERE id = :id")
    suspend fun get(id: String): ApiEntity?

    @Insert
    suspend fun insert(api: ApiEntity)

    @Update
    suspend fun update(api: ApiEntity)

    @Query("DELETE FROM apis WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM apis")
    suspend fun deleteAll()
}
