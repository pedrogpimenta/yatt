package org.yatt.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists local_id -> server_id so STOP/UPDATE/DELETE sync ops can resolve
 * local ids when processed in a later run (e.g. after CREATE was synced and app restarted).
 */
@Entity(tableName = "sync_id_mapping")
data class IdMappingEntity(
    @PrimaryKey val localId: String,
    val serverId: String
)
