package ru.zagrebin.culinaryblog.model

data class AdminPost(
    val id: Long,
    val title: String?,
    val status: String?,
    val postType: String?,
    val authorUsername: String?,
    val createdAt: String?,
    val updatedAt: String?
)
