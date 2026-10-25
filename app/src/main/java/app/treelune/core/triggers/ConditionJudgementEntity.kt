package app.treelune.core.triggers

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/**
 * The last judgement of a condition a trigger names, kept by what listens to it
 * (ConditionTriggers): one row per Task or routine and per condition, named by its stored text,
 * so that reordering the triggers keeps it and a condition changed starts a row of its own. Out
 * of backups, the trace of a moment: after a restore, each row starts again from a first
 * judgement, which starts nothing.
 */
@Entity(tableName = "condition_judgements", primaryKeys = ["owner_kind", "owner_id", "condition_key"])
data class ConditionJudgementEntity(
    /** What listens: [ConditionTriggers.TASK] or [ConditionTriggers.ROUTINE] */
    @ColumnInfo(name = "owner_kind") val ownerKind: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    /** The condition's stored text (Triggers.Trigger.ConditionBecameTrue.key) */
    @ColumnInfo(name = "condition_key") val conditionKey: String,
    /** True or false; null while no judgement answered (a value missing, a failure) */
    val verdict: Boolean?,
    @ColumnInfo(name = "judged_at") val judgedAt: Long,
    /** Why the last judgement failed, in words; null when it did not */
    val failure: String? = null,
    /** Since when it fails: its notification is sent once, when this is set */
    @ColumnInfo(name = "failed_since") val failedSince: Long? = null
)

@Dao
interface ConditionJudgementDao {
    @Query("SELECT * FROM condition_judgements")
    suspend fun getAll(): List<ConditionJudgementEntity>

    @Query("SELECT * FROM condition_judgements WHERE owner_kind = :ownerKind AND owner_id = :ownerId")
    suspend fun getByOwner(ownerKind: String, ownerId: String): List<ConditionJudgementEntity>

    @Upsert
    suspend fun upsert(judgement: ConditionJudgementEntity)

    @Query("DELETE FROM condition_judgements WHERE owner_kind = :ownerKind AND owner_id = :ownerId AND condition_key = :conditionKey")
    suspend fun delete(ownerKind: String, ownerId: String, conditionKey: String)
}
