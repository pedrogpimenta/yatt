package org.yatt.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY startTime DESC")
    fun observeTimers(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers ORDER BY startTime DESC")
    suspend fun getTimers(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getTimer(id: String): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTimer(timer: TimerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTimers(timers: List<TimerEntity>)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteTimer(id: String)

    @Query("DELETE FROM timers")
    suspend fun clearTimers()

    @Query("UPDATE timers SET id = :newId WHERE id = :oldId")
    suspend fun updateTimerId(oldId: String, newId: String)

    @Query(
        """
        SELECT tag, MAX(startTime) as last_used
        FROM timers
        WHERE tag IS NOT NULL AND tag != ''
        GROUP BY tag
        ORDER BY last_used DESC
        """
    )
    suspend fun getTagUsage(): List<TagUsage>
}
