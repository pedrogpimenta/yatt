package org.yatt.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey val id: String,
    val startTime: String,
    val endTime: String?,
    val tag: String?
)
