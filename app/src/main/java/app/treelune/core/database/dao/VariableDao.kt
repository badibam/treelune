package app.treelune.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.treelune.core.database.entities.VariableEntity

@Dao
interface VariableDao {
    @Query("SELECT * FROM variables ORDER BY zone_id ASC, grid_y ASC, grid_x ASC")
    suspend fun getAll(): List<VariableEntity>

    @Query("SELECT * FROM variables WHERE zone_id = :zoneId ORDER BY grid_y ASC, grid_x ASC")
    suspend fun getByZone(zoneId: String): List<VariableEntity>

    @Query("SELECT * FROM variables WHERE id = :id")
    suspend fun getById(id: String): VariableEntity?

    @Insert
    suspend fun insert(variable: VariableEntity)

    @Update
    suspend fun update(variable: VariableEntity)

    @Query("UPDATE variables SET grid_x = :gridX, grid_y = :gridY WHERE id = :id")
    suspend fun updatePosition(id: String, gridX: Int, gridY: Int)

    @Query("DELETE FROM variables WHERE id = :id")
    suspend fun deleteById(id: String)
}
