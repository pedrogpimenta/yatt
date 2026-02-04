package org.yatt.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getQueue(): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    fun observeQueue(): Flow<List<SyncOperationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getCount(): Int
}
