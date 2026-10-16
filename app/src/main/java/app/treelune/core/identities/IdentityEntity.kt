package app.treelune.core.identities

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import org.json.JSONObject

/**
 * An identity as stored (Identity): its texts, every language's, in one JSON column.
 *
 * @property languagesJson `{"<language>": {"voice"?, "mind"?, "portrait"?: {"content", "address"},
 *   "resources": [{"key", "title", "description", "content", "address"}]}}`
 */
@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "icon_name") val iconName: String,
    @ColumnInfo(name = "icon_color") val iconColor: String?,
    val language: String,
    @ColumnInfo(name = "languages_json") val languagesJson: String,
    @ColumnInfo(name = "published_at") val publishedAt: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    fun toIdentity() = Identity(id, name, iconName, iconColor, language,
        Identity.languagesFromJson(JSONObject(languagesJson)), publishedAt, createdAt, updatedAt)

    companion object {
        fun of(identity: Identity) = IdentityEntity(identity.id, identity.name, identity.iconName, identity.iconColor,
            identity.language, Identity.languagesToJson(identity.languages).toString(), identity.publishedAt,
            identity.createdAt, identity.updatedAt)
    }
}

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<IdentityEntity>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getById(id: String): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: IdentityEntity)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun deleteById(id: String)
}
