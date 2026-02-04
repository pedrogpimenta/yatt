package org.yatt.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timerId: String?,
    val localId: String?,
    val data: String?,
    val timestamp: Long
)
