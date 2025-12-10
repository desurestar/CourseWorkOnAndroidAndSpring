package ru.zagrebin.culinaryblog.model

import java.io.Serializable

data class PostCard(
    val id: Long,
    val title: String,
    val excerpt: String,
    val coverUrl: String?,
    val authorId: Long?,
    val postType: String?,
    val likesCount: Int,
    val cookingTimeMinutes: Int?,
    val calories: Int?,
    val authorName: String?,
    val publishedAt: String?,
    val tags: Set<String>?,
    val viewsCount: Long?
): Serializable
