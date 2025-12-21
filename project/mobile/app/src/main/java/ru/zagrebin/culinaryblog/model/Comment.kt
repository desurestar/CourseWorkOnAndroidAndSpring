package ru.zagrebin.culinaryblog.model

data class Comment(
    val id: Long,
    val postId: Long,
    val authorName: String,
    val authorId: Long? = null,
    val avatarUrl: String? = null,
    val message: String,
    val createdAt: String,
    val parentId: Long? = null
)
