package org.yatt.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey val id: String,
    val startTime: String,
    val endTime: String?,
    val tag: String?,
    val description: String? = null,
    @ColumnInfo(name = "project_id") val projectId: String? = null,
    @ColumnInfo(name = "project_name") val projectName: String? = null,
    @ColumnInfo(name = "client_name") val clientName: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)
