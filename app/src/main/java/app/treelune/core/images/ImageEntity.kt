package app.treelune.core.images

import androidx.room.*
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.database.entities.ToolDataEntity

/**
 * An image the app keeps, as a file of its own (Images), which this row describes: an image joined
 * to a message of a session, or one of an entry's IMAGE fields. Each row has exactly one owner,
 * [sessionId] or [entryId], never both, never none: the service that writes it says which. Its
 * row goes with its owner by cascade.
 *
 * The path follows from the owner's kind, the id and the format: none is stored. The file is
 * written before the row and deleted after it, so an orphan can only be a file, which the startup
 * sweep removes; a row without its file is an error, never repaired.
 */
@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = AISessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ToolDataEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id"]), Index(value = ["entry_id"])]
)
data class ImageEntity(
    @PrimaryKey val id: String,
    /** The session whose message joined it; null for an entry's image */
    @ColumnInfo(name = "session_id") val sessionId: String?,
    /** The entry whose IMAGE field holds it; null for a message's image */
    @ColumnInfo(name = "entry_id") val entryId: String?,
    /** An ImageFormat's name: what its file is written as */
    val format: String,
    /** The size of its file */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    init {
        require((sessionId == null) != (entryId == null)) { "Image $id: one owner, a session or an entry" }
    }
}

/** The format its file is written as. */
val ImageEntity.imageFormat: ImageFormat get() = ImageFormat.valueOf(format)

@Dao
interface ImageDao {
    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: String): ImageEntity?

    @Query("SELECT * FROM images WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ImageEntity>

    @Query("SELECT * FROM images WHERE session_id = :sessionId")
    suspend fun getForSession(sessionId: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE entry_id = :entryId")
    suspend fun getForEntry(entryId: String): List<ImageEntity>

    /** Every image, for a backup and for the startup sweep to tell the files no row names. */
    @Query("SELECT * FROM images")
    suspend fun getAll(): List<ImageEntity>

    /**
     * The entries' images their entry no longer names, in any of its fields: a field removed by a
     * change of config, or changed to another type, takes its values without a word to this table.
     * An id is a UUID, so finding it in the entry's text is finding it in a value.
     */
    @Query("""
        SELECT images.* FROM images JOIN tool_data ON tool_data.id = images.entry_id
        WHERE instr(tool_data.data, images.id) = 0 AND instr(COALESCE(tool_data.extra, ''), images.id) = 0
    """)
    suspend fun unnamedByTheirEntry(): List<ImageEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(image: ImageEntity)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun delete(id: String)
}
