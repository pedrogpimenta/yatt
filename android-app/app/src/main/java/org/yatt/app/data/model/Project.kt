package org.yatt.app.data.model

data class Project(
    val id: Long,
    val name: String,
    val type: String?,
    val clientId: Long?,
    val clientName: String?
)
