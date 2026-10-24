package app.treelune.core.files

import androidx.room.*
import app.treelune.core.ai.database.AISessionEntity
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.database.entities.ToolInstance

/**
 * A file the app keeps, in a file of its own (Files), which this row describes (docs/DATA.md,
 * « FILE »): an image or a text joined to a message of a session, a file of an entry's FILE
 * fields, or one of a tool's config (a step's images). Each row has exactly one owner,
 * [sessionId], [entryId] or [toolInstanceId], never two, never none: the service that writes it
 * says which. Its row goes with its owner by cascade.
 *
 * Its kind and format are settled once, when the file is set aside; the path follows from the
 * owner's kind, the id and the format: none is stored. The file is written before the row and
 * deleted after it, so an orphan can only be a file, which the startup sweep removes; a row
 * without its file is an error, never repaired.
 */
@Entity(
    tableName = "files",
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
        ),
        ForeignKey(
            entity = ToolInstance::class,
            parentColumns = ["id"],
            childColumns = ["tool_instance_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id"]), Index(value = ["entry_id"]), Index(value = ["tool_instance_id"])]
)
data class FileEntity(
    @PrimaryKey val id: String,
    /** The session whose message joined it; null otherwise */
    @ColumnInfo(name = "session_id") val sessionId: String?,
    /** The entry whose FILE field holds it; null otherwise */
    @ColumnInfo(name = "entry_id") val entryId: String?,
    /** The tool whose config holds it in a FILE setting; null otherwise */
    @ColumnInfo(name = "tool_instance_id") val toolInstanceId: String?,
    /** A FileKind's name, which its format says too: kept for the queries by kind */
    val kind: String,
    /** A FileFormat's name: what its file is written as */
    val format: String,
    /** Its name on the phone, "aliments.csv", for a file that came with one; null for a photo */
    val name: String?,
    /** The size of its file */
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    /** An image's size in pixels; null for any other kind */
    val width: Int?,
    val height: Int?,
    /** An audio's or a video's length; null for any other kind */
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    /** A text's lines; null for any other kind */
    @ColumnInfo(name = "line_count") val lineCount: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long
) {
    init {
        require(listOfNotNull(sessionId, entryId, toolInstanceId).size == 1) { "File $id: one owner, a session, an entry or a tool" }
        require(FileFormat.valueOf(format).kind.name == kind) { "File $id: format $format is not of kind $kind" }
    }

    companion object {
        /** An image's row: its format says its kind. */
        fun image(id: String, sessionId: String?, entryId: String?, toolInstanceId: String?, format: FileFormat, sizeBytes: Long, width: Int, height: Int, createdAt: Long) =
            FileEntity(id, sessionId, entryId, toolInstanceId, FileKind.IMAGE.name, format.name, null, sizeBytes, width, height, null, null, createdAt)
    }
}

/** The format its file is written as. */
val FileEntity.fileFormat: FileFormat get() = FileFormat.valueOf(format)

/** Its kind. */
val FileEntity.fileKind: FileKind get() = FileKind.valueOf(kind)

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getById(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<FileEntity>

    @Query("SELECT * FROM files WHERE session_id = :sessionId")
    suspend fun getForSession(sessionId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE entry_id = :entryId")
    suspend fun getForEntry(entryId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE tool_instance_id = :toolInstanceId")
    suspend fun getForTool(toolInstanceId: String): List<FileEntity>

    /** Every file, for a backup and for the startup sweep to tell the files no row names. */
    @Query("SELECT * FROM files")
    suspend fun getAll(): List<FileEntity>

    /**
     * The entries' files their entry no longer names, in any of its fields: a field removed by a
     * change of config, or changed to another type, takes its values without a word to this table.
     * An id is a UUID, so finding it in the entry's text is finding it in a value.
     */
    @Query("""
        SELECT files.* FROM files JOIN tool_data ON tool_data.id = files.entry_id
        WHERE instr(tool_data.data, files.id) = 0 AND instr(COALESCE(tool_data.extra, ''), files.id) = 0
    """)
    suspend fun unnamedByTheirEntry(): List<FileEntity>

    /** The tools' files their tool's config no longer names: a setting removed, as unnamedByTheirEntry. */
    @Query("""
        SELECT files.* FROM files JOIN tool_instances ON tool_instances.id = files.tool_instance_id
        WHERE instr(tool_instances.config_json, files.id) = 0
    """)
    suspend fun unnamedByTheirTool(): List<FileEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun delete(id: String)
}
