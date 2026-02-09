package org.yatt.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String? = null,
    val clientName: String? = null,
    val updatedAt: String? = null
)
