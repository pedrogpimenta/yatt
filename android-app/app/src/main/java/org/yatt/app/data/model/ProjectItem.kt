package org.yatt.app.data.model

/**
 * Unified project display model (id as String for both API and local).
 */
data class ProjectItem(
    val id: String,
    val name: String,
    val type: String?,
    val clientName: String?
) {
    fun formatLabel(): String {
        val parts = mutableListOf(name)
        type?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        clientName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" - ")
    }
}
