package org.yatt.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IdMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IdMappingEntity)

    @Query("SELECT serverId FROM sync_id_mapping WHERE localId = :localId LIMIT 1")
    suspend fun getServerId(localId: String): String?
}
