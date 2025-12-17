package ru.zagrebin.culinaryblog.model

data class Comment(
    val id: Long,
    val postId: Long,
    val authorName: String,
    val message: String,
    val createdAt: String,
    val parentId: Long? = null
)
